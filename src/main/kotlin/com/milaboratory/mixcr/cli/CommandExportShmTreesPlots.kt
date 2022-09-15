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
package com.milaboratory.mixcr.cli

import com.milaboratory.miplots.writePDF
import com.milaboratory.mixcr.postanalysis.plots.AlignmentOption
import com.milaboratory.mixcr.postanalysis.plots.SeqPattern
import com.milaboratory.mixcr.postanalysis.plots.ShmTreePlotter
import com.milaboratory.mixcr.postanalysis.plots.TreeFilter
import io.repseq.core.GeneFeature
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path
import kotlin.io.path.createDirectories

@Command(
    sortOptions = false,
    separator = " ",
    description = ["Visualize SHM tree and save in PDF format"]
)
class CommandExportShmTreesPlots : CommandExportShmTreesAbstract() {
    @Parameters(index = "1", paramLabel = "plots.pdf")
    lateinit var out: Path

    @Option(
        names = ["--metadata", "-m"],
        description = ["Path to metadata file"]
    )
    var metadata: Path? = null

    @Option(
        names = ["--filter-min-nodes"],
        description = ["Minimal number of nodes in tree"]
    )
    var minNodes: Int? = null

    @Option(
        names = ["--filter-min-height"],
        description = ["Minimal height of the tree "]
    )
    var minHeight: Int? = null

    @Option(
        names = ["--ids"],
        description = ["Filter specific trees by id"],
        split = ","
    )
    var treeIds: Set<Int>? = null

    @Option(
        names = ["--filter-aa-pattern"],
        description = ["Filter specific trees by aa pattern"]
    )
    var patternSeqAa: String? = null

    @Option(
        names = ["--filter-nt-pattern"],
        description = ["Filter specific trees by nt pattern"]
    )
    var patternSeqNt: String? = null

    @Option(
        names = ["--filter-in-feature"],
        description = ["Match pattern inside specified gene feature"]
    )
    var patternInFeature: String? = "CDR3"

    @Option(
        names = ["--pattern-max-errors"],
        description = ["Max allowed subs & indels"]
    )
    var patternMaxErrors: Int = 0

    @Option(
        names = ["--limit"],
        description = ["Take first N trees (for debug purposes)"]
    )
    var limit: Int? = null

    @Option(
        names = ["--node-color"],
        description = ["Color nodes with given metadata column"]
    )
    var nodeColor: String? = null

    @Option(
        names = ["--line-color"],
        description = ["Color lines with given metadata column"]
    )
    var lineColor: String? = null

    @Option(
        names = ["--node-size"],
        description = ["Size nodes with given metadata column. Predefined columns: \"Abundance\"."]
    )
    var nodeSize: String? = null

    @Option(
        names = ["--node-label"],
        description = ["Label nodes with given metadata column. Predefined columns: \"Isotype\""]
    )
    var nodeLabel: String? = null


    @Option(
        names = ["--alignment-nt"],
        description = ["Show tree nucleotide alignments using specified gene feature"]
    )
    var alignmentGeneFeatureNt: String? = null

    @Option(
        names = ["--alignment-aa"],
        description = ["Show tree amino acid alignments using specified gene feature"]
    )
    var alignmentGeneFeatureAa: String? = null

    @Option(
        names = ["--alignment-no-fill"],
        description = ["Do not highlight alignments with color"]
    )
    var noAlignmentFill: Boolean = false

    override fun getOutputFiles(): List<String> = listOf(out.toString())

    override fun validate() {
        super.validate()
        if (!out.endsWith(".pdf"))
            throwValidationExceptionKotlin("Output file must have .pdf extension")
    }

    val alignment by lazy {
        if (alignmentGeneFeatureAa == null && alignmentGeneFeatureNt == null)
            null
        else if (alignmentGeneFeatureAa != null)
            AlignmentOption(GeneFeature.parse(alignmentGeneFeatureAa), true, !noAlignmentFill)
        else
            AlignmentOption(GeneFeature.parse(alignmentGeneFeatureNt), false, !noAlignmentFill)
    }

    private val pattern by lazy {
        if (patternSeqAa == null && patternSeqNt == null)
            null
        else if (patternSeqNt != null)
            SeqPattern(patternSeqNt!!, false, GeneFeature.parse(patternInFeature), patternMaxErrors)
        else
            SeqPattern(patternSeqAa!!, true, GeneFeature.parse(patternInFeature), patternMaxErrors)
    }

    private val filter by lazy {
        if (minNodes == null && minHeight == null && treeIds == null && pattern == null)
            null
        else
            TreeFilter(
                minNodes = minNodes,
                minHeight = minHeight,
                treeIds = treeIds,
                seqPattern = pattern
            )
    }

    override fun run0() {
        val plots = ShmTreePlotter(
            `in`.toAbsolutePath(),
            metadata?.toAbsolutePath(),
            filter = filter,
            limit = limit,
            nodeColor = nodeColor,
            lineColor = lineColor,
            nodeSize = nodeSize,
            nodeLabel = nodeLabel,
            alignment = alignment
        ).plots

        `in`.toAbsolutePath().parent.createDirectories()
        writePDF(
            `in`.toAbsolutePath(),
            plots
        )
    }
}
