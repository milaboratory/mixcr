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
     * Minimal fraction of non edge points in variant that must be reached to consider the variant significant
     */
    double minimalNonEdgePointsFraction;
    /**
     * Positions having quality share less then this value, will not be represented in the output
     */
    double outputMinimalQualityShare;
    /**
     * Positions having sum quality less then this value, will not be represented in the output
     */
    long outputMinimalSumQuality;
    /**
     * Region where variants are allowed
     */
    GeneFeature subCloningRegion;
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
            @JsonProperty("outputMinimalQualityShare") double outputMinimalQualityShare,
            @JsonProperty("outputMinimalSumQuality") long outputMinimalSumQuality,
            @JsonProperty("subCloningRegion") GeneFeature subCloningRegion,
            @JsonProperty("trimmingParameters") QualityTrimmerParameters trimmingParameters,
            @JsonProperty("minimalContigLength") int minimalContigLength,
            @JsonProperty("alignedRegionsOnly") boolean alignedRegionsOnly) {
        this.branchingMinimalQualityShare = branchingMinimalQualityShare;
        this.branchingMinimalSumQuality = branchingMinimalSumQuality;
        this.decisiveBranchingSumQualityThreshold = decisiveBranchingSumQualityThreshold;
        this.alignedSequenceEdgeDelta = alignedSequenceEdgeDelta;
        this.alignmentEdgeRegionSize = alignmentEdgeRegionSize;
        this.minimalNonEdgePointsFraction = minimalNonEdgePointsFraction;
        this.outputMinimalQualityShare = outputMinimalQualityShare;
        this.outputMinimalSumQuality = outputMinimalSumQuality;
        this.subCloningRegion = subCloningRegion;
        this.trimmingParameters = trimmingParameters;
        this.minimalContigLength = minimalContigLength;
        this.alignedRegionsOnly = alignedRegionsOnly;
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

    @Override
    public FullSeqAssemblerParameters clone() {
        return new FullSeqAssemblerParameters(branchingMinimalQualityShare, branchingMinimalSumQuality, decisiveBranchingSumQualityThreshold,
                alignedSequenceEdgeDelta, alignmentEdgeRegionSize, minimalNonEdgePointsFraction,
                outputMinimalQualityShare, outputMinimalSumQuality, subCloningRegion,
                trimmingParameters, minimalContigLength, alignedRegionsOnly);
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
                        map = GlobalObjectMappers.ONE_LINE.readValue(is, typeRef);
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
}
