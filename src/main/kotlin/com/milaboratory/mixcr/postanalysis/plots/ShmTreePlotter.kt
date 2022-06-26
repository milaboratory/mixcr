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
import com.milaboratory.miplots.Position
import com.milaboratory.miplots.dendro.GGDendroPlot
import com.milaboratory.miplots.dendro.plus
import com.milaboratory.mixcr.trees.CloneOrFoundAncestor
import com.milaboratory.mixcr.trees.SHMTreeResult
import com.milaboratory.mixcr.trees.SHMTreesReader
import com.milaboratory.mixcr.trees.Tree
import com.milaboratory.util.StringUtil
import io.repseq.core.GeneType
import io.repseq.core.VDJCLibraryRegistry
import jetbrains.letsPlot.intern.Plot
import jetbrains.letsPlot.label.ggtitle
import org.jetbrains.kotlinx.dataframe.api.rows
import org.jetbrains.kotlinx.dataframe.api.toMap
import java.nio.file.Path
import kotlin.math.ln

class TreeFilter(
    val minNodes: Int? = null,
    val minHeight: Int? = null,
    val treeIds: Set<Int>? = null,
) {
    fun match(tree: SHMTreeResult): Boolean {
        if (minNodes != null && tree.tree.allNodes().count() < minNodes)
            return false
        if (minHeight != null && tree.tree.root.height() < minHeight)
            return false
        if (treeIds != null && !treeIds.contains(tree.treeId))
            return false
        return true
    }
}

object DefaultMeta {
    val Isotype = "Isotype"
    val Abundance = "Abundance"
}

typealias GGNode = com.milaboratory.miplots.dendro.Node<CloneOrFoundAncestor>

class ShmTreePlotter(
    treesPath: Path,
    metadataPath: Path? = null,
    val filter: TreeFilter? = null,

    val color: String? = null,
    val size: String? = null,
    val label: String? = null,
) {
    /** sampleId -> sample metadata */
    val metadata: Map<Int, Map<String, Any>>?
    val plots: List<Plot>

    init {
        // parse metadata
        if (metadataPath != null) {
            val df = readMetadata(metadataPath)
            val fileNames = SHMTreesReader(treesPath, VDJCLibraryRegistry.getDefault()).use { it.fileNames }

            val idsInMeta = df["sample"].toList().map { it.toString() }.distinct()
            val matched = StringUtil.matchLists(idsInMeta, fileNames)

            val fileNames2id = fileNames.mapIndexed { i, n -> n to i }.toMap()
            this.metadata = df.rows().map {
                fileNames2id[matched[it["sample"]].toString()]!! to it.toMap().mapValues { r -> r.value!! }
            }.toMap()
        } else {
            this.metadata = null
        }

        this.plots = SHMTreesReader(treesPath, VDJCLibraryRegistry.getDefault()).use {
            val list = mutableListOf<Plot>()

            for (t in CUtils.it(it.readTrees())) {
                if (filter != null && !filter.match(t))
                    continue
                list += plot(t).plot
            }

            list
        }
    }

    private fun toGGNode(node: Tree.Node<CloneOrFoundAncestor>) =
        toGGNode(node, 0.0)

    private fun toGGNode(
        node: Tree.Node<CloneOrFoundAncestor>,
        distanceToParent: Double
    ): GGNode = run {
        val nodeMetadata = mutableMapOf<String, Any?>()
        if (metadata != null && node.content.datasetId != null)
            nodeMetadata.putAll(metadata[node.content.datasetId]!!)

        node.content.clone?.also {
            val isotype = node.content.clone.getBestHit(GeneType.Constant)?.gene?.familyName
            if (isotype != null)
                nodeMetadata[DefaultMeta.Isotype] = isotype
            nodeMetadata[DefaultMeta.Abundance] = ln(node.content.clone.count)
        }

        GGNode(
            node.content,
            distanceToParent,
            node.links.map { toGGNode(it.node, it.node.content.distanceFromMostRecentAncestor?.toDouble() ?: 0.0) },
            metadata = nodeMetadata.toMap()
        )
    }

    fun plot(tree: SHMTreeResult) = run {
        GGDendroPlot(
            toGGNode(tree.tree.root),
            rpos = Position.Left,
            linecolor = "#aaaaaa",
            height = 1.0,
        ) {
            this.color = this@ShmTreePlotter.color
            this.size = this@ShmTreePlotter.size
        } + ggtitle(tree.treeId.let {
            "Id: $it"
        })
    }
}
