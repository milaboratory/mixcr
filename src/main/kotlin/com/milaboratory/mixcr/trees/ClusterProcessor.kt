@file:Suppress("LocalVariableName", "FunctionName")

package com.milaboratory.mixcr.trees

import com.milaboratory.core.Range
import com.milaboratory.core.alignment.Aligner
import com.milaboratory.core.alignment.Alignment
import com.milaboratory.core.alignment.AlignmentScoring
import com.milaboratory.core.alignment.AlignmentUtils
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.mutations.MutationsBuilder
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.VDJCHit
import com.milaboratory.mixcr.cli.BuildSHMTreeStep
import com.milaboratory.mixcr.trees.DebugInfo.MutationsSet
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
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import io.repseq.core.ReferencePoint
import io.repseq.core.VDJCGene
import java.math.BigDecimal
import java.util.*
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collectors
import java.util.stream.IntStream
import java.util.stream.Stream
import java.util.stream.StreamSupport
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
    fun applyStep(stepName: BuildSHMTreeStep, currentTrees: List<TreeWithMetaBuilder>): StepResult {
        val step = stepByName(stepName)
        return step.next(
            currentTrees
        ) {
            val clonesInTrees = currentTrees.stream()
                .flatMap { it.clonesAdditionHistory.stream() }
                .collect(Collectors.toSet())
            rebaseFromGermline(originalCluster.cluster.stream().filter { !clonesInTrees.contains(it.clone.id) })
        }
    }

    private fun stepByName(stepName: BuildSHMTreeStep): Step = when (stepName) {
        BuildSHMTreeStep.AttachClonesByDistanceChange -> object : Step {
            override fun next(
                originalTrees: List<TreeWithMetaBuilder>,
                clonesNotInClusters: () -> List<CloneWithMutationsFromVJGermline>
            ): StepResult = attachClonesByDistanceChange(
                originalTrees,
                clonesNotInClusters
            )
        }
        BuildSHMTreeStep.CombineTrees -> object : Step {
            override fun next(
                originalTrees: List<TreeWithMetaBuilder>,
                clonesNotInClusters: () -> List<CloneWithMutationsFromVJGermline>
            ): StepResult = combineTrees(originalTrees)
        }
        BuildSHMTreeStep.AttachClonesByNDN -> object : Step {
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
    fun buildTreeTopParts(): StepResult {
        //use only clones that are at long distance from any germline
        val clonesThatNotMatchAnyGermline = originalCluster.cluster.stream()
            .filter { !hasVJPairThatMatchesWithGermline(it.clone) }
        val clones = rebaseFromGermline(clonesThatNotMatchAnyGermline)
        val result = clusterByCommonMutations(clones)
            .stream()
            .filter { it.cluster.size > 1 }
            .map { cluster: Cluster<CloneWithMutationsFromVJGermline> -> buildATreeWithDecisionsInfo(cluster) }
            .collect(Collectors.toList())
        return buildStepResult(
            result.stream()
                .flatMap { it.first.stream() }
                .collect(Collectors.toMap({ it!!.first }) { it!!.second }) as Map<Int, TreeWithMetaBuilder.DecisionInfo>,
            result.stream()
                .map { obj -> obj.second }
                .collect(Collectors.toList()) as List<TreeWithMetaBuilder>
        )
    }

    private fun hasVJPairThatMatchesWithGermline(clone: Clone?): Boolean {
        return Arrays.stream(clone!!.getHits(GeneType.Variable))
            .flatMap { VHit: VDJCHit ->
                Arrays.stream(
                    clone.getHits(GeneType.Joining)
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
        val clonesInTrees = resultTrees.stream()
            .flatMap { it.clonesAdditionHistory.stream() }
            .collect(Collectors.toSet())
        val clonesByIds = originalCluster.cluster.stream()
            .filter { clonesInTrees.contains(it.clone.id) }
            .collect(
                Collectors.toMap({ it.clone.id }, Function.identity())
            )
        return resultTrees.stream()
            .map { treeSnapshot ->
                val treeWithMetaBuilder = TreeWithMetaBuilder(
                    createTreeBuilder(treeSnapshot.rootInfo),
                    treeSnapshot.rootInfo,
                    ClonesRebase(VSequence1, VScoring, NDNScoring, JSequence1, JScoring),
                    LinkedList(),
                    treeSnapshot.treeId
                )
                treeSnapshot.clonesAdditionHistory.forEach { cloneId: Int? ->
                    val rebasedClone = treeWithMetaBuilder.rebaseClone(rebaseFromGermline(clonesByIds[cloneId]))
                    treeWithMetaBuilder.addClone(rebasedClone)
                }
                treeWithMetaBuilder
            }
            .collect(Collectors.toList())
    }

    private fun rebaseFromGermline(clones: Stream<CloneWrapper>): List<CloneWithMutationsFromVJGermline> {
        return clones
            .filter { cloneWrapper: CloneWrapper? ->
                (clusterInfo.commonVAlignmentRanges.containsCloneWrapper(cloneWrapper)
                    && clusterInfo.commonJAlignmentRanges.containsCloneWrapper(cloneWrapper))
            }
            .map { cloneWrapper: CloneWrapper? -> this.rebaseFromGermline(cloneWrapper) }
            .collect(Collectors.toList())
    }

    private fun rebaseFromGermline(cloneWrapper: CloneWrapper?): CloneWithMutationsFromVJGermline {
        val CDR3 = cloneWrapper!!.getFeature(GeneFeature.CDR3)!!.sequence
        val VMutationsInCDR3WithoutNDN = MutationsWithRange(
            VSequence1,
            getMutationsForRange(cloneWrapper, clusterInfo.VRangeInCDR3, GeneType.Variable),
            RangeInfo(clusterInfo.VRangeInCDR3, false)
        )
        val JMutationsInCDR3WithoutNDN = MutationsWithRange(
            JSequence1,
            getMutationsForRange(cloneWrapper, clusterInfo.JRangeInCDR3, GeneType.Joining),
            RangeInfo(clusterInfo.JRangeInCDR3, false)
        )
        return CloneWithMutationsFromVJGermline(
            MutationsFromVJGermline(
                getMutationsWithoutCDR3(
                    cloneWrapper, GeneType.Variable,
                    Range(clusterInfo.VRangeInCDR3.lower, VSequence1.size()),
                    clusterInfo.commonVAlignmentRanges
                ),
                VMutationsInCDR3WithoutNDN,
                getVMutationsWithinNDN(cloneWrapper, clusterInfo.VRangeInCDR3.upper),
                CDR3.getRange(
                    clusterInfo.VRangeInCDR3.length() + VMutationsInCDR3WithoutNDN.lengthDelta(),
                    CDR3.size() - (clusterInfo.JRangeInCDR3.length() + JMutationsInCDR3WithoutNDN.lengthDelta())
                ),
                getJMutationsWithinNDN(cloneWrapper, clusterInfo.JRangeInCDR3.lower),
                JMutationsInCDR3WithoutNDN,
                getMutationsWithoutCDR3(
                    cloneWrapper, GeneType.Joining,
                    Range(0, clusterInfo.JRangeInCDR3.lower),
                    clusterInfo.commonJAlignmentRanges
                )
            ),
            cloneWrapper
        )
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
                        val noeToAttach: SyntheticNode =
                            SyntheticNode.createFromMutations(rebasedClone.mutationsFromRoot)
                        val NDNOfTreeToGrow = oldestAncestorOfTreeToGrow.fromRootToThis.knownNDN.buildSequence()
                        val NDNOfNodeToAttach = noeToAttach.fromRootToThis.knownNDN.buildSequence()
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

    private fun clusterByCommonMutations(clones: List<CloneWithMutationsFromVJGermline>): List<Cluster<CloneWithMutationsFromVJGermline>> {
        val matrix = AdjacencyMatrix(clones.size)
        for (i in clones.indices) {
            for (j in clones.indices) {
                if (commonMutationsCount(clones[i], clones[j]) >= parameters.commonMutationsCountForClustering) {
                    if (NDNDistance(clones[i], clones[j]) <= parameters.maxNDNDistanceForClustering) {
                        matrix.setConnected(i, j)
                    }
                }
            }
        }
        val notOverlappedCliques: MutableList<BitArrayInt> = ArrayList()
        val cliques = matrix.calculateMaximalCliques().iterator()
        StreamSupport.stream(Spliterators.spliteratorUnknownSize(cliques, Spliterator.SORTED), false)
            .filter { it.bitCount() > 1 }
            .sorted(Comparator.comparing { obj: BitArrayInt -> obj.bitCount() }.reversed())
            .forEach { clique: BitArrayInt ->
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
        first: CloneWithMutationsFromVJGermline,
        second: CloneWithMutationsFromVJGermline
    ): Int {
        val VAllelesMutations = mutationsFromThisAlleleToOthers(GeneType.Variable, first, second)
        val JAllelesMutations = mutationsFromThisAlleleToOthers(GeneType.Joining, first, second)
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
        first: List<MutationsWithRange>,
        second: List<MutationsWithRange>,
        allelesMutations: List<Mutations<NucleotideSequence>>
    ): Int {
        return Stream.concat(
            allelesMutations.stream(),
            Stream.of(Mutations.EMPTY_NUCLEOTIDE_MUTATIONS)
        )
            .distinct()
            .mapToInt { alleleMutations: Mutations<NucleotideSequence> ->
                commonMutationsCount(
                    without(first, alleleMutations),
                    without(second, alleleMutations)
                )
            }
            .min().orElseThrow { IllegalStateException() }
    }

    private fun without(
        cloneMutations: List<MutationsWithRange>,
        alleleMutations: Mutations<NucleotideSequence>
    ): List<MutationsWithRange> {
        val alleleMutationsSet = Arrays.stream(alleleMutations.rawMutations).boxed().collect(Collectors.toSet())
        return cloneMutations.stream()
            .map { mutations ->
                val builder = MutationsBuilder(NucleotideSequence.ALPHABET)
                Arrays.stream(mutations.mutations.rawMutations)
                    .filter { mutation: Int -> !alleleMutationsSet.contains(mutation) }
                    .forEach { mutation: Int -> builder.append(mutation) }
                MutationsWithRange(
                    mutations.sequence1,
                    builder.createAndDestroy(),
                    mutations.rangeInfo
                )
            }
            .collect(Collectors.toList())
    }

    private fun mutationsFromThisAlleleToOthers(
        geneType: GeneType,
        vararg clones: CloneWithMutationsFromVJGermline
    ): List<Mutations<NucleotideSequence>> {
        val baseGenes = Arrays.stream(clones)
            .map { it.cloneWrapper.getHit(geneType).gene }
            .distinct()
            .collect(Collectors.toList())
        require(baseGenes.size == 1)
        val baseGene = baseGenes[0]
        val mutationsOfCurrentAllele = alleleMutations(baseGene)
        return Arrays.stream(clones)
            .flatMap { clone: CloneWithMutationsFromVJGermline ->
                Arrays.stream(
                    clone.cloneWrapper.clone.getHits(
                        geneType
                    )
                )
            }
            .map { obj: VDJCHit -> obj.gene }
            .distinct()
            .filter { gene: VDJCGene -> gene.geneName == baseGene.geneName }
            .filter { gene: VDJCGene -> gene.name != baseGene.name }
            .map { gene: VDJCGene -> alleleMutations(gene) }
            .map { alleleMutations: Mutations<NucleotideSequence>? ->
                mutationsOfCurrentAllele.invert().combineWith(alleleMutations)
            }
            .collect(Collectors.toList())
    }

    private fun alleleMutations(gene: VDJCGene): Mutations<NucleotideSequence> {
        val result = gene.data.baseSequence.mutations
        return Objects.requireNonNullElse(result, Mutations.EMPTY_NUCLEOTIDE_MUTATIONS)
    }

    private fun commonMutationsCount(first: List<MutationsWithRange>, second: List<MutationsWithRange>): Int =
        MutationsUtils.fold(
            first, second
        ) { a, b ->
            MutationsUtils.intersection(
                a,
                b,
                a.rangeInfo.intersection(b.rangeInfo)!!
            ).mutationsCount()
        }.stream().mapToInt { it!! }.sum()

    private fun buildATreeWithDecisionsInfo(cluster: Cluster<CloneWithMutationsFromVJGermline>): Pair<List<Pair<Int, TreeWithMetaBuilder.DecisionInfo>>, TreeWithMetaBuilder> {
        val treeWithMetaBuilder = buildATree(cluster)
        val decisionsInfo = cluster.cluster.stream()
            .map {
                val effectiveParent = treeWithMetaBuilder.getEffectiveParent(it.cloneWrapper.clone)
                val VHit = it.cloneWrapper.getHit(GeneType.Variable)
                val JHit = it.cloneWrapper.getHit(GeneType.Joining)
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
        val rebasedCluster = cluster.cluster.stream()
            .map { clone: CloneWithMutationsFromVJGermline ->
                clonesRebase.rebaseClone(
                    rootInfo,
                    clone.mutations,
                    clone.cloneWrapper
                )
            }
            .sorted(Comparator.comparing { cloneDescriptor: CloneWithMutationsFromReconstructedRoot ->
                distance(
                    cloneDescriptor.mutationsFromRoot
                )
            }
                .reversed())
            .collect(Collectors.toList())
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
        val root: SyntheticNode = SyntheticNode.createRoot(
            clusterInfo.commonVAlignmentRanges,
            VSequence1,
            rootInfo,
            clusterInfo.commonJAlignmentRanges,
            JSequence1
        )
        return TreeBuilderByAncestors(
            root,
            { base, mutations ->
                distance(mutations).add(
                    penaltyForReversedMutations(base, mutations)
                )
            },
            { first, second ->
                MutationsUtils.mutationsBetween(
                    first.fromRootToThis,
                    second.fromRootToThis
                )
            },
            { parent, fromParentToThis -> parent.mutate(fromParentToThis) },
            { observed: CloneWithMutationsFromReconstructedRoot ->
                SyntheticNode.createFromMutations(
                    observed.mutationsFromRoot
                )
            },
            { first, second ->
                commonMutations(first, second)
            },
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
        val NDNBuilder = NucleotideSequence.ALPHABET.createBuilder()
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
        return IntStream.range(0, hit.alignments.size)
            .boxed()
            .flatMap { index: Int ->
                val alignment = hit.getAlignment(index)
                val mutations = alignment.absoluteMutations
                val rangesWithout = alignment.sequence1Range.without(CDR3Range)
                rangesWithout.stream()
                    .map { range: Range? -> commonAlignmentRanges.cutRange(range) }
                    .filter { range: Range -> !range.isEmpty }
                    .map { range: Range ->
                        MutationsWithRange(
                            alignment.sequence1,
                            mutations,
                            RangeInfo(range, alignment.sequence1Range.lower == range.lower)
                        )
                    }
            }
            .collect(Collectors.toList())
    }

    private fun getVMutationsWithinNDN(clone: CloneWrapper, from: Int): Pair<Mutations<NucleotideSequence>, Range> {
        val hit = clone.getHit(GeneType.Variable)
        val CDR3Begin = clone.getRelativePosition(GeneType.Variable, ReferencePoint.CDR3Begin)
        return IntStream.range(0, hit.alignments.size)
            .boxed()
            .map { target: Int -> hit.getAlignment(target) }
            .filter { alignment -> alignment.sequence1Range.contains(CDR3Begin) }
            .map { alignment: Alignment<NucleotideSequence> ->
                if (alignment.sequence1Range.contains(from)) {
                    return@map Optional.of(
                        Pair(
                            alignment.absoluteMutations,
                            Range(from, alignment.sequence1Range.upper)
                        )
                    )
                } else {
                    return@map Optional.empty<Pair<Mutations<NucleotideSequence>, Range>>()
                }
            }
            .flatMap { obj -> obj.stream() }
            .findFirst()
            .orElseGet { Pair(Mutations.EMPTY_NUCLEOTIDE_MUTATIONS, Range(from, from)) }
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
        val hit = clone.getHit(GeneType.Joining)
        val CDR3End = clone.getRelativePosition(GeneType.Joining, ReferencePoint.CDR3End)
        return IntStream.range(0, hit.alignments.size)
            .boxed()
            .map { target: Int -> hit.getAlignment(target) }
            .filter { alignment -> alignment.sequence1Range.contains(CDR3End) }
            .map { alignment ->
                if (alignment.sequence1Range.contains(to)) {
                    return@map Optional.of(
                        Pair(
                            alignment.absoluteMutations,
                            Range(alignment.sequence1Range.lower, to)
                        )
                    )
                } else {
                    return@map Optional.empty<Pair<Mutations<NucleotideSequence>, Range>>()
                }
            }
            .flatMap { obj -> obj.stream() }
            .findFirst()
            .orElseGet { Pair(Mutations.EMPTY_NUCLEOTIDE_MUTATIONS, Range(to, to)) }
    }

    private fun penaltyForReversedMutations(
        fromRootToBase: SyntheticNode,
        mutations: MutationsDescription
    ): BigDecimal {
        val reversedMutationsCount =
            reversedVMutationsCount(fromRootToBase, mutations) + reversedJMutationsCount(fromRootToBase, mutations)
        return BigDecimal.valueOf(parameters.penaltyForReversedMutations)
            .multiply(BigDecimal.valueOf(reversedMutationsCount))
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
        val JPenalties = maxScore(mutations.JMutationsWithoutCDR3, JScoring) - score(
            mutations.JMutationsWithoutCDR3,
            JScoring
        ) +
            maxScore(
                mutations.JMutationsInCDR3WithoutNDN,
                JScoring
            ) - score(mutations.JMutationsInCDR3WithoutNDN, JScoring)
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
            MutationsUtils.intersection(first.VMutationsWithoutCDR3, second.VMutationsWithoutCDR3),
            MutationsUtils.intersection(first.VMutationsInCDR3WithoutNDN, second.VMutationsInCDR3WithoutNDN),
            MutationsWithRange(
                first.knownNDN.sequence1,
                MutationsUtils.findNDNCommonAncestor(
                    first.knownNDN.mutations,
                    second.knownNDN.mutations
                ),
                first.knownNDN.rangeInfo
            ),
            MutationsUtils.intersection(first.JMutationsInCDR3WithoutNDN, second.JMutationsInCDR3WithoutNDN),
            MutationsUtils.intersection(first.JMutationsWithoutCDR3, second.JMutationsWithoutCDR3)
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
                anyClone.getHit(GeneType.Variable).getAlignment(0).sequence1,
                anyClone.getHit(GeneType.Joining).getAlignment(0).sequence1,
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
                minPortionOfClonesForCommonAlignmentRanges, GeneType.Variable
            ) { it.getHit(GeneType.Variable) },
            ClonesAlignmentRanges.commonAlignmentRanges(
                originalCluster.cluster,
                minPortionOfClonesForCommonAlignmentRanges, GeneType.Joining
            ) { it.getHit(GeneType.Joining) },
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
                                value.getGeneName(GeneType.Variable),
                                value.getGeneName(GeneType.Joining)
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
                    chooses[it]!!.getScore(GeneType.Variable) + chooses[it]!!.getScore(GeneType.Joining)
                })
                .orElseThrow { IllegalStateException() }!!
        }

        private fun reversedVMutationsCount(fromRootToBase: SyntheticNode, mutations: MutationsDescription): Long {
            val reversedMutationsNotInCDR3 = MutationsUtils.fold(
                fromRootToBase.fromRootToThis.VMutationsWithoutCDR3,
                mutations.VMutationsWithoutCDR3
            ) { a, b -> reversedMutationsCount(a, b) }
                .stream().mapToLong { it!! }.sum()
            val reversedMutationsInCDR3 = reversedMutationsCount(
                fromRootToBase.fromRootToThis.VMutationsInCDR3WithoutNDN,
                mutations.VMutationsInCDR3WithoutNDN
            )
            return reversedMutationsInCDR3 + reversedMutationsNotInCDR3
        }

        private fun reversedJMutationsCount(fromRootToBase: SyntheticNode, mutations: MutationsDescription): Long {
            val reversedMutationsNotInCDR3 = MutationsUtils.fold(
                fromRootToBase.fromRootToThis.JMutationsWithoutCDR3,
                mutations.JMutationsWithoutCDR3
            ) { a, b -> reversedMutationsCount(a, b) }
                .stream().mapToLong { it!! }.sum()
            val reversedMutationsInCDR3 = reversedMutationsCount(
                fromRootToBase.fromRootToThis.JMutationsInCDR3WithoutNDN,
                mutations.JMutationsInCDR3WithoutNDN
            )
            return reversedMutationsInCDR3 + reversedMutationsNotInCDR3
        }

        private fun reversedMutationsCount(a: MutationsWithRange, b: MutationsWithRange): Long {
            val reversedMutations = b.mutationsForRange().move(a.rangeInfo.range.lower).invert()
            val asSet = Arrays.stream(reversedMutations.rawMutations).boxed().collect(Collectors.toSet())
            return Arrays.stream(a.mutations.rawMutations).filter { o: Int -> asSet.contains(o) }.count()
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
            val alignments = clone.getHit(GeneType.Variable).alignments
            return Range(
                clone.getRelativePosition(GeneType.Variable, ReferencePoint.CDR3Begin),
                alignments[alignments.size - 1].sequence1Range.upper
            )
        }

        private fun JRangeInCDR3(clone: CloneWrapper): Range {
            return Range(
                clone.getHit(GeneType.Joining).getAlignment(0).sequence1Range.lower,
                clone.getRelativePosition(GeneType.Joining, ReferencePoint.CDR3End)
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
