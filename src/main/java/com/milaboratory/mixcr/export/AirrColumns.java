package com.milaboratory.mixcr.export;

import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.sequence.AminoAcidSequence;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.basictypes.VDJCPartitionedSequence;
import com.milaboratory.mixcr.export.AirrUtil.AirrAlignment;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGene;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class AirrColumns {
    private AirrColumns() {
    }

    public static final class CloneId implements FieldExtractor<AirrVDJCObjectWrapper> {
        @Override
        public String getHeader() {
            return "sequence_id";
        }

        @Override
        public String extractValue(AirrVDJCObjectWrapper object) {
            return "clone." + object.asClone().getId();
        }
    }

    public static final class AlignmentId implements FieldExtractor<AirrVDJCObjectWrapper> {
        @Override
        public String getHeader() {
            return "sequence_id";
        }

        @Override
        public String extractValue(AirrVDJCObjectWrapper object) {
            return "read." + object.asAlignment().getMinReadId();
        }
    }

    public static final class Sequence implements FieldExtractor<AirrVDJCObjectWrapper> {
        private final int targetId;

        public Sequence(int targetId) {
            this.targetId = targetId;
        }

        @Override
        public String getHeader() {
            return "sequence";
        }

        @Override
        public String extractValue(AirrVDJCObjectWrapper object) {
            int resolvedTargetId = targetId == -1 ? object.getBestTarget() : targetId;
            return object.object.getTarget(resolvedTargetId).getSequence().toString();
        }
    }

    public static final class RevComp implements FieldExtractor<AirrVDJCObjectWrapper> {
        @Override
        public String getHeader() {
            return "rev_comp";
        }

        @Override
        public String extractValue(AirrVDJCObjectWrapper object) {
            return "F";
        }
    }

    public static class Productive implements FieldExtractor<AirrVDJCObjectWrapper> {
        @Override
        public String getHeader() {
            return "productive";
        }

        @Override
        public String extractValue(AirrVDJCObjectWrapper object) {
            // TODO better implementation
            return "T";
        }
    }

    public static final class VDJCCalls implements FieldExtractor<AirrVDJCObjectWrapper> {
        private final GeneType gt;

        public VDJCCalls(GeneType gt) {
            this.gt = gt;
        }

        @Override
        public String getHeader() {
            return gt.getLetterLowerCase() + "_call";
        }

        @Override
        public String extractValue(AirrVDJCObjectWrapper object) {
            return Arrays.stream(object.object.getHits(gt))
                    .map(h -> h.getGene().getName())
                    .collect(Collectors.joining(","));
        }
    }

    public static final class BestVDJCCall implements FieldExtractor<AirrVDJCObjectWrapper> {
        private final GeneType gt;

        public BestVDJCCall(GeneType gt) {
            this.gt = gt;
        }

        @Override
        public String getHeader() {
            return Character.toLowerCase(gt.getLetter()) + "_call";
        }

        @Override
        public String extractValue(AirrVDJCObjectWrapper object) {
            VDJCGene bestHitGene = object.object.getBestHitGene(gt);
            return bestHitGene == null ? "" : bestHitGene.getGeneName();
        }
    }

    public static abstract class AirrAlignmentExtractor implements FieldExtractor<AirrVDJCObjectWrapper> {
        private final int targetId;

        public AirrAlignmentExtractor(int targetId) {
            this.targetId = targetId;
        }

        public abstract String extractValue(AirrAlignment object);

        @Override
        public String extractValue(AirrVDJCObjectWrapper object) {
            int resolvedTargetId = targetId == -1 ? object.getBestTarget() : targetId;
            AirrAlignment alignment = object.getAirrAlignment(resolvedTargetId);
            return extractValue(alignment);
        }
    }

    public static final class SequenceAlignment extends AirrAlignmentExtractor {
        public SequenceAlignment(int targetId) {
            super(targetId);
        }

        @Override
        public String getHeader() {
            return "sequence_alignment";
        }

        @Override
        public String extractValue(AirrAlignment object) {
            return object.sequence;
        }
    }

    public static final class GermlineAlignment extends AirrAlignmentExtractor {
        public GermlineAlignment(int targetId) {
            super(targetId);
        }

        @Override
        public String getHeader() {
            return "germline_alignment";
        }

        @Override
        public String extractValue(AirrAlignment object) {
            return object.germline;
        }
    }

    public static final class NFeature implements FieldExtractor<AirrVDJCObjectWrapper> {
        private final GeneFeature feature;
        private final String header;

        public NFeature(GeneFeature feature, String header) {
            this.feature = feature;
            this.header = header;
        }

        @Override
        public String getHeader() {
            return header;
        }

        @Override
        public String extractValue(AirrVDJCObjectWrapper object) {
            NSequenceWithQuality feature = object.object.getFeature(this.feature);
            if (feature == null)
                return "";
            return feature.getSequence().toString();
        }
    }

    public static final class AAFeature implements FieldExtractor<AirrVDJCObjectWrapper> {
        private final GeneFeature feature;
        private final String header;

        public AAFeature(GeneFeature feature, String header) {
            this.feature = feature;
            this.header = header;
        }

        @Override
        public String getHeader() {
            return header;
        }

        @Override
        public String extractValue(AirrVDJCObjectWrapper object) {
            AminoAcidSequence feature = object.object.getAAFeature(this.feature);
            if (feature == null)
                return "";
            return feature.toString();
        }
    }

    public static abstract class AlignmentColumn implements FieldExtractor<AirrVDJCObjectWrapper> {
        protected final int targetId;
        protected final GeneType geneType;

        public AlignmentColumn(int targetId, GeneType geneType) {
            this.targetId = targetId;
            this.geneType = geneType;
        }

        @Override
        public String extractValue(AirrVDJCObjectWrapper object) {
            int resolvedTargetId = targetId == -1 ? object.getBestTarget() : targetId;
            VDJCHit bestHit = object.object.getBestHit(geneType);
            if (bestHit == null)
                return "";
            Alignment<NucleotideSequence> alignment = bestHit.getAlignment(resolvedTargetId);
            if (alignment == null)
                return "";
            return extractValue(alignment);
        }

        public abstract String extractValue(Alignment<NucleotideSequence> alignment);
    }

    /**
     * Scoring for actual alignment in actual target, in contrast with normal MiXCR output where score shows the sum for
     * all targets, or an aggregated value for clonotypes.
     */
    public static final class AlignmentScoring extends AlignmentColumn {
        public AlignmentScoring(int targetId, GeneType geneType) {
            super(targetId, geneType);
        }

        @Override
        public String getHeader() {
            return geneType.getLetterLowerCase() + "_score";
        }

        @Override
        public String extractValue(Alignment<NucleotideSequence> alignment) {
            return "" + alignment.getScore();
        }
    }

    public static final class AlignmentCigar extends AlignmentColumn {
        public AlignmentCigar(int targetId, GeneType geneType) {
            super(targetId, geneType);
        }

        @Override
        public String getHeader() {
            return geneType.getLetterLowerCase() + "_cigar";
        }

        @Override
        public String extractValue(Alignment<NucleotideSequence> alignment) {
            return alignment.getCigarString(true);
        }
    }

    public static final class SequenceAlignmentBoundary extends AlignmentColumn {
        final boolean start, germline;

        public SequenceAlignmentBoundary(int targetId, GeneType geneType, boolean start, boolean germline) {
            super(targetId, geneType);
            this.start = start;
            this.germline = germline;
        }

        @Override
        public String getHeader() {
            return geneType.getLetterLowerCase() + "_" +
                    (germline ? "germline" : "sequence") + "_" +
                    (start ? "start" : "end");
        }

        @Override
        public String extractValue(Alignment<NucleotideSequence> alignment) {
            Range range = germline ? alignment.getSequence1Range() : alignment.getSequence2Range();
            return "" + (start ? range.getLower() + 1 : range.getUpper());
        }
    }

    public static final class AirrAlignmentBoundary extends AirrAlignmentExtractor {
        private final GeneType geneType;
        private final boolean start;

        public AirrAlignmentBoundary(int targetId, GeneType geneType, boolean start) {
            super(targetId);
            this.geneType = geneType;
            this.start = start;
        }

        @Override
        public String getHeader() {
            return geneType.getLetterLowerCase() + "_alignment_" +
                    (start ? "start" : "end");
        }

        @Override
        public String extractValue(AirrAlignment object) {
            Range range = object.ranges.get(geneType);
            if (range == null)
                return "";
            return "" + (start ? range.getLower() + 1 : range.getUpper());
        }
    }

    public static final class CloneCount implements FieldExtractor<AirrVDJCObjectWrapper> {
        @Override
        public String getHeader() {
            return "duplicate_count";
        }

        @Override
        public String extractValue(AirrVDJCObjectWrapper object) {
            return "" + (int) Math.round(object.asClone().getCount());
        }
    }

    public static final class CompleteVDJ implements FieldExtractor<AirrVDJCObjectWrapper> {
        private final int targetId;

        public CompleteVDJ(int targetId) {
            this.targetId = targetId;
        }

        @Override
        public String getHeader() {
            return "complete_vdj";
        }

        @Override
        public String extractValue(AirrVDJCObjectWrapper object) {
            int resolvedTargetId = targetId == -1 ? object.getBestTarget() : targetId;
            VDJCPartitionedSequence partitionedTarget = object.object.getPartitionedTarget(resolvedTargetId);
            return partitionedTarget.getPartitioning().isAvailable(GeneFeature.VDJRegion) ? "T" : "F";
        }
    }
}
