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
package com.milaboratory.mixcr.assembler;

import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.*;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.GeneAndScore;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.basictypes.tag.TagCount;
import com.milaboratory.mixcr.cli.ValidationException;
import com.milaboratory.mixcr.vdjaligners.SingleDAligner;
import com.milaboratory.mixcr.vdjaligners.VDJCAligner;
import io.repseq.core.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class CloneFactory {
    private final SingleDAligner dAligner;
    private final CloneFactoryParameters parameters;
    private final Map<VDJCGeneId, VDJCGene> usedGenes;
    private final GeneFeature[] assemblingFeatures;
    private final int indexOfAssemblingFeatureWithD;
    private final EnumMap<GeneType, GeneFeature> featuresToAlign;

    public CloneFactory(CloneFactoryParameters parameters, GeneFeature[] assemblingFeatures,
                        Collection<VDJCGene> usedGenes, EnumMap<GeneType, GeneFeature> featuresToAlign) {
        this(parameters, assemblingFeatures,
                usedGenes.stream().collect(Collectors.toMap(VDJCGene::getId, Function.identity())), featuresToAlign);
    }

    public CloneFactory(CloneFactoryParameters parameters, GeneFeature[] assemblingFeatures,
                        Map<VDJCGeneId, VDJCGene> usedGenes, EnumMap<GeneType, GeneFeature> featuresToAlign) {
        this.parameters = parameters.clone();
        this.assemblingFeatures = assemblingFeatures.clone();
        this.usedGenes = usedGenes;
        this.featuresToAlign = featuresToAlign;
        List<VDJCGene> dGenes = new ArrayList<>();
        for (VDJCGene gene : usedGenes.values())
            if (gene.getGeneType() == GeneType.Diversity)
                dGenes.add(gene);
        this.dAligner = new SingleDAligner(parameters.getDParameters(), featuresToAlign.get(GeneType.Diversity), dGenes);

        int indexOfAssemblingFeatureWithD = -1;
        for (int i = 0; i < assemblingFeatures.length; ++i)
            if (containsD(assemblingFeatures[i]))
                if (indexOfAssemblingFeatureWithD == -1)
                    indexOfAssemblingFeatureWithD = i;
                else
                    throw new IllegalArgumentException("Several features with D.");
        if (indexOfAssemblingFeatureWithD == -1)
            throw new ValidationException("Assembling features don't contain D gene: " + Arrays.deepToString(assemblingFeatures), false);
        this.indexOfAssemblingFeatureWithD = indexOfAssemblingFeatureWithD;
    }

    public CloneFactoryParameters getParameters() {
        return parameters;
    }

    public Clone create(int id, double count,
                        Map<GeneType, List<GeneAndScore>> geneScores,
                        TagCount tagCount,
                        NSequenceWithQuality[] targets,
                        Integer group) {
        EnumMap<GeneType, VDJCHit[]> hits = new EnumMap<>(GeneType.class);
        for (GeneType geneType : GeneType.VJC_REFERENCE) {
            VJCClonalAlignerParameters vjcParameters = parameters.getVJCParameters(geneType);
            if (vjcParameters == null)
                continue;

            GeneFeature featureToAlign = featuresToAlign.get(geneType);

            List<GeneAndScore> gtGeneScores = geneScores.get(geneType);
            if (gtGeneScores == null)
                continue;

            GeneFeature[] intersectingFeatures = new GeneFeature[assemblingFeatures.length];
            for (int i = 0; i < assemblingFeatures.length; ++i) {
                intersectingFeatures[i] = GeneFeature
                        .intersection(featureToAlign, assemblingFeatures[i]);
                if (intersectingFeatures[i] != null)
                    switch (geneType) {
                        case Variable:
                            if (!intersectingFeatures[i].getFirstPoint().equals(assemblingFeatures[i].getFirstPoint()))
                                throw new IllegalArgumentException();
                            break;
                        case Joining:
                            if (!intersectingFeatures[i].getLastPoint().equals(assemblingFeatures[i].getLastPoint()))
                                throw new IllegalArgumentException();
                            break;
                    }
            }

            VDJCHit[] result = new VDJCHit[gtGeneScores.size()];
            int pointer = 0;
            for (GeneAndScore gs : gtGeneScores) {
                VDJCGene gene = usedGenes.get(gs.geneId);
                Alignment<NucleotideSequence>[] alignments = new Alignment[assemblingFeatures.length];
                for (int i = 0; i < assemblingFeatures.length; ++i) {
                    if (intersectingFeatures[i] == null)
                        continue;
                    NucleotideSequence referenceSequence = gene.getFeature(featureToAlign);
                    Range rangeInReference = gene.getPartitioning().getRelativeRange(featureToAlign, intersectingFeatures[i]);

                    if (rangeInReference == null || referenceSequence == null)
                        continue;

                    Boolean leftSide;
                    switch (geneType) {
                        case Variable:
                            leftSide = intersectingFeatures[i].getLastPoint().isTrimmable() ? true : null;
                            break;
                        case Joining:
                            leftSide = intersectingFeatures[i].getFirstPoint().isTrimmable() ? false : null;
                            break;
                        case Constant:
                            leftSide = null;
                            break;
                        default:
                            throw new RuntimeException();
                    }

                    BandedAlignerParameters<NucleotideSequence> alignmentParameters = vjcParameters.getAlignmentParameters();
                    int referenceLength = rangeInReference.length();
                    NucleotideSequence target = targets[i].getSequence();
                    if (alignmentParameters.getScoring() instanceof LinearGapAlignmentScoring) {
                        if (leftSide == null) {
                            alignments[i] = BandedLinearAligner.align(
                                    (LinearGapAlignmentScoring<NucleotideSequence>) alignmentParameters.getScoring(),
                                    referenceSequence, target,
                                    rangeInReference.getFrom(), referenceLength,
                                    0, target.size(),
                                    alignmentParameters.getWidth());
                        } else if (leftSide) {
                            assert rangeInReference.getFrom() + referenceLength == referenceSequence.size();
                            alignments[i] = BandedLinearAligner.alignSemiLocalLeft(
                                    (LinearGapAlignmentScoring<NucleotideSequence>) alignmentParameters.getScoring(),
                                    referenceSequence, target,
                                    rangeInReference.getFrom(), referenceLength,
                                    0, target.size(),
                                    alignmentParameters.getWidth(),
                                    alignmentParameters.getStopPenalty());
                        } else {
                            assert rangeInReference.getFrom() == 0;
                            //int offset2 = Math.max(0, target.size() - referenceLength);
                            alignments[i] = BandedLinearAligner.alignSemiLocalRight(
                                    (LinearGapAlignmentScoring<NucleotideSequence>) alignmentParameters.getScoring(),
                                    referenceSequence, target,
                                    rangeInReference.getFrom(), referenceLength,
                                    0, target.size(),
                                    alignmentParameters.getWidth(),
                                    alignmentParameters.getStopPenalty());
                        }
                    } else {
                        if (leftSide == null) {
                            alignments[i] = BandedAffineAligner.align(
                                    (AffineGapAlignmentScoring<NucleotideSequence>) alignmentParameters.getScoring(),
                                    referenceSequence, target,
                                    rangeInReference.getFrom(), referenceLength,
                                    0, target.size(),
                                    alignmentParameters.getWidth());
                        } else if (leftSide) {
                            assert rangeInReference.getFrom() + referenceLength == referenceSequence.size();
                            alignments[i] = BandedAffineAligner.semiLocalRight(
                                    (AffineGapAlignmentScoring<NucleotideSequence>) alignmentParameters.getScoring(),
                                    referenceSequence, target,
                                    rangeInReference.getFrom(), referenceLength,
                                    0, target.size(),
                                    alignmentParameters.getWidth());
                        } else {
                            assert rangeInReference.getFrom() == 0;
                            //int offset2 = Math.max(0, target.size() - referenceLength);
                            alignments[i] = BandedAffineAligner.semiLocalLeft(
                                    (AffineGapAlignmentScoring<NucleotideSequence>) alignmentParameters.getScoring(),
                                    referenceSequence, target,
                                    rangeInReference.getFrom(), referenceLength,
                                    0, target.size(),
                                    alignmentParameters.getWidth());
                        }
                    }
                }
                result[pointer++] = new VDJCHit(gene, alignments, featureToAlign, gs.score);
            }
            // might actually not be needed
            Arrays.sort(result, 0, pointer);
            hits.put(geneType, pointer < result.length ? Arrays.copyOf(result, pointer) : result);
        }

        // D

        NucleotideSequence sequenceToAlign = targets[indexOfAssemblingFeatureWithD].getSequence();
        int from = 0;
        int to = sequenceToAlign.size();

        VDJCHit[] hs = hits.get(GeneType.Variable);
        if (hs != null && hs.length > 0) {
            int p = hs[0].getPartitioningForTarget(indexOfAssemblingFeatureWithD)
                    .getPosition(ReferencePoint.VEndTrimmed);
            if (p != -1) {
                if (p < 0)
                    p = -2 - p;
                from = p;
            }
        }

        hs = hits.get(GeneType.Joining);
        if (hs != null && hs.length > 0) {
            int p = hs[0].getPartitioningForTarget(indexOfAssemblingFeatureWithD)
                    .getPosition(ReferencePoint.JBeginTrimmed);
            if (p != -1) {
                if (p < 0)
                    p = -2 - p;
                to = p;
            }
        }

        if (from < to)
            hits.put(GeneType.Diversity,
                    dAligner.align(sequenceToAlign,
                            VDJCAligner.getPossibleDLoci(hits.get(GeneType.Variable), hits.get(GeneType.Joining),
                                    null),
                            from, to, indexOfAssemblingFeatureWithD,
                            assemblingFeatures.length));
        else
            hits.put(GeneType.Diversity, new VDJCHit[0]);

        return new Clone(targets, hits, tagCount, count, id, group);
    }

    public Clone create(int id, CloneAccumulator accumulator) {
        return create(id, accumulator.getCount(), accumulator.genes, accumulator.tagBuilder.createAndDestroy(), accumulator.getSequence().sequences, null);
    }

    private static boolean containsD(GeneFeature feature) {
        return feature.getFirstPoint().compareTo(ReferencePoint.DBeginTrimmed) <= 0 &&
                feature.getLastPoint().compareTo(ReferencePoint.DEndTrimmed) >= 0;
    }
}
