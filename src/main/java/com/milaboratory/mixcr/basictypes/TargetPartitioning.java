/*
 * Copyright (c) 2014-2015, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
 * commercial purposes should contact the Inventors using one of the following
 * email addresses: chudakovdm@mail.ru, chudakovdm@gmail.com
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
