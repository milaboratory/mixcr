package com.milaboratory.mixcr.export;

import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.AminoAcidSequence;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.TranslationParameters;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.basictypes.VDJCObject;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.ReferencePoints;

import static com.milaboratory.mixcr.export.FieldExtractors.NULL;

final class FeatureExtractors {
    private FeatureExtractors() {
    }

    static abstract class WithHeader extends FieldWithParameters<VDJCObject, GeneFeature[]> {
        final int nArgs;
        final String[] hPrefix, sPrefix;

        public WithHeader(String command, String description, int nArgs, String[] hPrefix, String[] sPrefix) {
            super(VDJCObject.class, command, description);
            this.nArgs = nArgs;
            this.hPrefix = hPrefix;
            this.sPrefix = sPrefix;
        }

        void validate() {}

        private String header0(String[] prefixes, GeneFeature[] features) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < prefixes.length; i++)
                sb.append(prefixes[i]).append(features[i]);
            return sb.toString();
        }

        @Override
        protected GeneFeature[] getParameters(String[] strings) {
            if (strings.length != nArgs)
                throw new IllegalArgumentException();
            GeneFeature[] features = new GeneFeature[strings.length];
            for (int i = 0; i < strings.length; i++)
                features[i] = GeneFeature.parse(strings[i]);
            return features;
        }

        @Override
        protected String getHeader(OutputMode outputMode, GeneFeature[] features) {
            return FieldExtractors.choose(outputMode, header0(hPrefix, features) + " ", header0(sPrefix, features));
        }
    }

    static abstract class NSeqExtractor extends WithHeader {
        public NSeqExtractor(String command, String description, String hPrefix, String sPrefix) {
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
        public MutationsExtractor(String command, String description, int nArgs, String[] hPrefix, String[] sPrefix) {
            super(command, description, nArgs, hPrefix, sPrefix);
        }

        @Override
        protected String extractValue(VDJCObject object, GeneFeature[] parameters) {
            GeneFeature bigGeneFeature = parameters[parameters.length - 1];
            GeneFeature smallGeneFeature = parameters[0];

            GeneType geneType = bigGeneFeature.getGeneType();
            VDJCHit hit = object.getBestHit(geneType);

            GeneFeature alignedFeature = hit.getAlignedFeature();
            if (!alignedFeature.contains(bigGeneFeature))
                return "-";

            ReferencePoints partitioning = hit.getGene().getPartitioning();
            Range targetRage = partitioning.getRelativeRange(alignedFeature, bigGeneFeature);
            Range smallTargetRage = partitioning.getRelativeRange(alignedFeature, smallGeneFeature);

            if (targetRage == null)
                return "-";
            for (int i = 0; i < hit.numberOfTargets(); i++) {
                Alignment<NucleotideSequence> alignment = hit.getAlignment(i);

                if (alignment == null || !alignment.getSequence1Range().contains(smallTargetRage))
                    continue;

                Mutations<NucleotideSequence> mutations = alignment.getAbsoluteMutations().extractRelativeMutationsForRange(targetRage);
                if (parameters.length == 2)
                    mutations = mutations.extractAbsoluteMutationsForRange(targetRage.getRelativeRangeOf(smallTargetRage));

                return convert(mutations, alignment.getSequence1().getRange(smallTargetRage), object.getFeature(smallGeneFeature).getSequence(), partitioning.getTranslationParameters(bigGeneFeature));
            }

            return "-";
        }

        abstract String convert(Mutations<NucleotideSequence> mutations, NucleotideSequence seq1, NucleotideSequence seq2, TranslationParameters tr);
    }

    static final class AAFeatureExtractor extends WithHeader {
        public AAFeatureExtractor() {
            super("-aaFeature", "Export amino acid sequence of specified gene feature", 1, new String[]{"AA. Seq."}, new String[]{"aaSeq"});
        }

        @Override
        protected String extractValue(VDJCObject object, GeneFeature[] parameters) {
            GeneFeature geneFeature = parameters[parameters.length - 1];
            NSequenceWithQuality feature = object.getFeature(geneFeature);
            if (feature == null)
                return NULL;
            int targetId = object.getTargetContainingFeature(geneFeature);
            TranslationParameters tr = targetId == -1 ?
                    TranslationParameters.FromLeftWithIncompleteCodon
                    : object.getPartitionedTarget(targetId).getPartitioning().getTranslationParameters(geneFeature);
            if (tr == null)
                return NULL;
            return AminoAcidSequence.translate(feature.getSequence(), tr).toString();
        }
    }

}
