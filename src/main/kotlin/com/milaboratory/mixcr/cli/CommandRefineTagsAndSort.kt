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
import com.milaboratory.cli.CmdParameterOverrideOps
import com.milaboratory.cli.PresetAware
import com.milaboratory.cli.PresetParser
import com.milaboratory.core.sequence.ShortSequenceSet
import com.milaboratory.mitool.pattern.SequenceSetCollection.loadSequenceSetByAddress
import com.milaboratory.mitool.refinement.CorrectionReport
import com.milaboratory.mitool.refinement.TagCorrector
import com.milaboratory.mitool.refinement.TagCorrectorParameters
import com.milaboratory.mixcr.Presets
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.basictypes.VDJCAlignments
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter
import com.milaboratory.mixcr.basictypes.tag.SequenceAndQualityTagValue
import com.milaboratory.mixcr.basictypes.tag.TagCount
import com.milaboratory.mixcr.basictypes.tag.TagTuple
import com.milaboratory.mixcr.basictypes.tag.TagValueType
import com.milaboratory.mixcr.util.MiXCRVersionInfo
import com.milaboratory.primitivio.GroupingCriteria
import com.milaboratory.primitivio.PrimitivIOStateBuilder
import com.milaboratory.primitivio.forEach
import com.milaboratory.primitivio.hashGrouping
import com.milaboratory.util.*
import gnu.trove.list.array.TIntArrayList
import org.apache.commons.io.FileUtils
import picocli.CommandLine.*
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
    )

    abstract class CmdBase : MiXCRCommand(), PresetAware<Params> {
        @Option(
            description = ["Don't correct barcodes, only sort alignments by tags"],
            names = ["--dont-correct"]
        )
        private var dontCorrect = false

        @Option(
            description = ["This parameter determines how thorough the procedure should eliminate variants looking like errors. " +
                    "Smaller value leave less erroneous variants at the cost of accidentally correcting true variants. " +
                    "This value approximates the fraction of erroneous variants the algorithm will miss (type II errors). "],
            names = ["-p", "--power"]
        )
        private var power: Double? = null

        @Option(
            description = ["Expected background non-sequencing-related substitution rate"],
            names = ["-s", "--substitution-rate"]
        )
        private var backgroundSubstitutionRate: Double? = null

        @Option(
            description = ["Expected background non-sequencing-related indel rate"],
            names = ["-i", "--indel-rate"]
        )
        private var backgroundIndelRate: Double? = null

        @Option(
            description = ["Minimal quality score for the tag. " +
                    "Tags having positions with lower quality score will be discarded, if not corrected"],
            names = ["-q", "--min-quality"]
        )
        private var minQuality: Int? = null

        @Option(
            description = ["Maximal number of substitutions to search for"],
            names = ["--max-substitutions"]
        )
        private var maxSubstitutions: Int? = null

        @Option(
            description = ["Maximal number of indels to search for"],
            names = ["--max-indels"]
        )
        private var maxIndels: Int? = null

        @Option(
            description = ["Maximal number of substitutions and indels combined to search for"],
            names = ["--max-errors"]
        )
        private var maxTotalErrors: Int? = null

        @Option(
            names = ["-w", "--whitelist"],
            description = ["Use whitelist-driven correction for one of the tags. Usage: " +
                    "--whitelist CELL=preset:737K-august-2016 or -w UMI=file:my_umi_whitelist.txt. If not specified mixcr will set " +
                    "correct whitelists if --tag-preset was used on align step."]
        )
        private var whitelists: Map<String, String> = mutableMapOf()

        override val presetParser = object : PresetParser<Params>(Presets.refineTagsAndSort) {
            override fun CmdParameterOverrideOps<Params>.overrideParameters() {
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
        name = COMMAND_NAME,
        sortOptions = false,
        separator = " ",
        description = ["Applies error correction algorithm for tag sequences and sorts resulting file by tags."]
    )
    class Cmd : CmdBase() {
        @Parameters(description = ["alignments.vdjca"], index = "0")
        lateinit var inputFile: String

        @Parameters(description = ["alignments.corrected.vdjca"], index = "1")
        lateinit var outputFile: String

        @Option(
            description = ["Use system temp folder for temporary files."],
            names = ["--use-system-temp"],
            hidden = true
        )
        fun useSystemTemp(value: Boolean) {
            warn(
                "--use-system-temp is deprecated, it is now enabled by default, use --use-local-temp to invert the " +
                        "behaviour and place temporary files in the same folder as the output file."
            )
        }

        @Option(
            description = ["Store temp files in the same folder as output file."],
            names = ["--use-local-temp"]
        )
        var useLocalTemp = false

        private val tempDest by lazy {
            TempFileManager.smartTempDestination(outputFile, "", !useLocalTemp)
        }

        @Option(description = ["Memory budget"], names = ["--memory-budget"])
        var memoryBudget = 4 * FileUtils.ONE_GB
        @Option(description = [CommonDescriptions.REPORT], names = ["-r", "--report"])
        var reportFile: String? = null

        override fun getInputFiles(): List<String> = listOf(inputFile)

        override fun getOutputFiles(): List<String> = listOf(outputFile)

        override fun run0() {
            val startTimeMillis = System.currentTimeMillis()

            val cmdParams: Params

            val refineTagsAndSortReport: RefineTagsAndSortReport
            val mitoolReport: CorrectionReport?
            VDJCAlignmentsReader(inputFile).use { mainReader ->
                cmdParams = presetParser.parse(mainReader.info.preset)
                val tagNames = mutableListOf<String>()
                val indicesBuilder = TIntArrayList()
                for (ti in mainReader.tagsInfo.indices) {
                    val tag = mainReader.tagsInfo[ti]
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
                            if (it.tagCount.size() != 1) throwExecutionException(
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
                    writer.header(
                        mainReader.info.updateTagInfo { tagsInfo -> tagsInfo.setSorted(tagsInfo.size) },
                        mainReader.usedGenes
                    )
                    writer.setNumberOfProcessedReads(mainReader.numberOfReads)
                    sorted.forEach { al ->
                        writer.write(al)
                    }
                    refineTagsAndSortReport = RefineTagsAndSortReport(
                        Date(),
                        commandLineArguments, arrayOf(inputFile), arrayOf(outputFile),
                        System.currentTimeMillis() - startTimeMillis,
                        MiXCRVersionInfo.get().shortestVersionString,
                        mitoolReport
                    )
                    writer.writeFooter(mainReader.reports(), null /*correctAndSortTagsReport*/) // fixme
                }
            }
            refineTagsAndSortReport.writeReport(ReportHelper.STDOUT)
            if (reportFile != null) ReportUtil.appendReport(reportFile, mitoolReport)
        }
    }
}