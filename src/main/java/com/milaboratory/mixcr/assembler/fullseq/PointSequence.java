package com.milaboratory.mixcr.assembler.fullseq;

import com.milaboratory.core.sequence.NSequenceWithQuality;

/**
 *
 */
final class PointSequence {
    final int point;
    final NSequenceWithQuality sequence;
    final byte quality;

    PointSequence(int point, NSequenceWithQuality sequence, byte quality) {
        assert point >= 0;
        this.point = point;
        this.sequence = sequence;
        this.quality = quality;
    }

    @Override
    public String toString() {
        return String.format("%s->(%s, %s)", point, sequence.getSequence(), sequence.getQuality().meanValue());
    }
}
