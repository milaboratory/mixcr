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
import com.milaboratory.core.sequence.NSequenceWithQuality;
import io.repseq.core.*;
import io.repseq.gen.VDJCGenes;


import java.util.*;

public class VDJCObject {
    protected final NSequenceWithQuality[] targets;
    protected final EnumMap<GeneType, VDJCHit[]> hits;
    protected volatile EnumMap<GeneType, Chains> allChains;
    protected VDJCPartitionedSequence[] partitionedTargets;

    public VDJCObject(EnumMap<GeneType, VDJCHit[]> hits, NSequenceWithQuality... targets) {
        this.targets = targets;
        this.hits = hits;

        // Sorting hits
        for (VDJCHit[] h : hits.values())
            Arrays.sort(h);
    }

    protected static EnumMap<GeneType, VDJCHit[]> createHits(VDJCHit[] vHits, VDJCHit[] dHits,
                                                             VDJCHit[] jHits, VDJCHit[] cHits) {
        EnumMap<GeneType, VDJCHit[]> hits = new EnumMap<GeneType, VDJCHit[]>(GeneType.class);
        if (vHits != null)
            hits.put(GeneType.Variable, vHits);
        if (dHits != null)
            hits.put(GeneType.Diversity, dHits);
        if (jHits != null)
            hits.put(GeneType.Joining, jHits);
        if (cHits != null)
            hits.put(GeneType.Constant, cHits);
        return hits;
    }

    @SuppressWarnings("unchecked")
    private Set<VDJCGeneId> getGenes(GeneType gt) {
        VDJCHit[] hits = getHits(gt);
        if (hits == null)
            return Collections.EMPTY_SET;
        Set<VDJCGeneId> genes = new HashSet<>();
        for (VDJCHit hit : hits)
            genes.add(hit.getGene().getId());
        return genes;
    }

    public final boolean hasCommonGenes(GeneType gt, VDJCObject other) {
        Set<VDJCGeneId> thisGenes = this.getGenes(gt);
        for (VDJCGeneId gene : other.getGenes(gt))
            if (thisGenes.contains(gene))
                return true;
        return false;
    }

    public final VDJCHit[] getHits(GeneType type) {
        VDJCHit[] hits = this.hits.get(type);
        return hits == null ? new VDJCHit[0] : hits;
    }

    public Chains getTopChain(GeneType gt) {
        final VDJCHit top = getBestHit(gt);
        if (top == null)
            return Chains.EMPTY;
        return top.getGene().getChains();
    }

    public Chains getAllChains(GeneType geneType) {
        if (allChains == null)
            synchronized (this) {
                if (allChains == null) {
                    allChains = new EnumMap<>(GeneType.class);
                    for (GeneType type : GeneType.VDJC_REFERENCE) {
                        Chains c = Chains.EMPTY;
                        VDJCHit[] hs = hits.get(type);
                        if (hs == null || hs.length == 0)
                            continue;
                        for (VDJCHit hit : hs)
                            c = c.merge(hit.getGene().getChains());
                        allChains.put(type, c);
                    }
                }
            }
        return allChains.get(geneType);
    }

    public final boolean isChimera() {
        return hasAnyHits() && commonChains().isEmpty();
    }

    public final boolean hasAnyHits() {
        for (GeneType gt : GeneType.values())
            if (getBestHit(gt) != null)
                return true;
        return false;
    }

    public final Chains commonChains() {
        Chains chains = Chains.ALL;
        boolean notNull = false;
        for (GeneType gt : GeneType.VJC_REFERENCE) {
            Chains c = getAllChains(gt);
            if (c == null)
                continue;
            notNull = true;//for safety
            chains = chains.intersection(c);
        }
        if (!notNull)//all null
            return Chains.EMPTY;
        return chains;
    }

    public final Chains commonTopChains() {
        Chains chains = Chains.ALL;
        boolean notNull = false;
        for (GeneType gt : GeneType.VJC_REFERENCE) {
            VDJCHit bestHit = getBestHit(gt);
            if (bestHit == null)
                continue;
            notNull = true;//for safety
            chains = chains.intersection(bestHit.getGene().getChains());
        }
        if (!notNull)//all null
            return Chains.EMPTY;
        return chains;
    }

    public final int numberOfTargets() {
        return targets.length;
    }

    public final NSequenceWithQuality getTarget(int target) {
        return targets[target];
    }

    public final NSequenceWithQuality[] getTargets() {
        return targets.clone();
    }

    public final VDJCPartitionedSequence getPartitionedTarget(int target) {
        if (partitionedTargets == null) {
            partitionedTargets = new VDJCPartitionedSequence[targets.length];
            EnumMap<GeneType, VDJCHit> topHits = new EnumMap<>(GeneType.class);
            for (GeneType geneType : GeneType.values()) {
                VDJCHit[] hits = this.hits.get(geneType);
                if (hits != null && hits.length > 0)
                    topHits.put(geneType, hits[0]);
            }
            for (int i = 0; i < targets.length; ++i)
                partitionedTargets[i] = new VDJCPartitionedSequence(targets[i], new TargetPartitioning(i, topHits));
        }
        return partitionedTargets[target];
    }

