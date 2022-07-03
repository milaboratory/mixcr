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
package com.milaboratory.mixcr.postanalysis.plots

import cc.redberry.pipe.CUtils
import com.milaboratory.core.alignment.Alignment
import com.milaboratory.core.motif.BitapPattern
import com.milaboratory.core.sequence.AminoAcidSequence
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.miplots.Position
import com.milaboratory.miplots.color.Palettes
import com.milaboratory.miplots.dendro.*
import com.milaboratory.miplots.stat.util.TestMethod
import com.milaboratory.mixcr.trees.SHMTreeForPostanalysis
import com.milaboratory.mixcr.trees.SHMTreeForPostanalysis.NodeWithClones
import com.milaboratory.mixcr.trees.SHMTreesReader
import com.milaboratory.mixcr.trees.Tree
import com.milaboratory.mixcr.trees.forPostanalysis
import com.milaboratory.util.StringUtil
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import io.repseq.core.VDJCLibraryRegistry
import jetbrains.letsPlot.intern.Plot
import jetbrains.letsPlot.label.ggtitle
import jetbrains.letsPlot.scale.guides
import jetbrains.letsPlot.scale.scaleColorManual
import jetbrains.letsPlot.scale.scaleFillManual
import org.jetbrains.kotlinx.dataframe.api.rows
import org.jetbrains.kotlinx.dataframe.api.toMap
import java.nio.file.Path
import kotlin.math.ln
import kotlin.random.Random

data class SeqPattern(
    /** Sequence */
    val seq: String,
    /** Amino acid or nucleotide */
    val isAA: Boolean,
    /** Match inside specified gene feature */
    val feature: GeneFeature?,
    /** Max allowed subs & indels */
    val maxErrors: Int
) {
    /** Compiled bitap pattern used for subsequence match */
    internal val bitapPattern: BitapPattern =
        if (isAA)
            AminoAcidSequence(seq).toMotif().bitapPattern
        else
            NucleotideSequence(seq).toMotif().bitapPattern

    internal fun bitapQ(seq: com.milaboratory.core.sequence.Sequence<*>) =
        bitapPattern.substitutionAndIndelMatcherLast(maxErrors, seq)?.findNext() != -1
}

class TreeFilter(
    /** minimal number of nodes in tree */
    val minNodes: Int? = null,
    /** minimal height of the tree */
    val minHeight: Int? = null,
    /** filter specific trees by id */
    val treeIds: Set<Int>? = null,
    /** filter specific trees by pattern */
    val seqPattern: SeqPattern? = null,
) {
    fun match(tree: SHMTreeForPostanalysis): Boolean {
        if (minNodes != null && tree.tree.allNodes().count() < minNodes)
            return false
        if (minHeight != null && tree.tree.root.height() < minHeight)
            return false
        if (treeIds != null && !treeIds.contains(tree.meta.treeId))
            return false
        if (seqPattern != null) {
            return tree.tree.allNodes().flatMap { it.node.content.clones.map { w -> w.clone } }.any { clone ->
                if (seqPattern.feature != null) {
                    if (seqPattern.isAA)
                        seqPattern.bitapQ(clone.getAAFeature(seqPattern.feature))
                    else
                        seqPattern.bitapQ(clone.getFeature(seqPattern.feature).sequence)
                } else {
                    clone.targets.any { seqPattern.bitapQ(it.sequence) }
                }
            }
        }
        return true
    }
}

data class StatOption(
    /** Metadata column to use */
    val metadataColumn: String,
    /** Stat method */
    val method: TestMethod = TestMethod.Wilcoxon
)

object DefaultMeta {
    val Isotype = "Isotype"
    val Abundance = "Abundance"
    val Alignment = "Alignment"
}

typealias GGNode = Node<NodeWithClones>

data class AlignmentOption(
    /** Gene feature to align */
    val gf: GeneFeature,
    /** Amino acid or nucleotide */
    val isAA: Boolean,
    /** Fill with color */
    val fill: Boolean
)

