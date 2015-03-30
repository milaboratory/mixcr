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
package com.milaboratory.mixcr.assembler;

import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.alignment.BandedAligner;
import com.milaboratory.core.alignment.BandedAlignerParameters;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.reference.*;
import com.milaboratory.mixcr.vdjaligners.SingleDAligner;
import gnu.trove.iterator.TObjectFloatIterator;
import gnu.trove.list.array.TFloatArrayList;
import gnu.trove.map.hash.TObjectFloatHashMap;

import java.util.*;

class CloneFactory {
    final SingleDAligner dAligner;
    final CloneFactoryParameters parameters;
    final HashMap<AlleleId, Allele> usedAlleles;
    final GeneFeature[] assemblingFeatures;
    final int indexOfAssemblingFeatureWithD;

    CloneFactory(CloneFactoryParameters parameters, GeneFeature[] assemblingFeatures,
                 HashMap<AlleleId, Allele> usedAlleles) {
        this.parameters = parameters.clone();
        this.assemblingFeatures = assemblingFeatures.clone();
        this.usedAlleles = usedAlleles;
        List<Allele> dAlleles = new ArrayList<>();
        for (Allele allele : usedAlleles.values())
            if (allele.getGeneType() == GeneType.Diversity)
                dAlleles.add(allele);
        this.dAligner = new SingleDAligner(parameters.getDParameters(), dAlleles);

        int indexOfAssemblingFeatureWithD = -1;
        for (int i = 0; i < assemblingFeatures.length; ++i)
            if (containsD(assemblingFeatures[i]))
                if (indexOfAssemblingFeatureWithD == -1)
                    indexOfAssemblingFeatureWithD = i;
                else
                    throw new IllegalArgumentException("Several features with D.");
        this.indexOfAssemblingFeatureWithD = indexOfAssemblingFeatureWithD;
    }

    Clone create(int id, CloneAccumulator accumulator) {
        EnumMap<GeneType, VDJCHit[]> hits = new EnumMap<>(GeneType.class);
        for (GeneType geneType : GeneType.values()) {
            if (geneType == GeneType.Diversity)
                continue;

            VJCClonalAlignerParameters vjcParameters = parameters.getVJCParameters(geneType);
            if (vjcParameters == null)
                continue;

            GeneFeature featureToAlign = vjcParameters.getFeatureToAlign();

            TObjectFloatHashMap<AlleleId> accumulatorAlleleIds = accumulator.geneScores.get(geneType);
            if (accumulatorAlleleIds == null)
                continue;

            TObjectFloatIterator<AlleleId> iterator = accumulatorAlleleIds.iterator();
            float maxScore = 0;
            while (iterator.hasNext()) {
                iterator.advance();
                float value = iterator.value();
                if (value > maxScore)
                    maxScore = value;
            }

            maxScore = maxScore * vjcParameters.getRelativeMinScore();
            List<Allele> alleles = new ArrayList<>();
            iterator = accumulatorAlleleIds.iterator();
            TFloatArrayList scores = new TFloatArrayList();
            while (iterator.hasNext()) {
                iterator.advance();
                if (maxScore <= iterator.value()) {
                    alleles.add(usedAlleles.get(iterator.key()));
                    scores.add(iterator.value());
                }
            }

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

            VDJCHit[] result = new VDJCHit[alleles.size()];
            int pointer = 0;
            for (int alleleIndex = 0; alleleIndex < alleles.size(); alleleIndex++) {
                Allele allele = alleles.get(alleleIndex);
                Alignment<NucleotideSequence>[] alignments = new Alignment[assemblingFeatures.length];
                for (int i = 0; i < assemblingFeatures.length; ++i) {
                    if (intersectingFeatures[i] == null)
                        continue;
                    NucleotideSequence referenceSequence = allele.getFeature(featureToAlign);
                    Range rangeInReference = allele.getPartitioning().getRelativeRange(featureToAlign, intersectingFeatures[i]);

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

                    BandedAlignerParameters alignmentParameters = vjcParameters.getAlignmentParameters();
                    int referenceLength = rangeInReference.length();
                    NucleotideSequence target = accumulator.getSequence().get(i).getSequence();
                    if (leftSide == null) {
                        alignments[i] = BandedAligner.align(alignmentParameters.getScoring(),
                                referenceSequence, target,
                                rangeInReference.getFrom(), referenceLength,
                                0, target.size(),
                                alignmentParameters.getWidth());
                    } else if (leftSide) {
                        assert rangeInReference.getFrom() + referenceLength == referenceSequence.size();
                        alignments[i] = BandedAligner.alignSemiLocalLeft(
                                alignmentParameters.getScoring(),
                                referenceSequence, target,
                                rangeInReference.getFrom(), referenceLength,
                                0, target.size(),
                                alignmentParameters.getWidth(),
                                alignmentParameters.getStopPenalty());
                    } else {
                        assert rangeInReference.getFrom() == 0;
                        //int offset2 = Math.max(0, target.size() - referenceLength);
                        alignments[i] = BandedAligner.alignSemiLocalRight(
                                alignmentParameters.getScoring(),
                                referenceSequence, target,
                                rangeInReference.getFrom(), referenceLength,
                                0, target.size(),
                                alignmentParameters.getWidth(),
                                alignmentParameters.getStopPenalty());
                    }
                }
                result[pointer++] = new VDJCHit(allele, alignments, featureToAlign, scores.get(alleleIndex) / accumulator.count);
            }
            Arrays.sort(result, 0, pointer);
            hits.put(geneType, pointer < result.length ? Arrays.copyOf(result, pointer) : result);
        }

        // D

        NucleotideSequence sequenceToAlign = accumulator.getSequence().get(indexOfAssemblingFeatureWithD).getSequence();
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
                    dAligner.align(sequenceToAlign, from, to, indexOfAssemblingFeatureWithD,
                            assemblingFeatures.length));
        else
            hits.put(GeneType.Diversity, new VDJCHit[0]);

        return new Clone(accumulator.getSequence().sequences, hits, assemblingFeatures, accumulator.count, id);
    }

    private static boolean containsD(GeneFeature feature) {
        return feature.getFirstPoint().compareTo(ReferencePoint.DBeginTrimmed) <= 0 &&
                feature.getLastPoint().compareTo(ReferencePoint.DEndTrimmed) >= 0;
    }
}
