/*
 * Copyright (c) 2014-2019, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
 * commercial purposes should contact MiLaboratory LLC, which owns exclusive
 * rights for distribution of this program for commercial purposes, using the
 * following email address: licensing@milaboratory.com.
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