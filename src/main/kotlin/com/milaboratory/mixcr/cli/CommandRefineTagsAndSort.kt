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

import cc.redberry.pipe.OutputPort
import cc.redberry.pipe.util.forEach
import cc.redberry.pipe.util.onEach
import com.milaboratory.app.ApplicationException
import com.milaboratory.app.InputFileType
import com.milaboratory.app.ValidationException
import com.milaboratory.app.logger
import com.milaboratory.cli.POverridesBuilderOps
import com.milaboratory.core.sequence.NSequenceWithQuality
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.core.sequence.ShortSequenceSet
import com.milaboratory.mitool.data.CriticalThresholdKey
import com.milaboratory.mitool.refinement.TagCorrectionPlan
import com.milaboratory.mitool.refinement.TagCorrectionReport
import com.milaboratory.mitool.refinement.TagCorrector
import com.milaboratory.mitool.refinement.TagCorrectorParameters
import com.milaboratory.mitool.refinement.gfilter.SequenceExtractor
import com.milaboratory.mitool.refinement.gfilter.SequenceExtractorsFactory
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.basictypes.VDJCAlignments
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter
import com.milaboratory.mixcr.basictypes.tag.SequenceAndQualityTagValue
import com.milaboratory.mixcr.basictypes.tag.SequenceTagValue
import com.milaboratory.mixcr.basictypes.tag.TagCount
import com.milaboratory.mixcr.basictypes.tag.TagTuple
import com.milaboratory.mixcr.basictypes.tag.TagValue
import com.milaboratory.mixcr.basictypes.tag.TagValueType
import com.milaboratory.mixcr.basictypes.tag.tagAliases
import com.milaboratory.mixcr.cli.CommonDescriptions.DEFAULT_VALUE_FROM_PRESET
import com.milaboratory.mixcr.cli.CommonDescriptions.Labels
import com.milaboratory.mixcr.cli.MiXCRMixinCollection.Companion.mixins
import com.milaboratory.mixcr.presets.AnalyzeCommandDescriptor
import com.milaboratory.mixcr.presets.MiXCRParamsBundle
import com.milaboratory.mixcr.presets.RefineTagsAndSortMixins.DontCorrectTagName
import com.milaboratory.mixcr.presets.RefineTagsAndSortMixins.DontCorrectTagType
import com.milaboratory.mixcr.presets.RefineTagsAndSortMixins.SetWhitelist
import com.milaboratory.mixcr.util.MiXCRVersionInfo
import com.milaboratory.primitivio.PrimitivIOStateBuilder
import com.milaboratory.util.ComparatorWithHash
import com.milaboratory.util.OutputPortWithProgress
import com.milaboratory.util.ReportHelper
import com.milaboratory.util.SmartProgressReporter
import com.milaboratory.util.TempFileManager
import com.milaboratory.util.sortByHashOnDisk
import org.apache.commons.io.FileUtils
import picocli.CommandLine.ArgGroup
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path
import java.util.*
import java.util.concurrent.atomic.AtomicLong

object CommandRefineTagsAndSort {
    const val COMMAND_NAME = AnalyzeCommandDescriptor.refineTagsAndSort.name

