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

import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.alignment.AlignmentHelper;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.primitivio.annotations.Serializable;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.ReferencePoint;
import io.repseq.core.VDJCGene;

import java.util.Arrays;
import java.util.function.Function;

@Serializable(by = IO.VDJCHitSerializer.class)
public final class VDJCHit implements Comparable<VDJCHit>, HasGene {
    private final VDJCGene gene;
    private final Alignment<NucleotideSequence>[] alignments;
    private final GeneFeature alignedFeature;
    private final float score;

    public VDJCHit(VDJCGene gene, Alignment<NucleotideSequence> alignments, GeneFeature alignedFeature) {
        this(gene, new Alignment[]{alignments}, alignedFeature);
    }

    public VDJCHit(VDJCGene gene, Alignment<NucleotideSequence>[] alignments, GeneFeature alignedFeature) {
        assert (alignments[0] == null || gene.getFeature(alignedFeature).equals(alignments[0].getSequence1()));
        assert (alignments.length < 2 || alignments[1] == null || gene.getFeature(alignedFeature).equals(alignments[1].getSequence1()));
        this.gene = gene;
        this.alignments = alignments;
        this.alignedFeature = alignedFeature;

        float sum = 0.0f;
        for (Alignment<NucleotideSequence> alignment : alignments)
            if (alignment != null)
                sum += alignment.getScore();
        this.score = sum;
    }

    public VDJCHit(VDJCGene gene, Alignment<NucleotideSequence>[] alignments, GeneFeature alignedFeature, float score) {
        assert (alignments[0] == null || gene.getFeature(alignedFeature).equals(alignments[0].getSequence1()));
        assert (alignments.length < 2 || alignments[1] == null || gene.getFeature(alignedFeature).equals(alignments[1].getSequence1()));
        this.gene = gene;
        this.alignments = alignments;
        this.alignedFeature = alignedFeature;
        this.score = score;
    }

    public VDJCHit setAlignment(int target, Alignment<NucleotideSequence> alignment) {
        Alignment<NucleotideSequence>[] newAlignments = alignments.clone();
        newAlignments[target] = alignment;
        return new VDJCHit(gene, newAlignments, alignedFeature);
    }

    @SuppressWarnings("unchecked")
    public VDJCHit mapAlignments(Function<Alignment<NucleotideSequence>, Alignment<NucleotideSequence>> processor) {
        return new VDJCHit(gene,
                Arrays
                        .stream(alignments)
                        .map(al -> al == null ? null : processor.apply(al))
                        .toArray(Alignment[]::new),
                alignedFeature, score);
    }

    public int getPosition(int target, ReferencePoint referencePoint) {
        if (alignments[target] == null)
            return -1;
        int positionInGene = gene.getPartitioning().getRelativePosition(alignedFeature, referencePoint);
        if (positionInGene == -1)
            return -1;
        return alignments[target].convertToSeq2Position(positionInGene);
    }

    public GeneType getGeneType() {
        return gene.getGeneType();
    }

    public float getScore() {
        return score;
    }

    @Override
    public VDJCGene getGene() {
        return gene;
    }

    public GeneAndScore getGeneAndScore() {
        return new GeneAndScore(getGene().getId(), getScore());
    }

    public GeneFeature getAlignedFeature() {
        return alignedFeature;
    }

    public Alignment<NucleotideSequence> getAlignment(int target) {
        return alignments[target];
    }

    public Alignment<NucleotideSequence>[] getAlignments() {
        return alignments.clone();
    }

    public int numberOfTargets() {
        return alignments.length;
    }

    public TargetPartitioning getPartitioningForTarget(int targetIndex) {
        return new TargetPartitioning(targetIndex, this);
    }

    public float getIdentity() {
        float identity = 0;
        int tSize = 0;
        for (Alignment<NucleotideSequence> alignment : alignments) {
            if (alignment == null)
                continue;
            AlignmentHelper h = alignment.getAlignmentHelper();
            identity += h.identity() * h.size();
            tSize += h.size();
        }
        return identity / tSize;
    }

    @Override
    public String toString() {
        return gene.getName() + "[" + score + "]";
    }

    @Override
    public int compareTo(VDJCHit o) {
        int compare = Float.compare(o.score, score);
        if (compare == 0)
            compare = gene.getId().compareTo(o.gene.getId());
        return compare;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VDJCHit)) return false;

        VDJCHit vdjcHit = (VDJCHit) o;

        if (Float.compare(vdjcHit.score, score) != 0) return false;
        if (!alignedFeature.equals(vdjcHit.alignedFeature)) return false;
        if (!Arrays.equals(alignments, vdjcHit.alignments)) return false;
        if (!gene.getId().equals(vdjcHit.gene.getId())) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = gene.getId().hashCode();
        result = 31 * result + Arrays.hashCode(alignments);
        result = 31 * result + alignedFeature.hashCode();
        result = 31 * result + (score != +0.0f ? Float.floatToIntBits(score) : 0);
        return result;
    }
}
