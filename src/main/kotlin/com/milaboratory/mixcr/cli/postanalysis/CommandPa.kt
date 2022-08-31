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

import com.milaboratory.mixcr.basictypes.CloneSetIO
import com.milaboratory.mixcr.basictypes.tag.TagsInfo
import com.milaboratory.mixcr.cli.CommonDescriptions
import com.milaboratory.mixcr.cli.MiXCRCommand
import com.milaboratory.mixcr.postanalysis.ui.DownsamplingParameters
import com.milaboratory.util.StringUtil
import io.repseq.core.Chains
import picocli.CommandLine
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.stream.Collectors
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readLines

/**
 *
 */
abstract class CommandPa : MiXCRCommand() {
    @CommandLine.Parameters(description = ["cloneset.{clns|clna}... result.json.gz|result.json"], arity = "2..*")
    var inOut: List<String> = mutableListOf()

    @CommandLine.Option(description = [CommonDescriptions.ONLY_PRODUCTIVE], names = ["--only-productive"])
    var onlyProductive = false

    @CommandLine.Option(description = [CommonDescriptions.DOWNSAMPLING_DROP_OUTLIERS], names = ["--drop-outliers"])
    var dropOutliers = false

    @CommandLine.Option(
        description = [CommonDescriptions.DOWNSAMPLING],
        names = ["--default-downsampling"],
        required = true
    )
    lateinit var defaultDownsampling: String

    @CommandLine.Option(
        description = [CommonDescriptions.WEIGHT_FUNCTION],
        names = ["--default-weight-function"],
        required = true
    )
    lateinit var defaultWeightFunction: String

    @CommandLine.Option(description = ["Filter specified chains"], names = ["--chains"])
    var chains = "ALL"

    @CommandLine.Option(description = [CommonDescriptions.METADATA], names = ["--metadata"], paramLabel = "metadata")
    var metadataFile: String? = null

    @CommandLine.Option(
        description = ["Metadata categories used to isolate samples into separate groups"],
        names = ["--group"]
    )
    var isolationGroups: List<String> = mutableListOf()

    @CommandLine.Option(description = ["Tabular results output path (path/table.tsv)."], names = ["--tables"])
    var tablesOut: String? = null

    @CommandLine.Option(description = ["Preprocessor summary output path."], names = ["--preproc-tables"])
    var preprocOut: String? = null

    @CommandLine.Option(names = ["-O"], description = ["Overrides default postanalysis settings"])
    var overrides: Map<String, String> = mutableMapOf()

    override fun getInputFiles(): List<String> = inOut.subList(0, inOut.size - 1)
        .flatMap { file ->
            val path = Paths.get(file)
            when {
                path.isDirectory() -> path.listDirectoryEntries()
                else -> listOf(path)
            }
        }
        .map { it.toString() }

    override fun getOutputFiles(): List<String> = listOf(inOut.last())

    protected val tagsInfo: TagsInfo by lazy {
        extractTagsInfo(inputFiles)
    }

    override fun validate() {
        super.validate()
        val out = inOut.last()
        if (!out.endsWith(".json") && !out.endsWith(".json.gz"))
            throwValidationExceptionKotlin("Output file name should ends with .json.gz or .json")
        try {
            DownsamplingParameters.parse(defaultDownsampling, tagsInfo, dropOutliers, onlyProductive)
        } catch (t: Throwable) {
            throwValidationExceptionKotlin(t.message ?: t.javaClass.name)
        }
        preprocOut?.let { preprocOut ->
            if (!preprocOut.endsWith(".tsv") && !preprocOut.endsWith(".csv"))
                throwValidationExceptionKotlin("--preproc-tables: table name should ends with .csv or .tsv")
            if (preprocOut.startsWith("."))
                throwValidationExceptionKotlin("--preproc-tables: cant' start with \".\"")
        }
        tablesOut?.let { tablesOut ->
            if (!tablesOut.endsWith(".tsv") && !tablesOut.endsWith(".csv"))
                throwValidationExceptionKotlin("--tables: table name should ends with .csv or .tsv")
            if (tablesOut.startsWith("."))
                throwValidationExceptionKotlin("--tables: cant' start with \".\"")
        }
        metadataFile?.let { metadataFile ->
            if (!metadataFile.endsWith(".csv") && !metadataFile.endsWith(".tsv"))
                throwValidationExceptionKotlin("Metadata should be .csv or .tsv")
        }
        val duplicates = inputFiles
            .groupingBy { it }.eachCount()
            .filterValues { it > 1 }
            .keys
        if (duplicates.isNotEmpty())
            throwValidationExceptionKotlin("Duplicated samples detected: ${duplicates.joinToString(",")}")
        metadata?.let { metadata ->
            if (!metadata.containsKey("sample"))
                throwValidationExceptionKotlin("Metadata must contain 'sample' column")
            val samples = inputFiles
            val mapping = StringUtil.matchLists(samples, metadata["sample"]!!.map { it as String })
            if (mapping.size < samples.size || mapping.values.any { it == null }) {
                throwValidationException(
                    "Metadata samples does not match input file names: " + samples.stream()
                        .filter { s: String -> mapping[s] == null }
                        .collect(Collectors.joining(",")))
            }
        }
    }

