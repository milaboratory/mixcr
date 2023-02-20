/*
 * Copyright (c) 2014-2023, MiLaboratories Inc. All Rights Reserved
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

import com.milaboratory.app.InputFileType
import com.milaboratory.app.ValidationException
import com.milaboratory.miplots.writePDF
import com.milaboratory.mixcr.cli.CommonDescriptions.Labels
import com.milaboratory.mixcr.postanalysis.plots.AlignmentOption
import com.milaboratory.mixcr.postanalysis.plots.DefaultMeta
import com.milaboratory.mixcr.postanalysis.plots.ShmTreePlotter
import io.repseq.core.GeneFeature
import picocli.CommandLine.Command
import picocli.CommandLine.Help.Visibility.ALWAYS
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path
import kotlin.io.path.createDirectories

@Command(
    description = ["Visualize SHM tree and save in PDF format"]
)
class CommandExportShmTreesPlots : CommandExportShmTreesAbstract() {
    @Parameters(
        index = "1",
        description = ["Path where to write PDF file with plots."],
        paramLabel = "plots.pdf"
    )
    lateinit var out: Path

    @set:Option(
        names = ["--metadata", "-m"],
        description = [
            "Path to metadata file",
            "Metadata should be a .tsv or .csv file with a column named 'sample' with filenames of .clns files used in findShmTrees"
        ],
        paramLabel = "<path.(tsv|csv)>",
        order = OptionsOrder.main + 10_000
    )
    var metadata: Path? = null
        set(value) {
            ValidationException.requireFileType(value, InputFileType.XSV)
            field = value
        }

    @set:Option(
        names = ["--limit"],
        description = ["Take first N trees (for debug purposes)"],
        hidden = true,
        paramLabel = "<n>"
    )
    var limit: Int? = null
        set(value) {
            ValidationException.require(value == null || value > 0) { "value must be greater then 0" }
            field = value
        }

    @Option(
        names = ["--node-color"],
        description = ["Color nodes with given metadata column"],
        paramLabel = "<meta>",
        order = OptionsOrder.main + 10_100
    )
    var nodeColor: String? = null

    @Option(
        names = ["--line-color"],
        description = ["Color lines with given metadata column"],
        paramLabel = "<meta>",
        order = OptionsOrder.main + 10_200
    )
    var lineColor: String? = null

    @Option(
        names = ["--node-size"],
        description = ["Size nodes with given metadata column. Predefined columns: \"${DefaultMeta.Abundance}\"."],
        paramLabel = "<meta>",
        showDefaultValue = ALWAYS,
        order = OptionsOrder.main + 10_300
    )
    var nodeSize: String = DefaultMeta.Abundance

    @Option(
        names = ["--node-label"],
        description = ["Label nodes with given metadata column. Predefined columns: \"${DefaultMeta.Isotype}\""],
        paramLabel = "<meta>",
        order = OptionsOrder.main + 10_400
    )
    var nodeLabel: String? = null


    @Option(
        names = ["--alignment-nt"],
        description = ["Show tree nucleotide alignments using specified gene feature"],
        paramLabel = Labels.GENE_FEATURE,
        order = OptionsOrder.main + 10_500,
        completionCandidates = GeneFeaturesCandidates::class
    )
    var alignmentGeneFeatureNt: GeneFeature? = null

    @Option(
        names = ["--alignment-aa"],
        description = ["Show tree amino acid alignments using specified gene feature"],
        paramLabel = Labels.GENE_FEATURE,
        order = OptionsOrder.main + 10_600,
        completionCandidates = GeneFeaturesCandidates::class
    )
    var alignmentGeneFeatureAa: GeneFeature? = null

    @Option(
        names = ["--alignment-no-fill"],
        description = ["Do not highlight alignments with color"],
        order = OptionsOrder.main + 10_700
    )
    var noAlignmentFill: Boolean = false

    override val inputFiles: List<Path>
        get() = listOfNotNull(input, metadata)

    override val outputFiles
        get() = listOf(out)

    override fun validate() {
        super.validate()
        ValidationException.requireFileType(out, InputFileType.PDF)
    }

    val alignment by lazy {
        if (alignmentGeneFeatureAa == null && alignmentGeneFeatureNt == null)
            null
        else if (alignmentGeneFeatureAa != null)
            AlignmentOption(alignmentGeneFeatureAa!!, true, !noAlignmentFill)
        else
            AlignmentOption(alignmentGeneFeatureNt!!, false, !noAlignmentFill)
    }

    override fun run1() {
        val plots = ShmTreePlotter(
            input.toAbsolutePath(),
            metadata?.toAbsolutePath(),
            filter = treeFilter,
            limit = limit,
            nodeColor = nodeColor,
            lineColor = lineColor,
            nodeSize = nodeSize,
            nodeLabel = nodeLabel,
            alignment = alignment
        ).plots

        out.toAbsolutePath().parent.createDirectories()
        writePDF(out.toAbsolutePath(), plots)
    }
}
