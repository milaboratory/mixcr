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

import cc.redberry.primitives.Filter
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.milaboratory.cli.POverridesBuilderOps
import com.milaboratory.mixcr.MiXCRCommandDescriptor
import com.milaboratory.mixcr.MiXCRParams
import com.milaboratory.mixcr.MiXCRParamsBundle
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.CloneSet
import com.milaboratory.mixcr.basictypes.CloneSetIO
import com.milaboratory.mixcr.basictypes.tag.TagInfo
import com.milaboratory.mixcr.basictypes.tag.TagType
import com.milaboratory.mixcr.cli.CommonDescriptions.DEFAULT_VALUE_FROM_PRESET
import com.milaboratory.mixcr.cli.CommonDescriptions.Labels
import com.milaboratory.mixcr.export.CloneFieldsExtractorsFactory
import com.milaboratory.mixcr.export.ExportDefaultOptions
import com.milaboratory.mixcr.export.ExportFieldDescription
import com.milaboratory.mixcr.export.InfoWriter
import com.milaboratory.mixcr.export.MetaForExport
import com.milaboratory.mixcr.export.RowMetaForExport
import com.milaboratory.mixcr.util.SubstitutionHelper
import com.milaboratory.util.CanReportProgressAndStage
import com.milaboratory.util.ReportHelper
import com.milaboratory.util.SmartProgressReporter
import io.repseq.core.Chains
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Model
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path
import kotlin.io.path.Path

object CommandExportClones {
    const val COMMAND_NAME = "exportClones"

    @JsonIgnoreProperties("splitByTags")
    data class Params(
        @JsonProperty("splitByTagType") val splitByTagType: TagType?,
        @JsonProperty("filterOutOfFrames") val filterOutOfFrames: Boolean,
        @JsonProperty("filterStops") val filterStops: Boolean,
        @JsonProperty("chains") val chains: String,
        @JsonProperty("noHeader") val noHeader: Boolean,
        @JsonProperty("splitFilesBy") val splitFilesBy: List<String>,
        @JsonProperty("fields") val fields: List<ExportFieldDescription>,
    ) : MiXCRParams {
        override val command get() = MiXCRCommandDescriptor.exportClones
    }

    fun Params.mkFilter(): Filter<Clone> {
        val chains = Chains.parse(chains)
        return Filter {
            if (filterOutOfFrames)
                if (it.isOutOfFrameOrAbsent(GeneFeature.CDR3)) return@Filter false

            if (filterStops)
                for (assemblingFeature in it.parentCloneSet.assemblingFeatures)
                    if (it.containsStopsOrAbsent(assemblingFeature)) return@Filter false

            for (gt in GeneType.VJC_REFERENCE) {
                val bestHit = it.getBestHit(gt)
                if (bestHit != null && chains.intersects(bestHit.gene.chains)) return@Filter true
            }

            false
        }
    }

