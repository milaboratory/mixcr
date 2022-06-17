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
