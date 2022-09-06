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

import cc.redberry.pipe.util.StatusReporter
import com.fasterxml.jackson.annotation.JsonMerge
import com.fasterxml.jackson.annotation.JsonProperty
import com.milaboratory.cli.CmdParameterOverrideOps
import com.milaboratory.cli.PresetAware
import com.milaboratory.cli.PresetParser
import com.milaboratory.mixcr.Presets
import com.milaboratory.mixcr.assembler.AlignmentsMappingMerger
import com.milaboratory.mixcr.assembler.CloneAssembler
import com.milaboratory.mixcr.assembler.CloneAssemblerParameters
import com.milaboratory.mixcr.assembler.CloneAssemblerRunner
import com.milaboratory.mixcr.assembler.preclone.PreCloneAssemblerParameters
import com.milaboratory.mixcr.assembler.preclone.PreCloneAssemblerRunner
import com.milaboratory.mixcr.assembler.preclone.PreCloneReader
import com.milaboratory.mixcr.basictypes.*
import com.milaboratory.mixcr.basictypes.tag.TagType
import com.milaboratory.util.ArraysUtils
import com.milaboratory.util.ReportUtil
import com.milaboratory.util.SmartProgressReporter
import com.milaboratory.util.TempFileManager
import io.repseq.core.GeneFeature.CDR3
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import picocli.CommandLine.*
import java.util.*

object CommandAssemble {
    const val COMMAND_NAME = "assemble"

    data class Params(
        @JsonProperty("sortBySequence") val sortBySequence: Boolean,
        @JsonProperty("clnaOutput") val clnaOutput: Boolean,
        @JsonProperty("cellLevel") val cellLevel: Boolean,
        @JsonProperty("consensusAssemblerParameters") @JsonMerge val consensusAssemblerParameters: PreCloneAssemblerParameters,
        @JsonProperty("cloneAssemblerParameters") @JsonMerge val cloneAssemblerParameters: CloneAssemblerParameters
    )

    abstract class CmdBase : MiXCRCommand(), PresetAware<Params> {
        @Option(
            description = ["If this option is specified, output file will be written in \"Clones & " +
                    "Alignments\" format (*.clna), containing clones and all corresponding alignments. " +
                    "This file then can be used to build wider contigs for clonal sequence and extract original " +
                    "reads for each clone (if -OsaveOriginalReads=true was use on 'align' stage)."],
            names = ["-a", "--write-alignments"]
        )
        private var isClnaOutput = false

        @Option(
            description = ["If tags are present, do assemble pre-clones on the cell level rather than on the molecule level. " +
                    "If there are no molecular tags in the data, but cell tags are present, this option will be used by default. " +
                    "This option has no effect on the data without tags."], names = ["--cell-level"]
        )
        private var cellLevel = false

        @Option(
            description = ["Sort by sequence. Clones in the output file will be sorted by clonal sequence," +
                    "which allows to build overlaps between clonesets."], names = ["-s", "--sort-by-sequence"]
        )
        private var sortBySequence = false

        @Option(names = ["-O"], description = ["Overrides default parameter values."])
        private val cloneAssemblerOverrides: Map<String, String> = mutableMapOf()

        @Option(names = ["-P"], description = ["Overrides default pre-clone assembler parameter values."])
        private val consensusAssemblerOverrides: Map<String, String> = mutableMapOf()

        override val presetParser = object : PresetParser<Params>(Presets.assemble) {
            override fun CmdParameterOverrideOps<Params>.overrideParameters() {
                Params::clnaOutput setIfTrue isClnaOutput
                Params::cellLevel setIfTrue cellLevel
                Params::sortBySequence setIfTrue sortBySequence
                Params::cloneAssemblerParameters jsonOverrideWith cloneAssemblerOverrides
                Params::consensusAssemblerParameters jsonOverrideWith consensusAssemblerOverrides
            }
        }
    }

    @Command(
        name = COMMAND_NAME,
        separator = " ",
        description = ["Assemble clones."]
    )
    class Cmd : CmdBase() {
        @Parameters(description = ["alignments.vdjca"], index = "0")
        lateinit var inputFile: String

        @Parameters(description = ["clones.[clns|clna]"], index = "1")
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

        @Option(description = ["Use higher compression for output file."], names = ["--high-compression"])
        var highCompression = false

        @Option(description = [CommonDescriptions.REPORT], names = ["-r", "--report"])
        var reportFile: String? = null

        @Option(description = [CommonDescriptions.JSON_REPORT], names = ["-j", "--json-report"])
        var jsonReport: String? = null

        @Option(description = ["Show buffer statistics."], names = ["--buffers"], hidden = true)
        var reportBuffers = false

        override fun getInputFiles(): List<String> = listOf(inputFile)

        override fun getOutputFiles(): List<String> = listOf(outputFile)

        /**
         * Assemble report
         */
        private val reportBuilder = CloneAssemblerReportBuilder()

