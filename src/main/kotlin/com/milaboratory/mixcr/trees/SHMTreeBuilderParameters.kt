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
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.milaboratory.mixcr.util.ParametersPresets

@JsonAutoDetect(
    fieldVisibility = ANY,
    isGetterVisibility = NONE,
    getterVisibility = NONE
)
data class SHMTreeBuilderParameters(
    val singleCell: SingleCell,
    /**
     * Use only productive clonotypes (no OOF, no stops).
     */
    val productiveOnly: Boolean,
    /**
     * Order of steps to postprocess trees.
     */
    val steps: List<BuildSHMTreeStep>,
    val topologyBuilder: TopologyBuilderParameters
) {

    @JsonAutoDetect(
        fieldVisibility = ANY,
        isGetterVisibility = NONE,
        getterVisibility = NONE
    )
    data class TopologyBuilderParameters(
        /**
         * Count of clones nearest to the root that will be used to determinate borders of NDN region.
         */
        val topToVoteOnNDNSize: Int,
        /**
         * Penalty that will be multiplied by reversed mutations count.
         */
        val penaltyForReversedMutations: Double,
        /**
         * Count of the nearest nodes to added that will be proceeded to find optimal insertion.
         */
        val countOfNodesToProbe: Int,
        /**
         * Multiplier of NDN score on calculating distance between clones in a tree.
         */
        val multiplierForNDNScore: Double,
    )

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
    )
    sealed interface ClusterizationAlgorithm {
        /**
         * Min count of common mutations in V and J genes for pair to form edge in cluster.
         */
        val commonMutationsCountForClustering: Int

        /**
         * Max penalty of NDN mutations per NDN length for pair to form edge in cluster.
         */
        val maxNDNDistanceForClustering: Double

        @JsonTypeName("SingleLinkage")
        data class SingleLinkage(
            override val commonMutationsCountForClustering: Int,
            override val maxNDNDistanceForClustering: Double
        ) : ClusterizationAlgorithm

        @JsonTypeName("BronKerbosch")
        data class BronKerbosch(
            override val commonMutationsCountForClustering: Int,
            override val maxNDNDistanceForClustering: Double
        ) : ClusterizationAlgorithm
    }

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
    )
    sealed interface ClusterizationAlgorithmForSingleCell {
        /**
         * Max penalty of NDN mutations per NDN length for a pair of heavy light chains
         */
        val maxNDNDistanceForHeavyChain: Double

        /**
         * Max penalty of NDN mutations per NDN length for a pair of light chains
         */
        val maxNDNDistanceForLightChain: Double

        @JsonTypeName("SingleLinkage")
        data class SingleLinkage(
            override val maxNDNDistanceForLightChain: Double,
            override val maxNDNDistanceForHeavyChain: Double
        ) : ClusterizationAlgorithmForSingleCell

        @JsonTypeName("BronKerbosch")
        data class BronKerbosch(
            override val maxNDNDistanceForLightChain: Double,
            override val maxNDNDistanceForHeavyChain: Double
        ) : ClusterizationAlgorithmForSingleCell
    }

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
    )
    sealed interface SingleCell {
        @JsonTypeName("noop")
        class NoOP : SingleCell {
            override fun equals(other: Any?): Boolean = other is NoOP
            override fun hashCode(): Int = 0
        }

        @JsonTypeName("simpleClustering")
        data class SimpleClustering @JsonCreator constructor(
            val algorithm: ClusterizationAlgorithmForSingleCell
        ) : SingleCell
    }

    companion object {
        val presets = ParametersPresets<SHMTreeBuilderParameters>("shm_tree_parameters")
    }
}

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = "name"
)
sealed class BuildSHMTreeStep {
    /**
     * @see SHMTreeBuilderBySteps.buildTreeTopParts
     */
    @JsonTypeName("BuildingInitialTrees")
    class BuildingInitialTrees(
        val algorithm: SHMTreeBuilderParameters.ClusterizationAlgorithm
    ) : BuildSHMTreeStep()

    /**
     * @see SHMTreeBuilderBySteps.attachClonesByDistanceChange
     */
    @JsonTypeName("AttachClonesByDistanceChange")
    class AttachClonesByDistanceChange(
        /**
         * Clone will be accepted if distanceFromRoot / changeOfDistanceOnCloneAdd >= threshold.
         */
        val threshold: Double,
        /**
         * Max penalty of NDN mutations per NDN length for pair.
         */
        val maxNDNDistance: Double,
    ) : BuildSHMTreeStep()

    /**
     * @see SHMTreeBuilderBySteps.combineTrees
     */
    @JsonTypeName("CombineTrees")
    class CombineTrees(
        /**
         * Trees will be combined if distance between roots will be less than this value.
         */
        val maxNDNDistanceBetweenRoots: Double
    ) : BuildSHMTreeStep()

    /**
     * @see SHMTreeBuilderBySteps.attachClonesByNDN
     */
    @JsonTypeName("AttachClonesByNDN")
    class AttachClonesByNDN(
        /**
         * Clone will be combined with a tree if penalty by letter on alignment of NDN on root will be less than this value.
         */
        val maxNDNDistance: Double
    ) : BuildSHMTreeStep()
}

val BuildSHMTreeStep.forPrint: String
    get() = when (this) {
        is BuildSHMTreeStep.BuildingInitialTrees -> "Building initial trees"
        is BuildSHMTreeStep.AttachClonesByDistanceChange -> "Attaching clones by distance change"
        is BuildSHMTreeStep.CombineTrees -> "Combining trees"
        is BuildSHMTreeStep.AttachClonesByNDN -> "Attaching clones by NDN"
    }
