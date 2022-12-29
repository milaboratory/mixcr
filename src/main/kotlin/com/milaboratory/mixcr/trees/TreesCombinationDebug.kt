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
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.cli.DebugDirOption
import com.milaboratory.mixcr.util.intersection
import com.milaboratory.mixcr.util.plus
import com.milaboratory.util.XSV
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import io.repseq.core.ReferencePoint.CDR3Begin
import io.repseq.core.ReferencePoint.DBeginTrimmed
import io.repseq.core.ReferencePoint.DEndTrimmed
import io.repseq.core.VDJCGene
import java.io.FileOutputStream
import java.io.PrintStream
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.math.min

class TreesCombinationDebug {
    private val topToVoteOnNDNSize: Int = 5

    fun debugCombiningTrees(trees: List<TreeWithMetaBuilder>) {
        DebugDirOption { debugDir ->
            val pathToWrite = debugDir.resolve("combineTress.tsv")
            val columns = buildMap<String, (Pair<TreeWithMetaBuilder, TreeWithMetaBuilder>) -> Any?> {
                fun property(name: String, function: (TreeWithMetaBuilder) -> Any?) {
                    put("first_$name") { (first, _) ->
                        function(first)
                    }
                    put("second_$name") { (_, second) ->
                        function(second)
                    }
                }
                put("VGeneName") { (first, _) ->
                    first.treeId.VJBase.geneIds.V.name
                }
                put("JGeneName") { (first, _) ->
                    first.treeId.VJBase.geneIds.J.name
                }
                put("CDR3Length") { (first, _) ->
                    first.treeId.VJBase.CDR3length
                }
                property("cloneNodesCount") { tree ->
                    tree.allClones().size
                }
                property("allFoundDGenes") { tree ->
                    tree.allClones()
                        .flatMap { it.getHits(GeneType.Diversity)?.toList() ?: emptyList() }
                        .map { it.gene.name }
                        .groupingBy { it }.eachCount()
                        .entries
                        .sortedByDescending { it.value }
                        .joinToString(";") { it.key + ":" + it.value }
                }
                property("consensusOfAllFoundDGenes") { tree ->
                    tree.allClones()
                        .flatMap { it.getHits(GeneType.Diversity)?.toList() ?: emptyList() }
                        .map { it.gene }
                        .groupingBy { it }.eachCount()
                        .maxByOrNull { it.value }
                        ?.key?.name
                }
                property("DGenes") { tree ->
                    tree.allClones()
                        .flatMap { clone ->
                            (clone.getHits(GeneType.Diversity)?.toList() ?: emptyList())
                                .filter { dGene ->
                                    clone.getFeature(
                                        GeneFeature(DBeginTrimmed, DEndTrimmed),
                                        tree.treeId.VJBase,
                                        dGene.gene.id
                                    ) != null
                                }
                        }
                        .map { it.gene }
                        .groupingBy { it }.eachCount()
                        .entries
                        .sortedByDescending { it.value }
                        .joinToString(";") { it.key.name + ":" + it.value }
                }
                property("DGene") { tree ->
                    (tree.extendedRootInfo as? RootInfoWithD)?.DGene?.name
                }
                property("DBeginTrimmed") { tree ->
                    (tree.extendedRootInfo as? RootInfoWithD)?.DBeginTrimmed
                }
                property("DEndTrimmed") { tree ->
                    (tree.extendedRootInfo as? RootInfoWithD)?.DEndTrimmed
                }
                property("DPartLength") { tree ->
                    val DBeginTrimmed = (tree.extendedRootInfo as? RootInfoWithD)?.DBeginTrimmed ?: return@property null
                    val DEndTrimmed = (tree.extendedRootInfo as? RootInfoWithD)?.DEndTrimmed ?: return@property null
                    DEndTrimmed - DBeginTrimmed
                }
                property("VPartLength") {
                    it.rootInfo.rangeInCDR3.V.length()
                }
                property("JPartLength") {
                    it.rootInfo.rangeInCDR3.J.length()
                }
                property("NSegmentsLength") { tree ->
                    val rootInfo = tree.extendedRootInfo
                    val dBeginTrimmed = (rootInfo as? RootInfoWithD)?.DBeginTrimmed
                    val dEndTrimmed = (rootInfo as? RootInfoWithD)?.DEndTrimmed

                    if (dBeginTrimmed == null || dEndTrimmed == null) {
                        rootInfo.base.VJBase.CDR3length - rootInfo.base.rangeInCDR3.J.length() - rootInfo.base.rangeInCDR3.V.length()
                    } else {
                        dBeginTrimmed - rootInfo.base.rangeInCDR3.V.length() +
                                rootInfo.base.VJBase.CDR3length - rootInfo.base.rangeInCDR3.J.length() - dEndTrimmed
                    }
                }
                put("consensus_DGene") { (first, second) ->
                    (consensus(first, second) as? RootInfoWithD)?.DGene?.name
                }
                put("consensus_DBeginTrimmed") { (first, second) ->
                    (consensus(first, second) as? RootInfoWithD)?.DBeginTrimmed
                }
                put("consensus_DEndTrimmed") { (first, second) ->
                    (consensus(first, second) as? RootInfoWithD)?.DEndTrimmed
                }
                property("CDR3") { tree ->
                    tree.mostRecentCommonAncestorCDR3()
                }
                fun CDR3Part(name: String, function: (ExtendedRootInfo) -> List<Range>) {
                    put("first_$name") { (first, _) ->
                        function(first.extendedRootInfo)
                            .map { first.mostRecentCommonAncestorCDR3().getRange(it) }
                            .fold(NucleotideSequence.EMPTY) { a, b -> a + b }
                    }
                    put("second_$name") { (_, second) ->
                        function(second.extendedRootInfo)
                            .map { second.mostRecentCommonAncestorCDR3().getRange(it) }
                            .fold(NucleotideSequence.EMPTY) { a, b -> a + b }
                    }
                    put("consensus_${name}_length") { (first, second) ->
                        val rootInfo = consensus(first, second)
                        function(rootInfo)
                            .map { first.mostRecentCommonAncestorCDR3().getRange(it) }
                            .sumOf { it.size() }
                    }
                    put("first_with_consensus_$name") { (first, second) ->
                        val rootInfo = consensus(first, second)
                        function(rootInfo)
                            .map { first.mostRecentCommonAncestorCDR3().getRange(it) }
                            .fold(NucleotideSequence.EMPTY) { a, b -> a + b }
                    }
                    put("second_with_consensus_$name") { (first, second) ->
                        val rootInfo = consensus(first, second)
                        function(rootInfo)
                            .map { second.mostRecentCommonAncestorCDR3().getRange(it) }
                            .fold(NucleotideSequence.EMPTY) { a, b -> a + b }
                    }
                    put("score_$name") { (first, second) ->
                        val rootInfo = consensus(first, second)
                        val firstPart = function(rootInfo)
                            .map { first.mostRecentCommonAncestorCDR3().getRange(it) }
                            .fold(NucleotideSequence.EMPTY) { a, b -> a + b }
                        val secondPart = function(rootInfo)
                            .map { second.mostRecentCommonAncestorCDR3().getRange(it) }
                            .fold(NucleotideSequence.EMPTY) { a, b -> a + b }
                        Aligner.alignGlobal(
                            MutationsUtils.NDNScoring(),
                            firstPart,
                            secondPart
                        ).score
                    }
                }
                CDR3Part("NDN") { rootInfo ->
                    listOf(
                        Range(
                            rootInfo.base.rangeInCDR3.V.length(),
                            rootInfo.base.VJBase.CDR3length - rootInfo.base.rangeInCDR3.J.length()
                        )
                    )
                }
                CDR3Part("VCDR3Part") { rootInfo ->
                    listOf(Range(0, rootInfo.base.rangeInCDR3.V.length()))
                }
                CDR3Part("VDJunction") { rootInfo ->
                    val dBeginTrimmed = (rootInfo as? RootInfoWithD)?.DBeginTrimmed ?: return@CDR3Part emptyList()
                    listOf(
                        Range(
                            rootInfo.base.rangeInCDR3.V.length(),
                            dBeginTrimmed
                        )
                    )
                }
                CDR3Part("DCDR3Part") { rootInfo ->
                    val dBeginTrimmed = (rootInfo as? RootInfoWithD)?.DBeginTrimmed ?: return@CDR3Part emptyList()
                    val dEndTrimmed = (rootInfo as? RootInfoWithD)?.DEndTrimmed ?: return@CDR3Part emptyList()
                    listOf(
                        Range(
                            dBeginTrimmed,
                            dEndTrimmed
                        )
                    )
                }
                CDR3Part("DJJunction") { rootInfo ->
                    val dEndTrimmed = (rootInfo as? RootInfoWithD)?.DEndTrimmed ?: return@CDR3Part emptyList()
                    listOf(
                        Range(
                            dEndTrimmed,
                            rootInfo.base.VJBase.CDR3length - rootInfo.base.rangeInCDR3.J.length()
                        )
                    )
                }
                CDR3Part("NSegments") { rootInfo ->
                    val dBeginTrimmed = (rootInfo as? RootInfoWithD)?.DBeginTrimmed
                    val dEndTrimmed = (rootInfo as? RootInfoWithD)?.DEndTrimmed

                    if (dBeginTrimmed == null || dEndTrimmed == null) {
                        listOf(
                            Range(
                                rootInfo.base.rangeInCDR3.V.length(),
                                rootInfo.base.VJBase.CDR3length - rootInfo.base.rangeInCDR3.J.length()
                            )
                        )
                    } else {
                        listOf(
                            Range(
                                rootInfo.base.rangeInCDR3.V.length(),
                                dBeginTrimmed
                            ), Range(
                                dEndTrimmed,
                                rootInfo.base.VJBase.CDR3length - rootInfo.base.rangeInCDR3.J.length()
                            )
                        )
                    }
                }
                CDR3Part("JCDR3Part") { rootInfo ->
                    listOf(
                        Range(
                            rootInfo.base.VJBase.CDR3length - rootInfo.base.rangeInCDR3.J.length(),
                            rootInfo.base.VJBase.CDR3length
                        )
                    )
                }

                put("common_VJ_count") { (first, second) ->
                    val commonVMutations = MutationsUtils.zip(
                        first.mostRecentCommonAncestor().fromRootToThis.mutations.V.mutationsOutsideOfCDR3,
                        second.mostRecentCommonAncestor().fromRootToThis.mutations.V.mutationsOutsideOfCDR3
                    ) { a, b, _ -> a.intersection(b).size() }.values.size
                    val commonJMutations = MutationsUtils.zip(
                        first.mostRecentCommonAncestor().fromRootToThis.mutations.J.mutationsOutsideOfCDR3,
                        second.mostRecentCommonAncestor().fromRootToThis.mutations.J.mutationsOutsideOfCDR3
                    ) { a, b, _ -> a.intersection(b).size() }.values.size
                    commonVMutations + commonJMutations
                }

                put("score_CDR3") { (first, second) ->
                    Aligner.alignGlobal(
                        MutationsUtils.NDNScoring(),
                        first.mostRecentCommonAncestorCDR3(),
                        second.mostRecentCommonAncestorCDR3()
                    ).score
                }
                put("score_broadest_NDN") { (first, second) ->
                    val VBeginTrimmed =
                        min(first.rootInfo.rangeInCDR3.V.length(), second.rootInfo.rangeInCDR3.V.length())
                    val JCDR3Length = min(first.rootInfo.rangeInCDR3.J.length(), second.rootInfo.rangeInCDR3.J.length())
                    val JEndTrimmed = first.treeId.VJBase.CDR3length - JCDR3Length
                    Aligner.alignGlobal(
                        MutationsUtils.NDNScoring(),
                        first.mostRecentCommonAncestorCDR3().getRange(VBeginTrimmed, JEndTrimmed),
                        second.mostRecentCommonAncestorCDR3().getRange(VBeginTrimmed, JEndTrimmed)
                    ).score
                }
                put("broadest_NDN_length") { (first, second) ->
                    val VBeginTrimmed =
                        min(first.rootInfo.rangeInCDR3.V.length(), second.rootInfo.rangeInCDR3.V.length())
                    val JCDR3Length = min(first.rootInfo.rangeInCDR3.J.length(), second.rootInfo.rangeInCDR3.J.length())
                    val JEndTrimmed = first.treeId.VJBase.CDR3length - JCDR3Length
                    JEndTrimmed - VBeginTrimmed
                }
            }
            val writer: PrintStream
            synchronized(DebugDirOption) {
                if (!pathToWrite.exists()) {
                    pathToWrite.createFile()
                    writer = PrintStream(FileOutputStream(pathToWrite.toFile(), true), true)
                    XSV.writeXSVHeaders(writer, columns.keys, "\t")
                } else {
                    writer = PrintStream(FileOutputStream(pathToWrite.toFile(), true), true)
                }
            }

            val pairsToCompare = trees.flatMapIndexed { index, firstTree ->
                trees.drop(index + 1).map { secondTree ->
                    firstTree to secondTree
                }
            }
            XSV.writeXSVBody(writer, pairsToCompare, columns, "\t")
        }
    }

