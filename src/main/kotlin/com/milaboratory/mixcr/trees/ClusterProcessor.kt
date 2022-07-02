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
import com.milaboratory.core.mutations.Mutations.EMPTY_NUCLEOTIDE_MUTATIONS
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.core.sequence.NucleotideSequence.ALPHABET
import com.milaboratory.mixcr.basictypes.VDJCHit
import com.milaboratory.mixcr.trees.BuildSHMTreeStep.*
import com.milaboratory.mixcr.trees.Tree.NodeWithParent
import com.milaboratory.mixcr.trees.TreeBuilderByAncestors.ObservedOrReconstructed
import com.milaboratory.mixcr.trees.TreeBuilderByAncestors.Reconstructed
import com.milaboratory.mixcr.trees.TreeWithMetaBuilder.MetricDecisionInfo
import com.milaboratory.mixcr.trees.TreeWithMetaBuilder.ZeroStepDecisionInfo
import com.milaboratory.mixcr.util.*
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters
import io.repseq.core.GeneFeature
import io.repseq.core.GeneFeature.JCDR3Part
import io.repseq.core.GeneFeature.VCDR3Part
import io.repseq.core.GeneType
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import io.repseq.core.ReferencePoint.CDR3Begin
import io.repseq.core.ReferencePoint.CDR3End
import io.repseq.core.ReferencePoints
import io.repseq.core.VDJCGeneId
import java.math.BigDecimal
import java.util.*
import java.util.function.Supplier
import java.util.stream.IntStream
import kotlin.math.max
import kotlin.math.min

