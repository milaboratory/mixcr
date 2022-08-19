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
package com.milaboratory.mixcr.assembler.fullseq;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.milaboratory.core.sequence.quality.QualityTrimmerParameters;
import com.milaboratory.util.GlobalObjectMappers;
import io.repseq.core.GeneFeature;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE
)
public class FullSeqAssemblerParameters {
    /**
     * Minimal quality fraction (variant may be marked significant if {@code variantQuality > totalSumQuality *
     * branchingMinimalQualityShare}
     */
    double branchingMinimalQualityShare;
    /**
     * Minimal variant quality threshold (variant may be marked significant if {@code variantQuality >
     * branchingMinimalSumQuality}
     */
    long branchingMinimalSumQuality;
    /**
     * Variant quality that guaranties that variant will be marked significant (even if other criteria are not
     * satisfied)
     */
    long decisiveBranchingSumQualityThreshold;
    /**
     * Maximal number of not aligned nucleotides at the edge of sequence so that sequence is still considered aligned
     * "to the end"
     */
    int alignedSequenceEdgeDelta;
    /**
     * Number of nucleotides at the edges of alignments (with almost fully aligned seq2) that are "not trusted"
     */
    int alignmentEdgeRegionSize;
    /**
     * Positions having mean normalized quality
     * (sum of quality scores for the variant / read count for the whole clonotype;
     * position coverage not taken into account) less then this value, will not be used for sub-cloning
     */
    double minimalMeanNormalizedQuality;
    /**
     * Minimal fraction of non edge points in variant that must be reached to consider the variant significant
     */
    double minimalNonEdgePointsFraction;
    /**
     * Positions having quality share less then this value, will not be represented in the output; used if no variants
     * are detected with standard pipeline
     */
    double outputMinimalQualityShare;
    /**
     * Positions having sum quality less then this value, will not be represented in the output
     */
    long outputMinimalSumQuality;
    /**
     * Gene feature limiting the set of positions where sufficient number of different nucleotides may split input
     * into several clonotypes. If position is not covered by the region, and significant disagreement between
     * nucleotides is observed, algorithm will produce "N" letter in the corresponding contig position to indicate the
     * ambiguity. Null - means no subcloning region, and guarantees one to one input to output clonotype correspondence.
     */
    GeneFeature subCloningRegion;
    /**
     * Limits the region of the sequence to assemble during the procedure, no nucleotides will be assembled outside it.
     * Null will result in assembly of the longest possible contig sequence.
     */
    GeneFeature assemblingRegion;
    /**
     * Used only if {@link #assemblingRegion} is not null. Sets filtering criteria to apply before outputting the
     * resulting clonotypes.
     */
    PostFiltering postFiltering;
    /**
     * Parameters of trimmer, that performs final processing of the output contigs
     */
    QualityTrimmerParameters trimmingParameters;
    /**
     * Minimal contiguous sequence length
     */
    int minimalContigLength;
    /**
     * Assemble only parts of sequences covered by alignments
     */
    boolean alignedRegionsOnly;

    @JsonCreator
    public FullSeqAssemblerParameters(
            @JsonProperty("branchingMinimalQualityShare") double branchingMinimalQualityShare,
            @JsonProperty("branchingMinimalSumQuality") long branchingMinimalSumQuality,
            @JsonProperty("decisiveBranchingSumQualityThreshold") long decisiveBranchingSumQualityThreshold,
            @JsonProperty("alignedSequenceEdgeDelta") int alignedSequenceEdgeDelta,
            @JsonProperty("alignmentEdgeRegionSize") int alignmentEdgeRegionSize,
            @JsonProperty("minimalNonEdgePointsFraction") double minimalNonEdgePointsFraction,
            @JsonProperty("minimalMeanNormalizedQuality") double minimalMeanNormalizedQuality,
            @JsonProperty("outputMinimalQualityShare") double outputMinimalQualityShare,
            @JsonProperty("outputMinimalSumQuality") long outputMinimalSumQuality,
            @JsonProperty("subCloningRegion") GeneFeature subCloningRegion,
            @JsonProperty("assemblingRegion") GeneFeature assemblingRegion,
            @JsonProperty("postFiltering") PostFiltering postFiltering,
            @JsonProperty("trimmingParameters") QualityTrimmerParameters trimmingParameters,
            @JsonProperty("minimalContigLength") int minimalContigLength,
            @JsonProperty("alignedRegionsOnly") boolean alignedRegionsOnly
    ) {
        this.branchingMinimalQualityShare = branchingMinimalQualityShare;
        this.branchingMinimalSumQuality = branchingMinimalSumQuality;
        this.decisiveBranchingSumQualityThreshold = decisiveBranchingSumQualityThreshold;
        this.alignedSequenceEdgeDelta = alignedSequenceEdgeDelta;
        this.alignmentEdgeRegionSize = alignmentEdgeRegionSize;
        this.minimalNonEdgePointsFraction = minimalNonEdgePointsFraction;
        this.minimalMeanNormalizedQuality = minimalMeanNormalizedQuality;
        this.outputMinimalQualityShare = outputMinimalQualityShare;
        this.outputMinimalSumQuality = outputMinimalSumQuality;
        this.subCloningRegion = subCloningRegion;
        this.assemblingRegion = assemblingRegion;
        this.postFiltering = postFiltering;
        this.trimmingParameters = trimmingParameters;
        this.minimalContigLength = minimalContigLength;
        this.alignedRegionsOnly = alignedRegionsOnly;
    }

