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
package com.milaboratory.mixcr.trees

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.milaboratory.mixcr.cli.BuildSHMTreeStep
import com.milaboratory.primitivio.annotations.Serializable
import io.repseq.core.GeneFeature

/**
 *
 */
@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.ANY,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE,
    getterVisibility = JsonAutoDetect.Visibility.NONE
)
@Serializable(asJson = true)
data class SHMTreeBuilderParameters @JsonCreator constructor(
    /**
     * Feature that is covered by all clonotypes (e.g. VDJRegion for full-length data).
     */
    @param:JsonProperty("targetRegion") val targetRegion: GeneFeature?,
    /**
     * Use only productive clonotypes (no OOF, no stops).
     */
    @param:JsonProperty("productiveOnly") val productiveOnly: Boolean,
    /**
     * Clone will be accepted if distanceFromRoot / changeOfDistanceOnCloneAdd >= thresholdForFreeClones.
     */
    @param:JsonProperty("thresholdForFreeClones") val thresholdForFreeClones: Double,
    /**
     * Clone will be combined with a tree if penalty by letter on alignment of NDN on root will be less than this value.
     */
    @param:JsonProperty("thresholdForCombineByNDN") val thresholdForCombineByNDN: Double,
    /**
     * Trees will be combined if distance between roots will be less than this value.
     */
    @param:JsonProperty("thresholdForCombineTrees") val thresholdForCombineTrees: Double,
    /**
     * Count of clones nearest to the root that will be used to determinate borders of NDN region.
     */
    @param:JsonProperty("topToVoteOnNDNSize") val topToVoteOnNDNSize: Int,
    /**
     * Penalty that will be multiplied by reversed mutations count.
     */
    @param:JsonProperty("penaltyForReversedMutations") val penaltyForReversedMutations: Double,
    /**
     * Hide small trees.
     */
    @param:JsonProperty("hideTreesLessThanSize") val hideTreesLessThanSize: Int,
    @param:JsonProperty("commonMutationsCountForClustering") val commonMutationsCountForClustering: Int,
    @param:JsonProperty("maxNDNDistanceForClustering") val maxNDNDistanceForClustering: Int,
    /**
     * Count of the nearest nodes to added that will be proceeded to find optimal insertion.
     */
    @param:JsonProperty("countOfNodesToProbe") val countOfNodesToProbe: Int,
    /**
     * Multiplier of NDN score on calculating distance between clones in a tree.
     */
    @param:JsonProperty("NDNScoreMultiplier") val NDNScoreMultiplier: Double,
    /**
     * Order of steps to postprocess trees.
     */
    @param:JsonProperty("stepsOrder") val stepsOrder: List<BuildSHMTreeStep>,
    /**
     * Min portion of clones to determinate common alignment ranges.
     */
    @param:JsonProperty("minPortionOfClonesForCommonAlignmentRanges") val minPortionOfClonesForCommonAlignmentRanges: Double,
    @param:JsonProperty("NDNSizeLimitForPublicClones") val NDNSizeLimitForPublicClones: Int
) : java.io.Serializable
