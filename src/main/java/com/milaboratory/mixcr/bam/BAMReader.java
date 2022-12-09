/*
 * Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
package com.milaboratory.mixcr.bam;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.blocks.FilteringPort;
import cc.redberry.pipe.util.CountingOutputPort;
import com.milaboratory.core.io.sequence.*;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequenceQuality;
import com.milaboratory.core.sequence.SequencesUtils;
import com.milaboratory.primitivio.PrimitivIOStateBuilder;
import com.milaboratory.util.OutputPortWithProgress;
import com.milaboratory.util.TempFileManager;
import com.milaboratory.util.sorting.HashSorter;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static com.milaboratory.core.sequence.NucleotideSequence.ALPHABET;
import static com.milaboratory.core.sequence.SequenceQuality.BAD_QUALITY_VALUE;
import static com.milaboratory.core.sequence.SequenceQuality.GOOD_QUALITY_VALUE;

public class BAMReader implements SequenceReader<SequenceRead>, OutputPortWithProgress<SequenceRead> {
    private final AtomicLong numberOfProcessedAlignments = new AtomicLong(0);
    private final AtomicLong numberOfPairedReads = new AtomicLong(0);
    private final AtomicLong numberOfUnpairedReads = new AtomicLong(0);
    private SingleReadImpl currRead;
    private SingleReadImpl nextRead;
    private OutputPort<SingleReadImpl> singleReadImplOutputPort;
    private final CountingOutputPort<SAMRecord> progressChecker;
    private final SamReader[] readers;
    private final boolean replaceWildcards;

    public long getNumberOfProcessedAlignments() {
        return numberOfProcessedAlignments.get();
    }

    public long getNumberOfPairedReads() {
        return numberOfPairedReads.get();
    }

    public long getNumberOfUnpairedReads() {
        return numberOfUnpairedReads.get();
    }

    public BAMReader(String[] bamFiles, boolean dropNonVDJChromosomes, boolean replaceWildcards) {
        this(Stream.of(bamFiles).map(Paths::get).toArray(Path[]::new), dropNonVDJChromosomes, replaceWildcards);
    }

    public BAMReader(Path[] bamFiles, boolean dropNonVDJChromosomes, boolean replaceWildcards) {
        this.replaceWildcards = replaceWildcards;
        readers = new SamReader[bamFiles.length];
        for (int i = 0; i < bamFiles.length; i++) {
            readers[i] = SamReaderFactory.makeDefault().open(bamFiles[i]);
        }

        boolean sorted = (readers.length == 1) &&
                (readers[0].getFileHeader().getSortOrder() == SAMFileHeader.SortOrder.queryname);
        OutputPort<SAMRecord> samRecordOutputPort = new BAMConcat(readers);

        // Filtering redundant (alternative or supplementary) alignments
        FilteringPort<SAMRecord> filteredSamRecordOutputPort = new FilteringPort<>(samRecordOutputPort,
                rec -> (!rec.isSecondaryOrSupplementary()));
        if (dropNonVDJChromosomes) {
            filteredSamRecordOutputPort = new FilteringPort<>(filteredSamRecordOutputPort,
                    rec -> {
                        String refName = rec.getReferenceName();
                        return refName.length() < 9 || !refName.startsWith("NC_0000") ||
                                (refName.startsWith("07", 7) ||
                                        refName.startsWith("14", 7) ||
                                        refName.startsWith("02", 7) ||
                                        refName.startsWith("22", 7));
                    });
        }

        progressChecker = CountingOutputPort.wrap(filteredSamRecordOutputPort);

        // Converting SAMRecord to SingleRead
        singleReadImplOutputPort = CUtils.wrap(progressChecker, rec -> {
            if (rec.getReadNegativeStrandFlag()) {
                rec.reverseComplement(true);
            }

            NSequenceWithQuality nSeq = new NSequenceWithQuality(rec.getReadString(), rec.getBaseQualityString());
            if (replaceWildcards) {
                nSeq = replaceWildcards(nSeq);
            }

            // The Id of paired read is it order in pair {1, 2}, or {0} for unpaired
            return new SingleReadImpl(rec.getReadPairedFlag() ? (rec.getFirstOfPairFlag() ? 1 : 2) : 0,
                    nSeq,
                    rec.getReadName());
        });

        // Sorting reads by name if not sorted
        if (!sorted) {
            long memoryBudget = Runtime.getRuntime().maxMemory() > 10_000_000_000L ?
                    Runtime.getRuntime().maxMemory() / 4L :
                    1 << 28;
            PrimitivIOStateBuilder stateBuilder = new PrimitivIOStateBuilder();

            HashSorter<SingleReadImpl> sorter = new HashSorter<>(SingleReadImpl.class,
                    r -> r.getDescription().hashCode(),
                    (l, r) -> l.getDescription().equals(r.getDescription()) ?
                            Long.compare(l.getId(), r.getId()) :
                            l.getDescription().compareTo(r.getDescription()),
                    5,
                    TempFileManager.systemTempFolderDestination("mixcr.bam."),
                    4, 4,
                    stateBuilder.getOState(), stateBuilder.getIState(),
                    memoryBudget, 1 << 17);
            singleReadImplOutputPort = sorter.port(singleReadImplOutputPort);
        }

        currRead = singleReadImplOutputPort.take();
        nextRead = singleReadImplOutputPort.take();
    }

    private static NSequenceWithQuality replaceWildcards(NSequenceWithQuality r) {
        NucleotideSequence sequence = r.getSequence();

        byte[] quality = new byte[sequence.size()];
        for (int i = 0; i < quality.length; ++i)
            quality[i] = ALPHABET.isWildcard(sequence.codeAt(i)) ?
                    BAD_QUALITY_VALUE : GOOD_QUALITY_VALUE;
        return new NSequenceWithQuality(SequencesUtils.wildcardsToRandomBasic(sequence, sequence.hashCode()),
                new SequenceQuality(quality));
    }

    @Override
    public synchronized SequenceRead take() {
        SequenceRead retVal = null;
        if (currRead != null && nextRead != null) {
            boolean sameName = currRead.getDescription().equals(nextRead.getDescription());
            while (sameName && (currRead.getId() != 1 || nextRead.getId() != 2)) {
                System.out.println("Reads with name " + nextRead.getDescription() + " are duplicated. Skipping.");
                currRead = nextRead;
                numberOfProcessedAlignments.incrementAndGet();
                nextRead = singleReadImplOutputPort.take();
                if (nextRead == null) {
                    return currRead;
                }
                sameName = currRead.getDescription().equals(nextRead.getDescription());
            }
            if (sameName) {
                currRead = (SingleReadImpl) SequenceReadUtil.setReadId(numberOfPairedReads.get(), currRead);
                nextRead = (SingleReadImpl) SequenceReadUtil.setReadId(numberOfPairedReads.get(), nextRead);

                retVal = new PairedRead(currRead, nextRead);

                numberOfPairedReads.incrementAndGet();
                numberOfProcessedAlignments.incrementAndGet();
                currRead = singleReadImplOutputPort.take();
            } else {
                retVal = currRead;
                numberOfUnpairedReads.incrementAndGet();
                currRead = nextRead;
            }
            numberOfProcessedAlignments.incrementAndGet();
            nextRead = singleReadImplOutputPort.take();
        } else if (currRead != null) {
            numberOfUnpairedReads.incrementAndGet();
            numberOfProcessedAlignments.incrementAndGet();
            retVal = currRead;
            currRead = null;
        }
        return retVal;
    }

    @Override
    public void close() {
        for (SamReader reader : readers) {
            try {
                reader.close();
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public long getNumberOfReads() {
        return numberOfPairedReads.get() + numberOfUnpairedReads.get();
    }

    @Override
    public double getProgress() {
        return (numberOfPairedReads.get() * 2. + numberOfUnpairedReads.get()) / progressChecker.getCurrentIndex();
    }

    @Override
    public boolean isFinished() {
        return currRead == null;
    }
}