    private fun outBase(): String {
        val out = inOut.last()
        return when {
            out.endsWith(".json.gz") -> out.dropLast(8)
            out.endsWith(".json") -> out.dropLast(5)
            else -> throw IllegalArgumentException("output extension is illegal")
        }
    }

    private fun tablesOut(): String = tablesOut ?: "${outBase()}.tsv"

    private fun preprocOut(): String = preprocOut ?: "${outBase()}.preproc.tsv"

    private fun outputPath(): Path = Paths.get(inOut.last()).toAbsolutePath()

    /** Map of columns  */
    protected val metadata: Map<String, List<Any>>? by lazy {
        val metadata = metadataFile ?: return@lazy null
        val content = Paths.get(metadata).toAbsolutePath().readLines()
        if (content.isEmpty()) return@lazy null
        val sep = if (metadata.endsWith(".csv")) "," else "\t"
        val header = content.first().split(sep.toRegex()).dropLastWhile { it.isEmpty() }
        val result = mutableMapOf<String, MutableList<String>>()
        for (iRow in 1 until content.size) {
            val row = content[iRow].split(sep.toRegex()).dropLastWhile { it.isEmpty() }
            for (iCol in row.indices) {
                result
                    .computeIfAbsent(header[iCol]) { mutableListOf() }
                    .add(row[iCol])
            }
        }
        result.mapValues { (_, value) ->
            when {
                value.any { !StringUtil.isNumber(it) } -> value
                else -> value.map { it.toDouble() }
            }
        }
    }

    private class SamplesGroup(
        /** sample names  */
        val samples: List<String>,
        /** metadata category = value  */
        val group: Map<String, Any>
    )

    private fun chainsColumn(): String? {
        val metadata = metadata ?: return null
        return metadata.keys
            .firstOrNull { col ->
                CHAINS_COLUMN_NAMES.any { anotherString -> col.equals(anotherString, ignoreCase = true) }
            }
    }

    /** group samples into isolated groups  */
    private fun groupSamples(): List<SamplesGroup> {
        val chainsColumn = chainsColumn()
        if (chainsColumn == null && isolationGroups.isEmpty()) {
            return listOf(SamplesGroup(inputFiles, emptyMap()))
        }
        val metadata = metadata!!
        val mSamples = metadata["sample"]!!.map { it as String }
        val qSamples = inputFiles
        val sample2meta = StringUtil.matchLists(qSamples, mSamples)
        for ((key, value) in sample2meta) {
            requireNotNull(value) { "Malformed metadata: can't find metadata row for sample $key" }
        }
        val meta2sample = sample2meta.entries
            .associate { (key, value) -> value to key }
        val nRows = metadata.values.first().size
        val samplesByGroup = mutableMapOf<Map<String, Any>, MutableList<String>>()
        for (i in 0 until nRows) {
            val group: MutableMap<String, Any> = HashMap()
            for (igr in isolationGroups + listOfNotNull(chainsColumn)) {
                group[igr] = metadata[igr]!![i]
            }
            val sample = metadata["sample"]!![i]
            val value = meta2sample[sample] ?: continue
            samplesByGroup.computeIfAbsent(group) { mutableListOf() }
                .add(value)
        }
        return samplesByGroup.entries
            .map { (key, value) -> SamplesGroup(value, key) }
    }

    override fun run0() {
        val chains = Chains.parse(chains)
        val chainsColumn = chainsColumn()
        val results: List<PaResultByGroup> = groupSamples().flatMap { group ->
            val chainsToExport = when {
                chainsColumn != null ->
                    listOf(Chains.getNamedChains(group.group[chainsColumn].toString().uppercase(Locale.getDefault())))
                else -> Chains.DEFAULT_EXPORT_CHAINS_LIST
            }
            chainsToExport
                .filter { chains.intersects(it.chains) }
                .map { run0(IsolationGroup(it, group.group), group.samples) }
        }
        val result = PaResult(metadata, isolationGroups, results)
        Files.createDirectories(outputPath().parent)
        PaResult.writeJson(outputPath(), result)

        // export tables & preprocessing summary
        CommandPaExportTables(result, tablesOut()).run0()
        CommandPaExportTablesPreprocSummary(result, preprocOut()).run0()
    }

    private fun run0(group: IsolationGroup, samples: List<String>): PaResultByGroup {
        println("Running for " + group.toString(chainsColumn() == null))
        return run(group, samples)
    }

    abstract fun run(group: IsolationGroup, samples: List<String>): PaResultByGroup

    @CommandLine.Command(
        name = "postanalysis",
        separator = " ",
        description = ["Run postanalysis routines."],
        subcommands = [CommandLine.HelpCommand::class]
    )
    class CommandPostanalysisMain

    companion object {
        fun extractTagsInfo(l: List<String>, check: Boolean = true): TagsInfo {
            val set = l.map { `in` -> CloneSetIO.extractTagsInfo(Paths.get(`in`)) }.toSet()
            if (check && set.size != 1)
                throw IllegalArgumentException("Input files have different tags structure")
            return set.iterator().next()
        }

        /** Get sample id from file name  */
        fun getSampleId(file: String): String = Paths.get(file).toAbsolutePath().fileName.toString()

        private val CHAINS_COLUMN_NAMES = arrayOf("chain", "chains")
    }
}
