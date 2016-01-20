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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.reference.GeneFeature;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE)
public final class CloneAssemblerParameters implements java.io.Serializable {
    private static final int MAX_MAPPING_REGION = 1000;
    GeneFeature[] assemblingFeatures;
    int minimalClonalSequenceLength;
    CloneClusteringParameters cloneClusteringParameters;
    CloneFactoryParameters cloneFactoryParameters;
    double maximalPreClusteringRatio;
    boolean addReadsCountOnClustering;
    byte badQualityThreshold;
    double maxBadPointsPercent;
    String mappingThreshold;
    @JsonIgnore
    long variants;

    @JsonCreator
    public CloneAssemblerParameters(@JsonProperty("assemblingFeatures") GeneFeature[] assemblingFeatures,
                                    @JsonProperty("minimalClonalSequenceLength") int minimalClonalSequenceLength,
                                    @JsonProperty("cloneClusteringParameters") CloneClusteringParameters cloneClusteringParameters,
                                    @JsonProperty("cloneFactoryParameters") CloneFactoryParameters cloneFactoryParameters,
                                    @JsonProperty("maximalPreClusteringRatio") double maximalPreClusteringRatio,
                                    @JsonProperty("addReadsCountOnClustering") boolean addReadsCountOnClustering,
                                    @JsonProperty("badQualityThreshold") byte badQualityThreshold,
                                    @JsonProperty("maxBadPointsPercent") double maxBadPointsPercent,
                                    @JsonProperty("mappingThreshold") String mappingThreshold) {
        this.assemblingFeatures = assemblingFeatures;
        this.minimalClonalSequenceLength = minimalClonalSequenceLength;
        this.cloneClusteringParameters = cloneClusteringParameters;
        this.cloneFactoryParameters = cloneFactoryParameters;
        this.maximalPreClusteringRatio = maximalPreClusteringRatio;
        this.addReadsCountOnClustering = addReadsCountOnClustering;
        this.badQualityThreshold = badQualityThreshold;
        this.maxBadPointsPercent = maxBadPointsPercent;
        this.mappingThreshold = mappingThreshold;
        updateVariants();
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

    public CloneFactoryParameters getCloneFactoryParameters() {
        return cloneFactoryParameters;
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

    public CloneAssemblerParameters setMaximalPreClusteringRatio(double maximalPreClusteringRatio) {
        this.maximalPreClusteringRatio = maximalPreClusteringRatio;
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
        return maxBadPointsPercent > 0.0;
    }

    public boolean isClusteringEnabled() {
        return cloneClusteringParameters != null;
    }

    @Override
    public CloneAssemblerParameters clone() {
        return new CloneAssemblerParameters(assemblingFeatures.clone(), minimalClonalSequenceLength,
                cloneClusteringParameters == null ? null : cloneClusteringParameters.clone(),
                cloneFactoryParameters.clone(), maximalPreClusteringRatio,
                addReadsCountOnClustering, badQualityThreshold, maxBadPointsPercent,
                mappingThreshold);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CloneAssemblerParameters)) return false;

        CloneAssemblerParameters that = (CloneAssemblerParameters) o;

        if (minimalClonalSequenceLength != that.minimalClonalSequenceLength) return false;
        if (Double.compare(that.maximalPreClusteringRatio, maximalPreClusteringRatio) != 0) return false;
        if (addReadsCountOnClustering != that.addReadsCountOnClustering) return false;
        if (badQualityThreshold != that.badQualityThreshold) return false;
        if (Double.compare(that.maxBadPointsPercent, maxBadPointsPercent) != 0) return false;
        if (variants != that.variants) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        if (!Arrays.equals(assemblingFeatures, that.assemblingFeatures)) return false;
        if (cloneClusteringParameters != null ? !cloneClusteringParameters.equals(that.cloneClusteringParameters) : that.cloneClusteringParameters != null)
            return false;
        return cloneFactoryParameters.equals(that.cloneFactoryParameters);

    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = Arrays.hashCode(assemblingFeatures);
        result = 31 * result + minimalClonalSequenceLength;
        result = 31 * result + (cloneClusteringParameters != null ? cloneClusteringParameters.hashCode() : 0);
        result = 31 * result + cloneFactoryParameters.hashCode();
        temp = Double.doubleToLongBits(maximalPreClusteringRatio);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (addReadsCountOnClustering ? 1 : 0);
        result = 31 * result + (int) badQualityThreshold;
        temp = Double.doubleToLongBits(maxBadPointsPercent);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (int) (variants ^ (variants >>> 32));
        return result;
    }
}