    abstract class CmdBase : MiXCRCommandWithOutputs(), MiXCRPresetAwareCommand<Params> {
        @Option(
            description = [
                "Limit export to specific chain (e.g. TRA or IGH) (fractions will be recalculated).",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["-c", "--chains"],
            paramLabel = Labels.CHAINS,
            order = OptionsOrder.main + 10_100
        )
        private var chains: String? = null

        @Option(
            description = [
                "Exclude clones with out-of-frame clone sequences (fractions will be recalculated).",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["-o", "--filter-out-of-frames"],
            order = OptionsOrder.main + 10_200
        )
        private var filterOutOfFrames = false

        @Option(
            description = [
                "Exclude sequences containing stop codons (fractions will be recalculated).",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["-t", "--filter-stops"],
            order = OptionsOrder.main + 10_300
        )
        private var filterStops = false

        @Suppress("unused", "UNUSED_PARAMETER")
        @Option(
            description = [
                "Split clones by tag values.",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["--split-by-tag"],
            paramLabel = "<tag>",
            order = OptionsOrder.main + 10_400,
            hidden = true
        )
        fun setSplitByTag(ignored: String) {
            throw ValidationException("`--split-by-tag <tag>` is deprecated, use `--split-by-tags ${Labels.TAG_TYPE}`")
        }

        @Option(
            description = [
                "Split clones by tag type. Will be calculated from export columns if not specified.",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["--split-by-tags"],
            paramLabel = Labels.TAG_TYPE,
            order = OptionsOrder.main + 10_401
        )
        private var splitByTagType: TagType? = null

        @Option(
            description = [
                "Split files by (currently the only supported value is \"geneLabel:reliableChain\" etc... ).",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["--split-files-by"],
            order = OptionsOrder.main + 10_500
        )
        private var splitFilesBy: List<String> = mutableListOf()

        @Option(
            description = ["Don't split files."],
            names = ["--dont-split-files"],
            order = OptionsOrder.main + 10_600
        )
        private var dontSplitFiles = false

        @Mixin
        lateinit var exportDefaults: ExportDefaultOptions

        override val paramsResolver = object : MiXCRParamsResolver<Params>(MiXCRParamsBundle::exportClones) {
            override fun POverridesBuilderOps<Params>.paramsOverrides() {
                Params::chains setIfNotNull chains
                Params::filterOutOfFrames setIfTrue filterOutOfFrames
                Params::filterStops setIfTrue filterStops
                Params::splitByTagType setIfNotNull splitByTagType
                Params::splitFilesBy setIfNotEmpty splitFilesBy
                Params::noHeader setIfTrue exportDefaults.noHeader
                Params::fields updateBy exportDefaults
                if (dontSplitFiles || (chains != null && chains != "ALL"))
                    Params::splitFilesBy setTo emptyList()
            }
        }
    }

    @Command(
        description = ["Export assembled clones into tab delimited file."]
    )
    class Cmd : CmdBase() {
        @Parameters(
            description = ["Path to input file with clones"],
            paramLabel = "data.(clns|clna)",
            index = "0"
        )
        lateinit var inputFile: Path

        @set:Parameters(
            description = [
                "Path where to write export table.",
                "If `--split-files-by` is specified (or was set in preset), than command will write several files `{outputDir}/{outputFileName}.{suffix}.tsv`",
                "Will write to output if omitted."
            ],
            paramLabel = "table.tsv",
            index = "1",
            arity = "0..1"
        )
        var outputFile: Path? = null
            set(value) {
                ValidationException.requireFileType(value, InputFileType.TSV)
                field = value
            }

        @Mixin
        lateinit var exportMixins: ExportMiXCRMixins.CommandSpecific

        override val inputFiles
            get() = listOf(inputFile)

        override val outputFiles
            get() = listOfNotNull(outputFile)

        override fun validate() {
            ValidationException.requireFileType(inputFile, InputFileType.CLNX)
        }

        override fun run0() {
            val initialSet = CloneSetIO.read(inputFile, VDJCLibraryRegistry.getDefault())
            val header = initialSet.header
            val (_, params) = paramsResolver.resolve(
                header.paramsSpec.addMixins(exportMixins.mixins),
                printParameters = logger.verbose && outputFile != null
            )

            // Calculating splitting keys
            val splitFileKeys = params.splitFilesBy
            val splitFileKeyExtractors: List<CloneSplittingKey> = splitFileKeys.map {
                when {
                    it.startsWith("geneLabel:", ignoreCase = true) ->
                        CloneGeneLabelSplittingKey(it.substring(10))

                    it.startsWith("tag:", ignoreCase = true) ->{
                        val tagName = it.substring(4)
                        CloneTagSplittingKey(header.tagsInfo.get(tagName).index)
                    }

                    else ->
                        throw ApplicationException("Unsupported splitting key: $it")
                }
            }

            val headerForExport = MetaForExport(initialSet)
            val fieldExtractors = CloneFieldsExtractorsFactory.createExtractors(params.fields, headerForExport)

            fun runExport(set: CloneSet, outFile: Path?) {
                val rowMetaForExport = RowMetaForExport(set.tagsInfo, headerForExport)
                InfoWriter.create(outFile, fieldExtractors, !params.noHeader) { rowMetaForExport }.use { writer ->
                    val splitByTagType = if (params.splitByTagType != null) {
                        params.splitByTagType
                    } else {
                        val tagsExportedByGroups = params.fields
                            .filter {
                                it.field.equals("-allTags", ignoreCase = true) ||
                                        it.field.equals("-tags", ignoreCase = true)
                            }
                            .map { TagType.valueOfCaseInsensitiveOrNull(it.args[0]) }
                        val newSpitBy = tagsExportedByGroups.maxOrNull()
                        if (newSpitBy != null && outputFile != null) {
                            println("Clone splitting by ${newSpitBy.name} added automatically because -tags ${newSpitBy.name} field is present in the list.")
                        }
                        newSpitBy
                    }

                    val splitByTag = when {
                        splitByTagType == null -> null
                        !header.tagsInfo.hasTagsWithType(splitByTagType) -> {
                            logger.warn("Input has no tags with type $splitByTagType")
                            null
                        }

                        else -> header.tagsInfo
                            .filter { it.type == splitByTagType }
                            .maxBy { it.index }
                    }
                    val exportClones = ExportClones(set, writer, Long.MAX_VALUE, splitByTag)
                    SmartProgressReporter.startProgressReport(exportClones, System.err)
                    exportClones.run()
                    if (initialSet.size() > set.size()) {
                        val initialCount = initialSet.clones.stream().mapToDouble { obj: Clone -> obj.count }
                            .sum()
                        val count = set.clones.stream().mapToDouble { obj: Clone -> obj.count }
                            .sum()
                        val di = initialSet.size() - set.size()
                        val cdi = initialCount - count
                        val percentageDI = ReportHelper.PERCENT_FORMAT.format(100.0 * di / initialSet.size())
                        logger.warn(
                            "Filtered ${set.size()} of ${initialSet.size()} clones ($percentageDI%)."
                        )
                        val percentageCDI = ReportHelper.PERCENT_FORMAT.format(100.0 * cdi / initialCount)
                        logger.warn(
                            "Filtered $count of $initialCount reads ($percentageCDI%)."
                        )
                    }
                }
            }

            if (outputFile == null)
                runExport(CloneSet.transform(initialSet, params.mkFilter()), null)
            else {
                val sFileName = outputFile!!.let { of ->
                    SubstitutionHelper.parseFileName(of.toString(), splitFileKeys.size)
                }

                CloneSet
                    .split(initialSet) { c ->
                        splitFileKeyExtractors.map { it.getLabel(c) }
                    }
                    .forEach { (key, set0) ->
                        val set = CloneSet.transform(set0, params.mkFilter())
                        val fileNameSV = SubstitutionHelper.SubstitutionValues()
                        var i = 1
                        for ((keyValue, keyName) in key.zip(splitFileKeys)) {
                            fileNameSV.add(keyValue, "$i", keyName)
                            i++
                        }
                        val keyString = key.joinToString("_")
                        System.err.println("Exporting $keyString")
                        runExport(set, Path(sFileName.render(fileNameSV)))
                    }
            }
        }

        class ExportClones(
            val clones: CloneSet,
            val writer: InfoWriter<Clone>,
            val limit: Long,
            private val splitByTag: TagInfo?
        ) : CanReportProgressAndStage {
            val size: Long = clones.size().toLong()

            @Volatile
            private var current: Long = 0

            override fun getStage(): String = "Exporting clones"

            override fun getProgress(): Double = 1.0 * current / size

            override fun isFinished(): Boolean = current == size

            fun run() {
                var currentLocal = current
                for (clone in clones.clones) {
                    if (currentLocal == limit) break
                    clone.splitByTag(splitByTag)
                        .forEach { writer.put(it) }
                    ++currentLocal
                    current = currentLocal
                }
            }
        }
    }

    private sealed interface CloneSplittingKey {
        fun getLabel(clone: Clone): String
    }

    private class CloneGeneLabelSplittingKey(private val labelName: String) : CloneSplittingKey {
        override fun getLabel(clone: Clone): String = clone.getGeneLabel(labelName)
    }

    private class CloneTagSplittingKey(private val tagIdx: Int) : CloneSplittingKey {
        override fun getLabel(clone: Clone): String =
            clone.tagFractions.asKeyPrefixOrError(tagIdx + 1).get(tagIdx).toString()
    }

    @JvmStatic
    fun mkSpec(): Model.CommandSpec {
        val cmd = Cmd()
        val spec = Model.CommandSpec.forAnnotatedObject(cmd)
        cmd.spec = spec // inject spec manually
        CloneFieldsExtractorsFactory.addOptionsToSpec(cmd.exportDefaults.addedFields, spec)
        return spec
    }
}
