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
package com.milaboratory.mixcr.trees;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.primitivio.annotations.Serializable;
import io.repseq.core.GeneFeature;

import java.util.Objects;

/**
 *
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE)
@Serializable(asJson = true)
public class SHMTreeBuilderParameters implements java.io.Serializable {
    /**
     * Feature that is covered by all clonotypes (e.g. VDJRegion for full-length data)
     */
    public final GeneFeature targetRegion;
    /**
     * Use only productive clonotypes (no OOF, no stops)
     */
    public final boolean productiveOnly;

    /**
     * Parameter that defines sensitivity of clusterisation
     */
    public final double maxDistanceWithinCluster;
    /**
     * Hide small trees
     */
    public final int hideTreesLessThanSize;
    public final int commonMutationsCountForClustering;

    @JsonCreator
    public SHMTreeBuilderParameters(
            @JsonProperty("targetRegion") GeneFeature targetRegion,
            @JsonProperty("productiveOnly") boolean productiveOnly,
            @JsonProperty("maxDistanceWithinCluster") double maxDistanceWithinCluster,
            @JsonProperty("hideTreesLessThanSize") int hideTreesLessThanSize,
            @JsonProperty("commonMutationsCountForClustering") int commonMutationsCountForClustering
    ) {
        this.targetRegion = targetRegion;
        this.productiveOnly = productiveOnly;
        this.maxDistanceWithinCluster = maxDistanceWithinCluster;
        this.hideTreesLessThanSize = hideTreesLessThanSize;
        this.commonMutationsCountForClustering = commonMutationsCountForClustering;
    }

    @Override
    public SHMTreeBuilderParameters clone() {
        return new SHMTreeBuilderParameters(targetRegion, productiveOnly, maxDistanceWithinCluster, hideTreesLessThanSize, commonMutationsCountForClustering);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SHMTreeBuilderParameters that = (SHMTreeBuilderParameters) o;
        return productiveOnly == that.productiveOnly && Double.compare(that.maxDistanceWithinCluster, maxDistanceWithinCluster) == 0 && hideTreesLessThanSize == that.hideTreesLessThanSize && commonMutationsCountForClustering == that.commonMutationsCountForClustering && Objects.equals(targetRegion, that.targetRegion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetRegion, productiveOnly, maxDistanceWithinCluster, hideTreesLessThanSize, commonMutationsCountForClustering);
    }
}