    private fun TreeWithMetaBuilder.mostRecentCommonAncestorCDR3() =
        mostRecentCommonAncestor().fromRootToThis.buildCDR3(rootInfo)

    private val List<Tree.NodeWithParent<TreeBuilderByAncestors.ObservedOrReconstructed<CloneWithMutationsFromReconstructedRoot, SyntheticNode>>>.theLessMutatedClone
        get() = filter { it.node.isLeaf() }
            .map { (_, node) ->
                node.content.convert({ it }, { null })!!
            }
            .minBy { it.mutationsFromVJGermline.VJMutationsCount }
            .clone.mainClone

    private fun consensusDGene(tree: TreeWithMetaBuilder): VDJCGene? = bestDGenes(tree.allClones(), tree.treeId.VJBase)
        .firstOrNull { candidate ->
            tree.DGeneFits(candidate)
        }

    private fun consensusDGene(first: TreeWithMetaBuilder, second: TreeWithMetaBuilder): VDJCGene? =
        bestDGenes(first.allClones() + second.allClones(), first.treeId.VJBase)
            .firstOrNull { candidate ->
                first.DGeneFits(candidate) && second.DGeneFits(candidate)
            }

    private fun TreeWithMetaBuilder.DGeneFits(candidate: VDJCGene): Boolean =
        allNodes().theLessMutatedClone
            .getFeature(
                GeneFeature(DBeginTrimmed, DEndTrimmed),
                treeId.VJBase,
                candidate.id
            ) != null

