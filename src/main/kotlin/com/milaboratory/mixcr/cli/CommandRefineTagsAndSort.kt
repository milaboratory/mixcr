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

import cc.redberry.pipe.OutputPort
import cc.redberry.pipe.util.CountingOutputPort
import com.fasterxml.jackson.annotation.JsonMerge
import com.fasterxml.jackson.annotation.JsonProperty
import com.milaboratory.cli.POverridesBuilderOps
import com.milaboratory.core.sequence.ShortSequenceSet
import com.milaboratory.mitool.data.CriticalThresholdKey
import com.milaboratory.mitool.pattern.SequenceSetCollection.loadSequenceSetByAddress
import com.milaboratory.mitool.refinement.TagCorrectionReport
import com.milaboratory.mitool.refinement.TagCorrector
import com.milaboratory.mitool.refinement.TagCorrectorParameters
import com.milaboratory.mixcr.MiXCRCommandDescriptor
import com.milaboratory.mixcr.MiXCRParams
import com.milaboratory.mixcr.MiXCRParamsBundle
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.basictypes.VDJCAlignments
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter
import com.milaboratory.mixcr.basictypes.tag.SequenceAndQualityTagValue
import com.milaboratory.mixcr.basictypes.tag.TagCount
import com.milaboratory.mixcr.basictypes.tag.TagTuple
import com.milaboratory.mixcr.basictypes.tag.TagValueType
import com.milaboratory.mixcr.cli.CommonDescriptions.DEFAULT_VALUE_FROM_PRESET
import com.milaboratory.mixcr.util.MiXCRVersionInfo
import com.milaboratory.primitivio.GroupingCriteria
import com.milaboratory.primitivio.PrimitivIOStateBuilder
import com.milaboratory.primitivio.forEach
import com.milaboratory.primitivio.hashGrouping
import com.milaboratory.util.CanReportProgress
import com.milaboratory.util.ReportHelper
import com.milaboratory.util.SmartProgressReporter
import com.milaboratory.util.TempFileManager
import gnu.trove.list.array.TIntArrayList
import org.apache.commons.io.FileUtils
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path
import java.util.*

object CommandRefineTagsAndSort {
    const val COMMAND_NAME = "refineTagsAndSort"

    data class Params(
        /** Whitelists to use on the correction step for barcodes requiring whitelist-driven correction */
        @JsonProperty("whitelists") val whitelists: Map<String, String> = emptyMap(),
        /** If false no correction will be performed, only sorting */
        @JsonProperty("runCorrection") val runCorrection: Boolean = true,
        /** Correction parameters */
        @JsonMerge @JsonProperty("parameters") val parameters: TagCorrectorParameters
    ) : MiXCRParams {
        override val command get() = MiXCRCommandDescriptor.refineTagsAndSort
    }