    public GeneFeature getSubCloningRegion() {
        return subCloningRegion;
    }

    public void setSubCloningRegion(GeneFeature subCloningRegion) {
        this.subCloningRegion = subCloningRegion;
    }

    public GeneFeature getAssemblingRegion() {
        return assemblingRegion;
    }

    public void setAssemblingRegion(GeneFeature assemblingRegion) {
        this.assemblingRegion = assemblingRegion;
    }

    public PostFiltering getPostFiltering() {
        return postFiltering;
    }

    public void setPostFiltering(PostFiltering postFiltering) {
        this.postFiltering = postFiltering;
    }

    public double getBranchingMinimalQualityShare() {
        return branchingMinimalQualityShare;
    }

    public void setBranchingMinimalQualityShare(double branchingMinimalQualityShare) {
        this.branchingMinimalQualityShare = branchingMinimalQualityShare;
    }

    public long getBranchingMinimalSumQuality() {
        return branchingMinimalSumQuality;
    }

    public void setBranchingMinimalSumQuality(long branchingMinimalSumQuality) {
        this.branchingMinimalSumQuality = branchingMinimalSumQuality;
    }

    public long getDecisiveBranchingSumQualityThreshold() {
        return decisiveBranchingSumQualityThreshold;
    }

    public void setDecisiveBranchingSumQualityThreshold(long decisiveBranchingSumQualityThreshold) {
        this.decisiveBranchingSumQualityThreshold = decisiveBranchingSumQualityThreshold;
    }

    public int getAlignedSequenceEdgeDelta() {
        return alignedSequenceEdgeDelta;
    }

    public void setAlignedSequenceEdgeDelta(int alignedSequenceEdgeDelta) {
        this.alignedSequenceEdgeDelta = alignedSequenceEdgeDelta;
    }

    public int getAlignmentEdgeRegionSize() {
        return alignmentEdgeRegionSize;
    }

    public void setAlignmentEdgeRegionSize(int alignmentEdgeRegionSize) {
        this.alignmentEdgeRegionSize = alignmentEdgeRegionSize;
    }

    public double getMinimalNonEdgePointsFraction() {
        return minimalNonEdgePointsFraction;
    }

    public void setMinimalNonEdgePointsFraction(double minimalNonEdgePointsFraction) {
        this.minimalNonEdgePointsFraction = minimalNonEdgePointsFraction;
    }

    public double getOutputMinimalQualityShare() {
        return outputMinimalQualityShare;
    }

    public void setOutputMinimalQualityShare(double outputMinimalQualityShare) {
        this.outputMinimalQualityShare = outputMinimalQualityShare;
    }

    public long getOutputMinimalSumQuality() {
        return outputMinimalSumQuality;
    }

    public void setOutputMinimalSumQuality(long outputMinimalSumQuality) {
        this.outputMinimalSumQuality = outputMinimalSumQuality;
    }

    public boolean isAlignedRegionsOnly() {
        return alignedRegionsOnly;
    }

    public void setAlignedRegionsOnly(boolean alignedRegionsOnly) {
        this.alignedRegionsOnly = alignedRegionsOnly;
    }

    public QualityTrimmerParameters getTrimmingParameters() {
        return trimmingParameters;
    }

    public void setTrimmingParameters(QualityTrimmerParameters trimmingParameters) {
        this.trimmingParameters = trimmingParameters;
    }

