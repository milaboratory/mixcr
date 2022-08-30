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
import com.milaboratory.core.sequence.AminoAcidSequence;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.basictypes.VDJCPartitionedSequence;
import com.milaboratory.mixcr.export.AirrUtil.AirrAlignment;
import io.repseq.core.*;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class AirrColumns {
    private AirrColumns() {
    }

    private static String airrStr(String str) {
        return str == null ? "" : str;
    }

    private static String airrBoolean(Boolean value) {
        return value == null ? "" : value ? "T" : "F";
    }

    public static final class CloneId implements FieldExtractor<AirrVDJCObjectWrapper> {
        @NotNull
        @Override
        public String getHeader() {
            return "sequence_id";
        }

        @NotNull
        @Override
        public String extractValue(AirrVDJCObjectWrapper object) {
            return "clone." + object.asClone().getId();
        }
    }

    public static final class AlignmentId implements FieldExtractor<AirrVDJCObjectWrapper> {
        @NotNull
        @Override
        public String getHeader() {
            return "sequence_id";
        }

        @NotNull
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

        @NotNull
        @Override
        public String getHeader() {
            return "sequence";
        }

        @NotNull
        @Override
        public String extractValue(AirrVDJCObjectWrapper object) {
            int resolvedTargetId = targetId == -1 ? object.getBestTarget() : targetId;
            return object.object.getTarget(resolvedTargetId).getSequence().toString();
        }
    }

    public static final class RevComp implements FieldExtractor<AirrVDJCObjectWrapper> {
        @NotNull
        @Override
        public String getHeader() {
            return "rev_comp";
        }

        @NotNull
        @Override
        public String extractValue(@NotNull AirrVDJCObjectWrapper object) {
            return "F";
        }
    }

    public static class Productive implements FieldExtractor<AirrVDJCObjectWrapper> {
        @NotNull
        @Override
        public String getHeader() {
            return "productive";
        }

        @NotNull
        @Override
        public String extractValue(AirrVDJCObjectWrapper object) {
            NSequenceWithQuality cdr3nt = object.object.getFeature(GeneFeature.CDR3);
            AminoAcidSequence cdr3aa = object.object.getAAFeature(GeneFeature.CDR3);
            return airrBoolean(
                    cdr3nt != null
                            && cdr3aa != null
                            && cdr3nt.size() % 3 == 0
                            && !cdr3aa.containStops()
            );
        }
    }

    public static final class VDJCCalls implements FieldExtractor<AirrVDJCObjectWrapper> {
        private final GeneType gt;

        public VDJCCalls(GeneType gt) {
            this.gt = gt;
        }

        @NotNull
        @Override
        public String getHeader() {
            return gt.getLetterLowerCase() + "_call";
        }

        @NotNull
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

        @NotNull
        @Override
        public String getHeader() {
            return Character.toLowerCase(gt.getLetter()) + "_call";
        }

        @NotNull
        @Override
        public String extractValue(AirrVDJCObjectWrapper object) {
            VDJCGene bestHitGene = object.object.getBestHitGene(gt);
            return bestHitGene == null ? "" : bestHitGene.getGeneName();
        }
    }

    public static abstract class AirrAlignmentExtractor implements FieldExtractor<AirrVDJCObjectWrapper> {
        private final int targetId;
        protected final boolean withPadding;

        public AirrAlignmentExtractor(int targetId, boolean withPadding) {
            this.targetId = targetId;
            this.withPadding = withPadding;
        }

        public abstract String extractValue(AirrAlignment object);

        @NotNull
        @Override
        public String extractValue(AirrVDJCObjectWrapper object) {
            int resolvedTargetId = targetId == -1 ? object.getBestTarget() : targetId;
            AirrAlignment alignment = object.getAirrAlignment(resolvedTargetId, withPadding);
            if (alignment == null)
                return "";
            return airrStr(extractValue(alignment));
        }
    }

    public static final class SequenceAlignment extends AirrAlignmentExtractor {
        public SequenceAlignment(int targetId, boolean withPadding) {
            super(targetId, withPadding);
        }

        @NotNull
        @Override
        public String getHeader() {
            return "sequence_alignment";
        }

        @NotNull
        @Override
        public String extractValue(AirrAlignment object) {
            return airrStr(object.getSequence(withPadding));
        }
    }

    public static final class GermlineAlignment extends AirrAlignmentExtractor {
        public GermlineAlignment(int targetId, boolean withPadding) {
            super(targetId, withPadding);
        }

        @NotNull
        @Override
        public String getHeader() {
            return "germline_alignment";
        }

        @NotNull
        @Override
        public String extractValue(AirrAlignment object) {
            return airrStr(object.getGermline(withPadding));
        }
    }

    public interface ComplexReferencePoint {
        int getPosition(SequencePartitioning partitioning, GeneFeature referenceFeature);
    }

    public static final class Single implements ComplexReferencePoint {
        private final ReferencePoint point;

        public Single(ReferencePoint refPoint) {
            this.point = refPoint;
        }

        @Override
        public int getPosition(SequencePartitioning partitioning, GeneFeature referenceFeature) {
            return referenceFeature == null
                    ? partitioning.getPosition(point)
                    : partitioning.getRelativePosition(referenceFeature, point);
        }

        @Override
        public String toString() {
            return point.toString();
        }
    }

    public static final class Rightmost implements ComplexReferencePoint {
        private final ComplexReferencePoint[] points;

        public Rightmost(ReferencePoint... points) {
            this.points = new ComplexReferencePoint[points.length];
            for (int i = 0; i < points.length; i++)
                this.points[i] = new Single(points[i]);
        }

        public Rightmost(ComplexReferencePoint... points) {
            this.points = points;
        }

        @Override
        public int getPosition(SequencePartitioning partitioning, GeneFeature referenceFeature) {
            int result = -1;
            for (ComplexReferencePoint rp : points) {
                int position = rp.getPosition(partitioning, referenceFeature);
                if (position < 0)
                    continue;
                result = Math.max(result, position);
            }
            return result;
        }

        @Override
        public String toString() {
            return "Rightmost{" + Arrays.toString(points) + '}';
        }
    }

    public static final class Leftmost implements ComplexReferencePoint {
        private final ComplexReferencePoint[] points;

        public Leftmost(ReferencePoint... points) {
            this.points = new ComplexReferencePoint[points.length];
            for (int i = 0; i < points.length; i++)
                this.points[i] = new Single(points[i]);
        }

        public Leftmost(ComplexReferencePoint... points) {
            this.points = points;
        }

        @Override
        public int getPosition(SequencePartitioning partitioning, GeneFeature referenceFeature) {
            int result = Integer.MAX_VALUE;
            for (ComplexReferencePoint rp : points) {
                int position = rp.getPosition(partitioning, referenceFeature);
                if (position < 0)
                    continue;
                result = Math.min(result, position);
            }
            return result == Integer.MAX_VALUE ? -1 : result;
        }

        @Override
        public String toString() {
            return "Leftmost{" + Arrays.toString(points) + '}';
        }
    }

    public final static class NFeatureFromAlign extends AirrAlignmentExtractor {
        private final ComplexReferencePoint from, to;
        private final String header;

        public NFeatureFromAlign(int targetId, boolean withPadding,
                                 ComplexReferencePoint from, ComplexReferencePoint to,
                                 String header) {
            super(targetId, withPadding);
            this.from = from;
            this.to = to;
            this.header = header;
        }

        @NotNull
        @Override
        public String getHeader() {
            return header;
        }

        @Override
        public String extractValue(AirrAlignment object) {
            return object.getSequence(from, to, withPadding);
        }
    }

    public abstract static class NFeatureAbstract implements FieldExtractor<AirrVDJCObjectWrapper> {
        /**
         * Used only for complex features
         */
        protected final int targetId;
        /**
         * Not null for simple gene features
         */
        protected final GeneFeature feature;
        /**
         * Not null for complex gene features.
         */
        protected final ComplexReferencePoint from, to;
        protected final String header;

        public NFeatureAbstract(int targetId,
                                ComplexReferencePoint from, ComplexReferencePoint to,
                                String header) {
            this.targetId = targetId;
            this.feature = null;
            this.from = from;
            this.to = to;
            this.header = header;
        }

        public NFeatureAbstract(GeneFeature feature, String header) {
            this.targetId = -1; // will not be used
            this.feature = feature;
            this.from = null;
            this.to = null;
            this.header = header;
        }

        @NotNull
        @Override
        public String getHeader() {
            return header;
        }

        @NotNull
        @Override
        public String extractValue(@NotNull AirrVDJCObjectWrapper object) {
            if (feature != null) {
                NSequenceWithQuality feature = object.object.getFeature(this.feature);
                if (feature == null)
                    return "";
                return extractValue(feature.getSequence());
            } else {
                int resolvedTargetId = targetId == -1 ? object.getBestTarget() : targetId;

                assert from != null && to != null;
                SequencePartitioning partitioning = object.object.getPartitionedTarget(resolvedTargetId).getPartitioning();

                int fromPosition = from.getPosition(partitioning, null);
                if (fromPosition < 0)
                    return "";

                int toPosition = to.getPosition(partitioning, null);
                if (toPosition < 0)
                    return "";

                if (fromPosition < toPosition)
                    return extractValue(object.object.getTarget(resolvedTargetId).getSequence().getRange(fromPosition, toPosition));
                else
                    return extractValue(NucleotideSequence.EMPTY);
            }
        }

        protected abstract String extractValue(NucleotideSequence feature);
    }

    public static final class NFeature extends NFeatureAbstract {
        public NFeature(int targetId,
                        ComplexReferencePoint from, ComplexReferencePoint to,
                        String header) {
            super(targetId, from, to, header);
        }

        public NFeature(GeneFeature feature, String header) {
            super(feature, header);
        }

        @Override
        public String extractValue(NucleotideSequence feature) {
            return feature.toString();
        }
    }

    public static final class NFeatureLength extends NFeatureAbstract {
        public NFeatureLength(int targetId,
                              ComplexReferencePoint from, ComplexReferencePoint to,
                              String header) {
            super(targetId, from, to, header);
        }

        public NFeatureLength(GeneFeature feature, String header) {
            super(feature, header);
        }

        @Override
        public String extractValue(NucleotideSequence feature) {
            return "" + feature.size();
        }
    }

    public static final class AAFeature implements FieldExtractor<AirrVDJCObjectWrapper> {
        private final GeneFeature feature;
        private final String header;

        public AAFeature(GeneFeature feature, String header) {
            this.feature = feature;
            this.header = header;
        }

        @NotNull
        @Override
        public String getHeader() {
            return header;
        }

        @NotNull
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

        @NotNull
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

        @NotNull
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

        @NotNull
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

        public AirrAlignmentBoundary(int targetId, boolean withPadding, GeneType geneType, boolean start) {
            super(targetId, withPadding);
            this.geneType = geneType;
            this.start = start;
        }

        @NotNull
        @Override
        public String getHeader() {
            return geneType.getLetterLowerCase() + "_alignment_" +
                    (start ? "start" : "end");
        }

        @Override
        public String extractValue(AirrAlignment object) {
            Range range = object.getRange(geneType, withPadding);
            if (range == null)
                return "";
            return "" + (start ? range.getLower() + 1 : range.getUpper());
        }
    }

    public static final class CloneCount implements FieldExtractor<AirrVDJCObjectWrapper> {
        @NotNull
        @Override
        public String getHeader() {
            return "duplicate_count";
        }

        @NotNull
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

        @NotNull
        @Override
        public String getHeader() {
            return "complete_vdj";
        }

        @NotNull
        @Override
        public String extractValue(AirrVDJCObjectWrapper object) {
            int resolvedTargetId = targetId == -1 ? object.getBestTarget() : targetId;
            VDJCPartitionedSequence partitionedTarget = object.object.getPartitionedTarget(resolvedTargetId);
            return airrBoolean(partitionedTarget.getPartitioning().isAvailable(GeneFeature.VDJRegion));
        }
    }
}