internal class ClusterProcessor(
    private val parameters: SHMTreeBuilderParameters,
    private val scoringSet: ScoringSet,
    private val assemblingFeatures: Array<GeneFeature>,
    private val alignerParameters: VDJCAlignerParameters,
    private val originalCluster: Cluster<CloneWrapper>,
    private val VSequence1: NucleotideSequence,
    private val JSequence1: NucleotideSequence,
    private val clusterInfo: CalculatedClusterInfo,
    private val idGenerator: IdGenerator,
    private val VJBase: VJBase
) {
    fun applyStep(
        stepName: BuildSHMTreeStep,
        currentTrees: List<TreeWithMetaBuilder>,
        allClonesInTress: Set<CloneWrapper.ID>
    ): StepResult = stepByName(stepName).next(currentTrees) {
        rebaseFromGermline(originalCluster.cluster.asSequence().filter { it.id !in allClonesInTress })
    }

    private fun stepByName(stepName: BuildSHMTreeStep): Step = when (stepName) {
        AttachClonesByDistanceChange -> object : Step {
            override fun next(
                originalTrees: List<TreeWithMetaBuilder>,
                clonesNotInClusters: () -> Sequence<CloneWithMutationsFromVJGermline>
            ): StepResult = attachClonesByDistanceChange(
                originalTrees,
                clonesNotInClusters
            )
        }
        CombineTrees -> object : Step {
            override fun next(
                originalTrees: List<TreeWithMetaBuilder>,
                clonesNotInClusters: () -> Sequence<CloneWithMutationsFromVJGermline>
            ): StepResult = combineTrees(originalTrees)
        }
        AttachClonesByNDN -> object : Step {
            override fun next(
                originalTrees: List<TreeWithMetaBuilder>,
                clonesNotInClusters: () -> Sequence<CloneWithMutationsFromVJGermline>
            ): StepResult = attachClonesByNDN(
                originalTrees,
                clonesNotInClusters
            )
        }
        BuildingInitialTrees -> throw IllegalArgumentException()
    }

    /**
     * Build clusters of clones that definitely in one clonal group.
     * Then build trees from clusters.
     *
     * See actual algorithm for forming clusters in link
     * @see ClusterProcessor.clusterByCommonMutationsAndNDNDistance
     */
    fun buildTreeTopParts(relatedAllelesMutations: Map<VDJCGeneId, List<Mutations<NucleotideSequence>>>): StepResult {
        //use only clones that are at long distance from any germline
        val clonesThatNotCloseToAnyGermline = originalCluster.cluster.asSequence()
            .filter { !hasVJPairThatCloseToGermline(it, parameters.commonMutationsCountForClustering) }
        val clones = rebaseFromGermline(clonesThatNotCloseToAnyGermline)
        val result = clusterByCommonMutationsAndNDNDistance(clones.toList(), relatedAllelesMutations).asSequence()
            .filter { it.cluster.size > 1 }
            .filter {
                //skip cluster if it formed by the same clones but with different C or from different samples
                val toCompare = it.cluster.first()
                it.cluster.subList(1, it.cluster.size).any { clone ->
                    !Arrays.equals(clone.cloneWrapper.clone.targets, toCompare.cloneWrapper.clone.targets)
                }
            }
            .map { cluster ->
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
                decisionsInfo to treeWithMetaBuilder
            }
            .toList()
        return buildStepResult(
            result.flatMap { it.first }.toMap(),
            result.map { it.second }.toList()
        )
    }

    fun buildTreeFromAllClones(treeId: Int): SHMTreeResult {
        val rebasedFromGermline = rebaseFromGermline(originalCluster.cluster.asSequence())
        val treeWithMetaBuilder = buildATree(Cluster(rebasedFromGermline.toList()))
        return SHMTreeResult(
            treeWithMetaBuilder.buildResult(),
            treeWithMetaBuilder.rootInfo,
            treeId
        )
    }

    private fun hasVJPairThatCloseToGermline(cloneWrapper: CloneWrapper, threashold: Int): Boolean =
        cloneWrapper.candidateVJBases
            .map { VJBase ->
                cloneWrapper.clone.getHit(Variable, VJBase).mutationsCount() +
                        cloneWrapper.clone.getHit(Joining, VJBase).mutationsCount()
            }
            .any { it < threashold }

    private fun VDJCHit.mutationsCount(): Int = alignments.sumOf { it.absoluteMutations.size() }

    fun restore(snapshot: TreeWithMetaBuilder.Snapshot): TreeWithMetaBuilder {
        val clonesInTrees = snapshot.clonesAdditionHistory.toSet()
        val rebasedClonesFromGermline = rebaseFromGermline(
            originalCluster.cluster
                .filter { it.id in clonesInTrees }.asSequence()
        )
        return buildATree(Cluster(rebasedClonesFromGermline.toList()))
    }

    private fun rebaseFromGermline(clones: Sequence<CloneWrapper>): Sequence<CloneWithMutationsFromVJGermline> = clones
        .filter { cloneWrapper ->
            (clusterInfo.commonVAlignmentRanges.containsCloneWrapper(cloneWrapper)
                    && clusterInfo.commonJAlignmentRanges.containsCloneWrapper(cloneWrapper))
        }
        .map { cloneWrapper -> rebaseFromGermline(cloneWrapper) }

    /**
     * Present all mutations as `MutationsFromVJGermline`
     * @see MutationsFromVJGermline
     */
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
                cloneWrapper.getMutationsWithoutCDR3(Variable),
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
                cloneWrapper.getMutationsWithoutCDR3(Joining)
            )
        )

        return CloneWithMutationsFromVJGermline(result, cloneWrapper)
    }

    /**
     * Try to compare NDN of MRCA of different trees to decide if we need to collide trees.
     *
     * If distance between NDN of MRCA less than parameters.thresholdForCombineTrees, then combine this trees.
     *
     * parameters.thresholdForCombineTrees must be very strict because NDN of MRCA contains wildcards.
     *
     * Thoughts:
     * - Use information about VJ of MRCA
     * - Try to calculate probabilities of resolving wildcards to specific letter. It is possible by using
     * topology of branches and possibility of mutations in VJ.
     * - Maybe we can calculate possibility of combined tree and use it as a metric.
     * - see NDNDistance
     */
    private fun combineTrees(originalTrees: List<TreeWithMetaBuilder>): StepResult {
        val clonesRebase = ClonesRebase(VSequence1, JSequence1, scoringSet)
        val result = mutableListOf<TreeWithMetaBuilder>()
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
                //try to calculate distances in both ways, it may differ
                val distance_1 = distanceBetweenTrees(clonesRebase, treeToAttach, treeToGrow)
                val distance_2 = distanceBetweenTrees(clonesRebase, treeToGrow, treeToAttach)
                val metric = min(distance_1.toDouble(), distance_2.toDouble())
                if (metric <= parameters.thresholdForCombineTrees) {
                    treeToGrow = buildATree(
                        Cluster(
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

    private fun distanceBetweenTrees(
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
            mutationsBetween(
                from.rootInfo,
                oldestAncestorOfFrom.fromRootToThis,
                destinationRebasedOnFrom
            )
        )
    }

    private fun mutationsBetween(rootInfo: RootInfo, first: MutationsSet, second: MutationsSet) =
        NodeMutationsDescription(
            mutationsBetween(rootInfo, first, second, Variable),
            MutationsUtils.difference(
                rootInfo.VSequence,
                first.VMutations.partInCDR3.mutations,
                second.VMutations.partInCDR3.mutations,
                rootInfo.VRangeInCDR3
            ),
            MutationsUtils.difference(
                rootInfo.reconstructedNDN,
                first.NDNMutations.mutations,
                second.NDNMutations.mutations,
                Range(0, rootInfo.reconstructedNDN.size())
            ),
            MutationsUtils.difference(
                rootInfo.JSequence,
                first.JMutations.partInCDR3.mutations,
                second.JMutations.partInCDR3.mutations,
                rootInfo.JRangeInCDR3
            ),
            mutationsBetween(rootInfo, first, second, Joining)
        )

    private fun mutationsBetween(
        rootInfo: RootInfo,
        firstMutations: MutationsSet,
        secondMutations: MutationsSet,
        geneType: GeneType
    ): Map<GeneFeature, MutationsWithRange> =
        MutationsUtils.fold(
            firstMutations.getGeneMutations(geneType).mutations,
            secondMutations.getGeneMutations(geneType).mutations
        ) { base, comparison, geneFeature ->
            MutationsUtils.difference(
                rootInfo.getSequence1(geneType),
                base,
                comparison,
                rootInfo.getPartitioning(geneType).getRange(geneFeature)
            )
        }


    /**
     * Work only with not so mutated clones
     *
     * Try to compare NDN of clone and NDN of MRCA of tree.
     * If distance less than parameters.thresholdForCombineByNDN than add this clone to the tree.
     *
     * If clone will attach to different tress (with different VJBase or not), then we will choose tree that closet to the clone by NDN of MRCA.
     *
     * parameters.thresholdForCombineByNDN must be strict because NDN of MRCA contains wildcards
     *
     * Thoughts:
     * - We may use information about VJ
     * - Try to calculate probabilities of resolving wildcards to specific letter. It is possible by using
     * topology of branches and possibility of mutations in VJ.
     * - see NDNDistance
     */
    private fun attachClonesByNDN(
        originalTrees: List<TreeWithMetaBuilder>,
        clonesNotInClusters: Supplier<Sequence<CloneWithMutationsFromVJGermline>>
    ): StepResult {
        val decisions = mutableMapOf<CloneWrapper.ID, TreeWithMetaBuilder.DecisionInfo>()
        val resultTrees = originalTrees.map { it.copy() }
        clonesNotInClusters.get()
            .filter { clone -> clone.mutations.VJMutationsCount < parameters.commonMutationsCountForClustering }
            .forEach { clone ->
                //find a tree that closed to the clone by NDN of MRCA
                val bestTreeToAttach = resultTrees
                    .map { tree ->
                        val rebasedClone = tree.rebaseClone(clone)
                        //TODO check that VJMutationsCount wasn't get more than parameters.commonMutationsCountForClustering after rabse
                        val oldestAncestorOfTreeToGrow = tree.mostRecentCommonAncestor()
                        //TODO check also every clone with minimum mutations from germline (align with oldest and use diff)
                        val NDNOfTreeToGrow =
                            oldestAncestorOfTreeToGrow.fromRootToThis.NDNMutations.buildSequence(tree.rootInfo)
                        val NDNOfNodeToAttach = rebasedClone.mutationsSet.NDNMutations.buildSequence(tree.rootInfo)
                        //try to calculate distances in both ways, it may differ
                        val metric_1 = NDNDistance(NDNOfTreeToGrow, NDNOfNodeToAttach)
                        val metric_2 = NDNDistance(NDNOfNodeToAttach, NDNOfTreeToGrow)
                        min(metric_1, metric_2) to { tree.addClone(rebasedClone) }
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

    /**
     * Working only with mutated clones.
     *
     * Try to insert every clone in a tree. Compare how far it from germline and how far it will be from branch.
     * So clone will be attached only if tree will not be too wide after attaching.
     *
     * Apart from that algorithm checks that NDN is not so different.
     *
     * If clone will attach to different tress (with different VJBase or not), then we will choose tree that most suitable for the clone by topology.
     *
     * Thoughts:
     * - Maybe this step will not yield anything after rework of buildTreeTopParts()
     * - Threshold of NDN distance may be function of distanceFromRoot and changeOfDistance.
     * So we allow to change NDN if we sure about information in VJ.
     * - Maybe we should to calculate information about VJ separately to make decision
     */
    private fun attachClonesByDistanceChange(
        originalTrees: List<TreeWithMetaBuilder>,
        clonesNotInClustersSupplier: Supplier<Sequence<CloneWithMutationsFromVJGermline>>
    ): StepResult {
        val result = originalTrees.map { it.copy() }
        val decisions = mutableMapOf<CloneWrapper.ID, TreeWithMetaBuilder.DecisionInfo>()
        val clonesNotInClusters = clonesNotInClustersSupplier.get()
            .sortedByDescending { it.mutations.VJMutationsCount }
        //try to add clones as nodes, more mutated first, so we build a tree from top to bottom
        clonesNotInClusters.forEach { clone ->
            if (clone.mutations.VJMutationsCount >= parameters.commonMutationsCountForClustering) {
                //choose tree that most suitable for the clone by topology
                val bestActionAndDistanceFromRoot = result
                    .mapNotNull { treeWithMeta ->
                        val rebasedClone = treeWithMeta.rebaseClone(clone)
                        val bestAction = treeWithMeta.bestAction(rebasedClone)
                        val NDNOfPossibleParent =
                            bestAction.parentContent().fromRootToThis.NDNMutations.buildSequence(treeWithMeta.rootInfo)
                        val NDNOfRebasedClone =
                            rebasedClone.mutationsSet.NDNMutations.buildSequence(treeWithMeta.rootInfo)
                        val NDNDistance = NDNDistance(NDNOfPossibleParent, NDNOfRebasedClone)
                        when {
                            //filter trees with too different NDN from the clone
                            NDNDistance > parameters.maxNDNDistanceForFreeClones -> null
                            else -> bestAction to treeWithMeta.distanceFromRootToClone(rebasedClone)
                        }
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
     * - There is no need for searching cliques. https://en.wikipedia.org/wiki/Single-linkage_clustering will do
     * because if assumption is true there is no need to enforce specific topology
     * - Lower parameters.commonMutationsCountForClustering may be used if we calculate probability of specific mutation.
     * Probabilities may be calculated on frequencies of mutations in specific gene in all clones of all samples.
     * Or it may be arbitrary data.
     * If it calculated from samples, then it will include impact both of hotspots and pressure of selection.
     * - Threshold of NDNDistance may be function of count of the same mutations in a pair and count of different synonymic mutations.
     */
    private fun clusterByCommonMutationsAndNDNDistance(
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
                    val NDNDistance = NDNDistance(clones[i].mutations.knownNDN, clones[j].mutations.knownNDN)
                    if (NDNDistance <= parameters.maxNDNDistanceForClustering) {
                        matrix.setConnected(i, j)
                    }
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
            .map { Cluster(it.bits.map { i -> clones[i] }) }
    }

    /**
     * Calculate common mutations in clone pair but mutations from allele mutations.
     * So if clones have a mutation, but there is allele of this gene with the same mutation, this mutation will be omitted in count.
     */
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

    /**
     * Penalty by length of scoring between NDN segments
     *
     * Thoughts:
     * - Try to calculate probabilities of length of N segments and align center to D if possible.
     * Use different penalties in N and D segments comparing to probability of generating the same NDN sequence
     */
    private fun NDNDistance(firstNDN: NucleotideSequence, secondNDN: NucleotideSequence): Double {
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
    ): Int = (allelesMutations.asSequence() + EMPTY_NUCLEOTIDE_MUTATIONS)
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
        rebasedCluster.forEach {
            treeWithMetaBuilder.addClone(it)
        }
        return treeWithMetaBuilder
    }

    private fun createTreeBuilder(rootInfo: RootInfo): TreeBuilderByAncestors<CloneWithMutationsFromReconstructedRoot, SyntheticNode, NodeMutationsDescription> {
        val root = SyntheticNode.createRoot(rootInfo)
        return TreeBuilderByAncestors(
            root,
            distance = { base, mutations ->
                distance(mutations) + penaltyForReversedMutations(
                    base,
                    mutations,
                    rootInfo
                )
            },
            mutationsBetween = { first, second ->
                mutationsBetween(rootInfo, first.fromRootToThis, second.fromRootToThis)
            },
            mutate = { parent, fromParentToThis -> parent.mutate(rootInfo, fromParentToThis) },
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
        val rootBasedOn = cluster.cluster.first()

        //TODO may be just get from root?
        val VRangeInCDR3 = mostLikableRangeInCDR3(cluster) { clone -> VRangeInCDR3(clone) }
        val JRangeInCDR3 = mostLikableRangeInCDR3(cluster) { clone -> JRangeInCDR3(clone) }
        val NDNRangeInKnownNDN: Range = NDNRangeInKnownNDN(rootBasedOn.mutations, VRangeInCDR3, JRangeInCDR3)
        val NDNBuilder = ALPHABET.createBuilder()
        IntStream.range(0, NDNRangeInKnownNDN.length()).forEach { NDNBuilder.append(NucleotideSequence.N) }
        return RootInfo(
            VSequence1,
            rootBasedOn.cloneWrapper.getPartitioning(Variable),
            rootBasedOn.mutations.VMutations.mutations.keys.sorted(),
            VRangeInCDR3,
            NDNBuilder.createAndDestroy(),
            JSequence1,
            rootBasedOn.cloneWrapper.getPartitioning(Joining),
            rootBasedOn.mutations.JMutations.mutations.keys.sorted(),
            JRangeInCDR3,
            rootBasedOn.cloneWrapper.VJBase
        )
    }

    private fun CloneWrapper.getMutationsWithoutCDR3(geneType: GeneType): Map<GeneFeature, Mutations<NucleotideSequence>> {
        val hit = getHit(geneType)
        val partitioning = getPartitioning(geneType)
        return hit.alignments.flatMap { alignment ->
            assemblingFeatures
                .mapNotNull { GeneFeature.intersection(it, hit.alignedFeature) }
                .map { it.cutCDR3PartOfFeature() }
                .map { geneFeature ->
                    val range = partitioning.getRange(geneFeature)
                    geneFeature to alignment.absoluteMutations.extractAbsoluteMutations(
                        range,
                        alignment.sequence1Range.lower == range.lower
                    )
                }
        }.toMap()
    }

    private fun GeneFeature.cutCDR3PartOfFeature(): GeneFeature = when {
        GeneFeature.intersection(this, VCDR3Part) != null -> GeneFeature(firstPoint, CDR3Begin)
        GeneFeature.intersection(this, JCDR3Part) != null -> GeneFeature(CDR3End, lastPoint)
        else -> this
    }

    private fun getVMutationsWithinNDN(clone: CloneWrapper, from: Int): Pair<Mutations<NucleotideSequence>, Range> {
        val hit = clone.getHit(Variable)
        val CDR3Begin = clone.getRelativePosition(Variable, CDR3Begin)
        val alignment = (0 until hit.alignments.size)
            .map { hit.getAlignment(it) }
            .firstOrNull { alignment ->
                alignment.sequence1Range.contains(CDR3Begin) && alignment.sequence1Range.contains(from)
            }
        return when (alignment) {
            null -> EMPTY_NUCLEOTIDE_MUTATIONS to Range(from, from)
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
        val CDR3End = clone.getRelativePosition(Joining, CDR3End)
        val alignment = (0 until hit.alignments.size)
            .map { hit.getAlignment(it) }
            .firstOrNull { alignment ->
                alignment.sequence1Range.contains(CDR3End) && alignment.sequence1Range.contains(to)
            }
        return when (alignment) {
            null -> EMPTY_NUCLEOTIDE_MUTATIONS to Range(to, to)
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
        mutations: NodeMutationsDescription,
        rootInfo: RootInfo
    ): BigDecimal {
        val reversedMutationsCount = reversedVMutationsCount(fromRootToBase, mutations, rootInfo) +
                reversedJMutationsCount(fromRootToBase, mutations, rootInfo)
        return BigDecimal.valueOf(parameters.penaltyForReversedMutations)
            .multiply(BigDecimal.valueOf(reversedMutationsCount.toLong()))
    }

    private fun reversedVMutationsCount(
        fromRootToBase: SyntheticNode,
        mutations: NodeMutationsDescription,
        rootInfo: RootInfo
    ): Int {
        //TODO don't reconstruct map
        val reversedMutationsNotInCDR3 = MutationsUtils.fold(
            fromRootToBase.fromRootToThis.VMutations.mutations,
            mutations.VMutationsWithoutCDR3
        ) { a, b, geneFeature ->
            val range = rootInfo.VPartitioning.getRange(geneFeature)
            reversedMutationsCount(a, b, range)
        }.values.sum()
        val reversedMutationsInCDR3 = reversedMutationsCount(
            fromRootToBase.fromRootToThis.VMutations.partInCDR3.mutations,
            mutations.VMutationsInCDR3WithoutNDN,
            fromRootToBase.fromRootToThis.VMutations.partInCDR3.range
        )
        return reversedMutationsInCDR3 + reversedMutationsNotInCDR3
    }

    private fun reversedJMutationsCount(
        fromRootToBase: SyntheticNode,
        mutations: NodeMutationsDescription,
        rootInfo: RootInfo
    ): Int {
        //TODO don't reconstruct map
        val reversedMutationsNotInCDR3 = MutationsUtils.fold(
            fromRootToBase.fromRootToThis.JMutations.mutations,
            mutations.JMutationsWithoutCDR3
        ) { a, b, geneFeature ->
            val range = rootInfo.JPartitioning.getRange(geneFeature)
            reversedMutationsCount(a, b, range)
        }.values.sum()
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

    /**
     * NDN penalties with multiplier plus V and J penalties normalized by overall length of sequence
     *
     * Thoughts:
     * - Use mutations possibilities for V and J
     * - Try other variants of end formula
     * - Reuse NDNDistance
     */
    private fun distance(mutations: NodeMutationsDescription): BigDecimal {
        val VPenalties = maxScore(mutations.VMutationsWithoutCDR3.values, scoringSet.VScoring) -
                score(mutations.VMutationsWithoutCDR3.values, scoringSet.VScoring) +
                maxScore(mutations.VMutationsInCDR3WithoutNDN, scoringSet.VScoring) -
                score(mutations.VMutationsInCDR3WithoutNDN, scoringSet.VScoring)
        val VLength = mutations.VMutationsWithoutCDR3.values.sumOf { it.range.length() } +
                mutations.VMutationsInCDR3WithoutNDN.range.length()
        val JPenalties = maxScore(mutations.JMutationsWithoutCDR3.values, scoringSet.JScoring) -
                score(mutations.JMutationsWithoutCDR3.values, scoringSet.JScoring) +
                maxScore(mutations.JMutationsInCDR3WithoutNDN, scoringSet.JScoring) -
                score(mutations.JMutationsInCDR3WithoutNDN, scoringSet.JScoring)
        val JLength = mutations.JMutationsWithoutCDR3.values.sumOf { it.range.length() } +
                mutations.JMutationsInCDR3WithoutNDN.range.length()
        val NDNPenalties =
            maxScore(mutations.knownNDN, scoringSet.NDNScoring) - score(mutations.knownNDN, scoringSet.NDNScoring)
        val NDNLength = mutations.knownNDN.range.length()
        return BigDecimal.valueOf(
            (NDNPenalties * parameters.NDNScoreMultiplier + VPenalties + JPenalties) /
                    (NDNLength + VLength + JLength).toDouble()
        )
    }

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
            nodeContent.fromRootToThis.getGeneMutations(Variable).mutations.keys.map {
                tree.rootInfo.getPartitioning(Variable).getRange(it)
            },
            nodeContent.fromRootToThis.getGeneMutations(Joining).mutations.keys.map {
                tree.rootInfo.getPartitioning(Joining).getRange(it)
            },
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

    private interface Step {
        fun next(
            originalTrees: List<TreeWithMetaBuilder>,
            clonesNotInClusters: () -> Sequence<CloneWithMutationsFromVJGermline>
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
        /**
         * Intersection of all [CDR3End:VEndTrimmed] of clones in the cluster
         */
        val VRangeInCDR3: Range,
        /**
         * Intersection of all [JEndTrimmed:CDR3End] of clones in the cluster
         */
        val JRangeInCDR3: Range
    )

    companion object {

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
        fun makeDecision(chooses: Map<VJBase, TreeWithMetaBuilder.DecisionInfo>): VJBase {
            if (chooses.size == 1) {
                return chooses.keys.iterator().next()
            }
            val anyElement = chooses.values.iterator().next()
            //all values are from the same class
            require(chooses.values.all { anyElement.javaClass.isInstance(it) })
            return when (anyElement) {
                is ZeroStepDecisionInfo -> ZeroStepDecisionInfo.makeDecision(chooses as Map<VJBase, ZeroStepDecisionInfo>)
                is MetricDecisionInfo -> MetricDecisionInfo.makeDecision(chooses as Map<VJBase, MetricDecisionInfo>)
            }
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
                clone.getRelativePosition(Variable, CDR3Begin),
                alignments[alignments.size - 1].sequence1Range.upper
            )
        }

        private fun JRangeInCDR3(clone: CloneWrapper): Range = Range(
            clone.getHit(Joining).getAlignment(0).sequence1Range.lower,
            clone.getRelativePosition(Joining, CDR3End)
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

private fun CloneWrapper.getPartitioning(geneType: GeneType): ReferencePoints =
    getHit(geneType).gene.partitioning.getRelativeReferencePoints(getHit(geneType).alignedFeature)
