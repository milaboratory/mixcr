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
import com.milaboratory.core.sequence.ShortSequenceSet
import com.milaboratory.mitool.pattern.LibraryStructurePresetCollection.LibraryStructurePreset
import com.milaboratory.mitool.pattern.LibraryStructurePresetCollection.getPresetByName
import com.milaboratory.mitool.pattern.SequenceSetCollection.loadSequenceSetByAddress
import com.milaboratory.mitool.refinement.CorrectionNode
import com.milaboratory.mitool.refinement.CorrectionReport
import com.milaboratory.mitool.refinement.TagCorrector
import com.milaboratory.mitool.refinement.TagCorrectorParameters
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.basictypes.VDJCAlignments
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter
import com.milaboratory.mixcr.basictypes.tag.SequenceAndQualityTagValue
import com.milaboratory.mixcr.basictypes.tag.TagCount
import com.milaboratory.mixcr.basictypes.tag.TagTuple
import com.milaboratory.mixcr.basictypes.tag.TagValueType
import com.milaboratory.mixcr.util.Defaults.default3
import com.milaboratory.mixcr.util.MiXCRVersionInfo
import com.milaboratory.primitivio.GroupingCriteria
import com.milaboratory.primitivio.PrimitivIOStateBuilder
import com.milaboratory.primitivio.filter
import com.milaboratory.primitivio.forEach
import com.milaboratory.primitivio.hashGrouping
import com.milaboratory.primitivio.map
import com.milaboratory.util.ReportHelper
import com.milaboratory.util.ReportUtil
import com.milaboratory.util.SmartProgressReporter
import com.milaboratory.util.TempFileManager
import gnu.trove.list.array.TIntArrayList
import org.apache.commons.io.FileUtils
import picocli.CommandLine
import java.util.*

@CommandLine.Command(
    name = CommandCorrectAndSortTags.CORRECT_AND_SORT_TAGS_COMMAND_NAME,
    sortOptions = false,
    separator = " ",
    description = ["Applies error correction algorithm for tag sequences and sorts resulting file by tags."]
)
class CommandCorrectAndSortTags : MiXCRCommand() {
    @CommandLine.Parameters(description = ["alignments.vdjca"], index = "0")
    lateinit var `in`: String

    @CommandLine.Parameters(description = ["alignments.corrected.vdjca"], index = "1")
    lateinit var out: String

    @CommandLine.Option(
        description = ["Don't correct barcodes, only sort alignments by tags."],
        names = ["--dont-correct"]
    )
    var noCorrect = false

    @CommandLine.Option(
        description = ["This parameter determines how thorough the procedure should eliminate variants looking like errors. " +
                "Smaller value leave less erroneous variants at the cost of accidentally correcting true variants. " +
                "This value approximates the fraction of erroneous variants the algorithm will miss (type II errors). " +
                "(default " + TagCorrectorParameters.DEFAULT_CORRECTION_POWER + " or from preset specified on align step)"],
        names = ["-p", "--power"]
    )
    var power: Double? = null

    @CommandLine.Option(
        description = ["Expected background non-sequencing-related substitution rate (default " +
                TagCorrectorParameters.DEFAULT_BACKGROUND_SUBSTITUTION_RATE + " or from preset specified on align step)"],
        names = ["-s", "--substitution-rate"]
    )
    var backgroundSubstitutionRate: Double? = null

    @CommandLine.Option(
        description = ["Expected background non-sequencing-related indel rate (default " +
                TagCorrectorParameters.DEFAULT_BACKGROUND_INDEL_RATE + " or from preset specified on align step)"],
        names = ["-i", "--indel-rate"]
    )
    var backgroundIndelRate: Double? = null

    @CommandLine.Option(
        description = ["Minimal quality score for the tag. " +
                "Tags having positions with lower quality score will be discarded, if not corrected (default " +
                TagCorrectorParameters.DEFAULT_MIN_QUALITY + " or from preset specified on align step)"],
        names = ["-q", "--min-quality"]
    )
    var minQuality: Int? = null

    @CommandLine.Option(
        description = ["Maximal number of substitutions to search for (default " +
                TagCorrectorParameters.DEFAULT_MAX_SUBSTITUTIONS + " or from preset specified on align step)"],
        names = ["--max-substitutions"]
    )
    var maxSubstitutions: Int? = null

    @CommandLine.Option(
        description = ["Maximal number of indels to search for (default " +
                TagCorrectorParameters.DEFAULT_MAX_INDELS + " or from preset specified on align step)"],
        names = ["--max-indels"]
    )
    var maxIndels: Int? = null

    @CommandLine.Option(
        description = ["Maximal number of substitutions and indels combined to search for (default " +
                TagCorrectorParameters.DEFAULT_MAX_TOTAL_ERROR + " or from preset specified on align step)"],
        names = ["--max-errors"]
    )
    var maxTotalErrors: Int? = null

