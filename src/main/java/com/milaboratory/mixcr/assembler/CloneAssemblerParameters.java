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

import com.fasterxml.jackson.annotation.*;
import com.milaboratory.core.sequence.quality.QualityAggregationType;
import com.milaboratory.mitool.refinement.gfilter.KeyedRecordFilter;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.primitivio.annotations.Serializable;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE)
@Serializable(asJson = true)
public final class CloneAssemblerParameters implements java.io.Serializable {
    private static final int MAX_MAPPING_REGION = 1000;
    GeneFeature[] assemblingFeatures;
    int minimalClonalSequenceLength;
    QualityAggregationType qualityAggregationType;
    CloneClusteringParameters cloneClusteringParameters;
    CloneFactoryParameters cloneFactoryParameters;
    boolean separateByV, separateByJ, separateByC;
    double maximalPreClusteringRatio;
    double preClusteringScoreFilteringRatio;
    double preClusteringCountFilteringRatio;
    boolean addReadsCountOnClustering;
    byte badQualityThreshold;
    double maxBadPointsPercent;
    String mappingThreshold;
    @JsonIgnore
    long variants;
    byte minimalQuality;
    /** Filter to apply after clone assembly to the stream of tag*clonotypes */
    @JsonProperty("postFilters")
    List<KeyedRecordFilter> postFilters;

    @JsonCreator
    public CloneAssemblerParameters(@JsonProperty("assemblingFeatures") GeneFeature[] assemblingFeatures,
                                    @JsonProperty("minimalClonalSequenceLength") int minimalClonalSequenceLength,
                                    @JsonProperty("qualityAggregationType") QualityAggregationType qualityAggregationType,
                                    @JsonMerge @JsonProperty("cloneClusteringParameters") CloneClusteringParameters cloneClusteringParameters,
                                    @JsonProperty("cloneFactoryParameters") CloneFactoryParameters cloneFactoryParameters,
                                    @JsonProperty("separateByV") boolean separateByV,
                                    @JsonProperty("separateByJ") boolean separateByJ,
                                    @JsonProperty("separateByC") boolean separateByC,
                                    @JsonProperty("maximalPreClusteringRatio") double maximalPreClusteringRatio,
                                    @JsonProperty("preClusteringScoreFilteringRatio") double preClusteringScoreFilteringRatio,
                                    @JsonProperty("preClusteringCountFilteringRatio") double preClusteringCountFilteringRatio,
                                    @JsonProperty("addReadsCountOnClustering") boolean addReadsCountOnClustering,
                                    @JsonProperty("badQualityThreshold") byte badQualityThreshold,
                                    @JsonProperty("maxBadPointsPercent") double maxBadPointsPercent,
                                    @JsonProperty("mappingThreshold") String mappingThreshold,
                                    @JsonProperty("minimalQuality") byte minimalQuality,
                                    @JsonProperty("postFilters") List<KeyedRecordFilter> postFilters) {
        this.assemblingFeatures = assemblingFeatures;
        this.minimalClonalSequenceLength = minimalClonalSequenceLength;
        this.qualityAggregationType = qualityAggregationType;
        this.cloneClusteringParameters = cloneClusteringParameters;
        this.cloneFactoryParameters = cloneFactoryParameters;
        this.separateByV = separateByV;
        this.separateByJ = separateByJ;
        this.separateByC = separateByC;
        this.maximalPreClusteringRatio = maximalPreClusteringRatio;
        this.preClusteringScoreFilteringRatio = preClusteringScoreFilteringRatio;
        this.preClusteringCountFilteringRatio = preClusteringCountFilteringRatio;
        this.addReadsCountOnClustering = addReadsCountOnClustering;
        this.badQualityThreshold = badQualityThreshold;
        this.maxBadPointsPercent = maxBadPointsPercent;
        this.mappingThreshold = mappingThreshold;
        this.minimalQuality = minimalQuality;
        this.postFilters = postFilters != null
                ? Collections.unmodifiableList(new ArrayList<>(postFilters))
                : null;
        updateVariants();
    }

    public CloneAssemblerParameters updateFrom(VDJCAlignerParameters alignerParameters) {
        if (cloneFactoryParameters != null)
            cloneFactoryParameters.update(alignerParameters);
        return this;
    }

    public boolean isComplete() {
        if (cloneFactoryParameters != null)
            return cloneFactoryParameters.isComplete();
        return true;
    }

    public static final Pattern thresholdPattern = Pattern.compile("\\s*(\\d+)\\s*(of|from)\\s*(\\d+)\\s*");

