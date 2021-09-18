/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 *
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 *
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.assembler;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.core.tree.TreeSearchParameters;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE)
public final class CloneClusteringParameters implements java.io.Serializable {
    private int searchDepth;
    private int allowedMutationsInNRegions;
    private TreeSearchParameters searchParameters;
    private ClusteringFilter clusteringFilter;

    @JsonCreator
    public CloneClusteringParameters(
            @JsonProperty("searchDepth") int searchDepth,
            @JsonProperty("allowedMutationsInNRegions") int allowedMutationsInNRegions,
            @JsonProperty("searchParameters") TreeSearchParameters searchParameters,
            @JsonProperty("clusteringFilter") ClusteringFilter clusteringFilter) {
        this.searchDepth = searchDepth;
        this.allowedMutationsInNRegions = allowedMutationsInNRegions;
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

    @Override
    public CloneClusteringParameters clone() {
        return new CloneClusteringParameters(searchDepth, allowedMutationsInNRegions, searchParameters, clusteringFilter);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CloneClusteringParameters)) return false;

        CloneClusteringParameters that = (CloneClusteringParameters) o;

        if (allowedMutationsInNRegions != that.allowedMutationsInNRegions) return false;
        if (searchDepth != that.searchDepth) return false;
        if (!clusteringFilter.equals(that.clusteringFilter)) return false;
        if (!searchParameters.equals(that.searchParameters)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = searchDepth;
        result = 31 * result + allowedMutationsInNRegions;
        result = 31 * result + searchParameters.hashCode();
        result = 31 * result + clusteringFilter.hashCode();
        return result;
    }
}
