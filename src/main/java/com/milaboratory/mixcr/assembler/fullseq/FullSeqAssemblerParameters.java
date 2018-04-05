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
     * minimalQualityShare}
     */
    double minimalQualityShare;
    /**
     * Minimal variant quality threshold (variant may be marked significant if {@code variantQuality >
     * minimalSumQuality}
     */
    long minimalSumQuality;
    /**
     * Variant quality that guaranties that variant will be marked significant (even if other criteria are not
     * satisfied)
     */
    long decisiveSumQualityThreshold;
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
     * Minimal fraction of non edge points in variant that should be reached to consider variant as significant
     */
    double minimalNonEdgePointsFraction;
    /**
     * Region where variants are allowed
     */
    GeneFeature subCloningRegion;
    /**
     * Parameters of trimmer, that performs final processing of the output contigs
     */
    QualityTrimmerParameters trimmingParameters;
    /**
     * Assemble only parts of sequences covered by alignments
     */
    boolean alignedRegionsOnly;

    @JsonCreator
    public FullSeqAssemblerParameters(
            @JsonProperty("minimalQualityShare") double minimalQualityShare,
            @JsonProperty("minimalSumQuality") long minimalSumQuality,
            @JsonProperty("decisiveSumQualityThreshold") long decisiveSumQualityThreshold,
            @JsonProperty("alignedSequenceEdgeDelta") int alignedSequenceEdgeDelta,
            @JsonProperty("alignmentEdgeRegionSize") int alignmentEdgeRegionSize,
            @JsonProperty("minimalNonEdgePointsFraction") double minimalNonEdgePointsFraction,
            @JsonProperty("subCloningRegion") GeneFeature subCloningRegion,
            @JsonProperty("trimmingParameters") QualityTrimmerParameters trimmingParameters,
            @JsonProperty("alignedRegionsOnly") boolean alignedRegionsOnly) {
        this.minimalQualityShare = minimalQualityShare;
        this.minimalSumQuality = minimalSumQuality;
        this.decisiveSumQualityThreshold = decisiveSumQualityThreshold;
        this.alignedSequenceEdgeDelta = alignedSequenceEdgeDelta;
        this.alignmentEdgeRegionSize = alignmentEdgeRegionSize;
        this.minimalNonEdgePointsFraction = minimalNonEdgePointsFraction;
        this.subCloningRegion = subCloningRegion;
        this.trimmingParameters = trimmingParameters;
        this.alignedRegionsOnly = alignedRegionsOnly;
    }

    public double getMinimalQualityShare() {
        return minimalQualityShare;
    }

    public void setMinimalQualityShare(double minimalQualityShare) {
        this.minimalQualityShare = minimalQualityShare;
    }

    public long getMinimalSumQuality() {
        return minimalSumQuality;
    }

    public void setMinimalSumQuality(long minimalSumQuality) {
        this.minimalSumQuality = minimalSumQuality;
    }

    public long getDecisiveSumQualityThreshold() {
        return decisiveSumQualityThreshold;
    }

    public void setDecisiveSumQualityThreshold(long decisiveSumQualityThreshold) {
        this.decisiveSumQualityThreshold = decisiveSumQualityThreshold;
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

    @Override
    public FullSeqAssemblerParameters clone() {
        return new FullSeqAssemblerParameters(minimalQualityShare, minimalSumQuality, decisiveSumQualityThreshold,
                alignedSequenceEdgeDelta, alignmentEdgeRegionSize, minimalNonEdgePointsFraction, subCloningRegion,
                trimmingParameters, alignedRegionsOnly);
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
