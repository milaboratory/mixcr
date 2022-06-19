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
@file:Suppress("LocalVariableName", "FunctionName")

package com.milaboratory.mixcr.trees

import com.milaboratory.core.Range
import com.milaboratory.core.alignment.Aligner
import com.milaboratory.core.alignment.AlignmentScoring
import com.milaboratory.core.alignment.AlignmentUtils
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.core.sequence.NucleotideSequence.ALPHABET
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.VDJCHit
import com.milaboratory.mixcr.cli.BuildSHMTreeStep
import com.milaboratory.mixcr.cli.BuildSHMTreeStep.AttachClonesByDistanceChange
import com.milaboratory.mixcr.cli.BuildSHMTreeStep.AttachClonesByNDN
import com.milaboratory.mixcr.cli.BuildSHMTreeStep.CombineTrees
import com.milaboratory.mixcr.trees.Tree.NodeWithParent
import com.milaboratory.mixcr.trees.TreeBuilderByAncestors.ObservedOrReconstructed
import com.milaboratory.mixcr.trees.TreeBuilderByAncestors.Reconstructed
import com.milaboratory.mixcr.trees.TreeWithMetaBuilder.MetricDecisionInfo
import com.milaboratory.mixcr.trees.TreeWithMetaBuilder.ZeroStepDecisionInfo
import com.milaboratory.mixcr.util.AdjacencyMatrix
import com.milaboratory.mixcr.util.BitArrayInt
import com.milaboratory.mixcr.util.ClonesAlignmentRanges
import com.milaboratory.mixcr.util.Cluster
import com.milaboratory.mixcr.util.asSequence
import com.milaboratory.mixcr.util.extractAbsoluteMutations
import com.milaboratory.mixcr.util.intersectionCount
import com.milaboratory.mixcr.util.without
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import io.repseq.core.ReferencePoint
import io.repseq.core.VDJCGeneId
import java.math.BigDecimal
import java.util.*
import java.util.function.Supplier
import java.util.stream.IntStream
import kotlin.math.max
import kotlin.math.min

