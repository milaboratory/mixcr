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
import com.milaboratory.mixcr.trees.DebugInfo.MutationsSet
import com.milaboratory.mixcr.trees.MutationsUtils.intersection
import com.milaboratory.mixcr.trees.Tree.NodeWithParent
import com.milaboratory.mixcr.trees.TreeBuilderByAncestors.ObservedOrReconstructed
import com.milaboratory.mixcr.trees.TreeBuilderByAncestors.Reconstructed
import com.milaboratory.mixcr.trees.TreeWithMetaBuilder.MetricDecisionInfo
import com.milaboratory.mixcr.trees.TreeWithMetaBuilder.ZeroStepDecisionInfo
import com.milaboratory.mixcr.util.AdjacencyMatrix
import com.milaboratory.mixcr.util.BitArrayInt
import com.milaboratory.mixcr.util.ClonesAlignmentRanges
import com.milaboratory.mixcr.util.Cluster
import com.milaboratory.mixcr.util.RangeInfo
import com.milaboratory.mixcr.util.asMutations
import com.milaboratory.mixcr.util.asSequence
import com.milaboratory.mixcr.util.extractAbsoluteMutations
import com.milaboratory.util.RangeMap
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import io.repseq.core.ReferencePoint
import java.math.BigDecimal
import java.util.*
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collectors
import java.util.stream.IntStream
import java.util.stream.Stream
import kotlin.math.max
import kotlin.math.min

