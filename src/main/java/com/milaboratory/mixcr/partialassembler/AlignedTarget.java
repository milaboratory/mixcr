/*
 * Copyright (c) 2014-2016, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
package com.milaboratory.mixcr.partialassembler;

import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.reference.Allele;
import com.milaboratory.mixcr.reference.GeneType;
import gnu.trove.iterator.TObjectLongIterator;
import gnu.trove.map.TObjectLongMap;
import gnu.trove.map.hash.TObjectLongHashMap;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

public final class AlignedTarget {
    private final VDJCAlignments alignments;
    private final int targetId;
    private final String descriptionOverride;

    public AlignedTarget(VDJCAlignments alignments, int targetId) {
        this(alignments, targetId, null);
    }

    public AlignedTarget(VDJCAlignments alignments, int targetId, String descriptionOverride) {
        this.alignments = alignments;
        this.targetId = targetId;
        this.descriptionOverride = descriptionOverride;
    }

    public VDJCAlignments getAlignments() {
        return alignments;
    }

    public int getTargetId() {
        return targetId;
    }

    public NSequenceWithQuality getTarget() {
        return alignments.getTarget(targetId);
    }

    public EnumSet<GeneType> alignedGenes() {
        return extractExpectedGenes(targetId, alignments);
    }

    public AlignedTarget overrideDescription(String newDescription) {
        return new AlignedTarget(alignments, targetId, newDescription);
    }

    public String getDescription() {
        if (alignments.getDescriptions() != null && alignments.getDescriptions().length - 1 >= targetId &&
                alignments.getDescriptions()[targetId] != null)
            return alignments.getDescriptions()[targetId];
        return null;
    }

    public static List<AlignedTarget> orderTargets(List<AlignedTarget> targets) {
        // Selecting best alleles by total score
        EnumMap<GeneType, Allele> bestAlleles = new EnumMap<>(GeneType.class);
        for (GeneType geneType : GeneType.VDJC_REFERENCE) {
            TObjectLongMap<Allele> scores = new TObjectLongHashMap<>();
            for (AlignedTarget target : targets) {
                for (VDJCHit hit : target.getAlignments().getHits(geneType)) {
                    Alignment<NucleotideSequence> alignment = hit.getAlignment(target.getTargetId());
                    if (alignment != null)
                        scores.adjustOrPutValue(hit.getAllele(), (long) alignment.getScore(), (long) alignment.getScore());
                }
            }
            Allele bestAllele = null;
            long bestScore = Long.MIN_VALUE;
            TObjectLongIterator<Allele> it = scores.iterator();
            while (it.hasNext()) {
                it.advance();
                if (bestScore < it.value()) {
                    bestScore = it.value();
                    bestAllele = it.key();
                }
            }
            if (bestAllele != null)
                bestAlleles.put(geneType, bestAllele);
        }

        class Wrapper{

        }
    }

    public static EnumSet<GeneType> extractExpectedGenes(int targetId, VDJCAlignments alignments) {
        EnumSet<GeneType> gts = EnumSet.noneOf(GeneType.class);
        for (GeneType geneType : GeneType.VDJC_REFERENCE) {
            boolean present = false;
            for (VDJCHit vdjcHit : alignments.getHits(geneType)) {
                if (vdjcHit.getAlignment(targetId) != null) {
                    present = true;
                    break;
                }
            }
            if (present)
                gts.add(geneType);
        }
        if (gts.contains(GeneType.Variable) && gts.contains(GeneType.Joining))
            gts.add(GeneType.Diversity);
        return gts;
    }
}