    public int getMinimalContigLength() {
        return minimalContigLength;
    }

    public void setMinimalContigLength(int minimalContigLength) {
        this.minimalContigLength = minimalContigLength;
    }

    public double getMinimalMeanNormalizedQuality() {
        return minimalMeanNormalizedQuality;
    }

    public void setMinimalMeanNormalizedQuality(double minimalMeanNormalizedQuality) {
        this.minimalMeanNormalizedQuality = minimalMeanNormalizedQuality;
    }

    @Override
    public FullSeqAssemblerParameters clone() {
        return new FullSeqAssemblerParameters(branchingMinimalQualityShare, branchingMinimalSumQuality, decisiveBranchingSumQualityThreshold,
                alignedSequenceEdgeDelta, alignmentEdgeRegionSize, minimalNonEdgePointsFraction, minimalMeanNormalizedQuality,
                outputMinimalQualityShare, outputMinimalSumQuality, subCloningRegion, assemblingRegion, postFiltering,
                trimmingParameters, minimalContigLength, alignedRegionsOnly);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FullSeqAssemblerParameters)) return false;
        FullSeqAssemblerParameters that = (FullSeqAssemblerParameters) o;
        return Double.compare(that.branchingMinimalQualityShare, branchingMinimalQualityShare) == 0 &&
                branchingMinimalSumQuality == that.branchingMinimalSumQuality &&
                decisiveBranchingSumQualityThreshold == that.decisiveBranchingSumQualityThreshold &&
                alignedSequenceEdgeDelta == that.alignedSequenceEdgeDelta &&
                alignmentEdgeRegionSize == that.alignmentEdgeRegionSize &&
                Double.compare(that.minimalMeanNormalizedQuality, minimalMeanNormalizedQuality) == 0 &&
                Double.compare(that.minimalNonEdgePointsFraction, minimalNonEdgePointsFraction) == 0 &&
                Double.compare(that.outputMinimalQualityShare, outputMinimalQualityShare) == 0 &&
                outputMinimalSumQuality == that.outputMinimalSumQuality &&
                minimalContigLength == that.minimalContigLength &&
                alignedRegionsOnly == that.alignedRegionsOnly &&
                Objects.equals(subCloningRegion, that.subCloningRegion) &&
                Objects.equals(trimmingParameters, that.trimmingParameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(branchingMinimalQualityShare, branchingMinimalSumQuality, decisiveBranchingSumQualityThreshold, alignedSequenceEdgeDelta, alignmentEdgeRegionSize, minimalMeanNormalizedQuality, minimalNonEdgePointsFraction, outputMinimalQualityShare, outputMinimalSumQuality, subCloningRegion, trimmingParameters, minimalContigLength, alignedRegionsOnly);
    }

    private static Map<String, FullSeqAssemblerParameters> knownParameters;

    private static void ensureInitialized() {
        if (knownParameters == null)
            synchronized (FullSeqAssemblerParameters.class) {
                if (knownParameters == null) {
                    Map<String, FullSeqAssemblerParameters> map;
                    try {
                        InputStream is = FullSeqAssemblerParameters.class.getClassLoader().getResourceAsStream("parameters/full_seq_assembler_parameters.json");
                        TypeReference<HashMap<String, FullSeqAssemblerParameters>> typeRef
                                = new TypeReference<HashMap<String, FullSeqAssemblerParameters>>() {
                        };
                        map = GlobalObjectMappers.getOneLine().readValue(is, typeRef);
                    } catch (IOException ioe) {
                        throw new RuntimeException(ioe);
                    }
                    knownParameters = map;
                }
            }
    }

    public static Set<String> getAvailableParameterNames() {
        ensureInitialized();
        return knownParameters.keySet();
    }

    public static FullSeqAssemblerParameters getByName(String name) {
        ensureInitialized();
        FullSeqAssemblerParameters params = knownParameters.get(name);
        if (params == null)
            return null;
        return params.clone();
    }

    /**
     * Used only if {@link #assemblingRegion} is not null.
     */
    public enum PostFiltering {
        /**
         * Don't filter output clonotypes
         */
        NoFiltering,
        /**
         * Only clonotypes completely covering {@link #assemblingRegion} will be retained.
         */
        OnlyFullyAssembled,
        /**
         * Only clonotypes completely covering {@link #assemblingRegion} and having no "N" letters will be retained.
         */
        OnlyFullyDefined
    }
}
