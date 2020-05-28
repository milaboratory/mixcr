/*
 * Copyright (c) 2014-2019, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
 * (here and after addressed as Inventors)
 * All Rights Reserved
 *
 * Permission to use, copy, modify and distribute any part of this program for
 * educational, research and non-profit purposes, by non-profit institutions
 * only, without fee, and without a written agreement is hereby granted,
 * provided that the above copyright notice, this paragraph and the following
 * three paragraphs appear in all copies.
 *
 * Those desiring to incorporate this work into commercial products or use for
 * commercial purposes should contact MiLaboratory LLC, which owns exclusive
 * rights for distribution of this program for commercial purposes, using the
 * following email address: licensing@milaboratory.com.
 *
 * IN NO EVENT SHALL THE INVENTORS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
 * SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
 * ARISING OUT OF THE USE OF THIS SOFTWARE, EVEN IF THE INVENTORS HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * THE SOFTWARE PROVIDED HEREIN IS ON AN "AS IS" BASIS, AND THE INVENTORS HAS
 * NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR
 * MODIFICATIONS. THE INVENTORS MAKES NO REPRESENTATIONS AND EXTENDS NO
 * WARRANTIES OF ANY KIND, EITHER IMPLIED OR EXPRESS, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY OR FITNESS FOR A
 * PARTICULAR PURPOSE, OR THAT THE USE OF THE SOFTWARE WILL NOT INFRINGE ANY
 * PATENT, TRADEMARK OR OTHER RIGHTS.
 */
package com.milaboratory.mixcr.basictypes;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.OutputPortCloseable;
import cc.redberry.pipe.util.CountingOutputPort;
import com.milaboratory.cli.AppVersionInfo;
import com.milaboratory.cli.PipelineConfiguration;
import com.milaboratory.cli.PipelineConfigurationWriter;
import com.milaboratory.mixcr.util.MiXCRVersionInfo;
import com.milaboratory.primitivio.PipeDataInputReader;
import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.PrimitivO;
import com.milaboratory.primitivio.blocks.PrimitivIOBlockHeader;
import com.milaboratory.primitivio.blocks.PrimitivOBlocks;
import com.milaboratory.primitivio.blocks.PrimitivOHybrid;
import com.milaboratory.util.CanReportProgressAndStage;
import com.milaboratory.util.ObjectSerializer;
import com.milaboratory.util.Sorter;
import com.milaboratory.util.io.HasPosition;
import gnu.trove.list.array.TLongArrayList;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGene;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ForkJoinPool;

/**
 * Writer for CLNA file format.
 *
 * Usage: 1. Constructor (opens the output file, buffered) 2. writeClones() 3. sortAlignments() 4.
 * writeAlignmentsAndIndex() 5. close()
 */