    private void updateVariants() {
        long variants = -1;
        int tmp;
        try {
            tmp = Integer.parseInt(mappingThreshold);
            variants = AssemblerUtils.mappingVariantsCount(tmp, tmp);
        } catch (NumberFormatException e) {
        }

        if (variants == -1) {
            Matcher matcher = thresholdPattern.matcher(mappingThreshold);
            if (matcher.matches()) {
                int k = Integer.parseInt(matcher.group(1));
                int n = Integer.parseInt(matcher.group(3));
                variants = AssemblerUtils.mappingVariantsCount(n, k);
            }
        }

        if (variants == -1)
            throw new IllegalArgumentException("Illegal value: " + mappingThreshold);

        this.variants = variants;
    }

    public AssemblerUtils.MappingThresholdCalculator getThresholdCalculator() {
        return new AssemblerUtils.MappingThresholdCalculator(variants, MAX_MAPPING_REGION);
    }

    public GeneFeature[] getAssemblingFeatures() {
        return assemblingFeatures;
    }

    public int getMinimalClonalSequenceLength() {
        return minimalClonalSequenceLength;
    }

    public QualityAggregationType getQualityAggregationType() {
        return qualityAggregationType;
    }

    public CloneAssemblerParameters setQualityAggregationType(QualityAggregationType qualityAggregationType) {
        this.qualityAggregationType = qualityAggregationType;
        return null;
    }

    public CloneFactoryParameters getCloneFactoryParameters() {
        return cloneFactoryParameters;
    }

    public boolean getSeparateByV() {
        return separateByV;
    }

    public boolean getSeparateByJ() {
        return separateByJ;
    }

    public boolean getSeparateByC() {
        return separateByC;
    }

    public boolean getSeparateBy(GeneType gt) {
        switch (gt) {
            case Variable:
                return getSeparateByV();
            case Joining:
                return getSeparateByJ();
            case Constant:
                return getSeparateByC();
            default:
                throw new IllegalArgumentException();
        }
    }

    public double getMaximalPreClusteringRatio() {
        return maximalPreClusteringRatio;
    }

    public boolean isAddReadsCountOnClustering() {
        return addReadsCountOnClustering;
    }

    public byte getBadQualityThreshold() {
        return badQualityThreshold;
    }

    public double getMaxBadPointsPercent() {
        return maxBadPointsPercent;
    }

    public void setMaxBadPointsPercent(double maxBadPointsPercent) {
        this.maxBadPointsPercent = maxBadPointsPercent;
    }

    public String getMappingThreshold() {
        return mappingThreshold;
    }

    public void setMappingThreshold(String mappingThreshold) {
        this.mappingThreshold = mappingThreshold;
        updateVariants();
    }

    public CloneAssemblerParameters setMinimalClonalSequenceLength(int minimalClonalSequenceLength) {
        this.minimalClonalSequenceLength = minimalClonalSequenceLength;
        return this;
    }

    public CloneClusteringParameters getCloneClusteringParameters() {
        return cloneClusteringParameters;
    }

    public CloneAssemblerParameters setCloneClusteringParameters(CloneClusteringParameters cloneClusteringParameters) {
        this.cloneClusteringParameters = cloneClusteringParameters;
        return this;
    }

    public CloneAssemblerParameters setAssemblingFeatures(GeneFeature[] assemblingFeatures) {
        this.assemblingFeatures = assemblingFeatures;
        return this;
    }

    public CloneAssemblerParameters setCloneFactoryParameters(CloneFactoryParameters cloneFactoryParameters) {
        this.cloneFactoryParameters = cloneFactoryParameters;
        return this;
    }

    public CloneAssemblerParameters setSeparateByV(boolean separateByV) {
        this.separateByV = separateByV;
        return this;
    }

    public CloneAssemblerParameters setSeparateByJ(boolean separateByJ) {
        this.separateByJ = separateByJ;
        return this;
    }

    public CloneAssemblerParameters setSeparateByC(boolean separateByC) {
        this.separateByC = separateByC;
        return this;
    }

    public CloneAssemblerParameters setSeparateBy(GeneType gt, boolean value) {
        switch (Objects.requireNonNull(gt)) {
            case Variable:
                separateByV = value;
                break;
            case Joining:
                separateByJ = value;
                break;
            case Constant:
                separateByC = value;
                break;
            default:
                throw new IllegalArgumentException();
        }
        return this;
    }

    public CloneAssemblerParameters setMaximalPreClusteringRatio(double maximalPreClusteringRatio) {
        this.maximalPreClusteringRatio = maximalPreClusteringRatio;
        return this;
    }

