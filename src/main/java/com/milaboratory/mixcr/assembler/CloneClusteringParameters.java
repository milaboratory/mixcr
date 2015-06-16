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