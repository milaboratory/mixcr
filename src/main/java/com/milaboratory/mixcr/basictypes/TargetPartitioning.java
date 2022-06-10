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
package com.milaboratory.mixcr.basictypes;

import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.sequence.NucleotideSequence;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.ReferencePoint;
import io.repseq.core.SequencePartitioning;

import java.util.EnumMap;

public final class TargetPartitioning extends SequencePartitioning {
    final int targetIndex;
    final EnumMap<GeneType, VDJCHit> hits;

    public TargetPartitioning(int targetIndex, VDJCHit hit) {
        this.targetIndex = targetIndex;
        this.hits = new EnumMap<>(GeneType.class);
        this.hits.put(hit.getGeneType(), hit);
    }

    public TargetPartitioning(int targetIndex, EnumMap<GeneType, VDJCHit> hits) {
        this.targetIndex = targetIndex;
        this.hits = hits;
    }

    @Override
    protected Range getRange(GeneFeature.ReferenceRange refRange) {
        Range range = super.getRange(refRange);
        return range == null ? null :
                range.isReverse() ?
                        null : range;
    }

    @Override
    public boolean isReversed() {
        return false;
    }

    @Override
    protected int getLength(GeneFeature.ReferenceRange refRange) {
        Range range = getRange(refRange);
        return range == null ? -1 : range.length();
    }

    @Override
    protected boolean isAvailable(GeneFeature.ReferenceRange refRange) {
        Range range = getRange(refRange);
        return range != null;
    }

    @Override
    public int getPosition(ReferencePoint referencePoint) {
        VDJCHit hit = hits.get(referencePoint.getGeneType());
        if (hit == null)
            return -1;
        int position;
        if (referencePoint.isAttachedToAlignmentBound()) {
            int positionInSeq1;
            Alignment<NucleotideSequence> alignment = hit.getAlignment(targetIndex);
            if (alignment == null)
                return -1;

            int positionOfActivationPoint = -2;
            if (referencePoint.getActivationPoint() != null)
                positionOfActivationPoint = hit.getGene().getPartitioning()
                        .getRelativePosition(hit.getAlignedFeature(), referencePoint.getActivationPoint());

            if (referencePoint.isAttachedToLeftAlignmentBound()) {
                positionInSeq1 = alignment.getSequence1Range().getFrom();
                if (positionOfActivationPoint != -2 && (positionOfActivationPoint == -1 || positionInSeq1 > positionOfActivationPoint))
                    return -1;
            } else {
                positionInSeq1 = alignment.getSequence1Range().getTo();
                if (positionOfActivationPoint != -2 && (positionOfActivationPoint == -1 || positionInSeq1 < positionOfActivationPoint))
                    return -1;
            }
            positionInSeq1 += referencePoint.getOffset();
            position = alignment.convertToSeq2Position(positionInSeq1);
        } else
            position = hit.getPosition(targetIndex, referencePoint);
        if (position == -1)
            return -1;
        if (position < 0)
            return -2 - position;
        return position;
    }
}