public final class ClnAWriter implements PipelineConfigurationWriter,
        AutoCloseable,
        CanReportProgressAndStage {
    static final String MAGIC_V5 = "MiXCR.CLNA.V05";
    static final String MAGIC = MAGIC_V5;
    static final int MAGIC_LENGTH = MAGIC.length(); //14

    /**
     * Separates blocks of alignments assigned to the same clonotype
     */
    static final PrimitivIOBlockHeader ALIGNMENT_BLOCK_SEPARATOR = PrimitivIOBlockHeader.specialHeader().setSpecialByte(0, (byte) 1);
    // /**
    //  * Added after the last alignments block
    //  */
    // static final PrimitivIOBlockHeader ALIGNMENTS_END = PrimitivIOBlockHeader.specialHeader().setSpecialByte(0, (byte) 2);
    // /**
    //  * Added after the clones block
    //  */
    // static final PrimitivIOBlockHeader CLONES_END = PrimitivIOBlockHeader.specialHeader().setSpecialByte(0, (byte) 3);

    /**
     * Will be used for alignments pre-sorting
     */
    private final File tempFile;
    /**
     * Used to read current position in output file
     */
    // private final CountingOutputStream outputStream;
    // private final PrimitivO output;
    private final PrimitivOHybrid output;
    private final PipelineConfiguration configuration;

    /**
     * Counter OP used to report progress during stage 2
     */
    private volatile CountingOutputPort<VDJCAlignments> toSorter;
    private volatile int numberOfClones = -1, numberOfClonesWritten = 0;
    private volatile OutputPortCloseable<VDJCAlignments> sortedAlignments = null;
    private volatile long numberOfAlignments = -1, numberOfAlignmentsWritten = 0;
    private volatile boolean clonesBlockFinished = false, finished = false;

    public ClnAWriter(PipelineConfiguration configuration, String fileName) throws IOException {
        this(configuration, new File(fileName));
    }

    public ClnAWriter(PipelineConfiguration configuration, File file) throws IOException {
        this.configuration = configuration;
        this.tempFile = new File(file.getAbsolutePath() + ".presorted");
        this.output = new PrimitivOHybrid(ForkJoinPool.commonPool(), file.toPath());
        try (PrimitivO o = this.output.beginPrimitivO()) {
            o.write(MAGIC.getBytes(StandardCharsets.US_ASCII));
        }
    }

    private long positionOfFirstClone = -1;

    private List<VDJCGene> usedGenes = null;
    private HasFeatureToAlign featureToAlignProvider = null;

    /**
     * Step 1
     */
    public synchronized void writeClones(CloneSet cloneSet) {
        // Checking state
        if (clonesBlockFinished)
            throw new IllegalArgumentException("Clone block was already written.");

        // Saving VDJC gene list
        this.usedGenes = cloneSet.getUsedGenes();

        // Writing header in raw primitivio mode
        try (PrimitivO o = this.output.beginPrimitivO(true)) {

            // Writing number of clones ahead of any other content to make it available
            // in a known file position (MAGIC_LENGTH)
            o.writeInt(cloneSet.size());

            // Writing version information
            o.writeUTF(MiXCRVersionInfo.get()
                    .getVersionString(AppVersionInfo.OutputType.ToFile));

            // Writing full pipeline configuration
            o.writeObject(configuration);

            // Writing aligner parameters
            Objects.requireNonNull(cloneSet.alignmentParameters);
            o.writeObject(cloneSet.alignmentParameters);
            featureToAlignProvider = cloneSet.alignmentParameters;

            // Writing assembler parameters
            o.writeObject(cloneSet.assemblerParameters);

            // During deserialization, the same procedure (in the same order) will be applied to
            // the PrimitivI object, so that correct singleton objects (GeneFeature objects and sequences) will be
            // deserialized from reference records.
            // The GeneFeature objects and corresponding nucleotide sequences from all
            // genes in analysis will be added to the set of known references of PrimitivO object
            // so that they will be serialized as 1-2 byte reference records (see PrimitivIO implementation).
            IOUtil.stdVDJCPrimitivOStateInit(o, usedGenes, cloneSet);

            // Saving stream position of the first clone object
            // this value will be written at the very end of the file
            positionOfFirstClone = output.getPosition();

            // Saving number of clones
            numberOfClones = cloneSet.size();
        }

        try (PrimitivOBlocks<Object>.Writer writer = this.output.beginPrimitivOBlocks(4, 1024)) {
            // Writing clones
            for (Clone clone : cloneSet) {
                writer.write(clone);
                // For progress reporting
                ++numberOfClonesWritten;
            }
            writer.flush();
            // writer.writeHeader(CLONES_END);
        } // will synchronize here (on close method invocation)

        // Setting flag telling other methods that clones block was written successfully
        clonesBlockFinished = true;
    }

    /**
     * Step 2
     */
    public synchronized void sortAlignments(OutputPort<VDJCAlignments> alignments,
                                            long numberOfAlignments) throws IOException {
        // Checking state
        if (!clonesBlockFinished)
            throw new IllegalStateException("Write clones before writing alignments.");
        if (sortedAlignments != null)
            throw new IllegalStateException("Alignments are already sorted.");

        // Saving number of alignments
        this.numberOfAlignments = numberOfAlignments;

        // Dirty heuristic to optimize trade-off between memory usage and number of random access places in a file
        // to read from
        int chunkSize = (int) Math.min(Math.max(16384, numberOfAlignments / 8), 1048576);

        // Sorting alignments by cloneId and then by mapping type (core alignments will be written before all others)
        // and saving sorting output port
        this.toSorter = new CountingOutputPort<>(alignments);
        this.sortedAlignments = Sorter.sort(
                toSorter,
                Comparator.comparingInt((VDJCAlignments o) -> o.cloneIndex).thenComparingInt(o -> o.mappingType),
                chunkSize,
                new VDJCAlignmentsSerializer(usedGenes, featureToAlignProvider),
                tempFile);
    }

    public static final class VDJCAlignmentsSerializer implements ObjectSerializer<VDJCAlignments> {
        private final List<VDJCGene> genes;
        private final HasFeatureToAlign featureToAlign;

        public VDJCAlignmentsSerializer(List<VDJCGene> genes, HasFeatureToAlign featureToAlign) {
            Objects.requireNonNull(genes);
            Objects.requireNonNull(featureToAlign);
            this.genes = genes;
            this.featureToAlign = featureToAlign;
        }

        @Override
        public void write(Collection<VDJCAlignments> data, OutputStream stream) {
            PrimitivO primitivO = new PrimitivO(stream);
            // Initializing PrimitivO object (see big comment block in writeClones(...) method
            IOUtil.registerGeneReferencesO(primitivO, genes, featureToAlign);
            for (VDJCAlignments alignments : data) {
                // Checking that alignments has the same alignedFeature as was in cloneSet
                assert Arrays.stream(GeneType.values())
                        .allMatch(gt -> Optional
                                .ofNullable(alignments.getBestHit(gt))
                                .map(VDJCHit::getAlignedFeature)
                                .map(f -> f.equals(featureToAlign.getFeatureToAlign(gt)))
                                .orElse(true));
                // Writing alignment
                primitivO.writeObject(alignments);
            }
            // Writing null in the end of the stream to detect end of block during deserialization
            primitivO.writeObject(null);
        }

        @Override
        public OutputPort<VDJCAlignments> read(InputStream stream) {
            PrimitivI primitivI = new PrimitivI(stream);
            IOUtil.registerGeneReferencesI(primitivI, genes, featureToAlign);
            // Will end on null object, that was added to the stream
            return new PipeDataInputReader<>(VDJCAlignments.class, primitivI);
        }
    }

    /**
     * Step 3
     */
    public synchronized void writeAlignmentsAndIndex() {
        // Checking state
        if (sortedAlignments == null)
            throw new IllegalStateException("Call sortAlignments before this method.");
        if (finished)
            throw new IllegalStateException("Writer already closed.");

        // Indices that will be written below all alignments
        final TLongArrayList aBlockOffset = new TLongArrayList();
        final TLongArrayList aBlockCount = new TLongArrayList();
        long previousAlsCount = 0;
        int currentCloneIndex = -1;
        long indexBeginOffset;

        try (PrimitivOBlocks<VDJCAlignments>.Writer o = output.beginPrimitivOBlocks(
                Math.min(4, Runtime.getRuntime().availableProcessors()), // TODO parametrize
                VDJCAlignmentsWriter.DEFAULT_ALIGNMENTS_IN_BLOCK)) {

            // Position of alignments with cloneIndex = -1 (not aligned alignments)
            aBlockOffset.add(o.getPosition());

            // List<VDJCAlignments> block = new ArrayList<>();
            // Writing alignments and writing indices
            for (VDJCAlignments alignments : CUtils.it(sortedAlignments)) {
                // End of clone
                if (currentCloneIndex != alignments.cloneIndex) {

                    // Async flush
                    o.flush();
                    o.writeHeader(ALIGNMENT_BLOCK_SEPARATOR);

                    // No synchronization here

                    ++currentCloneIndex;
                    if (currentCloneIndex != alignments.cloneIndex)
                        throw new IllegalArgumentException("No alignments for clone number " + currentCloneIndex);
                    if (alignments.cloneIndex >= numberOfClones)
                        throw new IllegalArgumentException("Out of range clone Index in alignment: " + currentCloneIndex);

                    // Write stream position as soon as all the blocks are flushed
                    // This will be the start position for the next block
                    o.run(c -> {
                        // In theory synchronization for aBlockOffset access here is not required as all the IO
                        // operations as well as this code are executed strictly sequentially

                        //synchronized (aBlockOffset){
                        aBlockOffset.add(((HasPosition) c).getPosition());
                        //}
                    });

                    aBlockCount.add(numberOfAlignmentsWritten - previousAlsCount);
                    previousAlsCount = numberOfAlignmentsWritten;
                }

                o.write(alignments);
                ++numberOfAlignmentsWritten;
            }

            // Writing last block, and waiting for all the data to be flushed
            o.flush();
            o.writeHeader(ALIGNMENT_BLOCK_SEPARATOR);
            // o.writeHeader(ALIGNMENTS_END);
            o.sync();

            // Writing position of last alignments block end
            aBlockOffset.add(o.getPosition());
            // o.close() will additionally write EOF header
            indexBeginOffset = o.getPosition() + PrimitivIOBlockHeader.HEADER_SIZE;
        }

        // Closing sorted output port, this will delete presorted file
        sortedAlignments.close();

        // Writing count of alignments in the last block
        aBlockCount.add(numberOfAlignmentsWritten - previousAlsCount);

        // To make counts index the same length as aBlockOffset
        aBlockCount.add(0);

        // Saving index offset in file to write in the end of the stream
        long previousValue = 0;

        try (PrimitivO o = output.beginPrimitivO()) {
            // Writing both indices
            for (int i = 0; i < aBlockOffset.size(); i++) {
                long iValue = aBlockOffset.get(i);
                // Writing offset index using deltas to save space
                // (smaller values are represented by less number of bytes in VarLong representation)
                o.writeVarLong(iValue - previousValue);
                previousValue = iValue;

                o.writeVarLong(aBlockCount.get(i));
            }

            // Writing two key positions in a file
            // This values will be using during deserialization to find certain blocks
            o.writeLong(positionOfFirstClone);
            o.writeLong(indexBeginOffset);

            // Writing end-magic as a file integrity sign
            o.write(IOUtil.getEndMagicBytes());
        }

        // Setting finished flag (will stop progress reporting)
        finished = true;
    }

    @Override
    public double getProgress() {
        if (!clonesBlockFinished)
            if (numberOfClones == -1)
                return Double.NaN;
            else
                return 1.0 * numberOfClonesWritten / numberOfClones;
        else if (sortedAlignments == null) {
            if (toSorter == null)
                return Double.NaN;
            else
                return 1.0 * toSorter.getCount() / numberOfAlignments;
        } else
            return 1.0 * numberOfAlignmentsWritten / numberOfAlignments;
    }

    @Override
    public String getStage() {
        if (!clonesBlockFinished)
            if (numberOfClones == -1)
                return "Initialization";
            else
                return "Writing clones";
        else if (sortedAlignments == null) {
            if (toSorter == null)
                return "Preparing for sorting";
            else
                return "Sorting alignments";
        } else
            return "Writing alignments";
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public void close() throws IOException {
        finished = true;
        output.close();
    }
}