    private fun bestDGenes(
        clones: List<Clone>,
        VJBase: VJBase
    ): List<VDJCGene> = clones
        .flatMap { clone ->
            (clone.getHits(GeneType.Diversity)?.toList() ?: emptyList())
                .filter { dGene ->
                    clone.getFeature(
                        GeneFeature(DBeginTrimmed, DEndTrimmed),
                        VJBase,
                        dGene.gene.id
                    ) != null
                }
        }
        .map { it.gene }
        .groupingBy { it }.eachCount()
        .filter { it.value >= clones.size * 0.5 }
        .entries
        .sortedByDescending { it.value }
        .map { it.key }

    private fun TreeWithMetaBuilder.allClones() = allNodes()
        .mapNotNull { (_, node) ->
            node.content.convert(
                { it.clone.mainClone },
                { null }
            )
        }

    private val TreeWithMetaBuilder.extendedRootInfo: ExtendedRootInfo
        get() {
            val DGene = consensusDGene(this)
            return if (DGene != null) {
                val theLessMutatedClone = allNodes().theLessMutatedClone
                RootInfoWithD(
                    rootInfo,
                    DGene,
                    theLessMutatedClone
                        .getFeature(GeneFeature(CDR3Begin, DBeginTrimmed), treeId.VJBase, DGene.id)!!
                        .size(),
                    theLessMutatedClone
                        .getFeature(GeneFeature(CDR3Begin, DEndTrimmed), treeId.VJBase, DGene.id)!!
                        .size()
                )
            } else {
                BaseRootInfo(rootInfo)
            }
        }

