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

import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.SequenceHistory;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import gnu.trove.iterator.TObjectLongIterator;
import gnu.trove.map.TObjectLongMap;
import gnu.trove.map.hash.TObjectLongHashMap;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGene;

import java.util.*;

import static com.milaboratory.mixcr.partialassembler.BPoint.OverlapBegin;
import static com.milaboratory.mixcr.partialassembler.BPoint.OverlapEnd;

public final class AlignedTarget {
    private final VDJCAlignments alignments;
    private final int targetId;
    private final int[] bPoints;

    public AlignedTarget(VDJCAlignments alignments, int targetId) {
        this(alignments, targetId, null);
    }

    public AlignedTarget(VDJCAlignments alignments, int targetId, int[] bPoints) {
        this.alignments = alignments;
        this.targetId = targetId;
        this.bPoints = bPoints;
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

    public SequenceHistory getHistory() {
        return alignments.getHistory(targetId);
    }

    public AlignedTarget setBPoint(BPoint point, int value) {
        int[] newBPoints;
        if (bPoints == null) {
            if (value == -1)
                return this;
            newBPoints = new int[BPoint.values().length];
            Arrays.fill(newBPoints, -1);
        } else
            newBPoints = bPoints.clone();
        newBPoints[point.ordinal()] = value;
        return new AlignedTarget(alignments, targetId, newBPoints);
    }

    public AlignedTarget setBPoints(BPoint point1, int value1, BPoint point2, int value2) {
        int[] newBPoints;
        if (bPoints == null) {
            newBPoints = new int[BPoint.values().length];
            Arrays.fill(newBPoints, -1);
        } else
            newBPoints = bPoints.clone();
        newBPoints[point1.ordinal()] = value1;
        newBPoints[point2.ordinal()] = value2;
        return new AlignedTarget(alignments, targetId, newBPoints);
    }

    public int getBPoint(BPoint point) {
        return bPoints == null ? -1 : bPoints[point.ordinal()];
    }

    public static List<AlignedTarget> orderTargets(List<AlignedTarget> targets) {
        // Selecting best gene by total score
        final EnumMap<GeneType, VDJCGene> bestGenes = new EnumMap<>(GeneType.class);
        for (GeneType geneType : GeneType.VDJC_REFERENCE) {
            TObjectLongMap<VDJCGene> scores = new TObjectLongHashMap<>();
            for (AlignedTarget target : targets) {
                for (VDJCHit hit : target.getAlignments().getHits(geneType)) {
                    Alignment<NucleotideSequence> alignment = hit.getAlignment(target.getTargetId());
                    if (alignment != null)
                        scores.adjustOrPutValue(hit.getGene(), (long) alignment.getScore(), (long) alignment.getScore());
                }
            }
            VDJCGene bestGene = null;
            long bestScore = Long.MIN_VALUE;
            TObjectLongIterator<VDJCGene> it = scores.iterator();
            while (it.hasNext()) {
                it.advance();
                if (bestScore < it.value()) {
                    bestScore = it.value();
                    bestGene = it.key();
                }
            }
            if (bestGene != null)
                bestGenes.put(geneType, bestGene);
        }

        // Class to facilitate comparison between targets
        final class Wrapper implements Comparable<Wrapper> {
            final AlignedTarget target;
            final EnumMap<GeneType, Alignment<NucleotideSequence>> alignments = new EnumMap<>(GeneType.class);

            Wrapper(AlignedTarget target) {
                this.target = target;
                for (VDJCGene gene : bestGenes.values())
                    for (VDJCHit hit : target.getAlignments().getHits(gene.getGeneType()))
                        if (hit.getGene() == gene) {
                            Alignment<NucleotideSequence> alignment = hit.getAlignment(target.targetId);
                            if (alignment != null) {
                                alignments.put(gene.getGeneType(), alignment);
                                break;
                            }
                        }
            }

            GeneType firstAlignedGeneType() {
                for (GeneType geneType : GeneType.VDJC_REFERENCE)
                    if (alignments.containsKey(geneType))
                        return geneType;
                return null;
            }

            @Override
            public int compareTo(Wrapper o) {
                GeneType thisFirstGene = firstAlignedGeneType();
                GeneType otherFirstGene = o.firstAlignedGeneType();
                int cmp = Byte.compare(thisFirstGene.getOrder(), otherFirstGene.getOrder());
                return cmp != 0 ? cmp :
                        Integer.compare(
                                alignments.get(thisFirstGene).getSequence1Range().getLower(),
                                o.alignments.get(thisFirstGene).getSequence1Range().getLower());
            }
        }

        // Creating wrappers and sorting list
        List<Wrapper> wrappers = new ArrayList<>(targets.size());
        for (AlignedTarget target : targets) {
            Wrapper wrapper = new Wrapper(target);
            if (wrapper.firstAlignedGeneType() == null)
                continue;
            wrappers.add(wrapper);
        }
        Collections.sort(wrappers);

        // Creating result
        List<AlignedTarget> result = new ArrayList<>(wrappers.size());
        for (Wrapper wrapper : wrappers)
            result.add(wrapper.target);

        return result;
    }

    public EnumSet<GeneType> getExpectedGenes() {
        return extractExpectedGenes(targetId, alignments);
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

    public static AlignedTarget setOverlapRange(AlignedTarget target, int begin, int end) {
        return target.setBPoints(OverlapBegin, begin, OverlapEnd, end);
    }

    public static Range getOverlapRange(AlignedTarget target) {
        int begin = target.getBPoint(OverlapBegin);
        int end = target.getBPoint(OverlapEnd);
        if (begin == -1 || end == -1)
            return null;
        else
            return new Range(begin, end);
    }
}