    abstract class CmdBase : MiXCRCommandWithOutputs(), MiXCRPresetAwareCommand<CommandRefineTagsAndSortParams> {
        @Option(
            description = [
                "Don't correct barcodes, only sort alignments by tags.",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["--dont-correct"],
            hidden = true,
            order = OptionsOrder.main + 10_000
        )
        private var dontCorrect: Boolean = false
            set(value) {
                if (value)
                    logger.warn { "`--dont-correct` is deprecated, use `${DontCorrectTagName.CMD_OPTION} ${Labels.TAG_NAME}` or `${DontCorrectTagType.CMD_OPTION} ${Labels.TAG_TYPE}`" }
                field = value
            }

        @Option(
            description = [
                "This parameter determines how thorough the procedure should eliminate variants looking like errors.",
                "Smaller value leave less erroneous variants at the cost of accidentally correcting true variants.",
                "This value approximates the fraction of erroneous variants the algorithm will miss (type II errors).",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["-p", "--power"],
            paramLabel = "<d>",
            order = OptionsOrder.main + 10_100
        )
        private var power: Double? = null

        @Option(
            description = [
                "Expected background non-sequencing-related substitution rate.",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["-s", "--substitution-rate"],
            paramLabel = "<d>",
            order = OptionsOrder.main + 10_200
        )
        private var backgroundSubstitutionRate: Double? = null

        @Option(
            description = [
                "Expected background non-sequencing-related indel rate.",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["-i", "--indel-rate"],
            paramLabel = "<d>",
            order = OptionsOrder.main + 10_300
        )
        private var backgroundIndelRate: Double? = null

        @Option(
            description = [
                "Minimal quality score for the tag.",
                "Tags having positions with lower quality score will be discarded, if not corrected.",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["-q", "--min-quality"],
            paramLabel = "<n>",
            order = OptionsOrder.main + 10_400
        )
        private var minQuality: Int? = null

        @Option(
            description = [
                "Maximal number of substitutions to search for.",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["--max-substitutions"],
            paramLabel = "<n>",
            order = OptionsOrder.main + 10_500
        )
        private var maxSubstitutions: Int? = null

        @Option(
            description = [
                "Maximal number of indels to search for.",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["--max-indels"],
            paramLabel = "<n>",
            order = OptionsOrder.main + 10_600
        )
        private var maxIndels: Int? = null

        @Option(
            description = [
                "Maximal number of substitutions and indels combined to search for.",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["--max-errors"],
            paramLabel = "<n>",
            order = OptionsOrder.main + 10_700
        )
        private var maxTotalErrors: Int? = null

        @Suppress("unused", "UNUSED_PARAMETER")
        @Option(
            names = ["-w", "--whitelist"],
            description = ["Deprecated"],
            paramLabel = "<tag=value>",
            hidden = true
        )
        fun whitelist(map: Map<String, String>) {
            throw ValidationException("\"-w\" and \"--whitelist\" options are deprecated, please use ${SetWhitelist.CMD_OPTION_SET} instead.")
        }

        override val paramsResolver =
            object : MiXCRParamsResolver<CommandRefineTagsAndSortParams>(MiXCRParamsBundle::refineTagsAndSort) {
                override fun POverridesBuilderOps<CommandRefineTagsAndSortParams>.paramsOverrides() {
                    CommandRefineTagsAndSortParams::runCorrection resetIfTrue dontCorrect

                    @Suppress("DuplicatedCode")
                    CommandRefineTagsAndSortParams::parameters.update {
                        TagCorrectorParameters::correctionPower setIfNotNull power
                        TagCorrectorParameters::backgroundSubstitutionRate setIfNotNull backgroundSubstitutionRate
                        TagCorrectorParameters::backgroundIndelRate setIfNotNull backgroundIndelRate
                        TagCorrectorParameters::minQuality setIfNotNull minQuality
                        TagCorrectorParameters::maxSubstitutions setIfNotNull maxSubstitutions
                        TagCorrectorParameters::maxIndels setIfNotNull maxIndels
                        TagCorrectorParameters::maxTotalErrors setIfNotNull maxTotalErrors
                    }
                }
            }
    }

    @Command(
        description = ["Applies error correction algorithm for tag sequences and sorts resulting file by tags."]
    )
    class Cmd : CmdBase() {
        @Parameters(
            description = ["Path to input alignments"],
            paramLabel = "alignments.vdjca",
            index = "0"
        )
        lateinit var inputFile: Path

        @Parameters(
            description = ["Path where to write corrected alignments"],
            paramLabel = "alignments.corrected.vdjca",
            index = "1"
        )
        lateinit var outputFile: Path

        @Mixin
        lateinit var useLocalTemp: UseLocalTempOption

        private val tempDest by lazy {
            TempFileManager.smartTempDestination(outputFile, "", !useLocalTemp.value)
        }

        @Option(
            description = ["Memory budget in bytes. Default: 4Gb"],
            names = ["--memory-budget"],
            paramLabel = "<n>",
            order = OptionsOrder.main + 10_900
        )
        var memoryBudget = 4 * FileUtils.ONE_GB

        @Mixin
        lateinit var reportOptions: ReportOptions

        @ArgGroup(
            validate = false,
            multiplicity = "0..*",
            order = OptionsOrder.mixins.refineTagsAndSort
        )
        var refineAndSortMixins: List<RefineTagsAndSortMiXCRMixins> = mutableListOf()

        @Mixin
        lateinit var resetPreset: ResetPresetOptions

        @Mixin
        lateinit var dontSavePresetOption: DontSavePresetOption

        private val mixins get() = refineAndSortMixins.mixins

        override val inputFiles
            get() = listOf(inputFile)

        override val outputFiles
            get() = listOf(outputFile)

        override fun validate() {
            ValidationException.requireFileType(inputFile, InputFileType.VDJCA)
            ValidationException.requireFileType(outputFile, InputFileType.VDJCA)
        }

        override fun run1() {
            val startTimeMillis = System.currentTimeMillis()

            val refineTagsAndSortReport: RefineTagsAndSortReport
            val mitoolReport: TagCorrectionReport?
            var thresholds: Map<CriticalThresholdKey, Double> = emptyMap()
            VDJCAlignmentsReader(inputFile).use { mainReader ->
                val header = mainReader.header
                val tagsInfo = header.tagsInfo
                require(!tagsInfo.hasNoTags()) { "input file has no tags" }
                val paramsSpec = resetPreset.overridePreset(header.paramsSpec).addMixins(mixins)
                val cmdParams = paramsResolver.resolve(paramsSpec).second

                // These tags will be corrected, other used as grouping keys
                val correctionEnabled = if (!cmdParams.runCorrection) {
                    tagsInfo.map { false }
                } else {
                    tagsInfo.map { tag ->
                        tag.valueType == TagValueType.SequenceAndQuality
                                && tag.name !in cmdParams.dontCorrectTagsNames
                                && tag.type !in cmdParams.dontCorrectTagsTypes
                    }
                }

                // All tag names
                val tagNames = tagsInfo.map { it.name }

                if (cmdParams.runCorrection && correctionEnabled.isNotEmpty())
                    logger.log {
                        "Correction will be applied to the following tags: " +
                                correctionEnabled
                                    .mapIndexedNotNull { i, b -> tagNames[i].takeIf { b } }
                                    .joinToString(", ")
                    }
                logger.log { "Sorting will be applied to the following tags: ${tagNames.joinToString(", ")}" }

                val corrected = when {
                    correctionEnabled.none { true } && cmdParams.whitelists.isEmpty() && cmdParams.parameters?.postFilter == null -> {
                        mitoolReport = null
                        mainReader.reportProgress("Sorting alignments by ${tagNames.last()}")
                    }

                    else -> {
                        // Running correction
                        val whitelists = mutableMapOf<Int, ShortSequenceSet>()
                        tagNames.forEachIndexed { i, tn ->
                            val t = cmdParams.whitelists[tn]
                            if (t != null) {
                                logger.log { "The following whitelist will be used for $tn: $t" }
                                whitelists[i] = t.load()
                            }
                        }

                        if (cmdParams.parameters == null)
                            throw ValidationException("No correction parameters provided.")

                        val correctionPlan = TagCorrectionPlan(
                            tagNames,
                            tagNames.indices.map { i ->
                                when {
                                    correctionEnabled[i] -> NSequenceWithQuality::class.java// Sequence&quality tags will be unwrapped for correction
                                    tagsInfo[i].valueType == TagValueType.NonSequence -> TagValue::class.java // Other tags will be left unchanged to be used as grouping keys
                                    // for usage of a whitelist nucleotide sequence is needed
                                    else -> NucleotideSequence::class.java
                                }
                            },
                            whitelists,
                            // For now all sequence&quality tags are corrected,
                            // more flexibility will be added in the future
                            correctionEnabled,
                            // Tag aliases for each specified tag type and alike
                            tagsInfo.tagAliases,
                            caseInsensitiveTagAliases = true
                        )

                        val corrector = TagCorrector(
                            cmdParams.parameters!!,
                            correctionPlan,
                            tempDest.addSuffix("tags"),
                            memoryBudget,
                            4, 4
                        )

                        SmartProgressReporter.startProgressReport(corrector)

                        // Will read the input stream once and extract all the required information from it
                        corrector.initialize(
                            mainReader,
                            AlignmentSequenceExtractor,
                            { al -> al.alignmentsIndex },
                            weightExtractor = { al -> al.weight }
                        ) { als ->
                            if (als.tagCount.size() != 1) throw ApplicationException(
                                "This procedure don't support aggregated tags. " +
                                        "Please run tag correction for *.vdjca files produced by 'align'."
                            )
                            val tagTuple = als.tagCount.singletonTuple
                            Array(tagNames.size) { tIdx -> // <- local index for the procedure
                                val tagValue = tagTuple[tIdx]
                                when {
                                    correctionEnabled[tIdx] -> (tagValue as SequenceAndQualityTagValue).data
                                    else -> when (val key = tagValue.extractKey()) {
                                        is SequenceTagValue -> key.value // actual sequence
                                        else -> key// converting any tag type to a key tag
                                    }
                                }
                            }
                        }

                        // Running correction, results are temporarily persisted in temp file, so the object can be used
                        // multiple time to perform the correction and stream corrected and filtered results
                        try {
                            corrector.calculate()
                        } catch (e: Throwable) {
                            throw TagCorrectionError(
                                correctionEnabled.mapIndexedNotNull { i, b -> tagNames[i].takeIf { b } }, e
                            )
                        }

                        // Available after calculation finishes
                        mitoolReport = corrector.report
                        thresholds = corrector.criticalThresholds

                        // Creating another alignment stream from the same container file to apply correction

                        corrector.applyCorrection(
                            mainReader.readAlignments(),
                            { al -> al.alignmentsIndex }
                        ) { al, newTagValues ->
                            // starting off the copy of original alignment tags array
                            val updatedTags = al.tagCount.singletonTuple.asArray()
                            tagNames.indices.forEach { tIdx ->
                                if (correctionEnabled[tIdx])
                                    updatedTags[tIdx] =
                                        SequenceAndQualityTagValue(newTagValues[tIdx] as NSequenceWithQuality)
                            }
                            // Applying updated tags values and returning updated alignments object
                            al.withTagCount(TagCount(TagTuple(*updatedTags), al.tagCount.singletonCount))
                        }
                            .reportProgress("Applying correction & sorting alignments by ${tagNames.last()}")
                    }
                }

                // Sorting

                VDJCAlignmentsWriter(outputFile).use { writer ->
                    val alPioState = PrimitivIOStateBuilder()
                    IOUtil.registerGeneReferences(alPioState, mainReader.usedGenes, mainReader.header)

                    // Reusable routine to perform hash-based soring of alignments by tag with specific index
                    var sorterCounter = 0
                    // TODO sortByHashOnDiskHierarchically?
                    val hashSort: OutputPort<VDJCAlignments>.(tagIdx: Int) -> OutputPortWithProgress<VDJCAlignments> =
                        { tIdx -> // <- index inside the alignment object
                            sortByHashOnDisk(
                                ComparatorWithHash.compareBy { al ->
                                    val tagTuple = al.tagCount.singletonTuple
                                    tagTuple[tIdx].extractKey()
                                },
                                tempDest,
                                "hashsorter.${sorterCounter++}.$tIdx",
                                stateBuilder = alPioState,
                                objectSizeInitialGuess = 10_000,
                                memoryBudget = memoryBudget
                            )
                        }

                    // reads count after filtering
                    val resultReadsCount = AtomicLong()
                    // Running initial hash sorter
                    var sorted = corrected
                        .onEach { al -> resultReadsCount.addAndGet(al.weight.toLong()) }
                        .hashSort(tagNames.size - 1)
                    corrected.close()

                    // Sorting by other tags
                    for (tIdx in tagNames.size - 2 downTo 0) {
                        sorted = sorted.reportProgress("Sorting alignments by " + tagNames[tIdx]).hashSort(tIdx)
                    }

                    // Initializing and writing results to the output file
                    writer.writeHeader(
                        header
                            .updateTagInfo { tagsInfo -> tagsInfo.setSorted(tagsInfo.size) }
                            .addStepParams(AnalyzeCommandDescriptor.refineTagsAndSort, cmdParams)
                            .copy(paramsSpec = dontSavePresetOption.presetToSave(paramsSpec)),
                        mainReader.usedGenes
                    )
                    writer.setNumberOfProcessedReads(resultReadsCount.get())
                    sorted.reportProgress("Writing result").forEach { al ->
                        writer.write(al)
                    }
                    refineTagsAndSortReport = RefineTagsAndSortReport(
                        Date(),
                        commandLineArguments, listOf(inputFile.toString()), listOf(outputFile.toString()),
                        System.currentTimeMillis() - startTimeMillis,
                        MiXCRVersionInfo.get().shortestVersionString,
                        mitoolReport
                    )
                    writer.setFooter(
                        mainReader.footer
                            .withThresholds(thresholds)
                            .addStepReport(AnalyzeCommandDescriptor.refineTagsAndSort, refineTagsAndSortReport)
                    )
                }
            }
            refineTagsAndSortReport.writeReport(ReportHelper.STDOUT)
            reportOptions.appendToFiles(refineTagsAndSortReport)
        }
    }

    object AlignmentSequenceExtractor : SequenceExtractorsFactory<VDJCAlignments> {
        override fun getSequenceExtractor(seqKey: String) = run {
            if (!"targets".equals(seqKey, ignoreCase = true))
                throw IllegalArgumentException("Unknown sequence key: $seqKey")
            SequenceExtractor<VDJCAlignments> { it.getTargets() }
        }
    }
}

class TagCorrectionError(val tags: List<String>, cause: Throwable) :
    RuntimeException("Error on tag correction of $tags", cause)
