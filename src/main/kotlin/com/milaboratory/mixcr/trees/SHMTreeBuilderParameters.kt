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

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.milaboratory.mixcr.util.ParametersPresets
import com.milaboratory.primitivio.annotations.Serializable

/**
 *
 */
@Serializable(asJson = true)
data class SHMTreeBuilderParameters @JsonCreator constructor(
    @param:JsonProperty("singleCell") val singleCell: SingleCell,
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
     * Count of the nearest nodes to added that will be proceeded to find optimal insertion.
     */
    @param:JsonProperty("countOfNodesToProbe") val countOfNodesToProbe: Int,
    /**
     * Multiplier of NDN score on calculating distance between clones in a tree.
     */
    @param:JsonProperty("NDNScoreMultiplier")
    @get:JsonProperty("NDNScoreMultiplier")
    val NDNScoreMultiplier: Double,
    /**
     * Algorithm for the zero step
     */
    @param:JsonProperty("initialClusterization") val initialClusterization: ClusterizationAlgorithm,
    /**
     * Order of steps to postprocess trees.
     */
    @param:JsonProperty("stepsOrder") val stepsOrder: List<BuildSHMTreeStep>,
    @param:JsonProperty("NDNSizeLimitForPublicClones")
    @get:JsonProperty("NDNSizeLimitForPublicClones")
    val NDNSizeLimitForPublicClones: Int
) {

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
    )
    @JsonSubTypes(
        JsonSubTypes.Type(ClusterizationAlgorithm.Hierarchical::class, name = "hierarchical"),
        JsonSubTypes.Type(ClusterizationAlgorithm.BronKerbosch::class, name = "BronKerbosch")
    )
    sealed class ClusterizationAlgorithm(
        /**
         * Min count of common mutations in VJ for pair to form first clusterization.
         */
        val commonMutationsCountForClustering: Int,
        /**
         * Max penalty of NDN mutations per NDN length for pair to form first clusterization.
         */
        val maxNDNDistanceForClustering: Double
    ) {
        class Hierarchical @JsonCreator constructor(
            @JsonProperty("commonMutationsCountForClustering") commonMutationsCountForClustering: Int,
            @JsonProperty("maxNDNDistanceForClustering") maxNDNDistanceForClustering: Double
        ) : ClusterizationAlgorithm(commonMutationsCountForClustering, maxNDNDistanceForClustering)

        class BronKerbosch @JsonCreator constructor(
            @JsonProperty("commonMutationsCountForClustering") commonMutationsCountForClustering: Int,
            @JsonProperty("maxNDNDistanceForClustering") maxNDNDistanceForClustering: Double
        ) : ClusterizationAlgorithm(commonMutationsCountForClustering, maxNDNDistanceForClustering)
    }

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
    )
    @JsonSubTypes(
        JsonSubTypes.Type(ClusterizationAlgorithmForSingleCell.Hierarchical::class, name = "hierarchical"),
        JsonSubTypes.Type(ClusterizationAlgorithmForSingleCell.BronKerbosch::class, name = "BronKerbosch")
    )
    sealed class ClusterizationAlgorithmForSingleCell(
        /**
         * Max penalty of NDN mutations per NDN length for a pair of heavy light chains
         */
        val maxNDNDistanceForHeavyChain: Double,
        /**
         * Max penalty of NDN mutations per NDN length for a pair of light chains
         */
        val maxNDNDistanceForLightChain: Double,
    ) {
        class Hierarchical @JsonCreator constructor(
            @JsonProperty("maxNDNDistanceForLightChain") maxNDNDistanceForLightChain: Double,
            @JsonProperty("maxNDNDistanceForHeavyChain") maxNDNDistanceForHeavyChain: Double
        ) : ClusterizationAlgorithmForSingleCell(maxNDNDistanceForHeavyChain, maxNDNDistanceForLightChain)

        class BronKerbosch @JsonCreator constructor(
            @JsonProperty("maxNDNDistanceForLightChain") maxNDNDistanceForLightChain: Double,
            @JsonProperty("maxNDNDistanceForHeavyChain") maxNDNDistanceForHeavyChain: Double
        ) : ClusterizationAlgorithmForSingleCell(maxNDNDistanceForHeavyChain, maxNDNDistanceForLightChain)
    }

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
    )
    @JsonSubTypes(
        JsonSubTypes.Type(SingleCell.NoOP::class, name = "no op"),
        JsonSubTypes.Type(SingleCell.SimpleClustering::class, name = "simpleClustering")
    )
    sealed class SingleCell {
        class NoOP : SingleCell() {
            override fun equals(other: Any?): Boolean = other is NoOP
            override fun hashCode(): Int = 0
        }

        data class SimpleClustering @JsonCreator constructor(
            @param:JsonProperty("algorithm") val algorithm: ClusterizationAlgorithmForSingleCell
        ) : SingleCell()
    }

    companion object {
        val presets = ParametersPresets<SHMTreeBuilderParameters>("shm_tree_parameters")
    }
}
