/*
 * Copyright (c) 2014-2024, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
package com.milaboratory.mixcr.cli.postanalysis

import com.milaboratory.app.InputFileType
import com.milaboratory.app.ValidationException
import com.milaboratory.miplots.writeFile
import com.milaboratory.mixcr.cli.CommonDescriptions
import com.milaboratory.mixcr.cli.CommonDescriptions.Labels
import com.milaboratory.mixcr.cli.exportTypes
import com.milaboratory.mixcr.postanalysis.plots.parseFilter
import com.milaboratory.mixcr.postanalysis.plots.readMetadata
import com.milaboratory.util.StringUtil
import jetbrains.letsPlot.intern.Plot
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.toDataFrame
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolute
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

abstract class CommandPaExportPlots : CommandPaExport() {
    @set:Option(
        description = [CommonDescriptions.METADATA],
        names = ["--metadata"],
        paramLabel = "<path.tsv>",
        order = OptionsOrder.main + 4_000
    )
    var metadata: Path? = null
        set(value) {
            ValidationException.requireFileType(value, InputFileType.TSV)
            ValidationException.requireFileExists(value)
            field = value
        }

    @Option(
        description = ["Plot width."],
        names = ["--width"],
        paramLabel = "<n>",
        order = OptionsOrder.width
    )
    var width = 0

    @Option(
        description = ["Plot height."],
        names = ["--height"],
        paramLabel = "<n>",
        order = OptionsOrder.height
    )
    var height = 0

    @Option(
        description = ["Filter samples to put on a plot by their metadata values. Filter allows equality (`species=cat`) or arithmetic comparison (`age>=10`) etc."],
        names = ["--filter"],
        split = ",",
        paramLabel = "<meta(|>|>=|=|<=|<)value>",
        order = OptionsOrder.main + 4_100
    )
    var filterByMetadata: List<String>? = null

    @Parameters(
        description = ["Output PDF/EPS/PNG/JPEG file name."],
        index = "1",
        paramLabel = "output.${Labels.EXPORT_TYPES}"
    )
    lateinit var out: Path

    protected fun <T> DataFrame<T>.filterByMetadata(): DataFrame<T> {
        var result = this
        filterByMetadata?.let { filterByMetadata ->
            for (f in filterByMetadata.map { f -> metadataDf!!.parseFilter(f) }) {
                result = f.apply(result)
            }
        }
        return result
    }

    /** Get metadata from file  */
    protected val metadataDf: DataFrame<*>? by lazy {
        when {
            metadata != null -> readMetadata(metadata!!)
            else -> paResult.metadata?.toDataFrame()
        }
    }

    override fun validate() {
        super.validate()
        ValidationException.requireFileType(out, InputFileType.exportTypes)
        metadataDf?.let { metadataDf ->
            if (!metadataDf.containsColumn("sample"))
                throw ValidationException("Metadata must contain 'sample' column")
            val samples = inputFiles
            val mapping = StringUtil.matchLists(
                samples.map { it.toString() },
                metadataDf["sample"].toList().map { it!!.toString() }
            )
            if (mapping.size < samples.size || mapping.values.any { it == null })
                throw ValidationException("Metadata samples does not match input file names.")
        }
        if (filterByMetadata != null && metadataDf == null)
            throw ValidationException("Filter is specified by metadata is not.")
    }

    private fun plotDestStr(group: IsolationGroup): String {
        val out = out.absolute()
        val ext = out.extension
        val withoutExt = out.parent.resolve(out.nameWithoutExtension)
        return "$withoutExt${group.extension()}.$ext"
    }

    private fun plotDestPath(group: IsolationGroup): Path = Paths.get(plotDestStr(group))

    private fun ensureOutputPathExists() {
        Files.createDirectories(out.toAbsolutePath().parent)
    }

    //    void writePlotsAndSummary(IsolationGroup group, List<byte[]> plots) {
    //        ensureOutputPathExists();
    //        ExportKt.writePDF(plotDestPath(group), plots);
    //    }
    fun writePlots(group: IsolationGroup, plots: List<Plot>) {
        ensureOutputPathExists()
        writeFile(plotDestPath(group), plots)
    }

    fun writePlots(group: IsolationGroup?, plot: Plot) {
        writePlots(group!!, listOf(plot))
    }

    @Command(
        description = ["Export postanalysis plots."],
        synopsisSubcommandLabel = "COMMAND"
    )
    class CommandExportPlotsMain
}
