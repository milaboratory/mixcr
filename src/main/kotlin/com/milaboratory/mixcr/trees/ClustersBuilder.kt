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
@file:Suppress("PrivatePropertyName", "LocalVariableName", "FunctionName")

package com.milaboratory.mixcr.trees

import com.milaboratory.core.Range
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.util.AdjacencyMatrix
import com.milaboratory.mixcr.util.BitArrayInt
import com.milaboratory.mixcr.util.intersectionCount
import com.milaboratory.mixcr.util.without
import io.repseq.core.GeneFeature
import io.repseq.core.VDJCGeneId
import java.util.*
import kotlin.math.max
import kotlin.math.min

sealed class ClustersBuilder<T : Any> {
    abstract fun buildClusters(original: List<T>): List<List<T>>

    interface ClusterPredictor<T> {
        fun prefer(first: T, second: T): Boolean
        fun fromTheSameCluster(first: T, second: T): Boolean
        fun fromTheSameCluster(cluster: Collection<T>, toCompare: T): Boolean
    }

    class ClusterPredictorForCellGroup(
        private val maxNDNDistanceForHeavyChain: Double,
        private val maxNDNDistanceForLightChain: Double,
        private val scoringSet: ScoringSet
    ) : ClusterPredictor<ChainPairRebasedFromGermline> {
        override fun prefer(first: ChainPairRebasedFromGermline, second: ChainPairRebasedFromGermline): Boolean =
            first.heavy.mutations.NDNRange == second.heavy.mutations.NDNRange && !first.heavy.mutations.NDNRange.isReverse &&
                    first.light.mutations.NDNRange == second.light.mutations.NDNRange && !first.light.mutations.NDNRange.isReverse


        override fun fromTheSameCluster(
            first: ChainPairRebasedFromGermline,
            second: ChainPairRebasedFromGermline
        ): Boolean = fromTheSameCluster(
            first = first,
            second = second,
            NDNRangeForHeavy = mergedNDNRange(first.heavy, second.heavy),
            NDNRangeForLight = mergedNDNRange(first.light, second.light)
        )

        override fun fromTheSameCluster(
            cluster: Collection<ChainPairRebasedFromGermline>,
            toCompare: ChainPairRebasedFromGermline
        ): Boolean {
            val NDNRangeForHeavy = mergeNDNRanges(
                cluster.map { it.heavy }.mostPossibleNDNRange(),
                toCompare.heavy.mutations.NDNRange
            )
            val NDNRangeForLight = mergeNDNRanges(
                cluster.map { it.light }.mostPossibleNDNRange(),
                toCompare.light.mutations.NDNRange
            )
            return cluster.any { fromTheSameCluster(it, toCompare, NDNRangeForHeavy, NDNRangeForLight) }
        }

        private fun fromTheSameCluster(
            first: ChainPairRebasedFromGermline,
            second: ChainPairRebasedFromGermline,
            NDNRangeForHeavy: Range,
            NDNRangeForLight: Range
        ): Boolean =
            scoringSet.NDNDistance(first.heavy, second.heavy, NDNRangeForHeavy) <= maxNDNDistanceForHeavyChain &&
                    scoringSet.NDNDistance(first.light, second.light, NDNRangeForLight) <= maxNDNDistanceForLightChain

    }

