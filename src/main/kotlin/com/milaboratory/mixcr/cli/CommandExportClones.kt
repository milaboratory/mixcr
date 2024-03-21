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
package com.milaboratory.mixcr.cli

import cc.redberry.pipe.util.asOutputPort
import cc.redberry.pipe.util.drainToAndClose
import cc.redberry.pipe.util.flatMap
import com.milaboratory.app.ApplicationException
import com.milaboratory.app.InputFileType
import com.milaboratory.app.ValidationException
import com.milaboratory.app.logger
import com.milaboratory.cli.POverridesBuilderOps
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.CloneSet
import com.milaboratory.mixcr.basictypes.CloneSet.Companion.divideClonesByTags
import com.milaboratory.mixcr.basictypes.CloneSet.Companion.filter
import com.milaboratory.mixcr.basictypes.CloneSet.Companion.mapWithoutStatsChange
import com.milaboratory.mixcr.basictypes.CloneSet.Companion.split
import com.milaboratory.mixcr.basictypes.CloneSetIO
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.basictypes.MiXCRHeader
import com.milaboratory.mixcr.basictypes.tag.TagType
import com.milaboratory.mixcr.cli.CommonDescriptions.DEFAULT_VALUE_FROM_PRESET
import com.milaboratory.mixcr.cli.CommonDescriptions.Labels
import com.milaboratory.mixcr.clonegrouping.CloneGrouper
import com.milaboratory.mixcr.export.CloneFieldsExtractorsFactory
import com.milaboratory.mixcr.export.InfoWriter
import com.milaboratory.mixcr.export.MetaForExport
import com.milaboratory.mixcr.export.RowMetaForExport
import com.milaboratory.mixcr.presets.AnalyzeCommandDescriptor
import com.milaboratory.mixcr.presets.MiXCRParamsBundle
import com.milaboratory.mixcr.util.SubstitutionHelper
import com.milaboratory.util.ReportHelper
import com.milaboratory.util.withExpectedSize
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
    const val COMMAND_NAME = AnalyzeCommandDescriptor.exportClones.name

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

        for (filterOutCloneGroup in filterOutCloneGroups) {
            val match = when (filterOutCloneGroup) {
                CommandExportClonesParams.CloneGroupTypes.found -> clone.groupIsDefined
                CommandExportClonesParams.CloneGroupTypes.undefined -> clone.group == CloneGrouper.undefinedGroup
                CommandExportClonesParams.CloneGroupTypes.contamination -> clone.group == CloneGrouper.contamination
            }
            if (match) return false
        }

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
                "Split files by. Possible values `chain`, `tag:...`, `tagType:(Sample|Cell|Molecule)`.",
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
        lateinit var exportMixins: ExportMiXCRMixins.CommandSpecific.ExportClones

        override val inputFiles
            get() = listOf(inputFile)

        override val outputFiles
            get() = listOfNotNull(outputFile)

        override fun validate() {
            ValidationException.requireFileType(inputFile, InputFileType.CLNX)
        }

        override fun initialize() {
            if (outputFile == null) {
                logger.redirectSysOutToSysErr()
            }
        }

        override fun run1() {
            val fileInfo = IOUtil.extractFileInfo(inputFile)
            val assemblingFeatures = fileInfo.header.assemblingFeatures!!
            val initialSet = CloneSetIO.read(inputFile, VDJCLibraryRegistry.getDefault())
            val (_, params) = paramsResolver.resolve(
                resetPreset.overridePreset(fileInfo.header.paramsSpec).addMixins(exportMixins.mixins)
            )

            ValidationException.chainsExist(Chains.parse(params.chains), initialSet.usedGenes)
            // Calculating splitting keys
            val splitFileKeys = params.splitFilesBy
            val splitFileKeyExtractors: List<CloneGroupingKey> = splitFileKeys.map {
                parseGroupingKey(fileInfo.header, it)
            }
            val groupByKeyExtractors: List<CloneGroupingKey> = params.groupClonesBy.map {
                parseGroupingKey(fileInfo.header, it)
            }

            val headerForExport = MetaForExport(fileInfo)
            val fieldExtractors = CloneFieldsExtractorsFactory.createExtractors(params.fields, headerForExport)

            val chains = Chains.parse(params.chains)

            val splitByTagType = if (params.splitByTagType != null) {
                params.splitByTagType
            } else {
                val tagTypeWithReason = params.fields
                    .filter {
                        it.field.equals("-allTags", ignoreCase = true) || it.field.equals("-tags", ignoreCase = true)
                    }
                    .map { TagType.valueOfCaseInsensitiveOrNull(it.args[0])!! to "`${it.field} ${it.args[0]}` field is present in the list" }
                    .toMutableList()
                if (params.fields.any { it.field.equals("-cellId", ignoreCase = true) }) {
                    tagTypeWithReason += TagType.Cell to "`-cellId` field is present in the list"
                }
                splitFileKeyExtractors
                    .filterIsInstance<CloneTagGroupingKey>()
                    .filter { it.tagType != null }
                    .forEach { key ->
                        tagTypeWithReason += key.tagType!! to "split files by tag was added"
                    }
                groupByKeyExtractors
                    .filterIsInstance<CloneTagGroupingKey>()
                    .filter { it.tagType != null }
                    .forEach { key ->
                        tagTypeWithReason += key.tagType!! to "group clones by tag was added"
                    }
                val newSpitBy = tagTypeWithReason.maxByOrNull { it.first }
                if (newSpitBy != null && outputFile != null) {
                    logger.log("Clone splitting by ${newSpitBy.first} added automatically because ${newSpitBy.second}.")
                }
                newSpitBy?.first
            }

            val tagDivisionDepth = when (splitByTagType) {
                null -> 0
                else -> {
                    if (!fileInfo.header.tagsInfo.hasTagsWithType(splitByTagType))
                    // best division depth will still be selected for this case
                        logger.warn("Input has no tags with type $splitByTagType")
                    fileInfo.header.tagsInfo.getDepthFor(splitByTagType)
                }
            }

            val requiredSplit = (splitFileKeyExtractors + groupByKeyExtractors)
                .filterIsInstance<CloneTagGroupingKey>()
                .maxOfOrNull { it.tagIdx }
            ValidationException.require(requiredSplit == null || tagDivisionDepth >= requiredSplit + 1) {
                "Splitting of clones by ${initialSet.tagsInfo[requiredSplit!!].name} required in order to split files or group clones. Please add it manually"
            }

            // Dividing clonotypes inside the cloneset into multiple clonotypes each having unique tag prefix
            // according to the provided depth
            val dividedSource = initialSet.divideClonesByTags(tagDivisionDepth)

            fun runExport(set: CloneSet, outFile: Path?) {
                val rowMetaForExport = RowMetaForExport(set.tagsInfo, headerForExport, exportDefaults.notCoveredAsEmpty)
                InfoWriter.create(outFile, fieldExtractors, !params.noHeader) { rowMetaForExport }.use { writer ->
                    // Splitting cloneset into multiple clonesets to calculate fraction characteristics
                    // (like read and tag fractions) relative to the defined clone grouping
                    val setsByGroup = set
                        .split { clone -> groupByKeyExtractors.map { it.getLabel(clone) } }
                        .values
                    setsByGroup.asOutputPort()
                        .flatMap { it.asOutputPort() }
                        .withExpectedSize(setsByGroup.sumOf { it.size() }.toLong())
                        .reportProgress("Exporting clones")
                        .drainToAndClose(writer)

                    if (dividedSource.size() > set.size()) {
                        // clones count
                        logger.log {
                            val initial = initialSet.map { it.id }.distinct().count()
                            val result = set.map { it.id }.distinct().count()
                            val delta = initial - result
                            val percentage = ReportHelper.PERCENT_FORMAT.format(100.0 * delta / initial)
                            "Filtered $result of $initial clones ($percentage%)."
                        }
                        // reads count
                        logger.log {
                            val initial = initialSet.cloneSetInfo.counts.totalCount
                            val result = set.cloneSetInfo.counts.totalCount
                            val delta = initial - result
                            val percentage = ReportHelper.PERCENT_FORMAT.format(100.0 * delta / initial)
                            "Filtered $result of $initial reads ($percentage%)."
                        }
                    }
                }
            }

            val forExport = dividedSource
                .filter { clone -> params.test(clone, assemblingFeatures, chains) }
                .mapWithoutStatsChange { clone -> clone.withResolvedOverlapOfTopHits() }

            if (outputFile == null) {
                runExport(forExport, null)
            } else {
                val sFileName = outputFile!!.let { of ->
                    SubstitutionHelper.parseFileName(of.toString(), splitFileKeyExtractors.size)
                }

                forExport
                    .split { clone -> splitFileKeyExtractors.map { it.getLabel(clone) } }
                    .forEach { (labels, cloneSet) ->
                        val fileNameSV = SubstitutionHelper.SubstitutionValues()
                        var i = 1
                        for ((keyValue, keyName) in labels.zip(splitFileKeys)) {
                            fileNameSV.add(keyValue, "$i", keyName)
                            i++
                        }
                        if (labels.isNotEmpty()) {
                            val keyString = labels.joinToString("_")
                            logger.progress { "Exporting $keyString" }
                        }
                        runExport(cloneSet, Path(sFileName.render(fileNameSV)))
                    }

                // in case of empty set, can't split, so can't calculate file names.
                if (initialSet.size() == 0) {
                    // write empty file with headers (if headers are requested)
                    val writer = InfoWriter.create(outputFile, fieldExtractors, !params.noHeader) {
                        RowMetaForExport(initialSet.tagsInfo, headerForExport, exportDefaults.notCoveredAsEmpty)
                    }
                    writer.close()
                }
            }
        }
    }

    private sealed interface CloneGroupingKey {
        fun getLabel(clone: Clone): String
    }

    private class CloneGeneLabelGroupingKey(private val labelName: String) : CloneGroupingKey {
        override fun getLabel(clone: Clone): String = clone.getGeneLabel(labelName)!!
    }

    private object ChainGroupingKey : CloneGroupingKey {
        override fun getLabel(clone: Clone): String = clone.chains.toString()
    }

    private class CloneTagGroupingKey(
        val tagIdx: Int,
        val tagType: TagType?
    ) : CloneGroupingKey {
        override fun getLabel(clone: Clone): String =
            clone.tagCount.asKeyPrefixOrError(tagIdx + 1).get(tagIdx).toString()
    }

    private fun parseGroupingKey(header: MiXCRHeader, key: String) =
        when {
            key == "chain" -> ChainGroupingKey
            key.startsWith("geneLabel:", ignoreCase = true) ->
                CloneGeneLabelGroupingKey(key.substring(10))

            key.startsWith("tag:", ignoreCase = true) -> {
                val tagName = key.substring(4)
                val tag = header.tagsInfo[tagName]
                ValidationException.requireNotNull(tag) {
                    "No tag `$tagName` in a file"
                }
                val tagType = when (tag.index) {
                    // If splitting by this tag means the same as splitting by tag type
                    header.tagsInfo.getDepthFor(tag.type) - 1 -> tag.type
                    else -> null
                }
                CloneTagGroupingKey(tag.index, tagType)
            }

            key.startsWith("tagType:", ignoreCase = true) -> {
                val tagType = TagType.valueOfCaseInsensitiveOrNull(key.substring(8))
                ValidationException.requireNotNull(tagType) {
                    "Unknown tag type `$key`"
                }
                ValidationException.require(header.tagsInfo.hasTagsWithType(tagType)) {
                    "No tag type `$tagType` in a file"
                }
                val depth = header.tagsInfo.getDepthFor(tagType)
                CloneTagGroupingKey(depth - 1, tagType)
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