class ShmTreePlotter(
    val shmtFile: Path,
    val metadataFile: Path? = null,
    /** Filter specific trees */
    val filter: TreeFilter? = null,
    /** Take first N trees (for debug purposes) */
    val limit: Int? = null,

    /** Color nodes with given metadata column */
    val nodeColor: String? = null,
    /** Color lines with given metadata column */
    val lineColor: String? = null,
    /** Scale node size with given metadata column */
    val nodeSize: String? = null,
    /** Label nodes with given metadata column */
    val nodeLabel: String? = null,
    /** Add alignment color layer */
    val alignment: AlignmentOption? = null,
    /** Compute and show statistics on the tree */
    val stats: List<StatOption> = emptyList()
) {
    /** sampleId -> sample metadata */
    val metadata: Map<Int, Map<String, Any>>?

    init {
        // parse metadata
        if (metadataFile != null) {
            val df = readMetadata(metadataFile)
            val fileNames = SHMTreesReader(shmtFile, VDJCLibraryRegistry.getDefault()).use { it.fileNames }

            val sampleColumn = df.columnNames().first { it.equals("sample", true) }
            val idsInMeta = df[sampleColumn].toList().map { it.toString() }.distinct()
            val matched = StringUtil.matchLists(idsInMeta, fileNames)

            val fileNames2id = fileNames.mapIndexed { i, n -> n to i }.toMap()
            this.metadata = df.rows().map {
                fileNames2id[matched[it[sampleColumn]].toString()]!! to it.toMap().mapValues { r -> r.value!! }
            }.toMap()
        } else {
            this.metadata = null
        }
    }

    val plots: List<Plot> by lazy {
        SHMTreesReader(shmtFile, VDJCLibraryRegistry.getDefault()).use {
            val list = mutableListOf<Plot>()

            it.readTrees().use { reader ->
                var c = 0
                for (t in CUtils.it(reader)) {
                    val tree = t.forPostanalysis(
                        it.fileNames,
                        it.alignerParameters,
                        VDJCLibraryRegistry.getDefault()
                    )

                    if (filter != null && !filter.match(tree))
                        continue
                    if (limit != null && c > limit)
                        break

                    list += plot(tree)

                    ++c
                }
            }

            list
        }
    }

    private fun toGGNode(node: Tree.Node<NodeWithClones>) =
        toGGNode(node, 0.0)

    private fun toGGNode(
        node: Tree.Node<NodeWithClones>,
        distanceToParent: Double
    ): GGNode = run {

        val clones = node.content.clones
        val nodeMetadata = mutableMapOf<String, Any?>()
        if (clones.size == 1) { // TODO implement for multiple clones
            val cloneWrapper = clones[0]
            val isotype = cloneWrapper.clone.getBestHit(GeneType.Constant)?.gene?.familyName
            if (isotype != null)
                nodeMetadata[DefaultMeta.Isotype] = isotype[3]
            nodeMetadata[DefaultMeta.Abundance] = ln(cloneWrapper.clone.count)

            if (alignment != null) {
                val mutationsDescription = node.content.mutationsDescription()
                val alignmentForFeature: Alignment<*>? = when {
                    alignment.isAA -> mutationsDescription.aaAlignment(alignment.gf)
                    else -> mutationsDescription.nAlignment(alignment.gf)
                }
                if (alignmentForFeature != null) {
                    nodeMetadata[DefaultMeta.Alignment] = alignmentForFeature.alignmentHelper.seq2String
                }
            }

            if (metadata != null)
                nodeMetadata.putAll(metadata[cloneWrapper.datasetId]!!)
        }

        GGNode(
            node.content,
            distanceToParent,
            node.links.map {
                toGGNode(
                    it.node,
                    it.node.content.distanceFromParent?.toDouble() ?: 0.0
                )
            },
            metadata = nodeMetadata.toMap()
        )
    }


    /** metadataColumn -> columnValue->rank */
    private val metadataRanks: Map<String, Map<Any, Double>> by lazy {
        metadata!!

        val columns = metadata.flatMap {
            it.value.keys
        }.distinct()

        columns.associateWith { column ->
            metadata.map { it.value[column]!! }.distinct().mapIndexed { i, v -> v to (1 + i.toDouble()) }.toMap()
        }
    }

    private fun pValue(
        tree: SHMTreeForPostanalysis,
        stat: StatOption
    ): Double = run {
        val leafs = tree.tree.allLeafs()

        val data = leafs
            .flatMap {
                it.node.content.clones.map { c ->
                    val height = it.node.content.distanceFrom(SHMTreeForPostanalysis.Base.root)?.toDouble() ?: 0.0
                    val metaValue = metadata?.get(c.datasetId)?.get(stat.metadataColumn)
                    metadataRanks[stat.metadataColumn]!![metaValue]!! to height
                }
            }

        val x = data.map { it.first }.toList().toDoubleArray()
        val y = data.map { it.second }.toList().toDoubleArray()

        if (x.size <= 2)
            1.0
        else
            stat.method.pValue(x, y, paired = true)
    }

    fun plot(tree: SHMTreeForPostanalysis) = run {

        val fixedLineColor = if (this@ShmTreePlotter.lineColor == null) "#555555" else null
        val fixedNodeColor = if (this@ShmTreePlotter.nodeColor == null) "#555555" else null
        val fixedNodeSize = if (this@ShmTreePlotter.nodeSize == null) 5.0 else null
        val fixedNodeAlpha = if (this@ShmTreePlotter.nodeSize == null) 0.8 else 0.5
        val colorAes = this@ShmTreePlotter.nodeColor ?: this@ShmTreePlotter.lineColor

        val dendroTree = toGGNode(tree.tree.root)

        val dendro = GGDendroPlot(
            dendroTree,
            rpos = Position.Left,
            lineColor = fixedLineColor,
            nodeColor = fixedNodeColor,
            nodeAlpha = fixedNodeAlpha,
            nodeSize = fixedNodeSize,
            height = 1.0,
        ) {
            this.color = colorAes
            this.size = this@ShmTreePlotter.nodeSize
        }

        if (nodeLabel != null)
            dendro.withLabels(nodeLabel, fillAlpha = 0.5)

        if (alignment != null)
            if (alignment.fill)
                dendro.withAlignmentLayer(DefaultMeta.Alignment, leafsOnly = true)
            else
                dendro.withTextLayer(DefaultMeta.Alignment, leafsOnly = true)

        var title = "Id: ${tree.meta.treeId}"
        if (stats.isNotEmpty()) {
            for (stat in stats) {
                title += " " + pValue(tree, stat)
            }
        }
        dendro.plusAssign(ggtitle(title))

        dendro.plusAssign(guides(size = "none"))

        var plt = dendro.plot

        // fix colors across plots
        val breaks = run {
            val l = ('A'..'Z').toMutableList<Any>()

            if (colorAes != null)
                l.addAll(dendroTree.toList().mapNotNull { it.metadata[colorAes] })

            l.distinct()
        }

        val colors = Palettes.Categorical.auto(breaks.size).colors.shuffled(Random(0))
        plt += scaleFillManual(
            values = colors,
            breaks = breaks,
            name = colorAes.toString()
        )
        plt += scaleColorManual(
            values = colors,
            breaks = breaks,
            name = colorAes.toString()
        )

        plt
    }
}