    abstract class CmdBase : MiXCRCommandWithOutputs(), MiXCRPresetAwareCommand<Params> {
        @Option(
            description = [
                "Don't correct barcodes, only sort alignments by tags.",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["--dont-correct"]
        )
        private var dontCorrect = false

        @Option(
            description = [
                "This parameter determines how thorough the procedure should eliminate variants looking like errors.",
                "Smaller value leave less erroneous variants at the cost of accidentally correcting true variants.",
                "This value approximates the fraction of erroneous variants the algorithm will miss (type II errors).",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["-p", "--power"],
            paramLabel = "<d>"
        )
        private var power: Double? = null

        @Option(
            description = [
                "Expected background non-sequencing-related substitution rate.",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["-s", "--substitution-rate"],
            paramLabel = "<d>"
        )
        private var backgroundSubstitutionRate: Double? = null

        @Option(
            description = [
                "Expected background non-sequencing-related indel rate.",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["-i", "--indel-rate"],
            paramLabel = "<d>"
        )
        private var backgroundIndelRate: Double? = null

        @Option(
            description = [
                "Minimal quality score for the tag.",
                "Tags having positions with lower quality score will be discarded, if not corrected.",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["-q", "--min-quality"],
            paramLabel = "<n>"
        )
        private var minQuality: Int? = null

        @Option(
            description = [
                "Maximal number of substitutions to search for.",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["--max-substitutions"],
            paramLabel = "<n>"
        )
        private var maxSubstitutions: Int? = null

        @Option(
            description = [
                "Maximal number of indels to search for.",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["--max-indels"],
            paramLabel = "<n>"
        )
        private var maxIndels: Int? = null

        @Option(
            description = [
                "Maximal number of substitutions and indels combined to search for.",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["--max-errors"],
            paramLabel = "<n>"
        )
        private var maxTotalErrors: Int? = null

        @Option(
            names = ["-w", "--whitelist"],
            description = [
                "Use whitelist-driven correction for one of the tags.",
                "Usage: --whitelist CELL=preset:737K-august-2016 or -w UMI=file:my_umi_whitelist.txt.",
                "If not specified mixcr will set correct whitelists if --tag-preset was used on align step.",
                DEFAULT_VALUE_FROM_PRESET
            ],
            paramLabel = "<tag=value>"
        )
        private var whitelists: Map<String, String> = mutableMapOf()

        override val paramsResolver = object : MiXCRParamsResolver<Params>(MiXCRParamsBundle::refineTagsAndSort) {
            override fun POverridesBuilderOps<Params>.paramsOverrides() {
                Params::whitelists setIfNotEmpty whitelists

                Params::runCorrection resetIfTrue dontCorrect

                @Suppress("DuplicatedCode")
                Params::parameters.update {
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

        @Suppress("unused", "UNUSED_PARAMETER")
        @Option(
            description = ["Use system temp folder for temporary files."],
            names = ["--use-system-temp"],
            hidden = true
        )
        fun useSystemTemp(ignored: Boolean) {
            logger.warn(
                "--use-system-temp is deprecated, it is now enabled by default, use --use-local-temp to invert the " +
                        "behaviour and place temporary files in the same folder as the output file."
            )
        }

        @Option(
            description = ["Store temp files in the same folder as output file."],
            names = ["--use-local-temp"],
            order = 1_000_000 - 5
        )
        var useLocalTemp = false

        private val tempDest by lazy {
            TempFileManager.smartTempDestination(outputFile, "", !useLocalTemp)
        }

        @Option(
            description = ["Memory budget in bytes. Default: 4Gb"],
            names = ["--memory-budget"],
            paramLabel = "<n>"
        )
        var memoryBudget = 4 * FileUtils.ONE_GB

        @Mixin
        lateinit var reportOptions: ReportOptions

        override val inputFiles
            get() = listOf(inputFile)

        override val outputFiles
            get() = listOf(outputFile)

        override fun run0() {
            val startTimeMillis = System.currentTimeMillis()

            val cmdParams: Params

            val refineTagsAndSortReport: RefineTagsAndSortReport
            val mitoolReport: TagCorrectionReport?
            var thresholds: Map<CriticalThresholdKey, Double> = emptyMap()
            VDJCAlignmentsReader(inputFile).use { mainReader ->
                val header = mainReader.header
                require(!header.tagsInfo.hasNoTags()) { "input file has no tags" }
                cmdParams = paramsResolver.resolve(header.paramsSpec, printParameters = logger.verbose).second
                val tagNames = mutableListOf<String>()
                val indicesBuilder = TIntArrayList()
                for (ti in header.tagsInfo.indices) {
                    val tag = header.tagsInfo[ti]
                    assert(ti == tag.index) /* just in case */
                    if (tag.valueType == TagValueType.SequenceAndQuality) indicesBuilder.add(ti)
                    tagNames += tag.name
                }
                val targetTagIndices = indicesBuilder.toArray()
                println(
                    (if (cmdParams.runCorrection) "Correction" else "Sorting") +
                            " will be applied to the following tags: ${tagNames.joinToString(", ")}"
                )

                val (corrected, progress: CanReportProgress, numberOfAlignments) = when {
                    !cmdParams.runCorrection -> {
                        mitoolReport = null
                        Triple(mainReader, mainReader, mainReader.numberOfAlignments)
                    }

                    else -> {
                        // Running correction
                        val whitelists = mutableMapOf<Int, ShortSequenceSet>()
                        for (i in tagNames.indices) {
                            val t = cmdParams.whitelists[tagNames[i]]
                            if (t != null) {
                                println("The following whitelist will be used for ${tagNames[i]}: $t")
                                whitelists[i] = loadSequenceSetByAddress(t)
                            }
                        }

                        val corrector = TagCorrector(
                            cmdParams.parameters,
                            tagNames,
                            tempDest.addSuffix("tags"),
                            whitelists,
                            memoryBudget,
                            4, 4
                        )

                        SmartProgressReporter.startProgressReport(corrector)

                        // Will read the input stream once and extract all the required information from it
                        corrector.initialize(mainReader, { al -> al.alignmentsIndex }) {
                            if (it.tagCount.size() != 1) throw ApplicationException(
                                "This procedure don't support aggregated tags. " +
                                        "Please run tag correction for *.vdjca files produced by 'align'."
                            )
                            val tagTuple = it.tagCount.tuples().iterator().next()
                            Array(targetTagIndices.size) { tIdxIdx -> // <- local index for the procedure
                                (tagTuple[targetTagIndices[tIdxIdx]] as SequenceAndQualityTagValue).data
                            }
                        }

                        // Running correction, results are temporarily persisted in temp file, so the object can be used
                        // multiple time to perform the correction and stream corrected and filtered results
                        corrector.calculate()

                        // Available after calculation finishes
                        mitoolReport = corrector.report
                        thresholds = corrector.criticalThresholds

                        // Creating another alignment stream from the same container file to apply correction
                        val secondaryReader = mainReader.readAlignments()

                        Triple(
                            corrector.applyCorrection(
                                secondaryReader,
                                { al -> al.alignmentsIndex }) { al, newTagValues ->
                                // starting off the copy of original alignment tags array
                                val updatedTags = al.tagCount.singletonTuple.asArray()
                                targetTagIndices.forEachIndexed { tIdxIdx, tIdx ->
                                    // tIdxIdx - local index for the procedure
                                    // tIdx - index inside the alignment object
                                    updatedTags[tIdx] = SequenceAndQualityTagValue(newTagValues[tIdxIdx])
                                }
                                // Applying updated tags values and returning updated alignments object
                                al.setTagCount(TagCount(TagTuple(*updatedTags)))
                            },
                            secondaryReader,
                            corrector.report.outputRecords
                        )
                    }
                }

                VDJCAlignmentsWriter(outputFile).use { writer ->
                    val alPioState = PrimitivIOStateBuilder()
                    IOUtil.registerGeneReferences(alPioState, mainReader.usedGenes, mainReader.parameters)

                    // Reusable routine to perform has-based soring of alignments by tag with specific index
                    val hashSort: OutputPort<VDJCAlignments>.(tagIdx: Int) -> OutputPort<VDJCAlignments> =
                        { tIdx -> // <- index inside the alignment object
                            hashGrouping(
                                GroupingCriteria.groupBy { al ->
                                    val tagTuple = al.tagCount.singletonTuple
                                    (tagTuple[tIdx] as SequenceAndQualityTagValue).data.sequence
                                },
                                alPioState,
                                tempDest.addSuffix("hashsorter.$tIdx"),
                                bitsPerStep = 4,
                                readerConcurrency = 4,
                                writerConcurrency = 4,
                                objectSizeInitialGuess = 10_000,
                                memoryBudget = memoryBudget
                            )
                        }

                    // Progress reporter for the first sorting step
                    SmartProgressReporter.startProgressReport(
                        when {
                            cmdParams.runCorrection -> "Applying correction & sorting alignments by ${tagNames[targetTagIndices.size - 1]}"
                            else -> "Sorting alignments by ${tagNames[targetTagIndices.size - 1]}"
                        },
                        progress
                    )

                    // Running initial hash sorter
                    var sorted = CountingOutputPort(
                        corrected.hashSort(targetTagIndices[targetTagIndices.size - 1])
                    )
                    corrected.close()

                    // Sorting by other tags
                    for (tIdxIdx in targetTagIndices.size - 2 downTo 0) {
                        SmartProgressReporter.startProgressReport(
                            "Sorting alignments by " + tagNames[tIdxIdx],
                            SmartProgressReporter.extractProgress(sorted, numberOfAlignments)
                        )
                        sorted = CountingOutputPort(sorted.hashSort(targetTagIndices[tIdxIdx]))
                    }
                    SmartProgressReporter.startProgressReport(
                        "Writing result",
                        SmartProgressReporter.extractProgress(sorted, numberOfAlignments)
                    )

                    // Initializing and writing results to the output file
                    writer.writeHeader(
                        header
                            .updateTagInfo { tagsInfo -> tagsInfo.setSorted(tagsInfo.size) }
                            .addStepParams(MiXCRCommandDescriptor.refineTagsAndSort, cmdParams),
                        mainReader.usedGenes
                    )
                    writer.setNumberOfProcessedReads(mainReader.numberOfReads)
                    sorted.forEach { al ->
                        writer.write(al)
                    }
                    refineTagsAndSortReport = RefineTagsAndSortReport(
                        Date(),
                        commandLineArguments, arrayOf(inputFile.toString()), arrayOf(outputFile.toString()),
                        System.currentTimeMillis() - startTimeMillis,
                        MiXCRVersionInfo.get().shortestVersionString,
                        mitoolReport
                    )
                    writer.setFooter(
                        mainReader.footer
                            .withThresholds(thresholds)
                            .addStepReport(MiXCRCommandDescriptor.refineTagsAndSort, refineTagsAndSortReport)
                    )
                }
            }
            refineTagsAndSortReport.writeReport(ReportHelper.STDOUT)
            reportOptions.appendToFiles(refineTagsAndSortReport)
        }
    }
}
