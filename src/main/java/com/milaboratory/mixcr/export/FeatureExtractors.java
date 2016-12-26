package com.milaboratory.mixcr.export;

import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.mutations.Mutation;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.mutations.MutationsUtil;
import com.milaboratory.core.sequence.AminoAcidSequence;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.TranslationParameters;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.basictypes.VDJCObject;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.ReferencePoints;
import io.repseq.core.VDJCGene;

import static com.milaboratory.mixcr.export.FieldExtractors.NULL;

final class FeatureExtractors {
    private FeatureExtractors() {
    }

    static abstract class WithHeader extends FieldWithParameters<VDJCObject, GeneFeature[]> {
        final int nArgs;
        final String[] hPrefix, sPrefix;

        WithHeader(String command, String description, int nArgs, String[] hPrefix, String[] sPrefix) {
            super(VDJCObject.class, command, description);
            this.nArgs = nArgs;
            this.hPrefix = hPrefix;
            this.sPrefix = sPrefix;
        }

        void validate(GeneFeature[] features) {
            if (features.length == 2 && !features[1].contains(features[0]))
                throw new IllegalArgumentException(String.format("%s: Base feature %s does not contain relative feature %s", command, features[1], features[0]));
        }

        private String header0(String[] prefixes, GeneFeature[] features) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < prefixes.length; i++)
                sb.append(prefixes[i]).append(GeneFeature.encode(features[i]));
            return sb.toString();
        }

        @Override
        protected GeneFeature[] getParameters(String[] strings) {
            if (strings.length != nArgs)
                throw new IllegalArgumentException("Wrong number of parameters for " + command);
            GeneFeature[] features = new GeneFeature[strings.length];
            for (int i = 0; i < strings.length; i++)
                features[i] = GeneFeature.parse(strings[i]);
            validate(features);
            return features;
        }

        @Override
        protected String getHeader(OutputMode outputMode, GeneFeature[] features) {
            return FieldExtractors.choose(outputMode, header0(hPrefix, features) + " ", header0(sPrefix, features));
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
        protected String extractValue(VDJCObject object, GeneFeature[] parameters) {
            GeneFeature smallGeneFeature = parameters[0];
            GeneFeature bigGeneFeature = parameters[parameters.length - 1];

            GeneType geneType = bigGeneFeature.getGeneType();
            VDJCHit hit = object.getBestHit(geneType);

            GeneFeature alignedFeature = hit.getAlignedFeature();
            if (!alignedFeature.contains(smallGeneFeature))
                return "-";

            VDJCGene gene = hit.getGene();
            ReferencePoints partitioning = gene.getPartitioning();
            if (!partitioning.isAvailable(bigGeneFeature))
                return "-";

            Range smallTargetRage = partitioning.getRelativeRange(alignedFeature, smallGeneFeature);

            for (int i = 0; i < hit.numberOfTargets(); i++) {
                Alignment<NucleotideSequence> alignment = hit.getAlignment(i);

                if (alignment == null || !alignment.getSequence1Range().contains(smallTargetRage))
                    continue;

                Mutations<NucleotideSequence> mutations = alignment.getAbsoluteMutations().extractRelativeMutationsForRange(smallTargetRage);
                if (parameters.length == 2) {
                    int shift = partitioning.getRelativePosition(bigGeneFeature, smallGeneFeature.getFirstPoint());
                    if (shift < 0)
                        continue;
                    mutations = mutations.move(shift);
                }

                return convert(mutations, gene.getFeature(bigGeneFeature), object.getFeature(smallGeneFeature).getSequence(), partitioning.getTranslationParameters(bigGeneFeature));
            }
            return "-";
        }

        abstract String convert(Mutations<NucleotideSequence> mutations, NucleotideSequence seq1, NucleotideSequence seq2, TranslationParameters tr);
    }
}