    class ClusterPredictorForOneChain(
        private val commonMutationsCountForClustering: Int,
        private val maxNDNDistance: Double,
        private val scoringSet: ScoringSet,
        originalClones: List<CloneWithMutationsFromVJGermline>,
        VJBase: VJBase,
        relatedAllelesMutations: Map<VDJCGeneId, List<Mutations<NucleotideSequence>>>
    ) : ClusterPredictor<CloneWithMutationsFromVJGermline> {
        private val VMutationWithoutAlleles: Map<CloneWrapper.ID, List<Iterable<Mutations<NucleotideSequence>>>>
        private val JMutationWithoutAlleles: Map<CloneWrapper.ID, List<Iterable<Mutations<NucleotideSequence>>>>

        init {
            val VAllelesMutations =
                relatedAllelesMutations[VJBase.geneIds.V] ?: listOf(Mutations.EMPTY_NUCLEOTIDE_MUTATIONS)
            val JAllelesMutations =
                relatedAllelesMutations[VJBase.geneIds.J] ?: listOf(Mutations.EMPTY_NUCLEOTIDE_MUTATIONS)

            VMutationWithoutAlleles = originalClones.associate { clone ->
                clone.cloneWrapper.id to VAllelesMutations.map { without(clone.mutations.mutations.V, it) }
            }
            JMutationWithoutAlleles = originalClones.associate { clone ->
                clone.cloneWrapper.id to JAllelesMutations.map { without(clone.mutations.mutations.J, it) }
            }
        }

        override fun prefer(
            first: CloneWithMutationsFromVJGermline,
            second: CloneWithMutationsFromVJGermline
        ): Boolean =
            first.mutations.NDNRange == second.mutations.NDNRange && !first.mutations.NDNRange.isReverse

        override fun fromTheSameCluster(
            first: CloneWithMutationsFromVJGermline,
            second: CloneWithMutationsFromVJGermline
        ): Boolean = fromTheSameCluster(first, second, mergedNDNRange(first, second))

        override fun fromTheSameCluster(
            cluster: Collection<CloneWithMutationsFromVJGermline>,
            toCompare: CloneWithMutationsFromVJGermline
        ): Boolean = if (cluster.size == 1) {
            fromTheSameCluster(cluster.first(), toCompare)
        } else {
            val NDNRange = mergeNDNRanges(cluster.mostPossibleNDNRange(), toCompare.mutations.NDNRange)
            cluster.any { fromTheSameCluster(it, toCompare, NDNRange) }
        }

        private fun fromTheSameCluster(
            first: CloneWithMutationsFromVJGermline,
            second: CloneWithMutationsFromVJGermline,
            NDNRange: Range
        ): Boolean =
            when {
                commonMutationsCount(first, second) < commonMutationsCountForClustering -> false
                else -> scoringSet.NDNDistance(first, second, NDNRange) <= maxNDNDistance
            }

        private fun without(
            mutations: SortedMap<GeneFeature, Mutations<NucleotideSequence>>,
            alleleMutations: Mutations<NucleotideSequence>
        ): Iterable<Mutations<NucleotideSequence>> = when {
            alleleMutations.isEmpty -> mutations.values
            else -> mutations.map { (_, mutations) ->
                mutations.without(alleleMutations)
            }
        }

        /**
         * Calculate common mutations in clone pair but mutations from allele mutations.
         * So if clones have a mutation, but there is allele of this gene with the same mutation, this mutation will be omitted in count.
         */
        private fun commonMutationsCount(
            first: CloneWithMutationsFromVJGermline,
            second: CloneWithMutationsFromVJGermline
        ): Int =
            commonMutationsCount(
                VMutationWithoutAlleles[first.cloneWrapper.id]!!,
                VMutationWithoutAlleles[second.cloneWrapper.id]!!
            ) + commonMutationsCount(
                JMutationWithoutAlleles[first.cloneWrapper.id]!!,
                JMutationWithoutAlleles[second.cloneWrapper.id]!!
            )

        private fun commonMutationsCount(
            first: List<Iterable<Mutations<NucleotideSequence>>>,
            second: List<Iterable<Mutations<NucleotideSequence>>>
        ): Int = first.indices.minOf { i ->
            commonMutationsCount(first[i], second[i])
        }

        private fun commonMutationsCount(
            first: Iterable<Mutations<NucleotideSequence>>,
            second: Iterable<Mutations<NucleotideSequence>>
        ): Int {
            var result = 0
            val secondIterator = second.iterator()
            first.iterator().forEach { firstElement ->
                val secondElement = secondIterator.next()
                result += secondElement.intersectionCount(firstElement)
            }
            return result
        }

    }

    class BronKerbosch<T : Any>(
        private val clusterPredictor: ClusterPredictor<T>
    ) : ClustersBuilder<T>() {
        /**
         * Contract: clones have min parameters.commonMutationsCountForClustering mutations from any germline
         *
         * Assumption: if there are more than parameters.commonMutationsCountForClustering the same mutations and
         * NDN region is somehow similar - than this clones from the same tree.
         *
         * 1. Make matrix with marked clone pairs that match assumption
         * 2. Find cliques in this matrix
         *
         * Thoughts:
         * - Lower parameters.commonMutationsCountForClustering may be used if we calculate probability of specific mutation.
         * Probabilities may be calculated on frequencies of mutations in specific gene in all clones of all samples.
         * Or it may be arbitrary data.
         * If it calculated from samples, then it will include impact both of hotspots and pressure of selection.
         * - Threshold of NDNDistance may be function of count of the same mutations in a pair and count of different synonymic mutations.
         */
        override fun buildClusters(original: List<T>): List<List<T>> {
            val matrix = AdjacencyMatrix(original.size)
            for (i in original.indices) {
                for (j in original.indices) {
                    if (clusterPredictor.fromTheSameCluster(original[i], original[j])) {
                        matrix.setConnected(i, j)
                    }
                }
            }
            val notOverlappedCliques = mutableListOf<BitArrayInt>()
            matrix.calculateMaximalCliques()
                .filter { it.bitCount() > 1 }
                .sortedByDescending { it.bitCount() }
                .forEach { clique ->
                    if (notOverlappedCliques.none { it.intersects(clique) }) {
                        notOverlappedCliques.add(clique)
                    }
                }
            return notOverlappedCliques
                .map { it.bits.map { i -> original[i] } }
        }
    }

