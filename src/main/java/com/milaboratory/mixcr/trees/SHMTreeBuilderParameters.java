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

import java.util.List;
import java.util.Objects;

/**
 *
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE)
@Serializable(asJson = true)
public class SHMTreeBuilderParameters implements java.io.Serializable {
    /**
     * Feature that is covered by all clonotypes (e.g. VDJRegion for full-length data).
     */
    public final GeneFeature targetRegion;
    /**
     * Use only productive clonotypes (no OOF, no stops).
     */
    public final boolean productiveOnly;

    /**
     * Clone will be accepted if distanceFromRoot / changeOfDistanceOnCloneAdd >= thresholdForFreeClones.
     */
    public final double thresholdForFreeClones;
    /**
     * Clone will be combined with a tree if penalty by letter on alignment of NDN on root will be less than this value.
     */
    public final double thresholdForCombineByNDN;
    /**
     * Trees will be combined if distance between roots will be less than this value.
     */
    public final double thresholdForCombineTrees;
    /**
     * Count of clones nearest to the root that will be used to determinate borders of NDN region.
     */
    public final int topToVoteOnNDNSize;
    /**
     * Penalty that will be multiplied by reversed mutations count.
     */
    public final double penaltyForReversedMutations;
    /**
     * Hide small trees.
     */
    public final int hideTreesLessThanSize;
    public final int commonMutationsCountForClustering;
    public final int maxNDNDistanceForClustering;
    /**
     * Count of the nearest nodes to added that will be proceeded to find optimal insertion.
     */
    public final int countOfNodesToProbe;
    /**
     * Multiplier of NDN score on calculating distance between clones in a tree.
     */
    public final double NDNScoreMultiplier;
    /**
     * Order of steps to postprocess trees.
     */
    public final List<String> stepsOrder;
    /**
     * Min portion of clones to determinate common alignment ranges.
     */
    public final double minPortionOfClonesForCommonAlignmentRanges;

    @JsonCreator
    public SHMTreeBuilderParameters(
            @JsonProperty("targetRegion") GeneFeature targetRegion,
            @JsonProperty("productiveOnly") boolean productiveOnly,
            @JsonProperty("thresholdForFreeClones") double thresholdForFreeClones,
            @JsonProperty("thresholdForCombineByNDN") double thresholdForCombineByNDN,
            @JsonProperty("thresholdForCombineTrees") double thresholdForCombineTrees,
            @JsonProperty("topToVoteOnNDNSize") int topToVoteOnNDNSize,
            @JsonProperty("penaltyForReversedMutations") double penaltyForReversedMutations,
            @JsonProperty("hideTreesLessThanSize") int hideTreesLessThanSize,
            @JsonProperty("commonMutationsCountForClustering") int commonMutationsCountForClustering,
            @JsonProperty("maxNDNDistanceForClustering") int maxNDNDistanceForClustering,
            @JsonProperty("countOfNodesToProbe") int countOfNodesToProbe,
            @JsonProperty("NDNScoreMultiplier") double NDNScoreMultiplier,
            @JsonProperty("stepsOrder") List<String> stepsOrder,
            @JsonProperty("minPortionOfClonesForCommonAlignmentRanges") double minPortionOfClonesForCommonAlignmentRanges
    ) {
        this.targetRegion = targetRegion;
        this.productiveOnly = productiveOnly;
        this.thresholdForFreeClones = thresholdForFreeClones;
        this.thresholdForCombineByNDN = thresholdForCombineByNDN;
        this.thresholdForCombineTrees = thresholdForCombineTrees;
        this.topToVoteOnNDNSize = topToVoteOnNDNSize;
        this.penaltyForReversedMutations = penaltyForReversedMutations;
        this.hideTreesLessThanSize = hideTreesLessThanSize;
        this.commonMutationsCountForClustering = commonMutationsCountForClustering;
        this.maxNDNDistanceForClustering = maxNDNDistanceForClustering;
        this.countOfNodesToProbe = countOfNodesToProbe;
        this.NDNScoreMultiplier = NDNScoreMultiplier;
        this.stepsOrder = stepsOrder;
        this.minPortionOfClonesForCommonAlignmentRanges = minPortionOfClonesForCommonAlignmentRanges;
    }

    @Override
    public SHMTreeBuilderParameters clone() {
        return new SHMTreeBuilderParameters(targetRegion, productiveOnly, thresholdForFreeClones, thresholdForCombineByNDN, thresholdForCombineTrees, topToVoteOnNDNSize, penaltyForReversedMutations, hideTreesLessThanSize, commonMutationsCountForClustering, maxNDNDistanceForClustering, countOfNodesToProbe, NDNScoreMultiplier, stepsOrder, minPortionOfClonesForCommonAlignmentRanges);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SHMTreeBuilderParameters that = (SHMTreeBuilderParameters) o;
        return productiveOnly == that.productiveOnly && Double.compare(that.thresholdForFreeClones, thresholdForFreeClones) == 0 && Double.compare(that.thresholdForCombineByNDN, thresholdForCombineByNDN) == 0 && topToVoteOnNDNSize == that.topToVoteOnNDNSize && Double.compare(that.penaltyForReversedMutations, penaltyForReversedMutations) == 0 && hideTreesLessThanSize == that.hideTreesLessThanSize && commonMutationsCountForClustering == that.commonMutationsCountForClustering && countOfNodesToProbe == that.countOfNodesToProbe && Double.compare(that.NDNScoreMultiplier, NDNScoreMultiplier) == 0 && Objects.equals(targetRegion, that.targetRegion) && Objects.equals(stepsOrder, that.stepsOrder);
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetRegion, productiveOnly, thresholdForFreeClones, thresholdForCombineByNDN, topToVoteOnNDNSize, penaltyForReversedMutations, hideTreesLessThanSize, commonMutationsCountForClustering, countOfNodesToProbe, NDNScoreMultiplier, stepsOrder);
    }
}