    public CloneAssemblerParameters setPreClusteringScoreFilteringRatio(double preClusteringScoreFilteringRatio) {
        this.preClusteringScoreFilteringRatio = preClusteringScoreFilteringRatio;
        return this;
    }

    public CloneAssemblerParameters setPreClusteringCountFilteringRatio(double preClusteringCountFilteringRatio) {
        this.preClusteringCountFilteringRatio = preClusteringCountFilteringRatio;
        return this;
    }


    public CloneAssemblerParameters setAddReadsCountOnClustering(boolean addReadsCountOnClustering) {
        this.addReadsCountOnClustering = addReadsCountOnClustering;
        return this;
    }

    public CloneAssemblerParameters setBadQualityThreshold(byte badQualityThreshold) {
        this.badQualityThreshold = badQualityThreshold;
        return this;
    }

    public boolean isMappingEnabled() {
        return badQualityThreshold > 0 && maxBadPointsPercent > 0.0;
    }

    public boolean isClusteringEnabled() {
        return cloneClusteringParameters != null;
    }

    public List<KeyedRecordFilter> getPostFilters() {
        return postFilters;
    }

    @Override
    @SuppressWarnings("MethodDoesntCallSuperMethod")
    public CloneAssemblerParameters clone() {
        return new CloneAssemblerParameters(assemblingFeatures.clone(), minimalClonalSequenceLength,
                qualityAggregationType,
                cloneClusteringParameters == null ? null : cloneClusteringParameters.clone(),
                cloneFactoryParameters.clone(), separateByV, separateByJ, separateByC,
                maximalPreClusteringRatio, preClusteringScoreFilteringRatio, preClusteringCountFilteringRatio, addReadsCountOnClustering, badQualityThreshold, maxBadPointsPercent,
                mappingThreshold, minimalQuality, postFilters);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CloneAssemblerParameters)) return false;

        CloneAssemblerParameters that = (CloneAssemblerParameters) o;

        if (minimalClonalSequenceLength != that.minimalClonalSequenceLength) return false;
        if (qualityAggregationType != that.qualityAggregationType) return false;
        if (separateByV != that.separateByV) return false;
        if (separateByJ != that.separateByJ) return false;
        if (separateByC != that.separateByC) return false;
        if (Double.compare(that.maximalPreClusteringRatio, maximalPreClusteringRatio) != 0) return false;
        if (Double.compare(that.preClusteringScoreFilteringRatio, preClusteringScoreFilteringRatio) != 0) return false;
        if (Double.compare(that.preClusteringCountFilteringRatio, preClusteringCountFilteringRatio) != 0) return false;
        if (addReadsCountOnClustering != that.addReadsCountOnClustering) return false;
        if (badQualityThreshold != that.badQualityThreshold) return false;
        if (Double.compare(that.maxBadPointsPercent, maxBadPointsPercent) != 0) return false;
        if (variants != that.variants) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        if (!Arrays.equals(assemblingFeatures, that.assemblingFeatures)) return false;
        if (cloneClusteringParameters != null ? !cloneClusteringParameters.equals(that.cloneClusteringParameters) : that.cloneClusteringParameters != null)
            return false;
        if (!cloneFactoryParameters.equals(that.cloneFactoryParameters))
            return false;
        if (minimalQuality != that.minimalQuality)
            return false;
        if (!Objects.equals(postFilters, that.postFilters))
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = Arrays.hashCode(assemblingFeatures);
        result = 31 * result + minimalClonalSequenceLength;
        result = 31 * result + qualityAggregationType.hashCode();
        result = 31 * result + (cloneClusteringParameters != null ? cloneClusteringParameters.hashCode() : 0);
        result = 31 * result + (cloneFactoryParameters != null ? cloneFactoryParameters.hashCode() : 0);
        result = 31 * result + (separateByV ? 1 : 0);
        result = 31 * result + (separateByJ ? 1 : 0);
        result = 31 * result + (separateByC ? 1 : 0);
        temp = Double.doubleToLongBits(maximalPreClusteringRatio);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(preClusteringScoreFilteringRatio);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(preClusteringCountFilteringRatio);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (addReadsCountOnClustering ? 1 : 0);
        result = 31 * result + (int) badQualityThreshold;
        temp = Double.doubleToLongBits(maxBadPointsPercent);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (int) (variants ^ (variants >>> 32));
        result = 31 * result + (int) (minimalQuality ^ (minimalQuality >>> 32));
        result = 31 * result + (postFilters != null ? postFilters.hashCode() : 0);
        return result;
    }
}