    class SingleLinkage<T : Any>(
        private val clusterPredictor: ClusterPredictor<T>
    ) : ClustersBuilder<T>() {
        /**
         * Contract: clones have min parameters.commonMutationsCountForClustering mutations from any germline
         *
         * Assumption: if there are more than parameters.commonMutationsCountForClustering the same mutations and
         * NDN region is somehow similar - than this clones from the same tree.
         *
         * Clusters formed by hierarchical clustering
         *
         * Optimizations that lose data:
         * 1. Process all files separately. Lose clusters with size 2 that form by clone pair from different files.
         *
         *
         * Thoughts:
         * - Lower parameters.commonMutationsCountForClustering may be used if we calculate probability of specific mutation.
         * Probabilities may be calculated on frequencies of mutations in specific gene in all clones of all samples.
         * Or it may be arbitrary data.
         * If it calculated from samples, then it will include impact both of hotspots and pressure of selection.
         * - Threshold of NDNDistance may be function of count of the same mutations in a pair and count of different synonymic mutations.
         */
        override fun buildClusters(original: List<T>): List<List<T>> {
            val result = mutableListOf<MutableList<T>>()
            //TODO just repeat the process instead of search of clusters
            //cluster only preferable clones
            original.forEach { nextClone ->
                val clustersToGrow = result
                    .filter { existedCluster ->
                        existedCluster.any { cloneInCluster ->
                            clusterPredictor.prefer(nextClone, cloneInCluster)
                        }
                    }
                    .filter { existedCluster ->
                        clusterPredictor.fromTheSameCluster(existedCluster, nextClone)
                    }
                if (clustersToGrow.isNotEmpty()) {
                    clustersToGrow.first() += nextClone
                    clustersToGrow.subList(1, clustersToGrow.size).forEach { clusterToMerge ->
                        clustersToGrow.first() += clusterToMerge
                        result.remove(clusterToMerge)
                    }
                } else {
                    result += mutableListOf(nextClone)
                }
            }
            val clonesThatWasNotClusteredInFirstTry = result.filter { it.size == 1 }.flatten()
            result.removeIf { it.size == 1 }

            //cluster clones that was left
            clonesThatWasNotClusteredInFirstTry.forEach { nextClone ->
                val clustersToGrow = result
                    .filter { existedCluster ->
                        clusterPredictor.fromTheSameCluster(existedCluster, nextClone)
                    }
                if (clustersToGrow.isNotEmpty()) {
                    clustersToGrow.first() += nextClone
                    clustersToGrow.subList(1, clustersToGrow.size).forEach { clusterToMerge ->
                        clustersToGrow.first() += clusterToMerge
                        result.remove(clusterToMerge)
                    }
                } else {
                    result += mutableListOf(nextClone)
                }
            }
            return result.filter { it.size > 1 }
        }
    }
}

private fun ScoringSet.NDNDistance(
    first: CloneWithMutationsFromVJGermline,
    second: CloneWithMutationsFromVJGermline,
    NDNRange: Range
): Double = NDNDistance(first.mutations.CDR3, second.mutations.CDR3, NDNRange)

private fun ScoringSet.NDNDistance(
    firstCDR3: NucleotideSequence,
    secondCDR3: NucleotideSequence,
    NDNRange: Range
): Double {
    check(firstCDR3.size() == secondCDR3.size())
    return NDNDistance(firstCDR3.getRange(NDNRange), secondCDR3.getRange(NDNRange))
}

private fun Collection<CloneWithMutationsFromVJGermline>.mostPossibleNDNRange(): Range =
    map { it.mutations.NDNRange }
        .groupingBy { it }.eachCount()
        .entries
        .maxByOrNull { it.value }!!.key

private fun mergedNDNRange(
    first: CloneWithMutationsFromVJGermline,
    second: CloneWithMutationsFromVJGermline
): Range = Range(
    min(first.mutations.VEndTrimmedPosition, second.mutations.VEndTrimmedPosition),
    max(first.mutations.JBeginTrimmedPosition, second.mutations.JBeginTrimmedPosition)
)

private fun mergeNDNRanges(
    firstNDNRange: Range,
    secondNDNRange: Range
) = Range(
    min(firstNDNRange.lower, secondNDNRange.lower),
    max(firstNDNRange.upper, secondNDNRange.upper)
)

/**
 * May be inverted in extreme cases
 */
private val MutationsFromVJGermline.NDNRange: Range
    get() = Range(VEndTrimmedPosition, JBeginTrimmedPosition)