    public VDJCHit getBestHit(GeneType type) {
        VDJCHit[] hits = this.hits.get(type);
        if (hits == null || hits.length == 0)
            return null;
        return hits[0];
    }

    public VDJCGene getBestHitGene(GeneType type) {
        VDJCHit hit = getBestHit(type);
        return hit == null ? null : hit.getGene();
    }

    public VDJCGenes getBestHitGenes() {
        return new VDJCGenes(
                getBestHitGene(GeneType.Variable),
                getBestHitGene(GeneType.Diversity),
                getBestHitGene(GeneType.Joining),
                getBestHitGene(GeneType.Constant));
    }

    public final Range getRelativeRange(GeneFeature big, GeneFeature subfeature) {
        int targetIndex = getTargetContainingFeature(big);
        if (targetIndex == -1)
            return null;
        return getPartitionedTarget(targetIndex).getPartitioning().getRelativeRange(big, subfeature);
    }

    public final int getTargetContainingFeature(GeneFeature feature) {
        NSequenceWithQuality tmp;
        int targetIndex = -1, quality = -1;
        for (int i = 0; i < targets.length; ++i) {
            tmp = getPartitionedTarget(i).getFeature(feature);
            if (tmp != null && quality < tmp.getQuality().minValue())
                targetIndex = i;
        }
        return targetIndex;
    }

    public NSequenceWithQuality getFeature(GeneFeature geneFeature) {
        NSequenceWithQuality feature = null, tmp;
        for (int i = 0; i < targets.length; ++i) {
            tmp = getPartitionedTarget(i).getFeature(geneFeature);
            if (tmp != null && (feature == null || feature.getQuality().minValue() < tmp.getQuality().minValue()))
                feature = tmp;
        }
//        if (feature == null && targets.length == 2) {
//            VDJCHit bestVHit = getBestHit(GeneType.Variable);
//            if (bestVHit == null)
//                return null;
//
//            //TODO check for V feature compatibility
//            Alignment<NucleotideSequence>
//                    lAlignment = bestVHit.getAlignment(0),
//                    rAlignment = bestVHit.getAlignment(1);
//
//            if (lAlignment == null || rAlignment == null)
//                return null;
//
//            int lTargetIndex = 0;
//
//            int lFrom, rTo, f, t;
//            if ((f = getPartitionedTarget(1).getPartitioning().getPosition(geneFeature.getFirstPoint())) >= 0 &&
//                    (t = getPartitionedTarget(0).getPartitioning().getPosition(geneFeature.getLastPoint())) >= 0) {
//                lAlignment = bestVHit.getAlignment(1);
//                rAlignment = bestVHit.getAlignment(0);
//                lFrom = f;
//                rTo = t;
//                lTargetIndex = 1;
//            } else if ((f = getPartitionedTarget(0).getPartitioning().getPosition(geneFeature.getFirstPoint())) < 0 ||
//                    (t = getPartitionedTarget(1).getPartitioning().getPosition(geneFeature.getLastPoint())) < 0)
//                return null;
//            else {
//                lFrom = f;
//                rTo = t;
//            }
//
//            Range intersection = lAlignment.getSequence1Range().intersection(rAlignment.getSequence1Range());
//            if (intersection == null)
//                return null;
//
//            NSequenceWithQuality intersectionSequence = Merger.merge(intersection,
//                    new Alignment[]{bestVHit.getAlignment(0), bestVHit.getAlignment(1)},
//                    targets);
//
//            Range lRange = new Range(
//                    lFrom,
//                    aabs(lAlignment.convertToSeq2Position(intersection.getFrom())));
//            Range rRange = new Range(
//                    aabs(rAlignment.convertToSeq2Position(intersection.getTo())),
//                    rTo);
//
//            feature =
//                    new NSequenceWithQualityBuilder()
//                            .ensureCapacity(lRange.length() + rRange.length() + intersectionSequence.size())
//                            .append(targets[lTargetIndex].getRange(lRange))
//                            .append(intersectionSequence)
//                            .append(targets[1 - lTargetIndex].getRange(rRange))
//                            .createAndDestroy();
//        }
        return feature;
    }

    private static int aabs(int position) {
        if (position == -1)
            return -1;
        if (position < 0)
            return -2 - position;
        return position;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VDJCObject)) return false;

        VDJCObject that = (VDJCObject) o;

        if (that.hits.size() != this.hits.size()) return false;

        for (Map.Entry<GeneType, VDJCHit[]> entry : this.hits.entrySet()) {
            VDJCHit[] thatHits = that.hits.get(entry.getKey());
            if (!Arrays.equals(entry.getValue(), thatHits))
                return false;
        }

        if (!Arrays.equals(targets, that.targets)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(targets);
        result = 31 * result + hits.hashCode();
        return result;
    }
}
