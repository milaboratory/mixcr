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
package com.milaboratory.mixcr.export;

import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.TranslationParameters;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.basictypes.VDJCObject;
import io.repseq.core.*;
import org.jetbrains.annotations.NotNull;

import static com.milaboratory.mixcr.export.FieldExtractors.NULL;

final class FeatureExtractors {
    private FeatureExtractors() {
    }

    static abstract class WithHeader extends FieldWithParameters<VDJCObject, GeneFeature[]> {
        final String[] hPrefix, sPrefix;

        WithHeader(String command, String description, int nArgs, String[] hPrefix, String[] sPrefix) {
            super(VDJCObject.class, command, description, nArgs);
            this.hPrefix = hPrefix;
            this.sPrefix = sPrefix;
        }

        void validate(GeneFeature[] features) {
            if (features.length == 2 && !features[1].contains(features[0]))
                throw new IllegalArgumentException(String.format("%s: Base feature %s does not contain relative feature %s",
                        command, GeneFeature.encode(features[1]), GeneFeature.encode(features[0])));
        }

        private String header0(String[] prefixes, GeneFeature[] features) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < prefixes.length; i++)
                sb.append(prefixes[i]).append(GeneFeature.encode(features[i]));
            return sb.toString();
        }

        @Override
        protected GeneFeature[] getParameters(@NotNull String[] args) {
            GeneFeature[] features = new GeneFeature[args.length];
            for (int i = 0; i < args.length; i++)
                features[i] = GeneFeature.parse(args[i]);
            validate(features);
            return features;
        }

        @NotNull
        @Override
        protected String getHeader(@NotNull OutputMode outputMode, GeneFeature[] features) {
            return FieldExtractors.choose(outputMode, header0(hPrefix, features), header0(sPrefix, features));
        }

        @Override
        public String metaVars() {
            if (getNArguments() == 1)
                return "<gene_feature>";
            else
                return "<gene_feature> <relative_to_gene_feature>";
        }
    }

    static abstract class NSeqExtractor extends WithHeader {
        NSeqExtractor(String command, String description, String hPrefix, String sPrefix) {
            super(command, description, 1, new String[]{hPrefix}, new String[]{sPrefix});
        }

        @Override
        protected String extractValue(VDJCObject object, GeneFeature[] parameters) {
            NSequenceWithQuality feature = object.getFeature(parameters[0]);
            if (feature == null)
                return NULL;
            return convert(feature);
        }

        abstract String convert(NSequenceWithQuality nseq);
    }

    static abstract class MutationsExtractor extends WithHeader {
        MutationsExtractor(String command, String description, int nArgs, String[] hPrefix, String[] sPrefix) {
            super(command, description, nArgs, hPrefix, sPrefix);
        }

        @Override
        void validate(GeneFeature[] features) {
            super.validate(features);
            for (GeneFeature feature : features)
                if (feature.getGeneType() == null)
                    throw new IllegalArgumentException(String.format("%s: Gene feature %s covers several gene types " +
                            "(not possible to select corresponding alignment)", command, GeneFeature.encode(feature)));

            if (features.length == 2 && features[1].isAlignmentAttached())
                throw new IllegalArgumentException(
                        String.format(
                                "%s: Alignment attached base gene features not allowed (error in %s)",
                                command, GeneFeature.encode(features[1])));

            if (features.length == 1 && features[0].isAlignmentAttached())
                throw new IllegalArgumentException(
                        String.format(
                                "%s: Please use %s option instead and specify base gene feature to extract mutations " +
                                        "from alignment-attached gene feature %s",
                                command, command + "Relative", GeneFeature.encode(features[0])));
        }

        @Override
        protected String extractValue(VDJCObject object, GeneFeature[] parameters) {
            GeneFeature smallGeneFeature = parameters[0];
            GeneFeature bigGeneFeature = parameters[parameters.length - 1];

            GeneType geneType = bigGeneFeature.getGeneType();
            assert geneType != null;

            VDJCHit hit = object.getBestHit(geneType);

            if (hit == null)
                return "-";

            GeneFeature alignedFeature = hit.getAlignedFeature();
            // if (!alignedFeature.contains(smallGeneFeature))
            //     return "-";

            VDJCGene gene = hit.getGene();
            ReferencePoints germlinePartitioning = gene.getPartitioning();
            if (!germlinePartitioning.isAvailable(bigGeneFeature))
                return "-";

            Range bigTargetRange = germlinePartitioning.getRelativeRange(alignedFeature, bigGeneFeature);
            Range smallTargetRange =
                    smallGeneFeature.isAlignmentAttached() ?
                            null :
                            germlinePartitioning.getRelativeRange(alignedFeature, smallGeneFeature);
            if (smallTargetRange == null)
                for (int i = 0; i < object.numberOfTargets(); i++) {
                    SequencePartitioning pt = object.getPartitionedTarget(i).getPartitioning();
                    Range range = pt.getRange(smallGeneFeature);
                    if (range == null)
                        continue;
                    Alignment<NucleotideSequence> alignment = object.getBestHit(geneType).getAlignment(i);
                    smallTargetRange = alignment.convertToSeq1Range(range);
                    if (smallTargetRange != null)
                        break;
                }

            if (smallTargetRange == null)
                return "-";

            GeneFeature intersectionBigAligned = GeneFeature.intersectionStrict(bigGeneFeature, alignedFeature);

            for (int i = 0; i < hit.numberOfTargets(); ++i) {
                Alignment<NucleotideSequence> alignment = hit.getAlignment(i);

                if (alignment == null || !alignment.getSequence1Range().contains(smallTargetRange))
                    continue;

                Mutations<NucleotideSequence> mutations;
                if (parameters.length == 2) {
                    mutations = alignment.getAbsoluteMutations().extractAbsoluteMutationsForRange(smallTargetRange);

                    ReferencePoint baIntersectionBegin = intersectionBigAligned.getFirstPoint();

                    int referencePosition = germlinePartitioning.getRelativePosition(alignedFeature, baIntersectionBegin);
                    int bigFeaturePosition = germlinePartitioning.getRelativePosition(bigGeneFeature, baIntersectionBegin);

                    if (bigFeaturePosition < 0 || referencePosition < 0)
                        continue;

                    int shift = bigFeaturePosition - referencePosition;

                    if (shift < 0)
                        mutations = mutations.getRange(Mutations.pabs(mutations.firstMutationWithPosition(-shift)), mutations.size());

                    mutations = mutations.move(shift);

                    // int shift = germlinePartitioning.getRelativePosition(bigGeneFeature, smallGeneFeature.getFirstPoint());
                    // if (shift < 0)
                    //     continue;
                    // mutations = mutations.move(shift);
                    // mutations = mutations.extractRelativeMutationsForRange(bigRange);
                    // if (mutations == null)
                    //     continue;
                } else
                    mutations = alignment.getAbsoluteMutations().extractRelativeMutationsForRange(smallTargetRange);

                return convert(mutations, gene.getFeature(bigGeneFeature),
                        object.getFeature(smallGeneFeature).getSequence(),
                        bigTargetRange.getRelativeRangeOf(smallTargetRange),
                        germlinePartitioning.getTranslationParameters(bigGeneFeature));
            }
            return "-";
        }

        abstract String convert(Mutations<NucleotideSequence> mutations, NucleotideSequence seq1, NucleotideSequence seq2, Range range, TranslationParameters tr);
    }
}