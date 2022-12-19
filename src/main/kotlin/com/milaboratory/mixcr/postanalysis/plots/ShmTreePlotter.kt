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

import cc.redberry.pipe.util.asSequence
import com.milaboratory.app.ValidationException
import com.milaboratory.core.alignment.Alignment
import com.milaboratory.core.motif.BitapPattern
import com.milaboratory.core.sequence.AminoAcidSequence
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.miplots.Position
import com.milaboratory.miplots.color.Palettes
import com.milaboratory.miplots.dendro.GGDendroPlot
import com.milaboratory.miplots.dendro.Node
import com.milaboratory.miplots.dendro.plusAssign
import com.milaboratory.miplots.dendro.withAlignmentLayer
import com.milaboratory.miplots.dendro.withLabels
import com.milaboratory.miplots.dendro.withTextLayer
import com.milaboratory.miplots.stat.util.TestMethod
import com.milaboratory.mixcr.postanalysis.plots.DefaultMeta.Abundance
import com.milaboratory.mixcr.postanalysis.plots.DefaultMeta.Alignment
import com.milaboratory.mixcr.postanalysis.plots.DefaultMeta.Isotype
import com.milaboratory.mixcr.trees.SHMTreeForPostanalysis.Base
import com.milaboratory.mixcr.trees.SHMTreeForPostanalysis.SplittedNode
import com.milaboratory.mixcr.trees.SHMTreesReader
import com.milaboratory.mixcr.trees.Tree
import com.milaboratory.mixcr.trees.TreeFilter
import com.milaboratory.mixcr.trees.forPostanalysisSplitted
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
    val feature: GeneFeature,
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

data class StatOption(
    /** Metadata column to use */
    val metadataColumn: String,
    /** Stat method */
    val method: TestMethod = TestMethod.Wilcoxon
)

object DefaultMeta {
    const val Isotype = "Isotype"
    const val Abundance = "Abundance"
    const val Alignment = "Alignment"
}

typealias GGNode = Node<SplittedNode>

data class AlignmentOption(
    /** Gene feature to align */
    val gf: GeneFeature,
    /** Amino acid or nucleotide */
    val isAA: Boolean,
    /** Fill with color */
    val fill: Boolean
)

class ShmTreePlotter(
    private val shmtFile: Path,
    metadataFile: Path? = null,
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

            val sampleColumn = df.columnNames().find { it.equals("sample", true) }
                ?: throw ValidationException("Metadata file should contains column with name 'sample'")
            val idsInMeta = df[sampleColumn].toList().map { it.toString() }.distinct()
            val matched = StringUtil.matchLists(idsInMeta, fileNames)

            val fileNames2id = fileNames.mapIndexed { i, n -> n to i }.toMap()
            this.metadata = df.rows().associate {
                val fileName = it[sampleColumn]
                val datasetId = matched[fileName] ?: throw ValidationException("sample $fileName not found in metadata")
                fileNames2id[datasetId]!! to it.toMap().mapValues { r -> r.value!! }
            }
        } else {
            this.metadata = null
        }
    }

    val plots: List<Plot> by lazy {
        SHMTreesReader(shmtFile, VDJCLibraryRegistry.getDefault()).use { reader ->
            reader.readTrees().use { trees ->
                trees
                    .asSequence()
                    .filter { filter?.match(it.treeId) != false }
                    .map { tree ->
                        tree.forPostanalysisSplitted(reader.fileNames, reader.libraryRegistry)
                    }
                    .filter { filter?.match(it) != false }
                    .take(limit ?: Int.MAX_VALUE)
                    .map { tree ->
                        plot(tree.meta.treeId, tree.tree)
                    }
                    .toList()
            }
        }
    }

    private fun toGGNode(node: Tree.Node<SplittedNode>) =
        toGGNode(node, 0.0)

    private fun toGGNode(
        node: Tree.Node<SplittedNode>,
        distanceToParent: Double
    ): GGNode = run {

        val nodeMetadata = mutableMapOf<String, Any?>()
        val cloneWrapper = node.content.clone
        if (cloneWrapper != null) {
            val isotype = cloneWrapper.clone.getBestHit(GeneType.Constant)?.gene?.familyName
            if (isotype != null)
                nodeMetadata[Isotype] = isotype[3]
            nodeMetadata[Abundance] = ln(cloneWrapper.clone.fraction)

            if (alignment != null) {
                val mutationsDescription = node.content.mutationsFromGermline()
                val alignmentForFeature: Alignment<*>? = when {
                    alignment.isAA -> mutationsDescription.aaAlignment(alignment.gf)
                    else -> mutationsDescription.nAlignment(alignment.gf)
                }
                if (alignmentForFeature != null) {
                    nodeMetadata[Alignment] = alignmentForFeature.alignmentHelper.seq2String
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
                    it.node.content.distanceFrom(Base.parent) ?: 0.0
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
        tree: Tree<SplittedNode>,
        stat: StatOption
    ): Double = run {
        val leafs = tree.allLeafs()

        val data = leafs
            .filter { it.node.content.clone != null }
            .map {
                val datasetId = it.node.content.clone!!.datasetId
                val height = it.node.content.distanceFrom(Base.germline) ?: 0.0
                val metaValue = metadata?.get(datasetId)?.get(stat.metadataColumn)
                metadataRanks[stat.metadataColumn]!![metaValue]!! to height
            }

        val x = data.map { it.first }.toList().toDoubleArray()
        val y = data.map { it.second }.toList().toDoubleArray()

        if (x.size <= 2)
            1.0
        else
            stat.method.pValue(x, y, paired = true)
    }

    fun plot(treeId: Int, tree: Tree<SplittedNode>) = run {

        val fixedLineColor = if (this@ShmTreePlotter.lineColor == null) "#555555" else null
        val fixedNodeColor = if (this@ShmTreePlotter.nodeColor == null) "#555555" else null
        val fixedNodeSize = if (this@ShmTreePlotter.nodeSize == null) 5.0 else null
        val fixedNodeAlpha = if (this@ShmTreePlotter.nodeSize == null) 0.8 else 0.5
        val colorAes = this@ShmTreePlotter.nodeColor ?: this@ShmTreePlotter.lineColor

        val dendroTree = toGGNode(tree.root)

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
                dendro.withAlignmentLayer(Alignment, leafsOnly = true)
            else
                dendro.withTextLayer(Alignment, leafsOnly = true)

        var title = "Id: $treeId"
        if (stats.isNotEmpty()) {
            for (stat in stats) {
                title += " " + pValue(tree, stat)
            }
        }
        dendro += ggtitle(title)

        dendro += guides(size = "none")

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
