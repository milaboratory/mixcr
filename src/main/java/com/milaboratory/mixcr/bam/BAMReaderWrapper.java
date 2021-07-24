package com.milaboratory.mixcr.bam;

import com.milaboratory.core.io.sequence.*;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequenceQuality;
import com.milaboratory.core.sequence.SequencesUtils;
import com.milaboratory.util.CanReportProgress;

import static com.milaboratory.core.sequence.NucleotideSequence.ALPHABET;
import static com.milaboratory.core.sequence.SequenceQuality.BAD_QUALITY_VALUE;
import static com.milaboratory.core.sequence.SequenceQuality.GOOD_QUALITY_VALUE;

public class BAMReaderWrapper implements SequenceReaderCloseable<SequenceRead>, CanReportProgress {

    private final BAMReader internalReader;

    public BAMReaderWrapper(BAMReader internalReader) {
        this.internalReader = internalReader;
    }

    @Override
    public double getProgress() {
        return internalReader.getProgress();
    }

    @Override
    public boolean isFinished() {
        return internalReader.isFinished();
    }

    @Override
    public void close() {
        internalReader.close();
    }

    @Override
    public long getNumberOfReads() {
        return internalReader.getNumberOfReads();
    }

    private SingleRead processRead(SingleRead read) {
        NSequenceWithQuality record = read.getData();
        NucleotideSequence sequence = record.getSequence();
        NSequenceWithQuality seq;

        byte[] quality = new byte[sequence.size()];
        for (int i = 0; i < quality.length; ++i)
            quality[i] = ALPHABET.isWildcard(sequence.codeAt(i)) ?
                    BAD_QUALITY_VALUE : GOOD_QUALITY_VALUE;
        seq = new NSequenceWithQuality(SequencesUtils.wildcardsToRandomBasic(sequence, read.getId()),
                new SequenceQuality(quality));

        return new SingleReadImpl(read.getId(), seq, read.getDescription());
    }

    @Override
    public synchronized SequenceRead take() {
        SequenceRead record = internalReader.take();
        if (record != null) {
            if (record instanceof SingleRead) {
                return processRead((SingleRead) record);
            } else if (record instanceof PairedRead) {
                return new PairedRead(processRead(record.getRead(0)), processRead(record.getRead(1)));
            }
        }
        return null;
    }
}