        override fun run0() {
            // Saving initial timestamp
            val beginTimestamp = System.currentTimeMillis()

            val cmdParam: Params
            VDJCAlignmentsReader(inputFile).use { alignmentsReader ->
                val inputInfo = alignmentsReader.info

                cmdParam = presetParser.parse(inputInfo.preset)

                // Checking consistency between actionParameters.doWriteClnA() value and file extension
                if (outputFile.lowercase(Locale.getDefault())
                        .endsWith(".clna") && !cmdParam.clnaOutput || outputFile.lowercase(Locale.getDefault())
                        .endsWith(".clns") && cmdParam.clnaOutput
                ) warn(
                    """
                    WARNING: Unexpected file extension, use .clns extension for clones-only (normal) output and
                    .clna if -a / --write-alignments options specified.
                    """.trimIndent()
                )

                // set aligner parameters
                val cloneAssemblerParameters = cmdParam.cloneAssemblerParameters.updateFrom(inputInfo.alignerParameters)

                // deducing ordering
                val ordering = if (cmdParam.sortBySequence) {
                    val assemblingFeatures = cloneAssemblerParameters.assemblingFeatures

                    // Any CDR3 containing feature will become first
                    for (i in assemblingFeatures.indices) {
                        if (CDR3 in assemblingFeatures[i]) {
                            if (i != 0) ArraysUtils.swap(assemblingFeatures, 0, i)
                            break
                        }
                    }
                    VDJCSProperties.cloneOrderingByNucleotide(assemblingFeatures, Variable, Joining)
                } else {
                    VDJCSProperties.CO_BY_COUNT
                }

                CloneAssembler(
                    cloneAssemblerParameters,
                    cmdParam.clnaOutput,
                    alignmentsReader.usedGenes,
                    inputInfo.alignerParameters.featuresToAlignMap
                ).use { assembler ->
                    // Creating event listener to collect run statistics
                    reportBuilder.setStartMillis(beginTimestamp)
                    reportBuilder.setInputFiles(inputFile)
                    reportBuilder.setOutputFiles(outputFile)
                    reportBuilder.commandLine = commandLineArguments
                    assembler.setListener(reportBuilder)
                    val preClones: PreCloneReader =
                        if (
                            inputInfo.tagsInfo.hasTagsWithType(TagType.Cell) ||
                            inputInfo.tagsInfo.hasTagsWithType(TagType.Molecule)
                        ) {
                            val preClonesFile = tempDest.resolvePath("preclones.pc")

                            val assemblerRunner = PreCloneAssemblerRunner(
                                alignmentsReader,
                                if (cmdParam.cellLevel) TagType.Cell else TagType.Molecule,
                                cloneAssemblerParameters.assemblingFeatures,
                                cmdParam.consensusAssemblerParameters, preClonesFile,
                                tempDest.addSuffix("pc.tmp")
                            )
                            assemblerRunner.setExtractionListener(reportBuilder)
                            SmartProgressReporter.startProgressReport(assemblerRunner)

                            // Pre-clone assembly happens here (file with pre-clones and alignments written as a result)
                            assemblerRunner.run()

                            // Setting report into a big report object
                            reportBuilder.setPreCloneAssemblerReportBuilder(assemblerRunner.report)
                            assemblerRunner.createReader()
                        } else  // If there are no tags in the data, alignments are just wrapped into pre-clones
                            PreCloneReader.fromAlignments(
                                alignmentsReader,
                                cloneAssemblerParameters.assemblingFeatures,
                                reportBuilder
                            )

                    // Running assembler
                    val assemblerRunner = CloneAssemblerRunner(
                        preClones,
                        assembler
                    )
                    SmartProgressReporter.startProgressReport(assemblerRunner)
                    if (reportBuffers) {
                        val reporter = StatusReporter()
                        reporter.addCustomProviderFromLambda {
                            StatusReporter.Status(
                                "Reader buffer: FIXME " /*+ assemblerRunner.getQueueSize()*/,
                                assemblerRunner.isFinished
                            )
                        }
                        reporter.start()
                    }
                    assemblerRunner.run()

                    // Getting results
                    val cloneSet = CloneSet.reorder(
                        assemblerRunner.getCloneSet(inputInfo.withAssemblerParameters(cloneAssemblerParameters)),
                        ordering
                    )

                    // Passing final cloneset to assemble last pieces of statistics for report
                    reportBuilder.onClonesetFinished(cloneSet)
                    assert(cloneSet.clones.size == reportBuilder.cloneCount)
                    reportBuilder.setTotalReads(alignmentsReader.numberOfReads)


                    // Writing results
                    var report: CloneAssemblerReport
                    if (cmdParam.clnaOutput) {
                        ClnAWriter(outputFile, tempDest, highCompression).use { writer ->

                            // writer will supply current stage and completion percent to the progress reporter
                            SmartProgressReporter.startProgressReport(writer)
                            // Writing clone block
                            writer.writeClones(cloneSet)
                            AlignmentsMappingMerger(preClones.readAlignments(), assembler.assembledReadsPort)
                                .use { merged -> writer.collateAlignments(merged, assembler.alignmentsCount) }
                            reportBuilder.setFinishMillis(System.currentTimeMillis())
                            report = reportBuilder.buildReport()
                            writer.writeFooter(alignmentsReader.reports(), report)
                            writer.writeAlignmentsAndIndex()
                        }
                    } else {
                        ClnsWriter(outputFile).use { writer ->
                            writer.writeCloneSet(cloneSet)
                            reportBuilder.setFinishMillis(System.currentTimeMillis())
                            report = reportBuilder.buildReport()
                            writer.writeFooter(alignmentsReader.reports(), report)
                        }
                    }

                    // Writing report to stout
                    ReportUtil.writeReportToStdout(report)
                    if (reportFile != null) ReportUtil.appendReport(reportFile, report)
                    if (jsonReport != null) ReportUtil.appendJsonReport(jsonReport, report)
                }
            }
        }
    }
}

