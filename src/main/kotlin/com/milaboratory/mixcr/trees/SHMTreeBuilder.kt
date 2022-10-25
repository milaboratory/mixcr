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
@file:Suppress("LocalVariableName")

package com.milaboratory.mixcr.trees

import com.milaboratory.core.Range
import com.milaboratory.core.alignment.AlignmentUtils
import com.milaboratory.core.mutations.Mutation
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.util.VJPair
import com.milaboratory.mixcr.util.asMutations
import com.milaboratory.mixcr.util.asSequence
import com.milaboratory.mixcr.util.intersection
import com.milaboratory.mixcr.util.intersectionCount
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class SHMTreeBuilder(
    private val parameters: SHMTreeBuilderParameters.TopologyBuilderParameters,
    private val scoringSet: ScoringSet
) {
    private val treeIdGenerators = ConcurrentHashMap<VJBase, IdGenerator>()

    fun rebuildFromMRCA(tree: TreeWithMetaBuilder): TreeWithMetaBuilder {
        val reconstructedNDN = tree.mostRecentCommonAncestorNDN()
        return buildATree(
            tree.allNodes()
                .mapNotNull { it.node.content as? TreeBuilderByAncestors.Observed }
                .map { CloneWithMutationsFromVJGermline(it.content.mutationsFromVJGermline, it.content.clone) },
            tree.rootInfo.copy(
                reconstructedNDN = reconstructedNDN
            )
        )
    }

    fun buildATreeFromRoot(cluster: List<CloneWithMutationsFromVJGermline>): TreeWithMetaBuilder =
        buildATree(cluster, cluster.buildRootInfo())

    private fun buildATree(
        cluster: List<CloneWithMutationsFromVJGermline>,
        rootInfo: RootInfo
    ): TreeWithMetaBuilder {
        val clonesRebase = ClonesRebase(rootInfo.sequence1, scoringSet)
        val rebasedCluster = cluster.asSequence()
            .map { clone ->
                clonesRebase.rebaseClone(
                    rootInfo,
                    clone.mutations,
                    clone.cloneWrapper
                )
            }
            .sortedWith(Comparator
                .comparingInt<CloneWithMutationsFromReconstructedRoot> { score(rootInfo, it.mutationsSet) }
                .thenBy(CloneWrapper.comparatorMaxFractionFirst) { it.clone }
            )
            .toList()
        val alignFeatures = cluster.first().mutations.mutations.map { it.keys.sorted() }
        val treeBuilder = createTreeBuilder(rootInfo, alignFeatures)
        val treeWithMetaBuilder = TreeWithMetaBuilder(
            treeBuilder,
            rootInfo,
            clonesRebase,
            LinkedList(),
            treeIdGenerators.computeIfAbsent(rootInfo.VJBase) { IdGenerator() }.next(rootInfo.VJBase)
        )
        rebasedCluster.forEach {
            treeWithMetaBuilder.addClone(it)
        }
        return treeWithMetaBuilder
    }

    fun distanceBetweenTrees(
        from: TreeWithMetaBuilder,
        destination: TreeWithMetaBuilder
    ): Double {
        val clonesRebase = ClonesRebase(from.rootInfo.sequence1, scoringSet)
        //TODO use not only MRCA, but bottom of the tree
        val oldestAncestorOfFrom = from.mostRecentCommonAncestor()
        val oldestAncestorOfDestination = destination.mostRecentCommonAncestor()
        val destinationRebasedOnFrom = clonesRebase.rebaseMutations(
            oldestAncestorOfDestination.fromRootToThis,
            originalRoot = destination.rootInfo,
            rebaseTo = from.rootInfo
        )
        val mutations = mutationsBetween(
            from.rootInfo,
            oldestAncestorOfFrom.fromRootToThis,
            destinationRebasedOnFrom
        )
        return distance(mutations)
    }

    private fun List<CloneWithMutationsFromVJGermline>.buildRootInfo(): RootInfo {
        val rootBasedOn = minWith(CloneWithMutationsFromVJGermline.comparatorByMutationsCount)

        val sequence1 = VJPair(
            rootBasedOn.cloneWrapper.getHit(Variable).getAlignment(0).sequence1,
            rootBasedOn.cloneWrapper.getHit(Joining).getAlignment(0).sequence1
        )
        val VJBase = rootBasedOn.cloneWrapper.VJBase


        val rangeInCDR3 = voteForRangesInCDR3()
        val NDNBuilder = NucleotideSequence.ALPHABET.createBuilder()
        repeat(rootBasedOn.mutations.CDR3.size() - rangeInCDR3.V.length() - rangeInCDR3.J.length()) {
            NDNBuilder.append(NucleotideSequence.N)
        }
        return RootInfo(
            sequence1,
            VJPair(
                rootBasedOn.cloneWrapper.getPartitioning(Variable),
                rootBasedOn.cloneWrapper.getPartitioning(Joining)
            ),
            rangeInCDR3,
            NDNBuilder.createAndDestroy(),
            VJBase
        )
    }


    private val voteForCDR3RangesComparator = Comparator
        .comparingInt<VJPair<Map.Entry<Range, Int>>> { (V, J) -> V.value + J.value }
        .thenComparing { (V, J) -> V.key.length() + J.key.length() }

    /**
     * Choose V and J regions in CDR3
     * Pair must not intersect (sum length is less or equals CDR3 size)
     * For possible combinations use top clones (with minimum mutations)
     *
     * If there is a pair in top that less than CDR3 then choose it. Otherwise, cut best pair to fit CDR3.
     */
    private fun List<CloneWithMutationsFromVJGermline>.voteForRangesInCDR3(): VJPair<Range> {
        val CDR3Size = first().mutations.CDR3.size()
        val possibleCombinationsFromTop = possibleRangesInCDR3(
            this,
            { it.mutations.knownMutationsWithinCDR3.V.second },
            parameters.topToVoteOnNDNSize
        )
            .flatMap { V ->
                possibleRangesInCDR3(
                    this,
                    { it.mutations.knownMutationsWithinCDR3.J.second },
                    parameters.topToVoteOnNDNSize
                ).map { J -> VJPair(V, J) }
            }
        val combinationsThatFitCDR3 = possibleCombinationsFromTop
            .filter { (V, J) -> V.key.length() + J.key.length() <= CDR3Size }

        return if (combinationsThatFitCDR3.isNotEmpty()) {
            combinationsThatFitCDR3
                .maxWithOrNull(voteForCDR3RangesComparator)!!
                .map { it.key }
        } else {
            val (V, J) = possibleCombinationsFromTop
                .maxWithOrNull(voteForCDR3RangesComparator)!!
                .map { it.key }
            val delta = V.length() + J.length() - CDR3Size
            val correctionForV = delta / 2
            val correctionForJ = delta - correctionForV
            VJPair(
                V.setUpper(V.upper - correctionForV),
                J.setLower(J.lower + correctionForJ)
            )
        }
    }

    //TODO it is more possible to decrease length of alignment than to increase
    private fun possibleRangesInCDR3(
        cluster: List<CloneWithMutationsFromVJGermline>,
        rangeSupplier: (CloneWithMutationsFromVJGermline) -> Range,
        limit: Int
    ): Map<Range, Int> = cluster.asSequence()
        .sortedBy { it.mutations.VJMutationsCount }
        .take(limit)
        .map(rangeSupplier)
        .groupingBy { it }.eachCount()

    private fun createTreeBuilder(
        rootInfo: RootInfo,
        alignFeatures: VJPair<List<GeneFeature>>
    ): TreeBuilderByAncestors<CloneWithMutationsFromReconstructedRoot, SyntheticNode, NodeMutationsDescription> {
        val root = SyntheticNode(
            MutationsSet(
                VGeneMutations(
                    alignFeatures.V.associateWith { Mutations.EMPTY_NUCLEOTIDE_MUTATIONS },
                    PartInCDR3(rootInfo.rangeInCDR3.V, Mutations.EMPTY_NUCLEOTIDE_MUTATIONS)
                ),
                NDNMutations(Mutations.EMPTY_NUCLEOTIDE_MUTATIONS),
                JGeneMutations(
                    PartInCDR3(rootInfo.rangeInCDR3.J, Mutations.EMPTY_NUCLEOTIDE_MUTATIONS),
                    alignFeatures.J.associateWith { Mutations.EMPTY_NUCLEOTIDE_MUTATIONS }
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
            mutate = SyntheticNode::mutate,
            asAncestor = { observed -> SyntheticNode(observed.mutationsSet) },
            findCommonMutations = ::commonMutations,
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

    private fun mutationsBetween(rootInfo: RootInfo, first: MutationsSet, second: MutationsSet) =
        NodeMutationsDescription(
            VJPair(
                mutationsBetween(rootInfo, first, second, Variable),
                mutationsBetween(rootInfo, first, second, Joining)
            ),
            VJPair(
                MutationsUtils.difference(
                    rootInfo.sequence1.V,
                    first.mutations.V.partInCDR3.mutations,
                    second.mutations.V.partInCDR3.mutations,
                    rootInfo.rangeInCDR3.V
                ),
                MutationsUtils.difference(
                    rootInfo.sequence1.J,
                    first.mutations.J.partInCDR3.mutations,
                    second.mutations.J.partInCDR3.mutations,
                    rootInfo.rangeInCDR3.J
                )
            ),
            MutationsUtils.difference(
                rootInfo.reconstructedNDN,
                first.NDNMutations.mutations,
                second.NDNMutations.mutations,
                Range(0, rootInfo.reconstructedNDN.size())
            )
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
        return (NDNPenalties * parameters.multiplierForNDNScore + VPenalties + JPenalties) /
                (NDNLength + VLength + JLength).toDouble()
    }

    private fun score(rootInfo: RootInfo, mutations: MutationsSet): Int {
        val VScore = AlignmentUtils.calculateScore(
            rootInfo.sequence1.V,
            mutations.mutations.V.combinedMutations(),
            scoringSet.V.scoring
        )
        val JScore = AlignmentUtils.calculateScore(
            rootInfo.sequence1.J,
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
    ): NodeMutationsDescription {
        var mutationsInCDR3 = VJPair(
            first.mutationsInCDR3.V.intersection(second.mutationsInCDR3.V),
            first.mutationsInCDR3.J.intersection(second.mutationsInCDR3.J),
        )
        // Found common mutations can change CDR3 length.
        // Example:
        // [I2A,D4A] and [I3A,D4A]. Common mutations will be [D4A]
        if (mutationsInCDR3.V.mutationsFromParentToThis.lengthDelta + mutationsInCDR3.J.mutationsFromParentToThis.lengthDelta != 0) {
            mutationsInCDR3 = mutationsInCDR3.map { compositeMutations ->
                compositeMutations.copy(mutationsFromParentToThis = compositeMutations.mutationsFromParentToThis
                    .asSequence()
                    .filter { Mutation.isSubstitution(it) }
                    .asMutations(NucleotideSequence.ALPHABET)
                )
            }
        }
        return NodeMutationsDescription(
            VJPair(
                first.mutationsOutsideCDR3.V.intersection(second.mutationsOutsideCDR3.V),
                first.mutationsOutsideCDR3.J.intersection(second.mutationsOutsideCDR3.J)
            ),
            mutationsInCDR3,
            first.knownNDN.copy(
                mutationsFromParentToThis = MutationsUtils.findNDNCommonAncestor(
                    first.knownNDN.mutationsFromParentToThis,
                    second.knownNDN.mutationsFromParentToThis
                )
            ),
        )
    }

    private fun Map<GeneFeature, CompositeMutations>.intersection(
        with: Map<GeneFeature, CompositeMutations>
    ): Map<GeneFeature, CompositeMutations> =
        MutationsUtils.zip(this, with) { a, b, _ -> a.intersection(b) }

    private fun CompositeMutations.intersection(with: CompositeMutations): CompositeMutations =
        copy(mutationsFromParentToThis = mutationsFromParentToThis.intersection(with.mutationsFromParentToThis))
}