internal class ClusterProcessor private constructor(
    private val parameters: SHMTreeBuilderParameters,
    private val VScoring: AlignmentScoring<NucleotideSequence>,
    private val JScoring: AlignmentScoring<NucleotideSequence>,
    private val originalCluster: Cluster<CloneWrapper>,
    private val VSequence1: NucleotideSequence,
    private val JSequence1: NucleotideSequence,
    private val NDNScoring: AlignmentScoring<NucleotideSequence>,
    private val clusterInfo: CalculatedClusterInfo,
    private val idGenerator: IdGenerator
) {
    fun applyStep(stepName: BuildSHMTreeStep, currentTrees: List<TreeWithMetaBuilder>): StepResult =
        stepByName(stepName).next(currentTrees) {
            val clonesInTrees = currentTrees.stream()
                .flatMap { it.clonesAdditionHistory.stream() }
                .collect(Collectors.toSet())
            rebaseFromGermline(originalCluster.cluster.stream().filter { !clonesInTrees.contains(it.clone.id) })
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
    fun buildTreeTopParts(relatedAllelesMutations: Map<String, List<Mutations<NucleotideSequence>>>): StepResult {
        //use only clones that are at long distance from any germline
        val clonesThatNotMatchAnyGermline = originalCluster.cluster.stream()
            .filter { !hasVJPairThatMatchesWithGermline(it.clone) }
        val clones = rebaseFromGermline(clonesThatNotMatchAnyGermline)
        val result = clusterByCommonMutations(clones, relatedAllelesMutations)
            .stream()
            .filter { it.cluster.size > 1 }
            .map { cluster: Cluster<CloneWithMutationsFromVJGermline> -> buildATreeWithDecisionsInfo(cluster) }
            .collect(Collectors.toList())
        return buildStepResult(
            result.flatMap { it.first }.toMap(),
            result.map { it.second }.toList()
        )
    }

    private fun hasVJPairThatMatchesWithGermline(clone: Clone?): Boolean {
        return Arrays.stream(clone!!.getHits(Variable))
            .flatMap { VHit: VDJCHit ->
                Arrays.stream(
                    clone.getHits(Joining)
                )
                    .map { JHit: VDJCHit -> mutationsCount(VHit) + mutationsCount(JHit) }
            }
            .anyMatch { it < parameters.commonMutationsCountForClustering }
    }

    private fun mutationsCount(hit: VDJCHit): Int {
        return Arrays.stream(hit.alignments)
            .mapToInt { it.absoluteMutations.size() }
            .sum()
    }

    fun restore(resultTrees: List<TreeWithMetaBuilder.Snapshot>): List<TreeWithMetaBuilder> {
        val clonesInTrees = resultTrees
            .flatMap { it.clonesAdditionHistory }
            .toSet()
        val clonesByIds = originalCluster.cluster
            .filter { it.clone.id in clonesInTrees }
            .associateBy { it.clone.id }
        return resultTrees.map { treeSnapshot ->
            val treeWithMetaBuilder = TreeWithMetaBuilder(
                createTreeBuilder(treeSnapshot.rootInfo),
                treeSnapshot.rootInfo,
                ClonesRebase(VSequence1, VScoring, NDNScoring, JSequence1, JScoring),
                LinkedList(),
                treeSnapshot.treeId
            )
            treeSnapshot.clonesAdditionHistory.forEach { cloneId ->
                val rebasedClone = treeWithMetaBuilder.rebaseClone(rebaseFromGermline(clonesByIds[cloneId]!!))
                treeWithMetaBuilder.addClone(rebasedClone)
            }
            treeWithMetaBuilder
        }
    }

    private fun rebaseFromGermline(clones: Stream<CloneWrapper>): List<CloneWithMutationsFromVJGermline> {
        return clones
            .filter { cloneWrapper ->
                (clusterInfo.commonVAlignmentRanges.containsCloneWrapper(cloneWrapper)
                    && clusterInfo.commonJAlignmentRanges.containsCloneWrapper(cloneWrapper))
            }
            .map { cloneWrapper -> rebaseFromGermline(cloneWrapper) }
            .collect(Collectors.toList())
    }

    private fun rebaseFromGermline(cloneWrapper: CloneWrapper): CloneWithMutationsFromVJGermline {
        val CDR3 = cloneWrapper.getFeature(GeneFeature.CDR3)!!.sequence
        val VMutationsInCDR3WithoutNDN = MutationsWithRange(
            VSequence1,
            getMutationsForRange(cloneWrapper, clusterInfo.VRangeInCDR3, Variable)
                .extractAbsoluteMutations(clusterInfo.VRangeInCDR3, false),
            RangeInfo(clusterInfo.VRangeInCDR3, false)
        )
        val JMutationsInCDR3WithoutNDN = MutationsWithRange(
            JSequence1,
            getMutationsForRange(cloneWrapper, clusterInfo.JRangeInCDR3, Joining)
                .extractAbsoluteMutations(clusterInfo.JRangeInCDR3, true),
            RangeInfo(clusterInfo.JRangeInCDR3, false)
        )
        val VMutationsWithoutCDR3 = getMutationsWithoutCDR3(
            cloneWrapper, Variable,
            Range(clusterInfo.VRangeInCDR3.lower, VSequence1.size()),
            clusterInfo.commonVAlignmentRanges
        )
        val JMutationsWithoutCDR3 = getMutationsWithoutCDR3(
            cloneWrapper, Joining,
            Range(0, clusterInfo.JRangeInCDR3.lower),
            clusterInfo.commonJAlignmentRanges
        )
        val result = MutationsFromVJGermline(
            VGeneMutations(
                VSequence1,
                RangeMap<Mutations<NucleotideSequence>>().also { map ->
                    VMutationsWithoutCDR3.forEach {
                        map.put(it.rangeInfo.range, it.mutations)
                    }
                },
                PartInCDR3(VMutationsInCDR3WithoutNDN.rangeInfo.range, VMutationsInCDR3WithoutNDN.mutations)
            ),
            JGeneMutations(
                JSequence1,
                PartInCDR3(JMutationsInCDR3WithoutNDN.rangeInfo.range, JMutationsInCDR3WithoutNDN.mutations),
                RangeMap<Mutations<NucleotideSequence>>().also { map ->
                    JMutationsWithoutCDR3.forEach {
                        map.put(it.rangeInfo.range, it.mutations)
                    }
                }
            ),
            VMutationsWithoutCDR3,
            VMutationsInCDR3WithoutNDN,
            getVMutationsWithinNDN(cloneWrapper, clusterInfo.VRangeInCDR3.upper),
            CDR3.getRange(
                clusterInfo.VRangeInCDR3.length() + VMutationsInCDR3WithoutNDN.mutations.lengthDelta,
                CDR3.size() - (clusterInfo.JRangeInCDR3.length() + JMutationsInCDR3WithoutNDN.mutations.lengthDelta)
            ),
            getJMutationsWithinNDN(cloneWrapper, clusterInfo.JRangeInCDR3.lower),
            JMutationsInCDR3WithoutNDN,
            JMutationsWithoutCDR3
        )

        //TODO remove
        assertClone(cloneWrapper, result.VMutations, result.JMutations, result.knownNDN)

        return CloneWithMutationsFromVJGermline(result, cloneWrapper)
    }

    private fun combineTrees(originalTrees: List<TreeWithMetaBuilder>): StepResult {
        val clonesRebase = ClonesRebase(VSequence1, VScoring, NDNScoring, JSequence1, JScoring)
        val result: MutableList<TreeWithMetaBuilder> = ArrayList()
        val originalTreesCopy = originalTrees.stream()
            .sorted(Comparator.comparingInt { obj: TreeWithMetaBuilder -> obj.clonesCount() }.reversed())
            .collect(Collectors.toList())
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
                        Stream.of(treeToGrow, treeToAttach)
                            .flatMap { treeWithMetaBuilder ->
                                treeWithMetaBuilder.allNodes()
                                    .map { it.node }
                                    .map { node ->
                                        node.content.convert({ value -> Optional.of(value) }) { Optional.empty() }
                                    }
                                    .flatMap { it.stream() }
                                    .map {
                                        CloneWithMutationsFromVJGermline(
                                            it.mutationsFromVJGermline,
                                            it.clone
                                        )
                                    }
                            }
                            .collect(Collectors.toList())
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
        val oldestAncestorOfFrom = from.oldestReconstructedAncestor()
        val oldestAncestorOfDestination = destination.oldestReconstructedAncestor()
        val destinationRebasedOnFrom = clonesRebase.rebaseMutations(
            oldestAncestorOfDestination.fromRootToThis,
            destination.rootInfo,
            from.rootInfo
        )
        return distance(MutationsUtils.mutationsBetween(oldestAncestorOfFrom.fromRootToThis, destinationRebasedOnFrom))
    }

    private fun attachClonesByNDN(
        originalTrees: List<TreeWithMetaBuilder>, clonesNotInClusters: Supplier<List<CloneWithMutationsFromVJGermline>>
    ): StepResult {
        val decisions: MutableMap<Int, TreeWithMetaBuilder.DecisionInfo> = HashMap()
        val resultTrees =
            originalTrees.stream().map { obj: TreeWithMetaBuilder -> obj.copy() }.collect(Collectors.toList())
        clonesNotInClusters.get().stream()
            .filter { clone -> clone.mutations.VJMutationsCount < parameters.commonMutationsCountForClustering }
            .forEach { clone ->
                val bestTreeToAttach = resultTrees.stream()
                    .map { tree: TreeWithMetaBuilder? ->
                        val rebasedClone = tree!!.rebaseClone(clone)
                        val oldestAncestorOfTreeToGrow = tree.oldestReconstructedAncestor()
                        val nodeToAttach = SyntheticNode.createFromMutations(rebasedClone.mutationsFromRoot)
                        val NDNOfTreeToGrow = oldestAncestorOfTreeToGrow.fromRootToThis.knownNDN.buildSequence()
                        val NDNOfNodeToAttach = nodeToAttach.fromRootToThis.knownNDN.buildSequence()
                        val score_1 = Aligner.alignGlobal(
                            NDNScoring,
                            NDNOfNodeToAttach,
                            NDNOfTreeToGrow
                        ).score
                        val score_2 = Aligner.alignGlobal(
                            NDNScoring,
                            NDNOfTreeToGrow,
                            NDNOfNodeToAttach
                        ).score
                        val NDNLength = tree.rootInfo.reconstructedNDN.size()
                        val maxScore = NDNScoring.maximalMatchScore * NDNLength
                        val metric_1 = (maxScore - score_1) / NDNLength.toDouble()
                        val metric_2 = (maxScore - score_2) / NDNLength.toDouble()
                        Pair(min(metric_1, metric_2)) { tree.addClone(rebasedClone) }
                    }
                    .min(Comparator.comparing { it.first })
                if (bestTreeToAttach.isPresent) {
                    val metric = bestTreeToAttach.get().first
                    if (metric <= parameters.thresholdForCombineByNDN) {
                        bestTreeToAttach.get().second()
                        decisions[clone.cloneWrapper.clone.id] = MetricDecisionInfo(metric)
                    }
                }
            }
        return buildStepResult(decisions, resultTrees)
    }

    private fun attachClonesByDistanceChange(
        originalTrees: List<TreeWithMetaBuilder>,
        clonesNotInClustersSupplier: Supplier<List<CloneWithMutationsFromVJGermline>>
    ): StepResult {
        val result = originalTrees.stream().map { obj: TreeWithMetaBuilder -> obj.copy() }.collect(Collectors.toList())
        val decisions: MutableMap<Int, TreeWithMetaBuilder.DecisionInfo> = HashMap()
        val clonesNotInClusters = clonesNotInClustersSupplier.get()
        //try to add as nodes clones that wasn't picked up by clustering
        for (i in clonesNotInClusters.indices.reversed()) {
            val clone = clonesNotInClusters[i]
            if (clone.mutations.VJMutationsCount >= parameters.commonMutationsCountForClustering) {
                val bestActionAndDistanceFromRoot = result.stream()
                    .map { treeWithMeta: TreeWithMetaBuilder? ->
                        val rebasedClone = treeWithMeta!!.rebaseClone(clone)
                        Pair(
                            treeWithMeta.bestAction(rebasedClone),
                            treeWithMeta.distanceFromRootToClone(rebasedClone)
                        )
                    }
                    .min(Comparator.comparing { p -> p.first.changeOfDistance() })
                if (bestActionAndDistanceFromRoot.isPresent) {
                    val bestAction = bestActionAndDistanceFromRoot.get().first
                    val distanceFromRoot = bestActionAndDistanceFromRoot.get().second
                    val metric = bestAction.changeOfDistance().toDouble() / distanceFromRoot
                    if (metric <= parameters.thresholdForFreeClones) {
                        decisions[clone.cloneWrapper.clone.id] = MetricDecisionInfo(metric)
                        bestAction.apply()
                    }
                }
            }
        }
        return buildStepResult(decisions, result)
    }

    private fun clusterByCommonMutations(
        clones: List<CloneWithMutationsFromVJGermline>,
        relatedAllelesMutations: Map<String, List<Mutations<NucleotideSequence>>>
    ): List<Cluster<CloneWithMutationsFromVJGermline>> {
        val matrix = AdjacencyMatrix(clones.size)
        for (i in clones.indices) {
            for (j in clones.indices) {
                if (commonMutationsCount(
                        relatedAllelesMutations,
                        clones[i],
                        clones[j]
                    ) >= parameters.commonMutationsCountForClustering
                ) {
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
                if (notOverlappedCliques.stream().noneMatch { it.intersects(clique) }) {
                    notOverlappedCliques.add(clique)
                }
            }
        val clusters: MutableList<Cluster<CloneWithMutationsFromVJGermline>> = ArrayList()
        for (clique in notOverlappedCliques) {
            clusters.add(Cluster(Arrays.stream(clique.bits)
                .mapToObj { index: Int -> clones[index] }
                .collect(Collectors.toList())
            ))
        }
        return clusters
    }

    private fun commonMutationsCount(
        relatedAllelesMutations: Map<String, List<Mutations<NucleotideSequence>>>,
        first: CloneWithMutationsFromVJGermline,
        second: CloneWithMutationsFromVJGermline
    ): Int {
        val VAllelesMutations = mutationsFromThisAlleleToOthers(Variable, first, second, relatedAllelesMutations)
        val JAllelesMutations = mutationsFromThisAlleleToOthers(Joining, first, second, relatedAllelesMutations)
        return commonMutationsCount(first.mutations.VMutations, second.mutations.VMutations, VAllelesMutations) +
            commonMutationsCount(first.mutations.JMutations, second.mutations.JMutations, JAllelesMutations)
    }

    private fun NDNDistance(first: CloneWithMutationsFromVJGermline, second: CloneWithMutationsFromVJGermline): Double {
        val firstNDN = first.mutations.knownNDN
        val secondNDN = second.mutations.knownNDN
        val score = Aligner.alignGlobal(
            NDNScoring,
            firstNDN,
            secondNDN
        ).score
        val maxScore = max(
            maxScore(firstNDN, NDNScoring),
            maxScore(secondNDN, NDNScoring)
        )
        return (maxScore - score) / min(firstNDN.size(), secondNDN.size()).toDouble()
    }

    private fun commonMutationsCount(
        first: GeneMutations,
        second: GeneMutations,
        allelesMutations: List<Mutations<NucleotideSequence>>
    ): Int = (allelesMutations.asSequence() + Mutations.EMPTY_NUCLEOTIDE_MUTATIONS)
        .distinct()
        .map { alleleMutations ->
            commonMutationsCount(without(first, alleleMutations), without(second, alleleMutations))
        }
        .minOrNull()!!

    private fun without(
        cloneMutations: GeneMutations,
        alleleMutations: Mutations<NucleotideSequence>
    ): RangeMap<Mutations<NucleotideSequence>> {
        if (alleleMutations.size() == 0) return cloneMutations.mutations.copy().also {
            if (!cloneMutations.partInCDR3.range.isEmpty) {
                it.put(cloneMutations.partInCDR3.range, cloneMutations.partInCDR3.mutations)
            }
        }
        val result = RangeMap<Mutations<NucleotideSequence>>()
        val alleleMutationsSet = alleleMutations.asSequence().toSet()
        cloneMutations.mutations.entrySet().forEach { (range, mutations) ->
            result.put(
                range,
                mutations.asSequence()
                    .filter { !alleleMutationsSet.contains(it) }
                    .asMutations(ALPHABET)
            )
        }
        if (!cloneMutations.partInCDR3.range.isEmpty) {
            result.put(
                cloneMutations.partInCDR3.range,
                cloneMutations.partInCDR3.mutations.asSequence()
                    .filter { !alleleMutationsSet.contains(it) }
                    .asMutations(ALPHABET)
            )
        }
        return result
    }

    private fun commonMutationsCount(
        first: RangeMap<Mutations<NucleotideSequence>>,
        second: RangeMap<Mutations<NucleotideSequence>>
    ): Int {
        check(first.keySet() == second.keySet())
        return first.keySet().sumOf { range ->
            val mutationsOfFirstAsSet = first.get(range).asSequence().toSet()
            second.get(range).asSequence().count { mutationsOfFirstAsSet.contains(it) }
        }
    }

    private fun mutationsFromThisAlleleToOthers(
        geneType: GeneType,
        first: CloneWithMutationsFromVJGermline,
        second: CloneWithMutationsFromVJGermline,
        relatedAllelesMutations: Map<String, List<Mutations<NucleotideSequence>>>,
    ): List<Mutations<NucleotideSequence>> {
        require(first.cloneWrapper.getHit(geneType).gene == second.cloneWrapper.getHit(geneType).gene)
        val baseGene = first.cloneWrapper.getHit(geneType).gene
        return relatedAllelesMutations[baseGene.name] ?: emptyList()
    }

    private fun commonMutationsCount(first: List<MutationsWithRange>, second: List<MutationsWithRange>): Int =
        MutationsUtils.fold(
            first, second
        ) { a, b ->
            val intersection = a.rangeInfo.intersection(b.rangeInfo)!!
            val mutations = intersection(
                a.mutations,
                b.mutations,
                intersection
            )
            mutations.size()
        }.sum()

    private fun buildATreeWithDecisionsInfo(cluster: Cluster<CloneWithMutationsFromVJGermline>): Pair<List<Pair<Int, TreeWithMetaBuilder.DecisionInfo>>, TreeWithMetaBuilder> {
        val treeWithMetaBuilder = buildATree(cluster)
        val decisionsInfo = cluster.cluster.stream()
            .map {
                val effectiveParent = treeWithMetaBuilder.getEffectiveParent(it.cloneWrapper.clone)
                val VHit = it.cloneWrapper.getHit(Variable)
                val JHit = it.cloneWrapper.getHit(Joining)
                Pair(
                    it.cloneWrapper.clone.id,
                    ZeroStepDecisionInfo(
                        effectiveParent.fromRootToThis.combinedVMutations().size() +
                            effectiveParent.fromRootToThis.combinedJMutations().size(),
                        VHit.gene.geneName,
                        JHit.gene.geneName,
                        VHit.score,
                        JHit.score
                    )
                )
            }
            .collect(Collectors.toList())
        return Pair(decisionsInfo, treeWithMetaBuilder)
    }

    private fun buildATree(cluster: Cluster<CloneWithMutationsFromVJGermline>): TreeWithMetaBuilder {
        val clonesRebase = ClonesRebase(VSequence1, VScoring, NDNScoring, JSequence1, JScoring)
        val rootInfo = buildRootInfo(cluster)
        val rebasedCluster = cluster.cluster.asSequence()
            .map { clone ->
                val result = clonesRebase.rebaseClone(
                    rootInfo,
                    clone.mutations,
                    clone.cloneWrapper
                )
                assertClone(
                    clone.cloneWrapper,
                    result.mutationsSet.VMutations,
                    result.mutationsSet.JMutations,
                    result.mutationsSet.NDNMutations.mutations.mutate(result.mutationsSet.NDNMutations.base)
                )
                result
            }
            .sortedByDescending { distance(it.mutationsFromRoot) }
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

    private fun createTreeBuilder(rootInfo: RootInfo): TreeBuilderByAncestors<CloneWithMutationsFromReconstructedRoot, SyntheticNode, MutationsDescription> {
        val root = SyntheticNode.createRoot(
            clusterInfo.commonVAlignmentRanges,
            VSequence1,
            rootInfo,
            clusterInfo.commonJAlignmentRanges,
            JSequence1
        )
        return TreeBuilderByAncestors(
            root,
            { base, mutations -> distance(mutations) + penaltyForReversedMutations(base, mutations) },
            { first, second ->
                MutationsUtils.mutationsBetween(
                    first.fromRootToThis,
                    second.fromRootToThis
                )
            },
            { parent, fromParentToThis -> parent.mutate(fromParentToThis) },
            { observed -> SyntheticNode.createFromMutations(observed.mutationsFromRoot) },
            { first, second -> commonMutations(first, second) },
            { parent, child ->
                SyntheticNode.createFromMutations(
                    child.fromRootToThis.withKnownNDNMutations(
                        MutationsWithRange(
                            child.fromRootToThis.knownNDN.sequence1,
                            MutationsUtils.concreteNDNChild(
                                parent.fromRootToThis.knownNDN.mutations,
                                child.fromRootToThis.knownNDN.mutations
                            ),
                            child.fromRootToThis.knownNDN.rangeInfo
                        )
                    )
                )
            },
            parameters.countOfNodesToProbe
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
            VRangeInCDR3,
            NDNBuilder.createAndDestroy(),
            JRangeInCDR3,
            rootBasedOn.cloneWrapper.VJBase
        )
    }

    private fun getMutationsWithoutCDR3(
        clone: CloneWrapper,
        geneType: GeneType,
        CDR3Range: Range,
        commonAlignmentRanges: ClonesAlignmentRanges
    ): List<MutationsWithRange> {
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
                    MutationsWithRange(
                        alignment.sequence1,
                        mutations.extractAbsoluteMutations(range, isIncludeFirstInserts),
                        RangeInfo(range, isIncludeFirstInserts)
                    )
                }
        }
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
        mutations: MutationsDescription
    ): BigDecimal {
        val reversedMutationsCount =
            reversedVMutationsCount(fromRootToBase, mutations) + reversedJMutationsCount(fromRootToBase, mutations)
        return BigDecimal.valueOf(parameters.penaltyForReversedMutations)
            .multiply(BigDecimal.valueOf(reversedMutationsCount.toLong()))
    }

    private fun distance(mutations: MutationsDescription): BigDecimal {
        val VPenalties = maxScore(mutations.VMutationsWithoutCDR3, VScoring) - score(
            mutations.VMutationsWithoutCDR3,
            VScoring
        ) +
            maxScore(
                mutations.VMutationsInCDR3WithoutNDN,
                VScoring
            ) - score(mutations.VMutationsInCDR3WithoutNDN, VScoring)
        val VLength = mutations.VMutationsWithoutCDR3.stream()
            .mapToInt { it.rangeInfo.range.length() }.sum() +
            mutations.VMutationsInCDR3WithoutNDN.rangeInfo.range.length()
        val JPenalties = maxScore(mutations.JMutationsWithoutCDR3, JScoring) -
            score(mutations.JMutationsWithoutCDR3, JScoring) +
            maxScore(mutations.JMutationsInCDR3WithoutNDN, JScoring) -
            score(mutations.JMutationsInCDR3WithoutNDN, JScoring)
        val JLength = mutations.JMutationsWithoutCDR3.stream()
            .mapToInt { it.rangeInfo.range.length() }.sum() +
            mutations.JMutationsInCDR3WithoutNDN.rangeInfo.range.length()
        val NDNPenalties = maxScore(mutations.knownNDN, NDNScoring) - score(mutations.knownNDN, NDNScoring)
        val NDNLength = mutations.knownNDN.rangeInfo.range.length().toDouble()

//        return BigDecimal.valueOf(NDNPenalties / NDNLength + (VPenalties + JPenalties) / (VLength + JLength));
        return BigDecimal.valueOf((NDNPenalties * parameters.NDNScoreMultiplier + VPenalties + JPenalties) / (NDNLength + VLength + JLength))
    }

    private fun commonMutations(first: MutationsDescription, second: MutationsDescription): MutationsDescription {
        return MutationsDescription(
            intersection(first.VMutationsWithoutCDR3, second.VMutationsWithoutCDR3),
            intersection(first.VMutationsInCDR3WithoutNDN, second.VMutationsInCDR3WithoutNDN),
            MutationsWithRange(
                first.knownNDN.sequence1,
                MutationsUtils.findNDNCommonAncestor(
                    first.knownNDN.mutations,
                    second.knownNDN.mutations
                ),
                first.knownNDN.rangeInfo
            ),
            intersection(first.JMutationsInCDR3WithoutNDN, second.JMutationsInCDR3WithoutNDN),
            intersection(first.JMutationsWithoutCDR3, second.JMutationsWithoutCDR3)
        )
    }

    //TODO it is more possible to decrease length of alignment than to increase
    private fun mostLikableRangeInCDR3(
        cluster: Cluster<CloneWithMutationsFromVJGermline>,
        rangeSupplier: (CloneWrapper) -> Range
    ): Range {
        return cluster.cluster.stream()
            .sorted(Comparator.comparing { it.mutations.VJMutationsCount })
            .limit(parameters.topToVoteOnNDNSize.toLong())
            .map { obj: CloneWithMutationsFromVJGermline -> obj.cloneWrapper }
            .map(rangeSupplier)
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
            .entries.stream()
            .max(java.util.Map.Entry.comparingByValue<Range, Long>()
                .thenComparing(Comparator.comparingInt { (key, _): Map.Entry<Range, Long> -> key.length() }
                    .reversed()))
            .map { (key, _) -> key }
            .orElseThrow { IllegalStateException() }
    }

    private fun buildStepResult(
        decisions: Map<Int, TreeWithMetaBuilder.DecisionInfo>,
        trees: List<TreeWithMetaBuilder>
    ): StepResult {
        return StepResult(
            decisions,
            trees.stream()
                .map { obj: TreeWithMetaBuilder? -> obj!!.snapshot() }
                .collect(Collectors.toList()),
            trees.stream()
                .flatMap { tree: TreeWithMetaBuilder ->
                    tree.allNodes()
                        .filter { it.node.content is Reconstructed<*, *> }
                        .map { nodeWithParent ->
                            buildDebugInfo(
                                decisions,
                                tree,
                                nodeWithParent
                            )
                        }
                }
                .collect(Collectors.toList())
        )
    }

    private fun buildDebugInfo(
        decisions: Map<Int, TreeWithMetaBuilder.DecisionInfo>,
        tree: TreeWithMetaBuilder,
        nodeWithParent: NodeWithParent<ObservedOrReconstructed<CloneWithMutationsFromReconstructedRoot, SyntheticNode>>
    ): DebugInfo {
        val nodeContent = nodeWithParent.node
            .content
            .convert(
                { Optional.empty() },
                { Optional.of(it) })
            .orElseThrow { IllegalArgumentException() }
        val cloneId = nodeWithParent.node.links.stream()
            .map { it.node }
            .map { child: Tree.Node<ObservedOrReconstructed<CloneWithMutationsFromReconstructedRoot, SyntheticNode>> ->
                child.content.convert(
                    { Optional.of(it.clone.clone.id) },
                    { Optional.empty() })
            }
            .flatMap { obj -> obj.stream() }
            .findAny().orElse(null)
        var metric: Double? = null
        if (cloneId != null) {
            val decision = decisions[cloneId]
            if (decision is MetricDecisionInfo) {
                metric = decision.metric
            }
        }
        val parentMutations = Optional.ofNullable(nodeWithParent.parent)
            .flatMap { parent ->
                parent.content.convert(
                    { Optional.empty() },
                    { Optional.of(it) })
            }
            .map { parent ->
                MutationsSet(
                    parent.fromRootToThis.combinedVMutations().invert()
                        .combineWith(nodeContent.fromRootToThis.combinedVMutations()),
                    parent.fromRootToThis.knownNDN.mutations.invert()
                        .combineWith(nodeContent.fromRootToThis.knownNDN.mutations),
                    parent.fromRootToThis.combinedJMutations().invert()
                        .combineWith(nodeContent.fromRootToThis.combinedJMutations())
                )
            }
        return DebugInfo(
            tree.treeId,
            tree.rootInfo,
            nodeContent.fromRootToThis.VMutationsWithoutCDR3.stream()
                .map { it.rangeInfo.range }
                .collect(Collectors.toList()),
            nodeContent.fromRootToThis.JMutationsWithoutCDR3.stream()
                .map { it.rangeInfo.range }
                .collect(Collectors.toList()),
            cloneId,
            nodeWithParent.node.content.id,
            Optional.ofNullable(nodeWithParent.parent)
                .map { it.content.id }
                .orElse(null),
            nodeContent.fromRootToThis.knownNDN.buildSequence(),
            MutationsSet(
                nodeContent.fromRootToThis.combinedVMutations(),
                nodeContent.fromRootToThis.knownNDN.mutations,
                nodeContent.fromRootToThis.combinedJMutations()
            ),
            parentMutations.orElse(null),
            metric,
            isPublic(tree.rootInfo)
        )
    }

    private fun isPublic(rootInfo: RootInfo): Boolean =
        rootInfo.reconstructedNDN.size() <= parameters.NDNSizeLimitForPublicClones

    fun debugInfos(currentTrees: List<TreeWithMetaBuilder?>?): List<DebugInfo> {
        return currentTrees!!.stream()
            .flatMap { tree: TreeWithMetaBuilder? ->
                tree!!.allNodes()
                    .filter { it.node.content is Reconstructed }
                    .map { nodeWithParent ->
                        buildDebugInfo(
                            emptyMap(),
                            tree,
                            nodeWithParent
                        )
                    }
            }
            .collect(Collectors.toList())
    }

    private interface Step {
        fun next(
            originalTrees: List<TreeWithMetaBuilder>,
            clonesNotInClusters: () -> List<CloneWithMutationsFromVJGermline>
        ): StepResult
    }

    class StepResult internal constructor(
        val decisions: Map<Int, TreeWithMetaBuilder.DecisionInfo>,
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
            idGenerator: IdGenerator
        ): ClusterProcessor {
            require(originalCluster.cluster.isNotEmpty())
            val anyClone = originalCluster.cluster[0]
            return ClusterProcessor(
                parameters,
                VScoring,
                JScoring,
                originalCluster,
                anyClone.getHit(Variable).getAlignment(0).sequence1,
                anyClone.getHit(Joining).getAlignment(0).sequence1,
                MutationsUtils.NDNScoring(),
                calculatedClusterInfo,
                idGenerator
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
            require(chooses.values.stream().allMatch { obj: E -> decisionType.isInstance(obj) })
            return when (decisionType) {
                ZeroStepDecisionInfo::class.java -> makeDecisionForZero(chooses as Map<VJBase, ZeroStepDecisionInfo>)
                MetricDecisionInfo::class.java -> makeDecisionByMetric(chooses as Map<VJBase, MetricDecisionInfo>)
                else -> throw UnsupportedOperationException()
            }
        }

        private fun makeDecisionByMetric(chooses: Map<VJBase, MetricDecisionInfo>): VJBase {
            return chooses.entries.stream()
                .min(Comparator.comparing { (_, value): Map.Entry<VJBase, MetricDecisionInfo> -> value.metric })
                .orElseThrow { IllegalStateException() }!!
                .key
        }

        private fun makeDecisionForZero(chooses: Map<VJBase, ZeroStepDecisionInfo>): VJBase {
            val filteredByAlleles =
                chooses.entries.stream() //group by the same origin VJ pair - group decisions by related alleles
                    .collect(
                        Collectors.groupingBy { (_, value) ->
                            Pair(
                                value.getGeneName(Variable),
                                value.getGeneName(Joining)
                            )
                        })
                    .values.stream() //choose allele pair with decision that is most closed to germline
                    .map { withTheSameGeneBase ->
                        withTheSameGeneBase.stream()
                            .min(Comparator.comparing { (_, value) -> value.commonMutationsCount })
                    }
                    .flatMap { obj -> obj.stream() }
                    .map { (key, _) -> key }
                    .collect(Collectors.toSet())
            return filteredByAlleles.stream()
                .max(Comparator.comparing {
                    chooses[it]!!.getScore(Variable) + chooses[it]!!.getScore(Joining)
                })
                .orElseThrow { IllegalStateException() }!!
        }

        private fun reversedVMutationsCount(fromRootToBase: SyntheticNode, mutations: MutationsDescription): Int {
            val reversedMutationsNotInCDR3 = MutationsUtils.fold(
                fromRootToBase.fromRootToThis.VMutationsWithoutCDR3,
                mutations.VMutationsWithoutCDR3
            ) { a, b -> reversedMutationsCount(a, b) }.sum()
            val reversedMutationsInCDR3 = reversedMutationsCount(
                fromRootToBase.fromRootToThis.VMutationsInCDR3WithoutNDN,
                mutations.VMutationsInCDR3WithoutNDN
            )
            return reversedMutationsInCDR3 + reversedMutationsNotInCDR3
        }

        private fun reversedJMutationsCount(fromRootToBase: SyntheticNode, mutations: MutationsDescription): Int {
            val reversedMutationsNotInCDR3 = MutationsUtils.fold(
                fromRootToBase.fromRootToThis.JMutationsWithoutCDR3,
                mutations.JMutationsWithoutCDR3
            ) { a, b -> reversedMutationsCount(a, b) }.sum()
            val reversedMutationsInCDR3 = reversedMutationsCount(
                fromRootToBase.fromRootToThis.JMutationsInCDR3WithoutNDN,
                mutations.JMutationsInCDR3WithoutNDN
            )
            return reversedMutationsInCDR3 + reversedMutationsNotInCDR3
        }

        private fun reversedMutationsCount(a: MutationsWithRange, b: MutationsWithRange): Int {
            val reversedMutations = b.mutationsForRange().move(a.rangeInfo.range.lower).invert()
            val asSet = reversedMutations.rawMutations.asSequence().toSet()
            return a.mutations.asSequence().count { asSet.contains(it) }
        }

        private fun minRangeInCDR3(
            cluster: Cluster<CloneWrapper>,
            rangeSupplier: Function<CloneWrapper, Range>
        ): Range {
            //TODO try to use alignment to calculate most possible position
            return cluster.cluster.stream()
                .map(rangeSupplier)
                .min(Comparator.comparing { obj: Range -> obj.length() })
                .orElseThrow { IllegalStateException() }
        }

        private fun VRangeInCDR3(clone: CloneWrapper): Range {
            val alignments = clone.getHit(Variable).alignments
            return Range(
                clone.getRelativePosition(Variable, ReferencePoint.CDR3Begin),
                alignments[alignments.size - 1].sequence1Range.upper
            )
        }

        private fun JRangeInCDR3(clone: CloneWrapper): Range {
            return Range(
                clone.getHit(Joining).getAlignment(0).sequence1Range.lower,
                clone.getRelativePosition(Joining, ReferencePoint.CDR3End)
            )
        }

        /**
         * sum score of given mutations
         */
        private fun score(
            mutationsWithRanges: List<MutationsWithRange>,
            scoring: AlignmentScoring<NucleotideSequence>
        ): Double = mutationsWithRanges.stream()
            .mapToDouble { mutations -> score(mutations, scoring).toDouble() }
            .sum()

        private fun score(mutations: MutationsWithRange, scoring: AlignmentScoring<NucleotideSequence>): Int =
            AlignmentUtils.calculateScore(
                mutations.sequence1,
                mutations.mutationsForRange(),
                scoring
            )

        private fun maxScore(
            vMutationsBetween: List<MutationsWithRange>,
            scoring: AlignmentScoring<NucleotideSequence>
        ): Double = vMutationsBetween.stream()
            .mapToDouble { mutations -> maxScore(mutations, scoring).toDouble() }
            .sum()

        private fun maxScore(mutations: MutationsWithRange, scoring: AlignmentScoring<NucleotideSequence>): Int =
            maxScore(mutations.sequence1, scoring)

        private fun maxScore(sequence: NucleotideSequence, scoring: AlignmentScoring<NucleotideSequence>): Int =
            sequence.size() * scoring.maximalMatchScore
    }
}
