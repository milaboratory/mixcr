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

import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.sequence.*;
import io.repseq.core.*;
import io.repseq.gen.VDJCGenes;

import java.util.*;
import java.util.stream.Collectors;

import static com.milaboratory.core.alignment.Alignment.aabs;
import static com.milaboratory.util.StreamUtil.noMerge;

public abstract class VDJCObject {
    protected final NSequenceWithQuality[] targets;
    protected final EnumMap<GeneType, VDJCHit[]> hits;
    protected volatile EnumMap<GeneType, Chains> allChains;
    protected VDJCPartitionedSequence[] partitionedTargets;
    protected final TagCounter tagCounter;

    public VDJCObject(EnumMap<GeneType, VDJCHit[]> hits, TagCounter tagCounter, NSequenceWithQuality... targets) {
        this.targets = targets;
        this.hits = hits;
        this.tagCounter = tagCounter;

        // Sorting hits
        for (VDJCHit[] h : hits.values())
            Arrays.sort(h);
    }

    public TagCounter getTagCounter() {
        return tagCounter;
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

    public boolean isAvailable(GeneFeature geneFeature) {
        for (int i = 0; i < targets.length; ++i)
            if (getPartitionedTarget(i).getPartitioning().isAvailable(geneFeature))
                return true;
        return false;
    }

    public boolean isAvailable(ReferencePoint referencePoint) {
        for (int i = 0; i < targets.length; ++i)
            if (getPartitionedTarget(i).getPartitioning().isAvailable(referencePoint))
                return true;
        return false;
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

    public EnumMap<GeneType, VDJCHit[]> getHits() {
        return hits;
    }

    public final VDJCHit[] getHits(GeneType type) {
        VDJCHit[] hits = this.hits.get(type);
        return hits == null ? new VDJCHit[0] : hits;
    }

    public final EnumMap<GeneType, VDJCHit[]> getHitsMap() {
        return hits.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().clone(),
                        noMerge(),
                        () -> new EnumMap<>(GeneType.class)));
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

    public final VDJCPartitionedSequence getPartitionedTarget(int target, VDJCGene vGene, VDJCGene dGene, VDJCGene jGene, VDJCGene cGene) {
        EnumMap<GeneType, VDJCGene> genes = new EnumMap<>(GeneType.class);
        if (vGene != null)
            genes.put(GeneType.Variable, vGene);
        if (dGene != null)
            genes.put(GeneType.Diversity, dGene);
        if (jGene != null)
            genes.put(GeneType.Joining, jGene);
        if (cGene != null)
            genes.put(GeneType.Constant, cGene);
        return getPartitionedTarget(target, genes);
    }

    public final VDJCPartitionedSequence getPartitionedTarget(int target, EnumMap<GeneType, VDJCGene> genes) {
        EnumMap<GeneType, VDJCHit> topHits = new EnumMap<>(GeneType.class);
        for (GeneType geneType : GeneType.values()) {
            if (!genes.containsKey(geneType))
                continue;
            VDJCHit[] hits = this.hits.get(geneType);
            if (hits == null)
                continue;
            Arrays.stream(hits)
                    .filter(hit -> hit.getGene().equals(genes.get(geneType)))
                    .findFirst()
                    .ifPresent(vdjcHit -> topHits.put(geneType, vdjcHit));
        }
        return new VDJCPartitionedSequence(targets[target], new TargetPartitioning(target, topHits));
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
        int tcf = getTargetContainingFeature(geneFeature);
        return tcf == -1 ? null : getPartitionedTarget(tcf).getFeature(geneFeature);
    }

    public NucleotideSequence getNFeature(GeneFeature geneFeature) {
        int tcf = getTargetContainingFeature(geneFeature);
        return tcf == -1 ? null : getPartitionedTarget(tcf).getFeature(geneFeature).getSequence();
    }

    public AminoAcidSequence getAAFeature(GeneFeature geneFeature) {
        int tcf = getTargetContainingFeature(geneFeature);
        if (tcf == -1)
            return null;
        VDJCPartitionedSequence target = getPartitionedTarget(tcf);
        TranslationParameters tp = target.getPartitioning().getTranslationParameters(geneFeature);
        return tp == null
                ? null
                : AminoAcidSequence.translate(
                target.getFeature(geneFeature).getSequence(), tp); // target.getFeature(geneFeature) uses exactly the same algorithm, guaranteed to be non-null at this point
    }

    public AminoAcidSequence getFeatureAA(GeneFeature geneFeature) {
        NSequenceWithQuality feature = getFeature(geneFeature);
        if (feature == null)
            return null;

        int targetId = getTargetContainingFeature(geneFeature);
        TranslationParameters tr = targetId == -1 ?
                TranslationParameters.FromLeftWithIncompleteCodon
                : getPartitionedTarget(targetId).getPartitioning().getTranslationParameters(geneFeature);
        if (tr == null)
            return null;
        return AminoAcidSequence.translate(feature.getSequence(), tr);
    }


    public int ntLengthOf(GeneFeature gf) {
        NSequenceWithQuality f = getFeature(gf);
        if (f == null)
            return -1;
        return f.size();
    }

    public int aaLengthOf(GeneFeature gf) {
        AminoAcidSequence f = getFeatureAA(gf);
        if (f == null)
            return -1;
        return f.size();
    }

    public CaseSensitiveNucleotideSequence getIncompleteFeature(GeneFeature geneFeature) {
        NSequenceWithQuality feature = getFeature(geneFeature);
        if (feature != null) {
            int iTarget = getTargetContainingFeature(geneFeature);
            return new CaseSensitiveNucleotideSequence(
                    feature.getSequence(),
                    false,
                    getPartitionedTarget(iTarget).getPartitioning(),
                    getPartitionedTarget(iTarget).getPartitioning().getTranslationParameters(geneFeature));
        }

        CaseSensitiveNucleotideSequenceBuilder builder = new CaseSensitiveNucleotideSequenceBuilder(new ArrayList<>(), new BitSet());
        // reference points for resulting sequence
        ExtendedReferencePointsBuilder partitioningBuilder = new ExtendedReferencePointsBuilder();

        // iterate over primitive features that constitute the given `geneFeature`
        for (GeneFeature.ReferenceRange rr : geneFeature) {
            ReferencePoint left = rr.begin, right = rr.end;

            if (left.getGeneType() != right.getGeneType())
                if (left.getGeneType() != GeneType.Variable || right.getGeneType() != GeneType.Joining)
                    throw new IllegalArgumentException();

            if (left.hasNoOffset())
                partitioningBuilder.setPosition(left, builder.size());

            GeneFeature primitiveFeature = new GeneFeature(left, right);
            // check whether primitive feature is already available
            NSequenceWithQuality seq = getFeature(primitiveFeature);
            if (seq != null) {
                builder.add(seq.getSequence(), false);
                if (right.hasNoOffset())
                    partitioningBuilder.setPosition(right, builder.size());
                continue;
            }

            VDJCHit[]
                    lHits = hits.get(left.getGeneType()),
                    rHits = hits.get(right.getGeneType());
            if (lHits == null || lHits.length == 0 || rHits == null || rHits.length == 0)
                return null;

            // left and right top hits
            VDJCHit
                    lHit = lHits[0],
                    rHit = rHits[0];

            int
                    lPositionInRef = lHit.getGene().getPartitioning().getRelativePosition(lHit.getAlignedFeature(), left),
                    rPositionInRef = rHit.getGene().getPartitioning().getRelativePosition(rHit.getAlignedFeature(), right);

            if (lPositionInRef < 0 || rPositionInRef < 0)
                return null;

            // left parts
            List<IncompleteSequencePart> leftParts = new ArrayList<>();
            int positionInRef = lPositionInRef;
            while (true) {
                int iLeftTarget = -1; // target that contains the left ref point

                // find the closest targets to the right and left points
                for (int i = 0; i < numberOfTargets(); ++i) {
                    Alignment<NucleotideSequence> lAl = lHit.getAlignment(i);
                    // check that there is no any unaligned piece
                    if (lAl != null
                            && positionInRef < lAl.getSequence1Range().getFrom()
                            && lAl.getSequence2Range().getFrom() != 0)
                        return null;

                    // select the closest target to the right of left point
                    if (lAl != null
                            && positionInRef < lAl.getSequence1Range().getTo() // getTo is exclusive
                            && (lHit != rHit || lAl.getSequence1Range().getFrom() <= rPositionInRef))
                        if (iLeftTarget == -1
                                || lAl.getSequence1Range().getFrom() < lHit.getAlignment(iLeftTarget).getSequence1Range().getFrom())
                            iLeftTarget = i;
                }

                if (iLeftTarget == -1)
                    break;

                Alignment<NucleotideSequence> lAl = lHit.getAlignment(iLeftTarget);
                if (!lAl.getSequence1Range().contains(positionInRef)) {
                    // add lowercase piece of germline
                    assert lAl.getSequence1Range().getFrom() > positionInRef;
                    if (leftParts.isEmpty() && lAl.getSequence1Range().getFrom() == rPositionInRef)
                        break;
                    IncompleteSequencePart part = new IncompleteSequencePart(lHit, true, iLeftTarget, positionInRef, lAl.getSequence1Range().getFrom());
                    if (part.begin != part.end)
                        leftParts.add(part);
                    positionInRef = lAl.getSequence1Range().getFrom();
                }
                assert lAl.getSequence1Range().containsBoundary(positionInRef);

                IncompleteSequencePart part = new IncompleteSequencePart(lHit, false, iLeftTarget,
                        aabsLeft(positionInRef, lAl),
                        lAl.getSequence2Range().getTo());
                if (part.begin != part.end)
                    leftParts.add(part);

                positionInRef = lAl.getSequence1Range().getTo();
            }

            // right parts (reversed)
            List<IncompleteSequencePart> rightParts = new ArrayList<>();
            positionInRef = rPositionInRef;
            while (true) {
                int iRightTarget = -1; // target that contains the left ref point

                // find the closest targets to the right and left points
                for (int i = 0; i < numberOfTargets(); ++i) {
                    Alignment<NucleotideSequence> rAl = rHit.getAlignment(i);

                    // check that there is no any unaligned piece
                    if (rAl != null
                            && rAl.getSequence1Range().getTo() < positionInRef
                            && rAl.getSequence2Range().getTo() != getTarget(i).size())
                        return null;

                    // select the closest target to the left of right point
                    if (rAl != null
                            && rAl.getSequence1Range().getFrom() < positionInRef // getFrom is inclusive
                            && (lHit != rHit || rAl.getSequence1Range().getTo() > lPositionInRef)) {
                        if (iRightTarget == -1 || rAl.getSequence1Range().getTo() > rHit.getAlignment(iRightTarget).getSequence1Range().getTo())
                            iRightTarget = i;
                    }
                }

                if (iRightTarget == -1)
                    break;

                Alignment<NucleotideSequence> rAl = rHit.getAlignment(iRightTarget);
                if (!rAl.getSequence1Range().contains(positionInRef)) {
                    // add lowercase piece of germline
                    assert rAl.getSequence1Range().getTo() <= positionInRef;
                    if (rightParts.isEmpty() && rAl.getSequence1Range().getTo() == lPositionInRef)
                        break;
                    IncompleteSequencePart part = new IncompleteSequencePart(rHit, true, iRightTarget, rAl.getSequence1Range().getTo(), positionInRef); // +1 to include positionInRef
                    if (part.begin != part.end)
                        rightParts.add(part);
                    positionInRef = rAl.getSequence1Range().getTo();
                }
                assert rAl.getSequence1Range().containsBoundary(positionInRef);

                IncompleteSequencePart part = new IncompleteSequencePart(rHit, false, iRightTarget,
                        rAl.getSequence2Range().getFrom(),
                        aabsRight(positionInRef, rAl));
                if (part.begin != part.end)
                    rightParts.add(part);

                positionInRef = rAl.getSequence1Range().getFrom();
            }
            Collections.reverse(rightParts);

            if (leftParts.isEmpty() && rightParts.isEmpty() && lHit == rHit) {
                // the feature is not covered by any target and
                // there are no targets in between :=> take everything from germline
                int
                        lAbs = lHit.getGene().getPartitioning().getAbsolutePosition(lHit.getAlignedFeature(), lPositionInRef),
                        rAbs = lHit.getGene().getPartitioning().getAbsolutePosition(lHit.getAlignedFeature(), rPositionInRef);
                // the only correct case
                NucleotideSequence germline = lHit
                        .getGene()
                        .getSequenceProvider()
                        .getRegion(new Range(lAbs, rAbs));
                if (germline == null)
                    return null;
                builder.add(germline, true);
            } else if (leftParts.isEmpty() || rightParts.isEmpty())
                return null;
            else {
                // final pieces
                List<IncompleteSequencePart> pieces;
                IncompleteSequencePart
                        lLast = leftParts.get(leftParts.size() - 1),
                        rLast = rightParts.get(0);

                if (lHit == rHit) {
                    Alignment<NucleotideSequence> lAl = lHit.getAlignment(lLast.iTarget);
                    Alignment<NucleotideSequence> rAl = lHit.getAlignment(rLast.iTarget);
                    if (lLast.iTarget > rLast.iTarget && lAl.getSequence1Range().getFrom() < rAl.getSequence1Range().getTo())
                        return null;
                    if (lAl.getSequence1Range().contains(rPositionInRef)) {
                        int aabs = aabs(lAl.convertToSeq2Position(rPositionInRef));
                        if (aabs < lLast.begin)
                            return null;

                        IncompleteSequencePart part = new IncompleteSequencePart(lHit, false, lLast.iTarget, lLast.begin, aabs);
                        if (part.begin == part.end)
                            leftParts.remove(leftParts.size() - 1);
                        else
                            leftParts.set(leftParts.size() - 1, part);
                    } else {
                        assert rPositionInRef >= lAl.getSequence1Range().getTo();
                        IncompleteSequencePart part = new IncompleteSequencePart(lHit, true,
                                lLast.iTarget,
                                lAl.getSequence1Range().getTo(), rPositionInRef);
                        if (part.begin != part.end)
                            leftParts.add(part);
                    }

                    if (rAl.getSequence1Range().contains(lPositionInRef)) {
                        int aabs = aabs(rAl.convertToSeq2Position(lPositionInRef));
                        if (aabs > rLast.end)
                            return null;

                        IncompleteSequencePart part = new IncompleteSequencePart(rHit, false, rLast.iTarget, aabs, rLast.end);
                        if (part.begin == part.end)
                            rightParts.remove(0);
                        else
                            rightParts.set(0, part);
                    } else {
                        assert lPositionInRef < rAl.getSequence1Range().getFrom();
                        IncompleteSequencePart part = new IncompleteSequencePart(rHit, true,
                                rLast.iTarget,
                                lPositionInRef, rAl.getSequence1Range().getFrom());
                        if (part.begin != part.end)
                            rightParts.add(0, part);
                    }

                    assert same(leftParts, rightParts) :
                            "\n" + leftParts
                                    + "\n" + rightParts
                                    + "\n" + (this instanceof Clone ? ((Clone) this).id : ((VDJCAlignments) this).getAlignmentsIndex());
                    pieces = leftParts;
                } else {
                    if (lLast.iTarget != rLast.iTarget)
                        return null;

                    if (lLast.begin > rLast.end)
                        return null;

                    if (lLast.germline || rLast.germline)
                        return null;

//                    assert lHit.getGene().getGeneType() == GeneType.Variable;
//                    if (!lHit
//                            .getPartitioningForTarget(lLast.iTarget)
//                            .isAvailable(ReferencePoint.CDR3Begin))
//                        return null;
//
//                    assert rHit.getGene().getGeneType() == GeneType.Joining;
//                    if (!rHit
//                            .getPartitioningForTarget(rLast.iTarget)
//                            .isAvailable(ReferencePoint.CDR3End))
//                        return null;

                    IncompleteSequencePart
                            merged = new IncompleteSequencePart(lHit, false, lLast.iTarget, lLast.begin, rLast.end);

                    pieces = new ArrayList<>();
                    pieces.addAll(leftParts.subList(0, leftParts.size() - 1));
                    pieces.add(merged);
                    pieces.addAll(rightParts.subList(1, rightParts.size()));
                }

                for (IncompleteSequencePart piece : pieces)
                    if (piece.germline)
                        builder.add(piece.hit.getAlignment(piece.iTarget).getSequence1().getRange(piece.begin, piece.end), true);
                    else
                        builder.add(getTarget(piece.iTarget).getSequence().getRange(piece.begin, piece.end), false);
            }

            if (right.hasNoOffset())
                partitioningBuilder.setPosition(right, builder.size());
        }
        ExtendedReferencePoints partition = partitioningBuilder.build();
        return new CaseSensitiveNucleotideSequence(
                builder.sequences.toArray(new NucleotideSequence[builder.sequences.size()]),
                builder.lowerCase,
                partition, partition.getTranslationParameters(geneFeature));
    }

    private static int aabsLeft(int positionInRef, Alignment<NucleotideSequence> lAl) {
        if (lAl.getSequence1Range().getFrom() == positionInRef)
            return lAl.getSequence2Range().getFrom();
        return aabs(lAl.convertToSeq2Position(positionInRef));
    }

    private static int aabsRight(int positionInRef, Alignment<NucleotideSequence> rAl) {
        if (rAl.getSequence1Range().getTo() == positionInRef)
            return rAl.getSequence2Range().getTo();
        return aabs(rAl.convertToSeq2Position(positionInRef));
    }

    private boolean same(IncompleteSequencePart a, IncompleteSequencePart b) {
        return a.hit == b.hit &&
                a.germline == b.germline &&
                (a.iTarget == b.iTarget || a.germline) &&
                a.begin == b.begin &&
                a.end == b.end;
    }

    private boolean same(List<IncompleteSequencePart> a, List<IncompleteSequencePart> b) {
        if (a.size() != b.size())
            return false;
        for (int i = 0; i < a.size(); ++i)
            if (!same(a.get(i), b.get(i)))
                return false;
        return true;
    }

    private static final class IncompleteSequencePart {
        final VDJCHit hit;
        final boolean germline;
        final int iTarget;
        final int begin, end;

        IncompleteSequencePart(VDJCHit hit, boolean germline, int iTarget, int begin, int end) {
            assert begin <= end : "" + begin + " - " + end;
            this.hit = hit;
            this.germline = germline;
            this.iTarget = iTarget;
            this.begin = begin;
            this.end = end;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            IncompleteSequencePart that = (IncompleteSequencePart) o;
            return germline == that.germline &&
                    iTarget == that.iTarget &&
                    begin == that.begin &&
                    end == that.end &&
                    Objects.equals(hit, that.hit);
        }

        @Override
        public int hashCode() {
            return Objects.hash(hit, germline, iTarget, begin, end);
        }

        @Override
        public String toString() {
            return "{" + germline + " " + iTarget + " " + begin + " " + end + "}";
        }
    }

    public static final class CaseSensitiveNucleotideSequenceBuilder {
        // sequences
        final List<NucleotideSequence> sequences;
        // upper or lower case
        final BitSet lowerCase;

        public CaseSensitiveNucleotideSequenceBuilder(List<NucleotideSequence> sequences, BitSet lowerCase) {
            this.sequences = sequences;
            this.lowerCase = lowerCase;
        }

        public int size() {
            return sequences.stream().mapToInt(s -> s.size()).sum();
        }

        public void add(NucleotideSequence seq, boolean lowerCase) {
            this.lowerCase.set(sequences.size(), lowerCase);
            sequences.add(seq);
        }
    }

    public static class CaseSensitiveNucleotideSequence {
        // sequences
        final NucleotideSequence[] seq;
        // upper or lower case
        final BitSet lowerCase;
        // sequence partitioning
        public final SequencePartitioning partitioning;
        // translation parameters
        public final TranslationParameters tr;

        CaseSensitiveNucleotideSequence(NucleotideSequence[] seq,
                                        BitSet lowerCase,
                                        SequencePartitioning partitioning,
                                        TranslationParameters tr) {
            this.seq = seq;
            this.lowerCase = lowerCase;
            this.partitioning = partitioning;
            this.tr = tr;
        }

        CaseSensitiveNucleotideSequence(NucleotideSequence seq,
                                        boolean lowerCase,
                                        SequencePartitioning partitioning, TranslationParameters tr) {
            this(new NucleotideSequence[]{seq}, new BitSet(), partitioning, tr);
            this.lowerCase.set(0, lowerCase);
        }

        public int size() {
            return seq.length;
        }

        public NucleotideSequence getSequence(int i) {
            return seq[i];
        }

        public boolean isLowerCase(int i) {
            return lowerCase.get(i);
        }

        public boolean containsWildcards() {
            for (NucleotideSequence s : seq)
                if (s.containsWildcards())
                    return true;
            return false;
        }

        boolean containsLowerCase() {
            for (int i = 0; i < seq.length; ++i)
                if (lowerCase.get(i))
                    return true;
            return false;
        }

        boolean containsUpperCase() {
            for (int i = 0; i < seq.length; ++i)
                if (!lowerCase.get(i))
                    return true;
            return false;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < seq.length; ++i) {
                String s = seq[i].toString();
                if (lowerCase.get(i))
                    s = s.toLowerCase();
                else
                    s = s.toUpperCase();
                sb.append(s);
            }
            return sb.toString();
        }

        public String toAminoAcidString() {
            if (tr == null)
                return null;

            NucleotideSequence concatenated = SequencesUtils.concatenate(seq);
            String aaSeq = AminoAcidSequence.translate(concatenated, tr).toString();
            int ntBegin = 0;
            for (int i = 0; i < seq.length; ++i) {
                AminoAcidSequence.AminoAcidSequencePosition aap;

                aap = AminoAcidSequence.convertNtPositionToAA(ntBegin, concatenated.size(), tr);
                int aaBegin;
                if (aap == null)
                    throw new RuntimeException();
                else
                    aaBegin = aap.aminoAcidPosition;

                ntBegin += seq[i].size();

                aap = AminoAcidSequence.convertNtPositionToAA(ntBegin, concatenated.size(), tr);
                int aaEnd;
                if (aap == null)
                    throw new RuntimeException();
                else
                    aaEnd = aap.aminoAcidPosition;

                if (lowerCase.get(i))
                    aaSeq = aaSeq.substring(0, aaBegin)
                            + aaSeq.substring(aaBegin, aaEnd).toLowerCase()
                            + aaSeq.substring(aaEnd, aaSeq.length());

            }
            return aaSeq;
        }
    }

    public boolean containsStops(GeneFeature feature) {
        GeneFeature codingFeature = GeneFeature.getCodingGeneFeature(feature);
        if (codingFeature == null)
            return true;

        for (int i = 0; i < numberOfTargets(); ++i) {
            NSequenceWithQuality codingSeq = getPartitionedTarget(i).getFeature(codingFeature);
            if (codingSeq == null)
                continue;
            TranslationParameters tr = getPartitionedTarget(i).getPartitioning().getTranslationParameters(codingFeature);
            if (tr == null)
                return true;
            if (AminoAcidSequence.translate(codingSeq.getSequence(), tr).containStops())
                return true;
        }

        return false;
    }

    public boolean isOutOfFrame(GeneFeature feature) {
        NSequenceWithQuality nt = getFeature(feature);
        if (nt == null || nt.size() % 3 != 0)
            return true;
        return false;
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

        if (!tagCounter.equals(that.tagCounter)) return false;
        if (!Arrays.equals(targets, that.targets)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(targets);
        result = 31 * result + hits.hashCode();
        result = 29 * result + tagCounter.hashCode();
        return result;
    }
}