    private fun consensus(first: TreeWithMetaBuilder, second: TreeWithMetaBuilder): ExtendedRootInfo {
        val allClones = (first.allNodes() + second.allNodes())
            .map { it.node }
            .filter { it.isLeaf() }
            .map { node ->
                node.content.convert({
                    CloneWithMutationsFromVJGermline(
                        it.mutationsFromVJGermline,
                        it.clone
                    )
                }, { null })!!
            }

        val rootInfo = SHMTreeBuilder.buildRootInfo(allClones, topToVoteOnNDNSize)
        val DGene = consensusDGene(first, second)
        return if (DGene != null) {
            val theLessMutatedClone = (first.allNodes() + second.allNodes()).theLessMutatedClone
            val VJBase = first.treeId.VJBase
            RootInfoWithD(
                rootInfo,
                DGene,
                theLessMutatedClone
                    .getFeature(GeneFeature(CDR3Begin, DBeginTrimmed), VJBase, DGene.id)!!
                    .size(),
                theLessMutatedClone
                    .getFeature(GeneFeature(CDR3Begin, DEndTrimmed), VJBase, DGene.id)!!
                    .size()
            )
        } else {
            BaseRootInfo(rootInfo)
        }
    }

    sealed interface ExtendedRootInfo {
        val base: RootInfo
    }

    data class BaseRootInfo(
        override val base: RootInfo
    ) : ExtendedRootInfo

    data class RootInfoWithD(
        override val base: RootInfo,
        val DGene: VDJCGene,
        val DBeginTrimmed: Int,
        val DEndTrimmed: Int
    ) : ExtendedRootInfo
}
