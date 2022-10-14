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
import com.milaboratory.mixcr.cli.ChainsUtil
import com.milaboratory.mixcr.cli.CommonDescriptions
import com.milaboratory.mixcr.cli.CommonDescriptions.Labels
import com.milaboratory.mixcr.cli.MiXCRCommandWithOutputs
import com.milaboratory.mixcr.cli.ValidationException
import com.milaboratory.mixcr.postanalysis.preproc.ChainsFilter
import com.milaboratory.mixcr.postanalysis.ui.DownsamplingParameters
import com.milaboratory.util.StringUtil
import io.repseq.core.Chains
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readLines

/**
 *
 */
abstract class CommandPa : MiXCRCommandWithOutputs() {
    @Parameters(
        index = "0",
        arity = "2..*",
        paramLabel = "$inputsLabel $outputLabel",
        hideParamSyntax = true,
        //help is covered by mkCommandSpec
        hidden = true
    )
    var inOut: List<Path> = mutableListOf()

    @Option(description = [CommonDescriptions.ONLY_PRODUCTIVE], names = ["--only-productive"])
    var onlyProductive = false

    @Option(description = [CommonDescriptions.DOWNSAMPLING_DROP_OUTLIERS], names = ["--drop-outliers"])
    var dropOutliers = false

    @Option(
        description = [CommonDescriptions.DOWNSAMPLING],
        names = ["--default-downsampling"],
        required = true,
        paramLabel = "<type>"
    )
    lateinit var defaultDownsampling: String

    @Option(
        description = ["Weight function"],
        names = ["--default-weight-function"],
        required = true,
        paramLabel = "<read|TAG>"
    )
    lateinit var defaultWeightFunction: String

    @Option(
        description = ["Limit analysis to specific chains (e.g. TRA or IGH) (fractions will be recalculated). " +
                "Possible values (multiple values allowed): TRA, TRD, TRAD (for human), TRG, IGH, IGK, IGL"],
        names = ["--chains"],
        split = ",",
        paramLabel = Labels.CHAIN
    )
    var chains: Set<String>? = null

    @Option(
        description = [CommonDescriptions.METADATA],
        names = ["--metadata"],
        paramLabel = "<path>"
    )
    var metadataFile: Path? = null

    @Option(
        description = ["Metadata categories used to isolate samples into separate groups"],
        names = ["--group"],
        paramLabel = "<group>"
    )
    var isolationGroups: List<String> = mutableListOf()

    @Option(
        description = ["Tabular results output path (path/table.tsv)."],
        names = ["--tables"],
        paramLabel = "<path>"
    )
    var tablesOut: Path? = null

    @Option(
        description = ["Preprocessor summary output path."],
        names = ["--preproc-tables"],
        paramLabel = "<path>"
    )
    var preprocOut: Path? = null

    @Option(
        names = ["-O"],
        description = ["Overrides default postanalysis settings"],
        paramLabel = Labels.OVERRIDES
    )
    var overrides: Map<String, String> = mutableMapOf()

    override val inputFiles: List<Path>
        get() = inOut.subList(0, inOut.size - 1)
            .flatMap { path ->
                when {
                    path.isDirectory() -> path.listDirectoryEntries()
                    else -> listOf(path)
                }
            }

    override val outputFiles
        get() = listOf(inOut.last())

    protected val tagsInfo: TagsInfo by lazy {
        extractTagsInfo(inputFiles)
    }

    override fun validate() {
        val out = inOut.last()
        if (!out.toString().endsWith(".json") && !out.toString().endsWith(".json.gz"))
            throw ValidationException("Output file name should ends with .json.gz or .json")
        try {
            DownsamplingParameters.parse(defaultDownsampling, tagsInfo, dropOutliers, onlyProductive)
        } catch (t: Throwable) {
            throw ValidationException(t.message ?: t.javaClass.name)
        }
        preprocOut?.let { preprocOut ->
            if (preprocOut.extension !in arrayOf("tsv", "csv"))
                throw ValidationException("--preproc-tables: table name should ends with .csv or .tsv, got $preprocOut")
            if (preprocOut.toString().startsWith("."))
                throw ValidationException("--preproc-tables: cant' start with \".\", got $preprocOut")
        }
        tablesOut?.let { tablesOut ->
            if (tablesOut.extension !in arrayOf("tsv", "csv"))
                throw ValidationException("--tables: table name should ends with .csv or .tsv, got $tablesOut")
            if (tablesOut.toString().startsWith("."))
                throw ValidationException("--tables: cant' start with \".\", got $tablesOut")
        }
        metadataFile?.let { metadataFile ->
            if (metadataFile.extension !in arrayOf("csv", "tsv"))
                throw ValidationException("Metadata should be .csv or .tsv, got $metadataFile")
        }
        val duplicates = inputFiles
            .groupingBy { it }.eachCount()
            .filterValues { it > 1 }
            .keys
        if (duplicates.isNotEmpty())
            throw ValidationException("Duplicated samples detected: ${duplicates.joinToString(",")}")
        metadata?.let { metadata ->
            if (!metadata.containsKey("sample"))
                throw ValidationException("Metadata must contain 'sample' column")
            val samples = inputFiles.map { it.toString() }
            val mapping = StringUtil.matchLists(samples, metadata["sample"]!!.map { it as String })
            if (mapping.size < samples.size || mapping.values.any { it == null }) {
                throw ValidationException("Metadata samples does not match input file names: " + samples
                    .filter { s -> mapping[s] == null }
                    .joinToString(","))
            }
        }
    }