internal class ClusterProcessor private constructor(
    private val parameters: SHMTreeBuilderParameters,
    private val scoringSet: ScoringSet,
    private val originalCluster: Cluster<CloneWrapper>,
    private val VSequence1: NucleotideSequence,
    private val JSequence1: NucleotideSequence,
    private val clusterInfo: CalculatedClusterInfo,
    private val idGenerator: IdGenerator,
    private val VJBase: VJBase
) {
    fun applyStep(stepName: BuildSHMTreeStep, currentTrees: List<TreeWithMetaBuilder>): StepResult =
        stepByName(stepName).next(currentTrees) {
            val clonesInTrees = currentTrees
                .flatMap { it.clonesAdditionHistory }
                .toSet()
            rebaseFromGermline(originalCluster.cluster.asSequence().filter { it.id !in clonesInTrees })
        }

    private fun stepByName(stepName: BuildSHMTreeStep): Step = when (stepName) {
        AttachClonesByDistanceChange -> object : Step {
            override fun next(
                originalTrees: List<TreeWithMetaBuilder>,
                clonesNotInClusters: () -> List<CloneWithMutationsFromVJGermline>
            ): StepResult = attachClonesByDistanceChange(
                originalTrees,
                clonesNotInClusters
            )
        }
        CombineTrees -> object : Step {
            override fun next(
                originalTrees: List<TreeWithMetaBuilder>,
                clonesNotInClusters: () -> List<CloneWithMutationsFromVJGermline>
            ): StepResult = combineTrees(originalTrees)
        }
        AttachClonesByNDN -> object : Step {
            override fun next(
                originalTrees: List<TreeWithMetaBuilder>,
                clonesNotInClusters: () -> List<CloneWithMutationsFromVJGermline>
            ): StepResult = attachClonesByNDN(
                originalTrees,
                clonesNotInClusters
            )
        }
        else -> throw UnsupportedOperationException()
    }

    /**
     * On stage of clustering we can't use VEnd and JBegin marking because hypermutation on P region affects accuracy.
     * While alignment in some cases it's not possible to determinate mutation of P segment from shorter V or J version and other N nucleotides.
     * So, there will be used CDR3 instead of NDN, VBegin-CDR3Begin instead V and CDR3End-JEnd instead J
     */
    fun buildTreeTopParts(relatedAllelesMutations: Map<VDJCGeneId, List<Mutations<NucleotideSequence>>>): StepResult {
        //use only clones that are at long distance from any germline
        val clonesThatNotMatchAnyGermline = originalCluster.cluster.asSequence()
            .filter { !hasVJPairThatMatchesWithGermline(it.clone) }
        val clones = rebaseFromGermline(clonesThatNotMatchAnyGermline)
        val result = clusterByCommonMutations(clones, relatedAllelesMutations).asSequence()
            .filter { it.cluster.size > 1 }
            .map { cluster -> buildATreeWithDecisionsInfo(cluster) }
            .toList()
        return buildStepResult(
            result.flatMap { it.first }.toMap(),
            result.map { it.second }.toList()
        )
    }

    private fun hasVJPairThatMatchesWithGermline(clone: Clone): Boolean = clone.getHits(Variable)
        .flatMap { VHit -> clone.getHits(Joining).map { JHit -> mutationsCount(VHit) + mutationsCount(JHit) } }
        .any { it < parameters.commonMutationsCountForClustering }

    private fun mutationsCount(hit: VDJCHit): Int = hit.alignments.sumOf { it.absoluteMutations.size() }

    fun restore(snapshot: TreeWithMetaBuilder.Snapshot): TreeWithMetaBuilder {
        val clonesInTrees = snapshot.clonesAdditionHistory.toSet()
        val clonesByIds = originalCluster.cluster
            .filter { it.id in clonesInTrees }
            .associateBy { it.id }
        val treeWithMetaBuilder = TreeWithMetaBuilder(
            createTreeBuilder(snapshot.rootInfo),
            snapshot.rootInfo,
            ClonesRebase(VSequence1, JSequence1, scoringSet),
            LinkedList(),
            snapshot.treeId
        )
        snapshot.clonesAdditionHistory.forEach { cloneId ->
            val rebasedClone = treeWithMetaBuilder.rebaseClone(rebaseFromGermline(clonesByIds[cloneId]!!))
            treeWithMetaBuilder.addClone(rebasedClone)
        }
        return treeWithMetaBuilder
    }

    private fun rebaseFromGermline(clones: Sequence<CloneWrapper>): List<CloneWithMutationsFromVJGermline> = clones
        .filter { cloneWrapper ->
            (clusterInfo.commonVAlignmentRanges.containsCloneWrapper(cloneWrapper)
                && clusterInfo.commonJAlignmentRanges.containsCloneWrapper(cloneWrapper))
        }
        .map { cloneWrapper -> rebaseFromGermline(cloneWrapper) }
        .toList()

    private fun rebaseFromGermline(cloneWrapper: CloneWrapper): CloneWithMutationsFromVJGermline {
        val CDR3 = cloneWrapper.getFeature(GeneFeature.CDR3)!!.sequence
        val VMutationsInCDR3WithoutNDN = PartInCDR3(
            clusterInfo.VRangeInCDR3,
            getMutationsForRange(cloneWrapper, clusterInfo.VRangeInCDR3, Variable)
                .extractAbsoluteMutations(clusterInfo.VRangeInCDR3, false)
        )
        val JMutationsInCDR3WithoutNDN = PartInCDR3(
            clusterInfo.JRangeInCDR3,
            getMutationsForRange(cloneWrapper, clusterInfo.JRangeInCDR3, Joining)
                .extractAbsoluteMutations(clusterInfo.JRangeInCDR3, true)
        )
        val result = MutationsFromVJGermline(
            VGeneMutations(
                getMutationsWithoutCDR3(
                    cloneWrapper, Variable,
                    Range(clusterInfo.VRangeInCDR3.lower, VSequence1.size()),
                    clusterInfo.commonVAlignmentRanges
                ),
                VMutationsInCDR3WithoutNDN
            ),
            getVMutationsWithinNDN(cloneWrapper, clusterInfo.VRangeInCDR3.upper),
            CDR3.getRange(
                clusterInfo.VRangeInCDR3.length() + VMutationsInCDR3WithoutNDN.mutations.lengthDelta,
                CDR3.size() - (clusterInfo.JRangeInCDR3.length() + JMutationsInCDR3WithoutNDN.mutations.lengthDelta)
            ),
            getJMutationsWithinNDN(cloneWrapper, clusterInfo.JRangeInCDR3.lower),
            JGeneMutations(
                JMutationsInCDR3WithoutNDN,
                getMutationsWithoutCDR3(
                    cloneWrapper, Joining,
                    Range(0, clusterInfo.JRangeInCDR3.lower),
                    clusterInfo.commonJAlignmentRanges
                )
            )
        )

        return CloneWithMutationsFromVJGermline(result, cloneWrapper)
    }

    private fun combineTrees(originalTrees: List<TreeWithMetaBuilder>): StepResult {
        val clonesRebase = ClonesRebase(VSequence1, JSequence1, scoringSet)
        val result: MutableList<TreeWithMetaBuilder> = ArrayList()
        val originalTreesCopy = originalTrees
            .sortedByDescending { it.clonesCount() }
            .toMutableList()
        //trying to grow the biggest trees first
        while (originalTreesCopy.isNotEmpty()) {
            var treeToGrow = originalTreesCopy[0]
            originalTreesCopy.removeAt(0)

            //trying to add the smallest trees first
            for (i in originalTreesCopy.indices.reversed()) {
                val treeToAttach = originalTreesCopy[i]
                val distance_1 = distance(clonesRebase, treeToAttach, treeToGrow)
                val distance_2 = distance(clonesRebase, treeToGrow, treeToAttach)
                val metric = min(distance_1.toDouble(), distance_2.toDouble())
                if (metric <= parameters.thresholdForCombineTrees) {
                    treeToGrow = buildATree(Cluster(
                        listOf(treeToGrow, treeToAttach).flatMap { treeWithMetaBuilder ->
                            treeWithMetaBuilder.allNodes()
                                .map { it.node }
                                .map { node -> node.content.convert({ it }) { null } }
                                .filterNotNull()
                                .map {
                                    CloneWithMutationsFromVJGermline(
                                        it.mutationsFromVJGermline,
                                        it.clone
                                    )
                                }
                        }
                    ))
                    originalTreesCopy.removeAt(i)
                }
            }
            result.add(treeToGrow)
        }
        return buildStepResult(HashMap(), result)
    }

    private fun distance(
        clonesRebase: ClonesRebase,
        from: TreeWithMetaBuilder,
        destination: TreeWithMetaBuilder
    ): BigDecimal {
        val oldestAncestorOfFrom = from.mostRecentCommonAncestor()
        val oldestAncestorOfDestination = destination.mostRecentCommonAncestor()
        val destinationRebasedOnFrom = clonesRebase.rebaseMutations(
            oldestAncestorOfDestination.fromRootToThis,
            originalRoot = destination.rootInfo,
            rebaseTo = from.rootInfo
        )
        return distance(
            MutationsUtils.mutationsBetween(
                from.rootInfo,
                oldestAncestorOfFrom.fromRootToThis,
                destinationRebasedOnFrom
            )
        )
    }

    private fun attachClonesByNDN(
        originalTrees: List<TreeWithMetaBuilder>, clonesNotInClusters: Supplier<List<CloneWithMutationsFromVJGermline>>
    ): StepResult {
        val decisions: MutableMap<CloneWrapper.ID, TreeWithMetaBuilder.DecisionInfo> = HashMap()
        val resultTrees = originalTrees.map { it.copy() }
        clonesNotInClusters.get().asSequence()
            .filter { clone -> clone.mutations.VJMutationsCount < parameters.commonMutationsCountForClustering }
            .forEach { clone ->
                val bestTreeToAttach = resultTrees
                    .map { tree: TreeWithMetaBuilder ->
                        val rebasedClone = tree.rebaseClone(clone)
                        val oldestAncestorOfTreeToGrow = tree.mostRecentCommonAncestor()
                        //TODO check also every clone with minimum mutations from germline (align with oldest and use diff)
                        val NDNOfTreeToGrow =
                            oldestAncestorOfTreeToGrow.fromRootToThis.NDNMutations.buildSequence(tree.rootInfo)
                        val NDNOfNodeToAttach = rebasedClone.mutationsSet.NDNMutations.buildSequence(tree.rootInfo)
                        val score_1 = Aligner.alignGlobal(
                            scoringSet.NDNScoring,
                            NDNOfNodeToAttach,
                            NDNOfTreeToGrow
                        ).score
                        val score_2 = Aligner.alignGlobal(
                            scoringSet.NDNScoring,
                            NDNOfTreeToGrow,
                            NDNOfNodeToAttach
                        ).score
                        val NDNLength = tree.rootInfo.reconstructedNDN.size()
                        val maxScore = scoringSet.NDNScoring.maximalMatchScore * NDNLength
                        val metric_1 = (maxScore - score_1) / NDNLength.toDouble()
                        val metric_2 = (maxScore - score_2) / NDNLength.toDouble()
                        Pair(min(metric_1, metric_2)) { tree.addClone(rebasedClone) }
                    }
                    .minByOrNull { it.first }
                if (bestTreeToAttach != null) {
                    val metric = bestTreeToAttach.first
                    if (metric <= parameters.thresholdForCombineByNDN) {
                        bestTreeToAttach.second()
                        decisions[clone.cloneWrapper.id] = MetricDecisionInfo(metric)
                    }
                }
            }
        return buildStepResult(decisions, resultTrees)
    }

    private fun attachClonesByDistanceChange(
        originalTrees: List<TreeWithMetaBuilder>,
        clonesNotInClustersSupplier: Supplier<List<CloneWithMutationsFromVJGermline>>
    ): StepResult {
        val result = originalTrees.map { it.copy() }
        val decisions: MutableMap<CloneWrapper.ID, TreeWithMetaBuilder.DecisionInfo> = HashMap()
        val clonesNotInClusters = clonesNotInClustersSupplier.get()
        //try to add as nodes clones that wasn't picked up by clustering
        for (i in clonesNotInClusters.indices.reversed()) {
            val clone = clonesNotInClusters[i]
            if (clone.mutations.VJMutationsCount >= parameters.commonMutationsCountForClustering) {
                val bestActionAndDistanceFromRoot = result
                    .map { treeWithMeta ->
                        val rebasedClone = treeWithMeta.rebaseClone(clone)
                        Pair(
                            treeWithMeta.bestAction(rebasedClone),
                            treeWithMeta.distanceFromRootToClone(rebasedClone)
                        )
                    }
                    .minByOrNull { p -> p.first.changeOfDistance() }
                if (bestActionAndDistanceFromRoot != null) {
                    val bestAction = bestActionAndDistanceFromRoot.first
                    val distanceFromRoot = bestActionAndDistanceFromRoot.second
                    val metric = bestAction.changeOfDistance().toDouble() / distanceFromRoot
                    if (metric <= parameters.thresholdForFreeClones) {
                        decisions[clone.cloneWrapper.id] = MetricDecisionInfo(metric)
                        bestAction.apply()
                    }
                }
            }
        }
        return buildStepResult(decisions, result)
    }

    private fun clusterByCommonMutations(
        clones: List<CloneWithMutationsFromVJGermline>,
        relatedAllelesMutations: Map<VDJCGeneId, List<Mutations<NucleotideSequence>>>
    ): List<Cluster<CloneWithMutationsFromVJGermline>> {
        val matrix = AdjacencyMatrix(clones.size)
        for (i in clones.indices) {
            for (j in clones.indices) {
                val commonMutationsCount = commonMutationsCount(
                    relatedAllelesMutations,
                    clones[i],
                    clones[j]
                )
                if (commonMutationsCount >= parameters.commonMutationsCountForClustering) {
                    if (NDNDistance(clones[i], clones[j]) <= parameters.maxNDNDistanceForClustering) {
                        matrix.setConnected(i, j)
                    }
                }
            }
        }
        val notOverlappedCliques: MutableList<BitArrayInt> = ArrayList()
        matrix.calculateMaximalCliques()
            .filter { it.bitCount() > 1 }
            .sortedByDescending { it.bitCount() }
            .forEach { clique ->
                if (notOverlappedCliques.none { it.intersects(clique) }) {
                    notOverlappedCliques.add(clique)
                }
            }
        val clusters: MutableList<Cluster<CloneWithMutationsFromVJGermline>> = ArrayList()
        for (clique in notOverlappedCliques) {
            clusters.add(Cluster(clique.bits.map { index: Int -> clones[index] }))
        }
        return clusters
    }

    private fun commonMutationsCount(
        relatedAllelesMutations: Map<VDJCGeneId, List<Mutations<NucleotideSequence>>>,
        first: CloneWithMutationsFromVJGermline,
        second: CloneWithMutationsFromVJGermline
    ): Int {
        val VAllelesMutations = relatedAllelesMutations[VJBase.VGeneId] ?: emptyList()
        val JAllelesMutations = relatedAllelesMutations[VJBase.JGeneId] ?: emptyList()
        return commonMutationsCount(first.mutations.VMutations, second.mutations.VMutations, VAllelesMutations) +
            commonMutationsCount(first.mutations.JMutations, second.mutations.JMutations, JAllelesMutations)
    }

    private fun NDNDistance(first: CloneWithMutationsFromVJGermline, second: CloneWithMutationsFromVJGermline): Double {
        val firstNDN = first.mutations.knownNDN
        val secondNDN = second.mutations.knownNDN
        val score = Aligner.alignGlobal(
            scoringSet.NDNScoring,
            firstNDN,
            secondNDN
        ).score
        val maxScore = max(
            maxScore(firstNDN, scoringSet.NDNScoring),
            maxScore(secondNDN, scoringSet.NDNScoring)
        )
        return (maxScore - score) / min(firstNDN.size(), secondNDN.size()).toDouble()
    }

    private fun commonMutationsCount(
        first: GeneMutations,
        second: GeneMutations,
        allelesMutations: List<Mutations<NucleotideSequence>>
    ): Int = (allelesMutations.asSequence() + Mutations.EMPTY_NUCLEOTIDE_MUTATIONS)
        .minOf { alleleMutations ->
            commonMutationsCount(without(first, alleleMutations), without(second, alleleMutations))
        }

    private fun without(
        cloneMutations: GeneMutations,
        alleleMutations: Mutations<NucleotideSequence>
    ): Iterable<Mutations<NucleotideSequence>> {
        if (alleleMutations.size() == 0) {
            return when {
                cloneMutations.partInCDR3.range.isEmpty -> cloneMutations.mutations.values
                else -> cloneMutations.mutations.values + cloneMutations.partInCDR3.mutations
            }
        }
        val result = cloneMutations.mutations.map { (_, mutations) ->
            mutations.without(alleleMutations)
        }
        return when {
            cloneMutations.partInCDR3.range.isEmpty -> result
            else -> result + cloneMutations.partInCDR3.mutations.without(alleleMutations)
        }
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

    private fun buildATreeWithDecisionsInfo(cluster: Cluster<CloneWithMutationsFromVJGermline>): Pair<List<Pair<CloneWrapper.ID, TreeWithMetaBuilder.DecisionInfo>>, TreeWithMetaBuilder> {
        val treeWithMetaBuilder = buildATree(cluster)
        val decisionsInfo = cluster.cluster.map {
            val effectiveParent = treeWithMetaBuilder.getEffectiveParent(it.cloneWrapper.id)
            val VHit = it.cloneWrapper.getHit(Variable)
            val JHit = it.cloneWrapper.getHit(Joining)
            it.cloneWrapper.id to ZeroStepDecisionInfo(
                effectiveParent.fromRootToThis.VMutations.mutationsCount() +
                    effectiveParent.fromRootToThis.JMutations.mutationsCount(),
                VHit.gene.geneName,
                JHit.gene.geneName,
                VHit.score,
                JHit.score
            )
        }
        return Pair(decisionsInfo, treeWithMetaBuilder)
    }

    private fun buildATree(cluster: Cluster<CloneWithMutationsFromVJGermline>): TreeWithMetaBuilder {
        val clonesRebase = ClonesRebase(VSequence1, JSequence1, scoringSet)
        val rootInfo = buildRootInfo(cluster)
        val rebasedCluster = cluster.cluster.asSequence()
            .map { clone ->
                clonesRebase.rebaseClone(
                    rootInfo,
                    clone.mutations,
                    clone.cloneWrapper
                )
            }
            .sortedBy { score(rootInfo, it.mutationsSet) }
            .toList()
        val treeBuilder = createTreeBuilder(rootInfo)
        val treeWithMetaBuilder = TreeWithMetaBuilder(
            treeBuilder,
            rootInfo,
            clonesRebase,
            LinkedList(),
            idGenerator.next(rootInfo.VJBase)
        )
        for (cloneWithMutationsFromReconstructedRoot in rebasedCluster) {
            treeWithMetaBuilder.addClone(cloneWithMutationsFromReconstructedRoot)
        }
        return treeWithMetaBuilder
    }

    private fun createTreeBuilder(rootInfo: RootInfo): TreeBuilderByAncestors<CloneWithMutationsFromReconstructedRoot, SyntheticNode, NodeMutationsDescription> {
        val root = SyntheticNode.createRoot(
            clusterInfo.commonVAlignmentRanges,
            rootInfo,
            clusterInfo.commonJAlignmentRanges
        )
        return TreeBuilderByAncestors(
            root,
            distance = { base, mutations -> distance(mutations) + penaltyForReversedMutations(base, mutations) },
            mutationsBetween = { first, second ->
                MutationsUtils.mutationsBetween(rootInfo, first.fromRootToThis, second.fromRootToThis)
            },
            mutate = { parent, fromParentToThis -> parent.mutate(fromParentToThis) },
            asAncestor = { observed -> SyntheticNode.createFromMutations(observed.mutationsSet) },
            findCommonMutations = { first, second -> commonMutations(first, second) },
            postprocessDescendants = { parent, child ->
                SyntheticNode.createFromMutations(
                    child.fromRootToThis.copy(
                        NDNMutations = child.fromRootToThis.NDNMutations.copy(
                            mutations = MutationsUtils.concreteNDNChild(
                                parent.fromRootToThis.NDNMutations.mutations,
                                child.fromRootToThis.NDNMutations.mutations
                            )
                        )
                    )
                )
            },
            countOfNodesToProbe = parameters.countOfNodesToProbe
        )
    }

    private fun buildRootInfo(cluster: Cluster<CloneWithMutationsFromVJGermline>): RootInfo {
        val rootBasedOn = cluster.cluster[0]

        //TODO may be just get from root?
        val VRangeInCDR3 = mostLikableRangeInCDR3(cluster) { clone -> VRangeInCDR3(clone) }
        val JRangeInCDR3 = mostLikableRangeInCDR3(cluster) { clone -> JRangeInCDR3(clone) }
        val NDNRangeInKnownNDN: Range = NDNRangeInKnownNDN(rootBasedOn.mutations, VRangeInCDR3, JRangeInCDR3)
        val NDNBuilder = ALPHABET.createBuilder()
        IntStream.range(0, NDNRangeInKnownNDN.length()).forEach { NDNBuilder.append(NucleotideSequence.N) }
        return RootInfo(
            VSequence1,
            VRangeInCDR3,
            NDNBuilder.createAndDestroy(),
            JSequence1,
            JRangeInCDR3,
            rootBasedOn.cloneWrapper.VJBase
        )
    }

    private fun getMutationsWithoutCDR3(
        clone: CloneWrapper,
        geneType: GeneType,
        CDR3Range: Range,
        commonAlignmentRanges: ClonesAlignmentRanges
    ): Map<Range, Mutations<NucleotideSequence>> {
        val hit = clone.getHit(geneType)
        return (0 until hit.alignments.size).flatMap { index: Int ->
            val alignment = hit.getAlignment(index)
            val mutations = alignment.absoluteMutations
            val rangesWithout = alignment.sequence1Range.without(CDR3Range)
            rangesWithout
                .map { commonAlignmentRanges.cutRange(it) }
                .filterNot { it.isEmpty }
                .map { range ->
                    val isIncludeFirstInserts = alignment.sequence1Range.lower == range.lower
                    range to mutations.extractAbsoluteMutations(range, isIncludeFirstInserts)
                }
        }.toMap()
    }

    private fun getVMutationsWithinNDN(clone: CloneWrapper, from: Int): Pair<Mutations<NucleotideSequence>, Range> {
        val hit = clone.getHit(Variable)
        val CDR3Begin = clone.getRelativePosition(Variable, ReferencePoint.CDR3Begin)
        val alignment = (0 until hit.alignments.size)
            .map { hit.getAlignment(it) }
            .firstOrNull { alignment ->
                alignment.sequence1Range.contains(CDR3Begin) && alignment.sequence1Range.contains(from)
            }
        return when (alignment) {
            null -> Mutations.EMPTY_NUCLEOTIDE_MUTATIONS to Range(from, from)
            else -> Pair(
                alignment.absoluteMutations.extractAbsoluteMutations(
                    Range(
                        from,
                        alignment.sequence1Range.upper
                    ), false
                ),
                Range(from, alignment.sequence1Range.upper)
            )
        }
    }

    private fun getMutationsForRange(
        clone: CloneWrapper,
        range: Range,
        geneType: GeneType
    ): Mutations<NucleotideSequence> {
        val hit = clone.getHit(geneType)
        return IntStream.range(0, hit.alignments.size)
            .boxed()
            .map { target -> hit.getAlignment(target) }
            .filter { alignment -> alignment.sequence1Range.contains(range) }
            .map { it.absoluteMutations }
            .findFirst()
            .orElseThrow { IllegalArgumentException() }
    }

    private fun getJMutationsWithinNDN(clone: CloneWrapper, to: Int): Pair<Mutations<NucleotideSequence>, Range> {
        val hit = clone.getHit(Joining)
        val CDR3End = clone.getRelativePosition(Joining, ReferencePoint.CDR3End)
        val alignment = (0 until hit.alignments.size)
            .map { hit.getAlignment(it) }
            .firstOrNull { alignment ->
                alignment.sequence1Range.contains(CDR3End) && alignment.sequence1Range.contains(to)
            }
        return when (alignment) {
            null -> Mutations.EMPTY_NUCLEOTIDE_MUTATIONS to Range(to, to)
            else -> Pair(
                alignment.absoluteMutations.extractAbsoluteMutations(
                    Range(alignment.sequence1Range.lower, to),
                    false
                ),
                Range(alignment.sequence1Range.lower, to)
            )
        }
    }

    private fun penaltyForReversedMutations(
        fromRootToBase: SyntheticNode,
        mutations: NodeMutationsDescription
    ): BigDecimal {
        val reversedMutationsCount =
            reversedVMutationsCount(fromRootToBase, mutations) + reversedJMutationsCount(fromRootToBase, mutations)
        return BigDecimal.valueOf(parameters.penaltyForReversedMutations)
            .multiply(BigDecimal.valueOf(reversedMutationsCount.toLong()))
    }

    private fun distance(mutations: NodeMutationsDescription): BigDecimal =
        scoringSet.distance(mutations)

    private fun score(rootInfo: RootInfo, mutations: MutationsSet): Int {
        val VScore = AlignmentUtils.calculateScore(
            VSequence1,
            mutations.VMutations.combinedMutations(),
            scoringSet.VScoring
        )
        val JScore = AlignmentUtils.calculateScore(
            JSequence1,
            mutations.JMutations.combinedMutations(),
            scoringSet.JScoring
        )
        val NDNScore = AlignmentUtils.calculateScore(
            rootInfo.reconstructedNDN,
            mutations.NDNMutations.mutations,
            scoringSet.NDNScoring
        )
        return VScore + JScore + NDNScore
    }

    private fun commonMutations(
        first: NodeMutationsDescription,
        second: NodeMutationsDescription
    ): NodeMutationsDescription =
        NodeMutationsDescription(
            first.VMutationsWithoutCDR3.intersection(second.VMutationsWithoutCDR3),
            first.VMutationsInCDR3WithoutNDN.intersection(second.VMutationsInCDR3WithoutNDN),
            first.knownNDN.copy(
                mutations = MutationsUtils.findNDNCommonAncestor(
                    first.knownNDN.mutations,
                    second.knownNDN.mutations
                )
            ),
            first.JMutationsInCDR3WithoutNDN.intersection(second.JMutationsInCDR3WithoutNDN),
            first.JMutationsWithoutCDR3.intersection(second.JMutationsWithoutCDR3)
        )

    //TODO it is more possible to decrease length of alignment than to increase
    private fun mostLikableRangeInCDR3(
        cluster: Cluster<CloneWithMutationsFromVJGermline>,
        rangeSupplier: (CloneWrapper) -> Range
    ): Range = cluster.cluster.asSequence()
        .sortedBy { it.mutations.VJMutationsCount }
        .take(parameters.topToVoteOnNDNSize)
        .map { obj: CloneWithMutationsFromVJGermline -> obj.cloneWrapper }
        .map(rangeSupplier)
        .groupingBy { it }.eachCount()
        .entries
        .maxWithOrNull(java.util.Map.Entry.comparingByValue<Range, Int>()
            .thenComparing(Comparator.comparingInt { (key, _): Map.Entry<Range, Int> -> key.length() }
                .reversed()))!!
        .key

    private fun buildStepResult(
        decisions: Map<CloneWrapper.ID, TreeWithMetaBuilder.DecisionInfo>,
        trees: List<TreeWithMetaBuilder>
    ): StepResult = StepResult(
        decisions,
        trees.map { it.snapshot() },
        trees.flatMap { tree: TreeWithMetaBuilder ->
            tree.allNodes()
                .filter { it.node.content is Reconstructed<*, *> }
                .map { nodeWithParent -> buildDebugInfo(decisions, tree, nodeWithParent) }
        }
    )

    private fun buildDebugInfo(
        decisions: Map<CloneWrapper.ID, TreeWithMetaBuilder.DecisionInfo>,
        tree: TreeWithMetaBuilder,
        nodeWithParent: NodeWithParent<ObservedOrReconstructed<CloneWithMutationsFromReconstructedRoot, SyntheticNode>>
    ): DebugInfo {
        val nodeContent = nodeWithParent.node
            .content
            .convert({ null }, { it })!!
        val cloneId = nodeWithParent.node.links
            .map { it.node }
            .firstNotNullOfOrNull { child -> child.content.convert({ it.clone.id }, { null }) }
        var metric: Double? = null
        if (cloneId != null) {
            val decision = decisions[cloneId]
            if (decision is MetricDecisionInfo) {
                metric = decision.metric
            }
        }
        val parentMutations = nodeWithParent.parent
            ?.content?.convert({ null }, { it })
            ?.fromRootToThis
        return DebugInfo(
            tree.treeId,
            tree.rootInfo,
            nodeContent.fromRootToThis.VMutations.mutations.keys,
            nodeContent.fromRootToThis.JMutations.mutations.keys,
            cloneId,
            nodeWithParent.node.content.id,
            nodeWithParent.parent?.content?.id,
            nodeContent.fromRootToThis.NDNMutations.buildSequence(tree.rootInfo),
            nodeContent.fromRootToThis,
            parentMutations,
            metric,
            isPublic(tree.rootInfo)
        )
    }

    private fun isPublic(rootInfo: RootInfo): Boolean =
        rootInfo.reconstructedNDN.size() <= parameters.NDNSizeLimitForPublicClones

    fun debugInfos(currentTrees: List<TreeWithMetaBuilder>): List<DebugInfo> = currentTrees.flatMap { tree ->
        tree.allNodes()
            .filter { it.node.content is Reconstructed }
            .map { nodeWithParent -> buildDebugInfo(emptyMap(), tree, nodeWithParent) }
    }

    private fun ScoringSet.distance(mutations: NodeMutationsDescription): BigDecimal {
        val VPenalties = maxScore(mutations.VMutationsWithoutCDR3.values, VScoring) -
            score(mutations.VMutationsWithoutCDR3.values, VScoring) +
            maxScore(mutations.VMutationsInCDR3WithoutNDN, VScoring) -
            score(mutations.VMutationsInCDR3WithoutNDN, VScoring)
        val VLength = mutations.VMutationsWithoutCDR3.values.sumOf { it.range.length() } +
            mutations.VMutationsInCDR3WithoutNDN.range.length()
        val JPenalties = maxScore(mutations.JMutationsWithoutCDR3.values, JScoring) -
            score(mutations.JMutationsWithoutCDR3.values, JScoring) +
            maxScore(mutations.JMutationsInCDR3WithoutNDN, JScoring) -
            score(mutations.JMutationsInCDR3WithoutNDN, JScoring)
        val JLength = mutations.JMutationsWithoutCDR3.values.sumOf { it.range.length() } +
            mutations.JMutationsInCDR3WithoutNDN.range.length()
        val NDNPenalties = maxScore(mutations.knownNDN, NDNScoring) - score(mutations.knownNDN, NDNScoring)
        val NDNLength = mutations.knownNDN.range.length()

        return BigDecimal.valueOf(
            (NDNPenalties * parameters.NDNScoreMultiplier + VPenalties + JPenalties) /
                (NDNLength + VLength + JLength).toDouble()
        )
    }

    private interface Step {
        fun next(
            originalTrees: List<TreeWithMetaBuilder>,
            clonesNotInClusters: () -> List<CloneWithMutationsFromVJGermline>
        ): StepResult
    }

    class StepResult internal constructor(
        val decisions: Map<CloneWrapper.ID, TreeWithMetaBuilder.DecisionInfo>,
        val snapshots: List<TreeWithMetaBuilder.Snapshot>,
        val nodesDebugInfo: List<DebugInfo>
    )

    internal class CalculatedClusterInfo(
        val commonVAlignmentRanges: ClonesAlignmentRanges,
        val commonJAlignmentRanges: ClonesAlignmentRanges,
        val VRangeInCDR3: Range,
        val JRangeInCDR3: Range
    )

    companion object {
        fun build(
            parameters: SHMTreeBuilderParameters,
            VScoring: AlignmentScoring<NucleotideSequence>,
            JScoring: AlignmentScoring<NucleotideSequence>,
            originalCluster: Cluster<CloneWrapper>,
            calculatedClusterInfo: CalculatedClusterInfo,
            idGenerator: IdGenerator,
            VJBase: VJBase
        ): ClusterProcessor {
            require(originalCluster.cluster.isNotEmpty())
            val anyClone = originalCluster.cluster[0]
            return ClusterProcessor(
                parameters,
                ScoringSet(
                    VScoring,
                    MutationsUtils.NDNScoring(),
                    JScoring
                ),
                originalCluster,
                anyClone.getHit(Variable).getAlignment(0).sequence1,
                anyClone.getHit(Joining).getAlignment(0).sequence1,
                calculatedClusterInfo,
                idGenerator,
                VJBase
            )
        }

        fun calculateClusterInfo(
            originalCluster: Cluster<CloneWrapper>,
            minPortionOfClonesForCommonAlignmentRanges: Double
        ): CalculatedClusterInfo = CalculatedClusterInfo(
            ClonesAlignmentRanges.commonAlignmentRanges(
                originalCluster.cluster,
                minPortionOfClonesForCommonAlignmentRanges, Variable
            ) { it.getHit(Variable) },
            ClonesAlignmentRanges.commonAlignmentRanges(
                originalCluster.cluster,
                minPortionOfClonesForCommonAlignmentRanges, Joining
            ) { it.getHit(Joining) },
            minRangeInCDR3(originalCluster) { clone -> VRangeInCDR3(clone) },
            minRangeInCDR3(originalCluster) { clone -> JRangeInCDR3(clone) }
        )

        @Suppress("UNCHECKED_CAST")
        fun <E : TreeWithMetaBuilder.DecisionInfo> makeDecision(chooses: Map<VJBase, E>): VJBase {
            if (chooses.size == 1) {
                return chooses.keys.iterator().next()
            }
            val decisionType = chooses.values.iterator().next().javaClass
            require(chooses.values.all { decisionType.isInstance(it) })
            return when (decisionType) {
                ZeroStepDecisionInfo::class.java -> makeDecisionForZero(chooses as Map<VJBase, ZeroStepDecisionInfo>)
                MetricDecisionInfo::class.java -> makeDecisionByMetric(chooses as Map<VJBase, MetricDecisionInfo>)
                else -> throw UnsupportedOperationException()
            }
        }

        private fun makeDecisionByMetric(chooses: Map<VJBase, MetricDecisionInfo>): VJBase = chooses.entries
            .minByOrNull { (_, value): Map.Entry<VJBase, MetricDecisionInfo> -> value.metric }!!
            .key

        private fun makeDecisionForZero(chooses: Map<VJBase, ZeroStepDecisionInfo>): VJBase {
            val filteredByAlleles =
                chooses.entries //group by the same origin VJ pair - group decisions by related alleles
                    .groupBy { (_, value) ->
                        Pair(value.getGeneName(Variable), value.getGeneName(Joining))
                    }
                    .values
                    .mapNotNull { withTheSameGeneBase ->
                        withTheSameGeneBase.minByOrNull { (_, value) -> value.commonMutationsCount }
                    }
                    .map { (key, _) -> key }
                    .toSet()
            return filteredByAlleles
                .maxByOrNull {
                    chooses[it]!!.getScore(Variable) + chooses[it]!!.getScore(Joining)
                }!!
        }

        private fun reversedVMutationsCount(fromRootToBase: SyntheticNode, mutations: NodeMutationsDescription): Int {
            //TODO don't reconstruct map
            val reversedMutationsNotInCDR3 = MutationsUtils.fold(
                fromRootToBase.fromRootToThis.VMutations.mutations,
                mutations.VMutationsWithoutCDR3
            ) { a, b, range -> reversedMutationsCount(a, b, range) }.values.sum()
            val reversedMutationsInCDR3 = reversedMutationsCount(
                fromRootToBase.fromRootToThis.VMutations.partInCDR3.mutations,
                mutations.VMutationsInCDR3WithoutNDN,
                fromRootToBase.fromRootToThis.VMutations.partInCDR3.range
            )
            return reversedMutationsInCDR3 + reversedMutationsNotInCDR3
        }

        private fun reversedJMutationsCount(fromRootToBase: SyntheticNode, mutations: NodeMutationsDescription): Int {
            //TODO don't reconstruct map
            val reversedMutationsNotInCDR3 = MutationsUtils.fold(
                fromRootToBase.fromRootToThis.JMutations.mutations,
                mutations.JMutationsWithoutCDR3
            ) { a, b, range -> reversedMutationsCount(a, b, range) }.values.sum()
            val reversedMutationsInCDR3 = reversedMutationsCount(
                fromRootToBase.fromRootToThis.JMutations.partInCDR3.mutations,
                mutations.JMutationsInCDR3WithoutNDN,
                fromRootToBase.fromRootToThis.JMutations.partInCDR3.range
            )
            return reversedMutationsInCDR3 + reversedMutationsNotInCDR3
        }

        private fun reversedMutationsCount(a: Mutations<NucleotideSequence>, b: MutationsWithRange, range: Range): Int {
            val reversedMutations = b.mutations.move(range.lower).invert()
            val asSet = reversedMutations.asSequence().toSet()
            return a.asSequence().count { it in asSet }
        }

        private fun minRangeInCDR3(
            cluster: Cluster<CloneWrapper>,
            rangeSupplier: (CloneWrapper) -> Range
        ): Range {
            //TODO try to align to calculate most possible position
            return cluster.cluster
                .map(rangeSupplier)
                .minByOrNull { it.length() }!!
        }

        private fun VRangeInCDR3(clone: CloneWrapper): Range {
            val alignments = clone.getHit(Variable).alignments
            return Range(
                clone.getRelativePosition(Variable, ReferencePoint.CDR3Begin),
                alignments[alignments.size - 1].sequence1Range.upper
            )
        }

        private fun JRangeInCDR3(clone: CloneWrapper): Range = Range(
            clone.getHit(Joining).getAlignment(0).sequence1Range.lower,
            clone.getRelativePosition(Joining, ReferencePoint.CDR3End)
        )
    }
}

private fun maxScore(
    mutationsBetween: Collection<MutationsWithRange>,
    scoring: AlignmentScoring<NucleotideSequence>
): Double = mutationsBetween.sumOf { mutations -> maxScore(mutations, scoring).toDouble() }

private fun maxScore(mutations: MutationsWithRange, scoring: AlignmentScoring<NucleotideSequence>): Int =
    maxScore(mutations.sequence1, scoring)

private fun maxScore(sequence: NucleotideSequence, scoring: AlignmentScoring<NucleotideSequence>): Int =
    sequence.size() * scoring.maximalMatchScore

private fun score(
    mutationsWithRanges: Collection<MutationsWithRange>,
    scoring: AlignmentScoring<NucleotideSequence>
): Double = mutationsWithRanges.sumOf { mutations -> score(mutations, scoring).toDouble() }

private fun score(mutations: MutationsWithRange, scoring: AlignmentScoring<NucleotideSequence>): Int =
    AlignmentUtils.calculateScore(
        mutations.sequence1,
        mutations.mutations,
        scoring
    )