    @CommandLine.Option(
        names = ["-w", "--whitelist"], description = ["Use whitelist-driven correction for one of the tags. Usage: " +
                "--whitelist CELL=preset:737K-august-2016 or -w UMI=file:my_umi_whitelist.txt. If not specified mixcr will set " +
                "correct whitelists if --tag-preset was used on align step."]
    )
    var whitelists: Map<String, String> = mutableMapOf()

    @CommandLine.Option(description = ["Use system temp folder for temporary files."], names = ["--use-system-temp"])
    var useSystemTemp = false

    @CommandLine.Option(description = ["Memory budget"], names = ["--memory-budget"])
    var memoryBudget = 4 * FileUtils.ONE_GB

    @CommandLine.Option(description = [CommonDescriptions.REPORT], names = ["-r", "--report"])
    var reportFile: String? = null
    override fun getInputFiles(): List<String> = listOf(`in`)

    override fun getOutputFiles(): List<String> = listOf(out)

    private fun LibraryStructurePreset?.getWhitelistsOptions(): Map<String, String> = whitelists.ifEmpty {
        when (this) {
            null -> emptyMap()
            else -> this.whitelists.mapValues { (_, value) -> "preset:$value" }
        }
    }

    private fun <T> LibraryStructurePreset?.defaultHelper(
        optionValue: T?,
        presetExtractor2: TagCorrectorParameters.() -> T?,
        defaultValue: T
    ): T = default3(
        optionValue,
        this,
        LibraryStructurePreset::tagCorrectionParameters,
        presetExtractor2,
        defaultValue
    )

    private fun LibraryStructurePreset?.calculateParameters(): TagCorrectorParameters = TagCorrectorParameters(
        defaultHelper(
            power,
            TagCorrectorParameters::correctionPower,
            TagCorrectorParameters.DEFAULT_CORRECTION_POWER
        ),
        defaultHelper(
            backgroundSubstitutionRate,
            TagCorrectorParameters::backgroundSubstitutionRate,
            TagCorrectorParameters.DEFAULT_BACKGROUND_SUBSTITUTION_RATE
        ),
        defaultHelper(
            backgroundIndelRate,
            TagCorrectorParameters::backgroundIndelRate,
            TagCorrectorParameters.DEFAULT_BACKGROUND_INDEL_RATE
        ),
        defaultHelper(
            minQuality,
            TagCorrectorParameters::minQuality,
            TagCorrectorParameters.DEFAULT_MIN_QUALITY
        ),
        defaultHelper(
            maxSubstitutions,
            TagCorrectorParameters::maxSubstitutions,
            TagCorrectorParameters.DEFAULT_MAX_SUBSTITUTIONS
        ),
        defaultHelper(
            maxIndels,
            TagCorrectorParameters::maxIndels,
            TagCorrectorParameters.DEFAULT_MAX_INDELS
        ),
        defaultHelper(
            maxTotalErrors,
            TagCorrectorParameters::maxTotalError,
            TagCorrectorParameters.DEFAULT_MAX_TOTAL_ERROR
        )
    )

    private val tempDest by lazy {
        TempFileManager.smartTempDestination(out, "", useSystemTemp)
    }