    private fun outBase(): String {
        val out = inOut.last()
        return when {
            out.endsWith(".json.gz") -> out.toString().removeSuffix(".json.gz")
            out.endsWith(".json") -> out.toString().removeSuffix(".json")
            else -> throw IllegalArgumentException("output extension is illegal")
        }
    }

    private fun tablesOut(): Path = tablesOut ?: Paths.get("${outBase()}.tsv")

    private fun preprocOut(): Path = preprocOut ?: Paths.get("${outBase()}.preproc.tsv")

    private fun outputPath(): Path = inOut.last().toAbsolutePath()

    /** Map of columns  */
    protected val metadata: Map<String, List<Any>>? by lazy {
        val metadata = metadataFile ?: return@lazy null
        val content = metadata.toAbsolutePath().readLines()
        if (content.isEmpty()) return@lazy null
        val sep = if (metadata.extension == "csv") "," else "\t"
        val header = content.first().split(sep.toRegex()).dropLastWhile { it.isEmpty() }.map { it.lowercase() }
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
            return listOf(SamplesGroup(inputFiles.map { it.toString() }, emptyMap()))
        }
        val metadata = metadata!!
        val mSamples = metadata["sample"]!!.map { it as String }
        val qSamples = inputFiles
        val sample2meta = StringUtil.matchLists(qSamples.map { it.toString() }, mSamples)
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

    private val chainsToProcess by lazy {
        val availableChains = ChainsUtil.allChainsFromClnx(inputFiles)
        println("The following chains present in the data: $availableChains")
        if (chains == null)
            availableChains
        else
            availableChains.intersect(ChainsFilter.parseChainsList(this.chains))
    }

    override fun run0() {
        val chainsColumn = chainsColumn()
        val results: List<PaResultByGroup> = groupSamples().flatMap { group ->
            val chainsForGroup = when {
                chainsColumn != null -> setOf(Chains.parse(group.group[chainsColumn].toString()))
                else -> chainsToProcess
            }
            chainsForGroup.map {
                run0(IsolationGroup(it, group.group), group.samples)
            }
        }
        val result = PaResult(metadata, isolationGroups, results)
        Files.createDirectories(outputPath().parent)
        result.writeJson(outputPath())

        // export tables & preprocessing summary
        CommandPaExportTables(result, tablesOut()).run0()
        CommandPaExportTablesPreprocSummary(result, preprocOut()).run0()
    }

    private fun run0(group: IsolationGroup, samples: List<String>): PaResultByGroup {
        println("Running for ${group.toString(chainsColumn() == null)}")
        return run(group, samples)
    }

    abstract fun run(group: IsolationGroup, samples: List<String>): PaResultByGroup

    @Command(
        description = ["Run postanalysis routines."],
        synopsisSubcommandLabel = "COMMAND"
    )
    class CommandPostanalysisMain

    companion object {
        fun extractTagsInfo(l: List<Path>, check: Boolean = true): TagsInfo {
            val set = l.map { input -> CloneSetIO.extractTagsInfo(input) }.toSet()
            if (check && set.size != 1)
                throw IllegalArgumentException("Input files have different tags structure")
            return set.iterator().next()
        }

        /** Get sample id from file name  */
        fun getSampleId(file: String): String = Paths.get(file).toAbsolutePath().fileName.toString()

        private val CHAINS_COLUMN_NAMES = arrayOf("chain", "chains")

        private const val inputsLabel = "cloneset.(clns|clna)..."

        private const val outputLabel = "result.json[.gz]"

        fun CommandSpec.addInputsHelp(): CommandSpec =
            addPositional(
                CommandLine.Model.PositionalParamSpec.builder()
                    .index("0")
                    .required(false)
                    .arity("0..*")
                    .type(Path::class.java)
                    .paramLabel(inputsLabel)
                    .hideParamSyntax(true)
                    .description("Paths to input clnx files.")
                    .build()
            )
                .addPositional(
                    CommandLine.Model.PositionalParamSpec.builder()
                        .index("1")
                        .required(false)
                        .arity("0..*")
                        .type(Path::class.java)
                        .paramLabel(outputLabel)
                        .hideParamSyntax(true)
                        .description("Path where to write postanalysis result.")
                        .build()
                )
    }
}
