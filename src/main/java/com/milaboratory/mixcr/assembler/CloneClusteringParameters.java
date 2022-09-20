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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonMerge;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.core.tree.TreeSearchParameters;

import java.util.Objects;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE)
public final class CloneClusteringParameters implements java.io.Serializable {
    private int searchDepth;
    private int allowedMutationsInNRegions;
    private double minimalTagSetOverlap;
    private TreeSearchParameters searchParameters;
    private ClusteringFilter clusteringFilter;

    @JsonCreator
    public CloneClusteringParameters(
            @JsonProperty("searchDepth") int searchDepth,
            @JsonProperty("allowedMutationsInNRegions") int allowedMutationsInNRegions,
            @JsonProperty("minimalTagSetOverlap") double minimalTagSetOverlap,
            @JsonProperty("searchParameters") TreeSearchParameters searchParameters,
            @JsonProperty("clusteringFilter") ClusteringFilter clusteringFilter) {
        this.searchDepth = searchDepth;
        this.allowedMutationsInNRegions = allowedMutationsInNRegions;
        this.minimalTagSetOverlap = minimalTagSetOverlap;
        this.searchParameters = searchParameters;
        this.clusteringFilter = clusteringFilter;
    }

    public int getSearchDepth() {
        return searchDepth;
    }

    public int getAllowedMutationsInNRegions() {
        return allowedMutationsInNRegions;
    }

    public TreeSearchParameters getSearchParameters() {
        return searchParameters;
    }

    public ClusteringFilter getClusteringFilter() {
        return clusteringFilter;
    }

    public CloneClusteringParameters setSearchDepth(int searchDepth) {
        this.searchDepth = searchDepth;
        return this;
    }

    public CloneClusteringParameters setAllowedMutationsInNRegions(int allowedMutationsInNRegions) {
        this.allowedMutationsInNRegions = allowedMutationsInNRegions;
        return this;
    }

    public CloneClusteringParameters setSearchParameters(TreeSearchParameters searchParameters) {
        this.searchParameters = searchParameters;
        return this;
    }

    public CloneClusteringParameters setClusteringFilter(ClusteringFilter clusteringFilter) {
        this.clusteringFilter = clusteringFilter;
        return this;
    }

    public double getMinimalTagSetOverlap() {
        return minimalTagSetOverlap;
    }

    public CloneClusteringParameters setMinimalTagSetOverlap(double minimalTagSetOverlap) {
        this.minimalTagSetOverlap = minimalTagSetOverlap;
        return this;
    }

    @Override
    public CloneClusteringParameters clone() {
        return new CloneClusteringParameters(searchDepth, allowedMutationsInNRegions, minimalTagSetOverlap, searchParameters, clusteringFilter);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CloneClusteringParameters that = (CloneClusteringParameters) o;
        return getSearchDepth() == that.getSearchDepth() &&
                getAllowedMutationsInNRegions() == that.getAllowedMutationsInNRegions() &&
                Double.compare(that.minimalTagSetOverlap, minimalTagSetOverlap) == 0 &&
                Objects.equals(getSearchParameters(), that.getSearchParameters()) &&
                Objects.equals(getClusteringFilter(), that.getClusteringFilter());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getSearchDepth(), getAllowedMutationsInNRegions(), minimalTagSetOverlap, getSearchParameters(), getClusteringFilter());
    }
}