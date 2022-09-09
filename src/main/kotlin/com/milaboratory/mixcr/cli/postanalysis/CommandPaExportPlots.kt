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
package com.milaboratory.mixcr.cli.postanalysis

import com.milaboratory.miplots.ExportType.Companion.determine
import com.milaboratory.miplots.writeFile
import com.milaboratory.mixcr.cli.CommonDescriptions
import com.milaboratory.mixcr.postanalysis.plots.parseFilter
import com.milaboratory.mixcr.postanalysis.plots.readMetadata
import com.milaboratory.util.StringUtil
import jetbrains.letsPlot.intern.Plot
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.toDataFrame
import picocli.CommandLine
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

@CommandLine.Command(name = "exportPlots", separator = " ", description = ["Export postanalysis plots."])
abstract class CommandPaExportPlots : CommandPaExport() {
    @CommandLine.Option(description = [CommonDescriptions.METADATA], names = ["--metadata"])
    var metadata: String? = null

    @CommandLine.Option(description = ["Plot width"], names = ["--width"])
    var width = 0

    @CommandLine.Option(description = ["Plot height"], names = ["--height"])
    var height = 0

    @CommandLine.Option(
        description = ["Filter by metadata. Possible filters column=value, column>=value etc."],
        names = ["--filter"],
        split = ","
    )
    var filterByMetadata: List<String>? = null

    @CommandLine.Parameters(description = ["Output PDF/EPS/PNG/JPEG file name"], index = "1")
    lateinit var out: String

    override fun getOutputFiles(): List<String> = emptyList() // output will be always overriden

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
            metadata != null -> readMetadata(metadata)
            else -> getPaResult().metadata?.toDataFrame()
        }
    }

    override fun validate() {
        super.validate()
        try {
            determine(Paths.get(out))
        } catch (e: Exception) {
            throwValidationExceptionKotlin("Unsupported file extension (possible: pdf, eps, svg, png): $out")
        }
        metadata?.let { metadata ->
            if (!metadata.endsWith(".csv") && !metadata.endsWith(".tsv"))
                throwValidationExceptionKotlin("Metadata should be .csv or .tsv")

            if (!metadataDf!!.containsColumn("sample"))
                throwValidationExceptionKotlin("Metadata must contain 'sample' column")
            val samples = inputFiles
            val mapping = StringUtil.matchLists(
                samples,
                metadataDf!!["sample"].toList().map { it!!.toString() }
            )
            if (mapping.size < samples.size || mapping.values.any { it == null })
                throwValidationExceptionKotlin("Metadata samples does not match input file names.")
        }
        if (filterByMetadata != null && metadataDf == null)
            throwValidationExceptionKotlin("Filter is specified by metadata is not.")
    }

    private fun plotDestStr(group: IsolationGroup): String {
        val out = Path(out)
        val ext = out.extension
        val withoutExt = out.nameWithoutExtension
        return "$withoutExt.${group.extension()}.$ext"
    }

    private fun plotDestPath(group: IsolationGroup): Path = Paths.get(plotDestStr(group))

    private fun ensureOutputPathExists() {
        Files.createDirectories(Paths.get(out).toAbsolutePath().parent)
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

    @CommandLine.Command(
        name = "exportPlots",
        separator = " ",
        description = ["Export postanalysis plots."],
        subcommands = [CommandLine.HelpCommand::class]
    )
    class CommandExportPlotsMain
}
