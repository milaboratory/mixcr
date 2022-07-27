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
@file:Suppress("LocalVariableName", "FunctionName", "PrivatePropertyName", "PropertyName")

package com.milaboratory.mixcr.trees

import com.milaboratory.core.Range
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
import io.repseq.core.GeneFeature
import io.repseq.core.GeneFeature.JCDR3Part
import io.repseq.core.GeneFeature.VCDR3Part
import io.repseq.core.GeneType
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import io.repseq.core.ReferencePoint.CDR3Begin
import io.repseq.core.ReferencePoint.CDR3End
import io.repseq.core.VDJCGeneId
import java.util.*
import java.util.function.Supplier
import kotlin.math.max
import kotlin.math.min

internal class ClusterProcessor(
    private val parameters: SHMTreeBuilderParameters,
    private val scoringSet: ScoringSet,
    private val assemblingFeatures: Array<GeneFeature>,
    private val originalCluster: List<CloneWrapper>,
    private val VSequence1: NucleotideSequence,
    private val JSequence1: NucleotideSequence,
    private val idGenerator: IdGenerator,
    private val VJBase: VJBase
) {
    fun applyStep(
        stepName: BuildSHMTreeStep,
        currentTrees: List<TreeWithMetaBuilder>,
        allClonesInTress: Set<CloneWrapper.ID>
    ): StepResult = stepByName(stepName).next(currentTrees) {
        originalCluster.asSequence()
            .filter { it.id !in allClonesInTress }
            .map { cloneWrapper -> rebaseFromGermline(cloneWrapper) }
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
     * @see OriginalClustersBuilder.buildClusters
     */
    fun buildTreeTopParts(relatedAllelesMutations: Map<VDJCGeneId, List<Mutations<NucleotideSequence>>>): StepResult {
        //use only clones that are at long distance from any germline
        val clonesThatNotCloseToAnyGermline = originalCluster.asSequence()
            .filter { !hasVJPairThatCloseToGermline(it, parameters.commonMutationsCountForClustering) }
        val clones = clonesThatNotCloseToAnyGermline
            .map { cloneWrapper -> rebaseFromGermline(cloneWrapper) }
            .toList()
        val originalClustersBuilder: OriginalClustersBuilder = when (parameters.buildingInitialTreesAlgorithm) {
            "BronKerbosch" -> OriginalClustersBuilder.BronKerbosch(
                parameters,
                scoringSet,
                clones,
                VJBase,
                relatedAllelesMutations
            )
            "Hierarchical" -> OriginalClustersBuilder.Hierarchical(
                parameters,
                scoringSet,
                clones,
                VJBase,
                relatedAllelesMutations
            )
            else -> throw IllegalArgumentException("buildingInitialTreesAlgorithm param must be Hierarchical|BronKerbosch, got ${parameters.buildingInitialTreesAlgorithm}")
        }
        val clusters = originalClustersBuilder.buildClusters()
        val result = clusters
            .asSequence()
            .filter { it.size > 1 }
            .filter {
                //skip cluster if it formed by the same clones but with different C or from different samples
                val toCompare = it.first()
                it.subList(1, it.size).any { clone ->
                    !Arrays.equals(clone.cloneWrapper.clone.targets, toCompare.cloneWrapper.clone.targets)
                }
            }
            .map { cluster ->
                val treeWithMetaBuilder = buildATree(cluster)
                val decisionsInfo = cluster.map {
                    val effectiveParent = treeWithMetaBuilder.getEffectiveParent(it.cloneWrapper.id)
                    val VHit = it.cloneWrapper.getHit(Variable)
                    val JHit = it.cloneWrapper.getHit(Joining)
                    it.cloneWrapper.id to ZeroStepDecisionInfo(
                        effectiveParent.fromRootToThis.mutations.V.mutationsCount() +
                                effectiveParent.fromRootToThis.mutations.J.mutationsCount(),
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
        val rebasedFromGermline = originalCluster
            .map { cloneWrapper -> rebaseFromGermline(cloneWrapper) }
        val treeWithMetaBuilder = buildATree(rebasedFromGermline)
        return SHMTreeResult(
            treeWithMetaBuilder.buildResult(),
            treeWithMetaBuilder.rootInfo,
            treeId
        )
    }

    private fun hasVJPairThatCloseToGermline(cloneWrapper: CloneWrapper, threashold: Int): Boolean =
        cloneWrapper.candidateVJBases
            .map { VJBase ->
                cloneWrapper.clone.getHit(VJBase, Variable).mutationsCount() +
                        cloneWrapper.clone.getHit(VJBase, Joining).mutationsCount()
            }
            .any { it < threashold }

    private fun VDJCHit.mutationsCount(): Int = alignments.sumOf { it.absoluteMutations.size() }

    fun restore(snapshot: TreeWithMetaBuilder.Snapshot): TreeWithMetaBuilder {
        val clonesInTrees = snapshot.clonesAdditionHistory.toSet()
        val rebasedClonesFromGermline = originalCluster.asSequence()
            .filter { it.id in clonesInTrees }
            .map { cloneWrapper -> rebaseFromGermline(cloneWrapper) }
            .toList()
        return buildATree(rebasedClonesFromGermline)
    }

    /**
     * Base tree on NDN that was found before instead of sequence of N
     */
    fun restoreWithNDNFromMRCA(snapshot: TreeWithMetaBuilder.Snapshot): TreeWithMetaBuilder {
        val clonesInTrees = snapshot.clonesAdditionHistory.toSet()

        val rebasedCluster = originalCluster
            .asSequence()
            .filter { it.id in clonesInTrees }
            .map { cloneWrapper -> rebaseFromGermline(cloneWrapper) }
            .toList()

        val treeWithSequenceOfNInRoot = buildATree(rebasedCluster)
        val reconstructedNDN = treeWithSequenceOfNInRoot.mostRecentCommonAncestorNDN()
        return buildATree(
            rebasedCluster,
            rootInfo = treeWithSequenceOfNInRoot.rootInfo.copy(
                reconstructedNDN = reconstructedNDN
            )
        )
    }

    /**
     * Present all mutations as `MutationsFromVJGermline`
     * @see MutationsFromVJGermline
     */
    private fun rebaseFromGermline(cloneWrapper: CloneWrapper): CloneWithMutationsFromVJGermline =
        CloneWithMutationsFromVJGermline(
            MutationsFromVJGermline(
                VJPair(
                    cloneWrapper.getMutationsWithoutCDR3(Variable).toSortedMap(),
                    cloneWrapper.getMutationsWithoutCDR3(Joining).toSortedMap()
                ),
                VJPair(
                    getVMutationsWithinCDR3(cloneWrapper),
                    getJMutationsWithinCDR3(cloneWrapper)
                ),
                cloneWrapper.getFeature(GeneFeature.CDR3)!!.sequence
            ),
            cloneWrapper
        )

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
                val metric = min(distance_1, distance_2)
                if (metric <= parameters.thresholdForCombineTrees) {
                    treeToGrow = buildATree(
                        listOf(treeToGrow, treeToAttach).flatMap { treeWithMetaBuilder ->
                            treeWithMetaBuilder.allNodes()
                                .asSequence()
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
                    )
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
    ): Double {
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
                rootInfo.sequence1.V,
                first.mutations.V.partInCDR3.mutations,
                second.mutations.V.partInCDR3.mutations,
                rootInfo.rangeInCDR3.V
            ),
            MutationsUtils.difference(
                rootInfo.reconstructedNDN,
                first.NDNMutations.mutations,
                second.NDNMutations.mutations,
                Range(0, rootInfo.reconstructedNDN.size())
            ),
            MutationsUtils.difference(
                rootInfo.sequence1.J,
                first.mutations.J.partInCDR3.mutations,
                second.mutations.J.partInCDR3.mutations,
                rootInfo.rangeInCDR3.J
            ),
            mutationsBetween(rootInfo, first, second, Joining)
        )

    private fun mutationsBetween(
        rootInfo: RootInfo,
        firstMutations: MutationsSet,
        secondMutations: MutationsSet,
        geneType: GeneType
    ): Map<GeneFeature, CompositeMutations> =
        MutationsUtils.zip(
            firstMutations.mutations[geneType].mutationsOutsideOfCDR3,
            secondMutations.mutations[geneType].mutationsOutsideOfCDR3
        ) { base, comparison, geneFeature ->
            MutationsUtils.difference(
                rootInfo.sequence1[geneType],
                base,
                comparison,
                rootInfo.partitioning[geneType].getRange(geneFeature)
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
                        val metric_1 = scoringSet.NDNDistance(NDNOfTreeToGrow, NDNOfNodeToAttach)
                        val metric_2 = scoringSet.NDNDistance(NDNOfNodeToAttach, NDNOfTreeToGrow)
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
                        val NDNDistance = scoringSet.NDNDistance(NDNOfPossibleParent, NDNOfRebasedClone)
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
                    val metric = bestAction.changeOfDistance() / distanceFromRoot
                    if (metric <= parameters.thresholdForFreeClones) {
                        decisions[clone.cloneWrapper.id] = MetricDecisionInfo(metric)
                        bestAction.apply()
                    }
                }
            }
        }
        return buildStepResult(decisions, result)
    }

    private fun buildATree(
        cluster: List<CloneWithMutationsFromVJGermline>,
        rootInfo: RootInfo = cluster.buildRootInfo()
    ): TreeWithMetaBuilder {
        val clonesRebase = ClonesRebase(VSequence1, JSequence1, scoringSet)
        val rebasedCluster = cluster.asSequence()
            .map { clone ->
                clonesRebase.rebaseClone(
                    rootInfo,
                    clone.mutations,
                    clone.cloneWrapper
                )
            }
            .sortedBy { score(rootInfo, it.mutationsSet) }
            .toList()
        val alignFeatures = VJPair(
            cluster.first().mutations.mutations.V.keys.sorted(),
            cluster.first().mutations.mutations.J.keys.sorted(),
        )
        val treeBuilder = createTreeBuilder(rootInfo, alignFeatures)
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

    private fun createTreeBuilder(
        rootInfo: RootInfo,
        alignFeatures: VJPair<List<GeneFeature>>
    ): TreeBuilderByAncestors<CloneWithMutationsFromReconstructedRoot, SyntheticNode, NodeMutationsDescription> {
        val root = SyntheticNode(
            MutationsSet(
                VGeneMutations(
                    alignFeatures.V.associateWith { EMPTY_NUCLEOTIDE_MUTATIONS },
                    PartInCDR3(rootInfo.rangeInCDR3.V, EMPTY_NUCLEOTIDE_MUTATIONS)
                ),
                NDNMutations(EMPTY_NUCLEOTIDE_MUTATIONS),
                JGeneMutations(
                    PartInCDR3(rootInfo.rangeInCDR3.J, EMPTY_NUCLEOTIDE_MUTATIONS),
                    alignFeatures.J.associateWith { EMPTY_NUCLEOTIDE_MUTATIONS }
                )
            )
        )
        return TreeBuilderByAncestors(
            root,
            distance = { base, mutations ->
                distance(mutations) + penaltyForReversedMutations(base, mutations)
            },
            mutationsBetween = { first, second ->
                mutationsBetween(rootInfo, first.fromRootToThis, second.fromRootToThis)
            },
            mutate = { parent, fromParentToThis -> parent.mutate(fromParentToThis) },
            asAncestor = { observed -> SyntheticNode(observed.mutationsSet) },
            findCommonMutations = { first, second -> commonMutations(first, second) },
            postprocessDescendants = { parent, child ->
                SyntheticNode(
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

    private fun List<CloneWithMutationsFromVJGermline>.buildRootInfo(): RootInfo {
        val rootBasedOn = first()

        //TODO may be just get from root?
        val NDNBuilder = ALPHABET.createBuilder()
        repeat(
            rootBasedOn.mutations.CDR3.size() - mostLikableRangeInCDR3(this) { it.mutations.knownMutationsWithinCDR3.V.second }.length() - mostLikableRangeInCDR3(
                this
            ) { it.mutations.knownMutationsWithinCDR3.J.second }.length()
        ) {
            NDNBuilder.append(NucleotideSequence.N)
        }
        return RootInfo(
            VJPair(
                VSequence1,
                JSequence1,
            ),
            VJPair(
                rootBasedOn.cloneWrapper.getPartitioning(Variable),
                rootBasedOn.cloneWrapper.getPartitioning(Joining)
            ),
            VJPair(
                mostLikableRangeInCDR3(this) { it.mutations.knownMutationsWithinCDR3.V.second },
                mostLikableRangeInCDR3(this) { it.mutations.knownMutationsWithinCDR3.J.second }
            ),
            NDNBuilder.createAndDestroy(),
            VJBase
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

    private fun getVMutationsWithinCDR3(clone: CloneWrapper): Pair<Mutations<NucleotideSequence>, Range> {
        val hit = clone.getHit(Variable)
        val CDR3Begin = hit.gene.partitioning.getRelativePosition(hit.alignedFeature, CDR3Begin)
        val alignment = (0 until hit.alignments.size)
            .map { hit.getAlignment(it) }
            .firstOrNull { alignment ->
                alignment.sequence1Range.contains(CDR3Begin)
            }
        return when (alignment) {
            null -> EMPTY_NUCLEOTIDE_MUTATIONS to Range(CDR3Begin, CDR3Begin)
            else -> {
                val range = Range(CDR3Begin, alignment.sequence1Range.upper)
                alignment.absoluteMutations.extractAbsoluteMutations(range, false) to range
            }
        }
    }

    private fun getJMutationsWithinCDR3(clone: CloneWrapper): Pair<Mutations<NucleotideSequence>, Range> {
        val hit = clone.getHit(Joining)
        val CDR3End = hit.gene.partitioning.getRelativePosition(hit.alignedFeature, CDR3End)
        val alignment = (0 until hit.alignments.size)
            .map { hit.getAlignment(it) }
            .firstOrNull { alignment ->
                alignment.sequence1Range.contains(CDR3End)
            }
        return when (alignment) {
            null -> EMPTY_NUCLEOTIDE_MUTATIONS to Range(CDR3End, CDR3End)
            else -> {
                val range = Range(alignment.sequence1Range.lower, CDR3End)
                alignment.absoluteMutations.extractAbsoluteMutations(range, true) to range
            }
        }
    }

    private fun penaltyForReversedMutations(
        fromRootToBase: SyntheticNode,
        mutations: NodeMutationsDescription
    ): Double {
        val reversedMutationsCount = reversedVMutationsCount(fromRootToBase, mutations) +
                reversedJMutationsCount(fromRootToBase, mutations)
        return parameters.penaltyForReversedMutations * reversedMutationsCount
    }

    private fun reversedVMutationsCount(
        fromRootToBase: SyntheticNode,
        mutations: NodeMutationsDescription
    ): Int {
        val reversedMutationsNotInCDR3 = MutationsUtils.fold(
            fromRootToBase.fromRootToThis.mutations.V.mutationsOutsideOfCDR3,
            mutations.mutationsOutsideCDR3.V
        ) { a, b, _ ->
            reversedMutationsCount(a, b)
        }.sum()
        val reversedMutationsInCDR3 = reversedMutationsCount(
            fromRootToBase.fromRootToThis.mutations.V.partInCDR3.mutations,
            mutations.mutationsInCDR3.V
        )
        return reversedMutationsInCDR3 + reversedMutationsNotInCDR3
    }

    private fun reversedJMutationsCount(
        fromRootToBase: SyntheticNode,
        mutations: NodeMutationsDescription
    ): Int {
        val reversedMutationsNotInCDR3 = MutationsUtils.fold(
            fromRootToBase.fromRootToThis.mutations.J.mutationsOutsideOfCDR3,
            mutations.mutationsOutsideCDR3.J
        ) { a, b, _ ->
            reversedMutationsCount(a, b)
        }.sum()
        val reversedMutationsInCDR3 = reversedMutationsCount(
            fromRootToBase.fromRootToThis.mutations.J.partInCDR3.mutations,
            mutations.mutationsInCDR3.J
        )
        return reversedMutationsInCDR3 + reversedMutationsNotInCDR3
    }

    private fun reversedMutationsCount(a: Mutations<NucleotideSequence>, b: CompositeMutations): Int {
        val reversedMutations = b.mutationsFromParentToThis.invert()
        return a.intersectionCount(reversedMutations)
    }

    /**
     * NDN penalties with multiplier plus V and J penalties normalized by overall length of sequence
     *
     * Thoughts:
     * - Use mutations possibilities for V and J
     * - Try other variants of end formula
     * - Reuse NDNDistance
     */
    private fun distance(mutations: NodeMutationsDescription): Double {
        val VPenalties = scoringSet.V.penalties(mutations.mutationsOutsideCDR3.V.values) +
                scoringSet.V.penalties(mutations.mutationsInCDR3.V)
        val VLength = mutations.mutationsOutsideCDR3.V.values.sumOf { it.rangeInParent.length() } +
                mutations.mutationsInCDR3.V.rangeInParent.length()
        val JPenalties = scoringSet.J.penalties(mutations.mutationsOutsideCDR3.J.values) +
                scoringSet.J.penalties(mutations.mutationsInCDR3.J)
        val JLength = mutations.mutationsOutsideCDR3.J.values.sumOf { it.rangeInParent.length() } +
                mutations.mutationsInCDR3.J.rangeInParent.length()
        val NDNPenalties = scoringSet.NDN.penalties(mutations.knownNDN)
        val NDNLength = mutations.knownNDN.rangeInParent.length()
        return (NDNPenalties * parameters.NDNScoreMultiplier + VPenalties + JPenalties) /
                (NDNLength + VLength + JLength).toDouble()
    }

    private fun score(rootInfo: RootInfo, mutations: MutationsSet): Int {
        val VScore = AlignmentUtils.calculateScore(
            VSequence1,
            mutations.mutations.V.combinedMutations(),
            scoringSet.V.scoring
        )
        val JScore = AlignmentUtils.calculateScore(
            JSequence1,
            mutations.mutations.J.combinedMutations(),
            scoringSet.J.scoring
        )
        val NDNScore = AlignmentUtils.calculateScore(
            rootInfo.reconstructedNDN,
            mutations.NDNMutations.mutations,
            scoringSet.NDN.scoring
        )
        return VScore + JScore + NDNScore
    }

    private fun commonMutations(
        first: NodeMutationsDescription,
        second: NodeMutationsDescription
    ): NodeMutationsDescription =
        NodeMutationsDescription(
            first.mutationsOutsideCDR3.V.intersection(second.mutationsOutsideCDR3.V),
            first.mutationsInCDR3.V.intersection(second.mutationsInCDR3.V),
            first.knownNDN.copy(
                mutationsFromParentToThis = MutationsUtils.findNDNCommonAncestor(
                    first.knownNDN.mutationsFromParentToThis,
                    second.knownNDN.mutationsFromParentToThis
                )
            ),
            first.mutationsInCDR3.J.intersection(second.mutationsInCDR3.J),
            first.mutationsOutsideCDR3.J.intersection(second.mutationsOutsideCDR3.J),
        )

    //TODO it is more possible to decrease length of alignment than to increase
    private fun mostLikableRangeInCDR3(
        cluster: List<CloneWithMutationsFromVJGermline>,
        rangeSupplier: (CloneWithMutationsFromVJGermline) -> Range
    ): Range = cluster.asSequence()
        .sortedBy { it.mutations.VJMutationsCount }
        .take(parameters.topToVoteOnNDNSize)
        .map(rangeSupplier)
        .groupingBy { it }.eachCount()
        .entries
        .maxWithOrNull(java.util.Map.Entry.comparingByValue<Range, Int>()
            .thenComparing(Comparator.comparingInt { (key, _) -> key.length() })
        )!!
        .key

    private fun buildStepResult(
        decisions: Map<CloneWrapper.ID, TreeWithMetaBuilder.DecisionInfo>,
        trees: List<TreeWithMetaBuilder>
    ): StepResult = StepResult(
        decisions,
        trees.map { it.snapshot() },
        trees.flatMap { tree: TreeWithMetaBuilder ->
            tree.allNodes()
                .asSequence()
                .filter { it.node.content is Reconstructed }
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
            nodeContent.fromRootToThis.mutations.V.mutationsOutsideOfCDR3.keys.map {
                tree.rootInfo.partitioning.V.getRange(it)
            },
            nodeContent.fromRootToThis.mutations.J.mutationsOutsideOfCDR3.keys.map {
                tree.rootInfo.partitioning.J.getRange(it)
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
            .asSequence()
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

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun makeDecision(chooses: Map<VJBase, TreeWithMetaBuilder.DecisionInfo>): VJBase {
            if (chooses.size == 1) {
                return chooses.keys.first()
            }
            val anyElement = chooses.values.first()
            //all values are from the same class
            require(chooses.values.all { anyElement.javaClass.isInstance(it) })
            return when (anyElement) {
                is ZeroStepDecisionInfo -> ZeroStepDecisionInfo.makeDecision(chooses as Map<VJBase, ZeroStepDecisionInfo>)
                is MetricDecisionInfo -> MetricDecisionInfo.makeDecision(chooses as Map<VJBase, MetricDecisionInfo>)
            }
        }
    }
}

private sealed class OriginalClustersBuilder(
    protected val parameters: SHMTreeBuilderParameters,
    protected val scoringSet: ScoringSet,
    protected val originalClones: List<CloneWithMutationsFromVJGermline>,
    VJBase: VJBase,
    relatedAllelesMutations: Map<VDJCGeneId, List<Mutations<NucleotideSequence>>>
) {

    protected val VMutationWithoutAlleles: Map<CloneWrapper.ID, List<Iterable<Mutations<NucleotideSequence>>>>
    protected val JMutationWithoutAlleles: Map<CloneWrapper.ID, List<Iterable<Mutations<NucleotideSequence>>>>

    init {
        val VAllelesMutations = relatedAllelesMutations[VJBase.geneIds.V] ?: listOf(EMPTY_NUCLEOTIDE_MUTATIONS)
        val JAllelesMutations = relatedAllelesMutations[VJBase.geneIds.J] ?: listOf(EMPTY_NUCLEOTIDE_MUTATIONS)

        VMutationWithoutAlleles = originalClones.associate { clone ->
            clone.cloneWrapper.id to VAllelesMutations.map { without(clone.mutations.mutations.V, it) }
        }
        JMutationWithoutAlleles = originalClones.associate { clone ->
            clone.cloneWrapper.id to JAllelesMutations.map { without(clone.mutations.mutations.J, it) }
        }
    }

    private fun without(
        mutations: SortedMap<GeneFeature, Mutations<NucleotideSequence>>,
        alleleMutations: Mutations<NucleotideSequence>
    ): Iterable<Mutations<NucleotideSequence>> =
        if (alleleMutations.size() == 0) {
            mutations.values
        } else {
            mutations.map { (_, mutations) ->
                mutations.without(alleleMutations)
            }
        }

    /**
     * Calculate common mutations in clone pair but mutations from allele mutations.
     * So if clones have a mutation, but there is allele of this gene with the same mutation, this mutation will be omitted in count.
     */
    protected fun commonMutationsCount(
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

    protected fun NDNDistance(first: MutationsFromVJGermline, second: MutationsFromVJGermline): Double {
        check(first.CDR3.size() == second.CDR3.size())
        val NDNRange = Range(
            min(first.VEndTrimmedPosition, second.VEndTrimmedPosition),
            max(first.JBeginTrimmedPosition, second.JBeginTrimmedPosition)
        )
        return scoringSet.NDNDistance(first.CDR3.getRange(NDNRange), second.CDR3.getRange(NDNRange))
    }

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

    abstract fun buildClusters(): List<List<CloneWithMutationsFromVJGermline>>

    class BronKerbosch(
        parameters: SHMTreeBuilderParameters,
        scoringSet: ScoringSet,
        originalClones: List<CloneWithMutationsFromVJGermline>,
        VJBase: VJBase,
        relatedAllelesMutations: Map<VDJCGeneId, List<Mutations<NucleotideSequence>>>
    ) : OriginalClustersBuilder(parameters, scoringSet, originalClones, VJBase, relatedAllelesMutations) {
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
        override fun buildClusters(): List<List<CloneWithMutationsFromVJGermline>> {
            //TODO process different files separately
            return clusterByCommonMutationsAndNDNDistance(originalClones)
        }

        private fun clusterByCommonMutationsAndNDNDistance(
            clones: List<CloneWithMutationsFromVJGermline>
        ): List<List<CloneWithMutationsFromVJGermline>> {
            val matrix = AdjacencyMatrix(clones.size)
            for (i in clones.indices) {
                for (j in clones.indices) {
                    val commonMutationsCount = commonMutationsCount(clones[i], clones[j])
                    if (commonMutationsCount >= parameters.commonMutationsCountForClustering) {
                        val NDNDistance = NDNDistance(clones[i].mutations, clones[j].mutations)
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
                .map { it.bits.map { i -> clones[i] } }
        }
    }

    class Hierarchical(
        parameters: SHMTreeBuilderParameters,
        scoringSet: ScoringSet,
        originalClones: List<CloneWithMutationsFromVJGermline>,
        VJBase: VJBase,
        relatedAllelesMutations: Map<VDJCGeneId, List<Mutations<NucleotideSequence>>>
    ) : OriginalClustersBuilder(parameters, scoringSet, originalClones, VJBase, relatedAllelesMutations) {
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
        override fun buildClusters(): List<List<CloneWithMutationsFromVJGermline>> {
            val clonesFromDifferentFiles = originalClones.groupBy { it.cloneWrapper.id.datasetId }
                .mapValues { it.value.toMutableList() }
            return if (clonesFromDifferentFiles.size == 1) {
                hierarchicalClustering(originalClones)
            } else {
                //find clusters from each file separately
                val foundClusters = clonesFromDifferentFiles
                    .entries
                    //process small one first. It may reduce big files before processing
                    .sortedBy { it.value.size }
                    .flatMap { (datasetId, clonesFromAFile) ->
                        val foundClusters = hierarchicalClustering(clonesFromAFile)
                            .map { it.toMutableList() }
                        //search for clones from other files that can be attached to found clusters
                        foundClusters.forEach { foundCluster ->
                            clonesFromDifferentFiles
                                .filterKeys { it != datasetId }
                                .values
                                .forEach { clonesFromOtherFile ->
                                    val clonesToAttach = clonesFromOtherFile
                                        .filter { it.mayBeAttachedToAnyCluster() }
                                        .filter { nextClone ->
                                            foundCluster.any { cloneInCluster ->
                                                fromTheSameCluster(nextClone, cloneInCluster)
                                            }
                                        }
                                    //remove found clones from file (will not be processed)
                                    clonesFromOtherFile.removeAll(clonesToAttach)
                                    foundCluster.addAll(clonesToAttach)
                                }
                        }
                        foundClusters
                    }
                //try to merge found clusters
                tryToMergeClusters(foundClusters)
            }
        }

        private fun tryToMergeClusters(
            original: List<List<CloneWithMutationsFromVJGermline>>
        ): List<List<CloneWithMutationsFromVJGermline>> {
            val result = mutableListOf<MutableList<CloneWithMutationsFromVJGermline>>()
            for (nextCluster in original) {
                val clusterToGrow = result.firstOrNull { existedCluster ->
                    existedCluster.any { cloneInCluster ->
                        nextCluster.any { nextClone ->
                            fromTheSameCluster(nextClone, cloneInCluster)
                        }
                    }
                }
                if (clusterToGrow != null) {
                    clusterToGrow += nextCluster
                } else {
                    result += nextCluster.toMutableList()
                }
            }
            return result
        }

        private fun hierarchicalClustering(
            clones: List<CloneWithMutationsFromVJGermline>
        ): List<List<CloneWithMutationsFromVJGermline>> {
            val result = mutableListOf<MutableList<CloneWithMutationsFromVJGermline>>()
            clones
                .filter { it.mayBeAttachedToAnyCluster() }
                .sortedByDescending { it.mutations.VJMutationsCount }
                .forEach { nextClone ->
                    val clusterToGrow = result.firstOrNull { existedCluster ->
                        existedCluster.any { cloneInCluster ->
                            fromTheSameCluster(nextClone, cloneInCluster)
                        }
                    }
                    if (clusterToGrow != null) {
                        clusterToGrow += nextClone
                    } else {
                        result += mutableListOf(nextClone)
                    }
                }
            return result.filter { it.size > 1 }
        }

        private fun CloneWithMutationsFromVJGermline.mayBeAttachedToAnyCluster() =
            mutations.VJMutationsCount >= parameters.commonMutationsCountForClustering

        /**
         * Calculate common mutations in clone pair but mutations from allele mutations.
         * So if clones have a mutation, but there is allele of this gene with the same mutation, this mutation will be omitted in count.
         */
        private fun fromTheSameCluster(
            first: CloneWithMutationsFromVJGermline,
            second: CloneWithMutationsFromVJGermline
        ): Boolean =
            if (commonMutationsCount(first, second) >= parameters.commonMutationsCountForClustering) {
                val NDNDistance = NDNDistance(first.mutations, second.mutations)
                NDNDistance <= parameters.maxNDNDistanceForClustering
            } else {
                false
            }
    }
}
