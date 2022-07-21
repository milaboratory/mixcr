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
package com.milaboratory.mixcr.trees

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.milaboratory.mixcr.util.ParametersPresets
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
     * Max penalty of NDN mutations per NDN length for pair for attaching on AttachClonesByDistanceChange step.
     */
    @param:JsonProperty("maxNDNDistanceForFreeClones") val maxNDNDistanceForFreeClones: Double,
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
     * Min count of common mutations in VJ for pair to form first clusterization.
     */
    @param:JsonProperty("commonMutationsCountForClustering") val commonMutationsCountForClustering: Int,
    /**
     * Max penalty of NDN mutations per NDN length for pair to form first clusterization.
     */
    @param:JsonProperty("maxNDNDistanceForClustering") val maxNDNDistanceForClustering: Double,
    /**
     * Count of the nearest nodes to added that will be proceeded to find optimal insertion.
     */
    @param:JsonProperty("countOfNodesToProbe") val countOfNodesToProbe: Int,
    /**
     * Multiplier of NDN score on calculating distance between clones in a tree.
     */
    @param:JsonProperty("NDNScoreMultiplier") val NDNScoreMultiplier: Double,
    /**
     * Algorithm for the zero step. BronKerbosch|Hierarchical
     */
    @param:JsonProperty("buildingInitialTreesAlgorithm") val buildingInitialTreesAlgorithm: String,
    /**
     * Order of steps to postprocess trees.
     */
    @param:JsonProperty("stepsOrder") val stepsOrder: List<BuildSHMTreeStep>,
    /**
     * Min portion of clones to determinate common alignment ranges.
     */
    @param:JsonProperty("minPortionOfClonesForCommonAlignmentRanges") val minPortionOfClonesForCommonAlignmentRanges: Double,
    @param:JsonProperty("NDNSizeLimitForPublicClones") val NDNSizeLimitForPublicClones: Int
) : java.io.Serializable {
    companion object {
        val presets = ParametersPresets<SHMTreeBuilderParameters>("shm_tree_parameters")
    }
}
