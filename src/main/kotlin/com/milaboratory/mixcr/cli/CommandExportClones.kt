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

import com.milaboratory.app.ApplicationException
import com.milaboratory.app.InputFileType
import com.milaboratory.app.ValidationException
import com.milaboratory.app.logger
import com.milaboratory.cli.POverridesBuilderOps
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.CloneSet
import com.milaboratory.mixcr.basictypes.CloneSet.Companion.divideClonesByTags
import com.milaboratory.mixcr.basictypes.CloneSet.Companion.filter
import com.milaboratory.mixcr.basictypes.CloneSet.Companion.split
import com.milaboratory.mixcr.basictypes.CloneSetIO
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.basictypes.MiXCRHeader
import com.milaboratory.mixcr.basictypes.tag.TagType
import com.milaboratory.mixcr.cli.CommonDescriptions.DEFAULT_VALUE_FROM_PRESET
import com.milaboratory.mixcr.cli.CommonDescriptions.Labels
import com.milaboratory.mixcr.export.CloneFieldsExtractorsFactory
import com.milaboratory.mixcr.export.InfoWriter
import com.milaboratory.mixcr.export.MetaForExport
import com.milaboratory.mixcr.export.RowMetaForExport
import com.milaboratory.mixcr.presets.MiXCRCommandDescriptor
import com.milaboratory.mixcr.presets.MiXCRParamsBundle
import com.milaboratory.mixcr.util.SubstitutionHelper
import com.milaboratory.util.CanReportProgressAndStage
import com.milaboratory.util.ReportHelper
import com.milaboratory.util.SmartProgressReporter
import io.repseq.core.Chains
import io.repseq.core.GeneFeature
import io.repseq.core.GeneFeature.CDR3
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
    const val COMMAND_NAME = MiXCRCommandDescriptor.exportClones.name

    private fun CommandExportClonesParams.test(
        clone: Clone,
        assemblingFeatures: Array<GeneFeature>,
        chains: Chains
    ): Boolean {
        if (filterOutOfFrames)
            if (clone.isOutOfFrameOrAbsent(CDR3)) return false

        if (filterStops)
            for (assemblingFeature in assemblingFeatures)
                if (clone.containsStopsOrAbsent(assemblingFeature)) return false

        if (chains == Chains.ALL)
            return true

        return GeneType.VJC_REFERENCE.any {
            val hit = clone.getBestHit(it)
            hit != null && chains.intersects(hit.gene.chains)
        }
    }

    abstract class CmdBase : MiXCRCommandWithOutputs(), MiXCRPresetAwareCommand<CommandExportClonesParams> {
        @Option(
            description = [
                "Limit export to specific chain (e.g. TRA or IGH) (fractions will be recalculated).",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["-c", "--chains"],
            paramLabel = Labels.CHAINS,
            order = OptionsOrder.main + 10_100,
            completionCandidates = ChainsCandidates::class
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

        @Mixin
        lateinit var resetPreset: ResetPresetOptions


        override val paramsResolver =
            object : MiXCRParamsResolver<CommandExportClonesParams>(MiXCRParamsBundle::exportClones) {
                override fun POverridesBuilderOps<CommandExportClonesParams>.paramsOverrides() {
                    CommandExportClonesParams::chains setIfNotNull chains
                    CommandExportClonesParams::filterOutOfFrames setIfTrue filterOutOfFrames
                    CommandExportClonesParams::filterStops setIfTrue filterStops
                    CommandExportClonesParams::splitByTagType setIfNotNull splitByTagType
                    CommandExportClonesParams::splitFilesBy setIfNotEmpty splitFilesBy
                    CommandExportClonesParams::noHeader setIfTrue exportDefaults.noHeader
                    CommandExportClonesParams::fields updateBy exportDefaults
                    if (dontSplitFiles || (chains != null && chains != "ALL"))
                        CommandExportClonesParams::splitFilesBy setTo emptyList()
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
        lateinit var exportMixins: ExportMiXCRMixins.CommandSpecificExportClones

        override val inputFiles
            get() = listOf(inputFile)

        override val outputFiles
            get() = listOfNotNull(outputFile)

        override fun validate() {
            ValidationException.requireFileType(inputFile, InputFileType.CLNX)
        }

        override fun run1() {
            val assemblingFeatures = IOUtil.extractHeader(inputFile).assemblerParameters!!.assemblingFeatures
            val initialSet = CloneSetIO.read(inputFile, VDJCLibraryRegistry.getDefault())
            val header = initialSet.header
            val (_, params) = paramsResolver.resolve(
                resetPreset.overridePreset(header.paramsSpec).addMixins(exportMixins.mixins),
                printParameters = logger.verbose && outputFile != null
            )

            ValidationException.chainsExist(Chains.parse(params.chains), initialSet.usedGenes)
            // Calculating splitting keys
            val splitFileKeys = params.splitFilesBy
            val splitFileKeyExtractors: List<CloneGroupingKey> = splitFileKeys.map { parseGroupingKey(header, it) }
            val groupByKeyExtractors: List<CloneGroupingKey> = params.groupClonesBy.map { parseGroupingKey(header, it) }

            val headerForExport = MetaForExport(initialSet)
            val fieldExtractors = CloneFieldsExtractorsFactory.createExtractors(params.fields, headerForExport)

            fun runExport(set: CloneSet, outFile: Path?) {
                val rowMetaForExport = RowMetaForExport(set.tagsInfo, headerForExport, exportDefaults.notCoveredAsEmpty)
                InfoWriter.create(outFile, fieldExtractors, !params.noHeader) { rowMetaForExport }.use { writer ->
                    val splitByTagType = if (params.splitByTagType != null) {
                        params.splitByTagType
                    } else {
                        var tagsExportedByGroups = params.fields
                            .filter {
                                it.field.equals("-allTags", ignoreCase = true) ||
                                        it.field.equals("-tags", ignoreCase = true)
                            }
                            .map { TagType.valueOfCaseInsensitiveOrNull(it.args[0])!! }
                        if (params.fields.any { it.field.equals("-cellId", ignoreCase = true) }) {
                            tagsExportedByGroups = tagsExportedByGroups + TagType.Cell
                        }
                        val newSpitBy = tagsExportedByGroups.maxOrNull()
                        if (newSpitBy != null && outputFile != null) {
                            println("Clone splitting by ${newSpitBy.name} added automatically because -tags ${newSpitBy.name} field is present in the list.")
                        }
                        newSpitBy
                    }

                    val tagDivisionDepth = when (splitByTagType) {
                        null -> 0
                        else -> {
                            if (!header.tagsInfo.hasTagsWithType(splitByTagType))
                            // best division depth will still be selected for this case
                                logger.warn("Input has no tags with type $splitByTagType")
                            header.tagsInfo.getDepthFor(splitByTagType)
                        }
                    }

                    // Dividing clonotypes inside the cloneset into multiple clonotypes each having unique tag prefix
                    // according to the provided depth
                    val dividedSet = set.divideClonesByTags(tagDivisionDepth)
                    // Splitting cloneset into multiple clonesets to calculate fraction characteristics
                    // (like read and tag fractions) relative to the defined clone grouping
                    val setsByGroup = dividedSet
                        .split { clone -> groupByKeyExtractors.map { it.getLabel(clone) } }
                        .values
                    val exportClones = ExportClones(setsByGroup, writer, Long.MAX_VALUE)
                    SmartProgressReporter.startProgressReport(exportClones, System.err)
                    exportClones.run()
                    if (initialSet.size() > set.size()) {
                        val initialCount = initialSet.clones.sumOf { obj: Clone -> obj.count }
                        val count = set.clones.sumOf { obj: Clone -> obj.count }
                        val di = initialSet.size() - set.size()
                        val cdi = initialCount - count
                        val percentageDI = ReportHelper.PERCENT_FORMAT.format(100.0 * di / initialSet.size())
                        logger.warnUnfomatted(
                            "Filtered ${set.size()} of ${initialSet.size()} clones ($percentageDI%)."
                        )
                        val percentageCDI = ReportHelper.PERCENT_FORMAT.format(100.0 * cdi / initialCount)
                        logger.warnUnfomatted(
                            "Filtered $count of $initialCount reads ($percentageCDI%)."
                        )
                    }
                }
            }

            val chains = Chains.parse(params.chains)
            if (outputFile == null) {
                runExport(
                    initialSet.filter { clone ->
                        params.test(clone, assemblingFeatures, chains)
                    },
                    null
                )
            } else {
                val sFileName = outputFile!!.let { of ->
                    SubstitutionHelper.parseFileName(of.toString(), splitFileKeyExtractors.size)
                }

                initialSet
                    .split { clone -> splitFileKeyExtractors.map { it.getLabel(clone) } }
                    .forEach { (labels, cloneSet) ->
                        val set = cloneSet.filter { clone ->
                            params.test(clone, assemblingFeatures, chains)
                        }
                        val fileNameSV = SubstitutionHelper.SubstitutionValues()
                        var i = 1
                        for ((keyValue, keyName) in labels.zip(splitFileKeys)) {
                            fileNameSV.add(keyValue, "$i", keyName)
                            i++
                        }
                        val keyString = labels.joinToString("_")
                        System.err.println("Exporting $keyString")
                        runExport(set, Path(sFileName.render(fileNameSV)))
                    }
            }
        }

        class ExportClones(
            private val cloneSets: Collection<CloneSet>,
            private val writer: InfoWriter<Clone>,
            private val limit: Long
        ) : CanReportProgressAndStage {
            val size: Long = cloneSets.sumOf { it.size().toLong() }

            @Volatile
            private var current: Long = 0

            override fun getStage(): String = "Exporting clones"

            override fun getProgress(): Double = 1.0 * current / size

            override fun isFinished(): Boolean = current == size

            fun run() {
                check(current == 0L)
                var currentLocal = 0L
                for (cloneSet in cloneSets)
                    for (clone in cloneSet) {
                        if (currentLocal == limit) break
                        writer.put(clone)
                        ++currentLocal
                        current = currentLocal
                    }
            }
        }
    }

    private sealed interface CloneGroupingKey {
        fun getLabel(clone: Clone): String
    }

    private class CloneGeneLabelGroupingKey(private val labelName: String) : CloneGroupingKey {
        override fun getLabel(clone: Clone): String = clone.getGeneLabel(labelName)
    }

    private class CloneTagGroupingKey(private val tagIdx: Int) : CloneGroupingKey {
        override fun getLabel(clone: Clone): String =
            clone.tagFractions!!.asKeyPrefixOrError(tagIdx + 1).get(tagIdx).toString()
    }

    private fun parseGroupingKey(header: MiXCRHeader, key: String) =
        when {
            key.startsWith("geneLabel:", ignoreCase = true) ->
                CloneGeneLabelGroupingKey(key.substring(10))

            key.startsWith("tag:", ignoreCase = true) -> {
                val tagName = key.substring(4)
                CloneTagGroupingKey(header.tagsInfo.get(tagName).index)
            }

            else -> throw ApplicationException("Unsupported splitting key: $key")
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