    override fun run0() {
        val startTimeMillis = System.currentTimeMillis()
        val correctAndSortTagsReport: CorrectAndSortTagsReport
        val mitoolReport: CorrectionReport?
        VDJCAlignmentsReader(`in`).use { mainReader ->
            val preset = mainReader.info.tagPreset?.let { getPresetByName(it) }
            val tagNames = mutableListOf<String>()
            val indicesBuilder = TIntArrayList()
            for (ti in mainReader.tagsInfo.indices) {
                val tag = mainReader.tagsInfo[ti]
                assert(ti == tag.index) /* just in case*/
                if (tag.valueType == TagValueType.SequenceAndQuality) indicesBuilder.add(ti)
                tagNames += tag.name
            }
            val targetTagIndices = indicesBuilder.toArray()
            println(
                (if (noCorrect) "Sorting" else "Correction") +
                        " will be applied to the following tags: ${tagNames.joinToString(", ")}"
            )
            val corrector = when {
                noCorrect -> null
                else -> TagCorrector(
                    preset.calculateParameters(),
                    tempDest.addSuffix("tags"),
                    memoryBudget,
                    4, 4
                )
            }
            if (corrector != null) {
                SmartProgressReporter.startProgressReport(corrector)
            }
            val correctionResult: CorrectionNode? = when (corrector) {
                null -> null
                else -> {
                    // Extractor of tag information from the alignments for the tag corrector
                    val cInput = mainReader.map { input: VDJCAlignments ->
                        if (input.tagCount.size() != 1) throwExecutionException(
                            "This procedure don't support aggregated tags. " +
                                    "Please run tag correction for *.vdjca files produced by 'align'."
                        )
                        val tagTuple = input.tagCount.tuples().iterator().next()
                        Array(targetTagIndices.size) { i ->
                            (tagTuple[targetTagIndices[i]] as SequenceAndQualityTagValue).data
                        }
                    }

                    // Running correction
                    val whitelistsOptions = preset.getWhitelistsOptions()
                    val whitelists = mutableMapOf<Int, ShortSequenceSet>()
                    for (i in tagNames.indices) {
                        val t = whitelistsOptions[tagNames[i]]
                        if (t != null) {
                            println("The following whitelist will be used for ${tagNames[i]}: $t")
                            whitelists[i] = loadSequenceSetByAddress(t)
                        }
                    }
                    corrector.correct(cInput, tagNames, whitelists, mainReader)
                }
            }
            mitoolReport = corrector?.report
            VDJCAlignmentsWriter(out).use { writer ->
                val secondaryReader = mainReader.readAlignments()
                val stateBuilder = PrimitivIOStateBuilder()
                IOUtil.registerGeneReferences(stateBuilder, mainReader.usedGenes, mainReader.parameters)
                val hashSort: OutputPort<VDJCAlignments>.(tagIdx: Int) -> OutputPort<VDJCAlignments> = { tagIdx ->
                    hashGrouping(
                        GroupingCriteria.groupBy { al ->
                            val tagTuple = al.tagCount.singletonTuple
                            (tagTuple[tagIdx] as SequenceAndQualityTagValue).data.sequence
                        },
                        stateBuilder,
                        tempDest.addSuffix("hashsorter.$tagIdx"),
                        bitsPerStep = 4,
                        readerConcurrency = 4,
                        writerConcurrency = 4,
                        objectSizeInitialGuess = 10_000,
                        memoryBudget = memoryBudget
                    )
                }
                SmartProgressReporter.startProgressReport(
                    when {
                        !noCorrect -> "Applying correction & sorting alignments by ${tagNames[targetTagIndices.size - 1]}"
                        else -> "Sorting alignments by ${tagNames[targetTagIndices.size - 1]}"
                    },
                    secondaryReader
                )

                // Creating output port with corrected and filtered tags
                val hsInput = secondaryReader
                    .map { vdjcAlignments ->
                        if (noCorrect) return@map vdjcAlignments
                        val newTags = vdjcAlignments.tagCount.singletonTuple.asArray()
                        var cn = correctionResult
                        for (i in targetTagIndices) {
                            val current = (newTags[i] as SequenceAndQualityTagValue).data.sequence
                            cn = cn!!.nextLevel[current]
                            if (cn == null) {
                                mitoolReport!!.filteredRecords = mitoolReport.filteredRecords + 1
                                return@map vdjcAlignments.setTagCount(null) // will be filtered right before hash sorter
                            }
                            newTags[i] = SequenceAndQualityTagValue(cn.correctValue)
                        }
                        vdjcAlignments.setTagCount(TagCount(TagTuple(*newTags)))
                    }
                    .filter { vdjcAlignments -> vdjcAlignments.tagCount != null }

                // Running initial hash sorter
                var sorted = CountingOutputPort(
                    hsInput.hashSort(targetTagIndices[targetTagIndices.size - 1])
                )

                // Sorting by other tags
                for (tagIdxIdx in targetTagIndices.size - 2 downTo 0) {
                    SmartProgressReporter.startProgressReport(
                        "Sorting alignments by " + tagNames[tagIdxIdx],
                        SmartProgressReporter.extractProgress(sorted, mainReader.numberOfAlignments)
                    )
                    sorted = CountingOutputPort(sorted.hashSort(targetTagIndices[tagIdxIdx]))
                }
                SmartProgressReporter.startProgressReport(
                    "Writing result",
                    SmartProgressReporter.extractProgress(sorted, mainReader.numberOfAlignments)
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
                correctAndSortTagsReport = CorrectAndSortTagsReport(
                    Date(),
                    commandLineArguments, arrayOf(`in`), arrayOf(out),
                    System.currentTimeMillis() - startTimeMillis,
                    MiXCRVersionInfo.get().shortestVersionString,
                    mitoolReport
                )
                writer.writeFooter(mainReader.reports(), null /*correctAndSortTagsReport*/) //fixme
            }
        }
        correctAndSortTagsReport.writeReport(ReportHelper.STDOUT)
        if (reportFile != null) ReportUtil.appendReport(reportFile, mitoolReport)
    }

    companion object {
        const val CORRECT_AND_SORT_TAGS_COMMAND_NAME = "correctAndSortTags"
    }
}
