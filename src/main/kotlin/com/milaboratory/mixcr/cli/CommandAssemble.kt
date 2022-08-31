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
import com.milaboratory.mixcr.assembler.AlignmentsMappingMerger
import com.milaboratory.mixcr.assembler.CloneAssembler
import com.milaboratory.mixcr.assembler.CloneAssemblerParameters
import com.milaboratory.mixcr.assembler.CloneAssemblerParametersPresets
import com.milaboratory.mixcr.assembler.CloneAssemblerRunner
import com.milaboratory.mixcr.assembler.preclone.PreCloneAssemblerParameters
import com.milaboratory.mixcr.assembler.preclone.PreCloneAssemblerRunner
import com.milaboratory.mixcr.assembler.preclone.PreCloneReader
import com.milaboratory.mixcr.basictypes.ClnAWriter
import com.milaboratory.mixcr.basictypes.ClnsWriter
import com.milaboratory.mixcr.basictypes.CloneSet
import com.milaboratory.mixcr.basictypes.MiXCRMetaInfo
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader
import com.milaboratory.mixcr.basictypes.VDJCSProperties
import com.milaboratory.mixcr.basictypes.VDJCSProperties.CloneOrdering
import com.milaboratory.mixcr.basictypes.tag.TagType
import com.milaboratory.util.ArraysUtils
import com.milaboratory.util.JsonOverrider
import com.milaboratory.util.ReportUtil
import com.milaboratory.util.SmartProgressReporter
import com.milaboratory.util.TempFileManager
import io.repseq.core.GeneFeature.CDR3
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import io.repseq.core.VDJCGene
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine
import java.util.*

@CommandLine.Command(name = CommandAssemble.ASSEMBLE_COMMAND_NAME, separator = " ", description = ["Assemble clones."])
class CommandAssemble : MiXCRCommand() {
    @CommandLine.Parameters(description = ["alignments.vdjca"], index = "0")
    lateinit var `in`: String

    @CommandLine.Parameters(description = ["clones.[clns|clna]"], index = "1")
    lateinit var out: String

    @CommandLine.Option(description = ["Clone assembling parameters preset."], names = ["-p", "--preset"])
    var assemblerParametersName = "default"

    @CommandLine.Option(description = ["Processing threads"], names = ["-t", "--threads"])
    fun setThreads(threads: Int) {
        println("-t / --threads is deprecated for \"mixcr assemble ...\" and ignored for this call...")
    }

    @CommandLine.Option(
        description = ["Use system temp folder for temporary files, the output folder will be used if this option is omitted."],
        names = ["--use-system-temp"]
    )
    var useSystemTemp = false

    @CommandLine.Option(description = ["Use higher compression for output file."], names = ["--high-compression"])
    var highCompression = false

    @CommandLine.Option(
        description = ["Sort by sequence. Clones in the output file will be sorted by clonal sequence," +
                "which allows to build overlaps between clonesets."], names = ["-s", "--sort-by-sequence"]
    )
    var sortBySequence = false

    @CommandLine.Option(description = [CommonDescriptions.REPORT], names = ["-r", "--report"])
    var reportFile: String? = null

    @CommandLine.Option(description = [CommonDescriptions.JSON_REPORT], names = ["-j", "--json-report"])
    var jsonReport: String? = null

    @CommandLine.Option(description = ["Show buffer statistics."], names = ["--buffers"], hidden = true)
    var reportBuffers = false

    @CommandLine.Option(
        description = ["If this option is specified, output file will be written in \"Clones & " +
                "Alignments\" format (*.clna), containing clones and all corresponding alignments. " +
                "This file then can be used to build wider contigs for clonal sequence and extract original " +
                "reads for each clone (if -OsaveOriginalReads=true was use on 'align' stage)."],
        names = ["-a", "--write-alignments"]
    )
    var isClnaOutput = false

    @CommandLine.Option(
        description = ["If tags are present, do assemble pre-clones on the cell level rather than on the molecule level. " +
                "If there are no molecular tags in the data, but cell tags are present, this option will be used by default. " +
                "This option has no effect on the data without tags."], names = ["--cell-level"]
    )
    var cellLevel = false

    @CommandLine.Option(names = ["-O"], description = ["Overrides default parameter values."])
    private val overrides: Map<String, String> = mutableMapOf()

    @CommandLine.Option(names = ["-P"], description = ["Overrides default pre-clone assembler parameter values."])
    private val preCloneAssemblerOverrides: Map<String, String> = mutableMapOf()
    override fun getInputFiles(): List<String> = listOf(`in`)

    override fun getOutputFiles(): List<String> = listOf(out)

    // Extracting V/D/J/C gene list from input vdjca file
    private lateinit var genes: List<VDJCGene>
    private lateinit var info: MiXCRMetaInfo
    lateinit var cloneAssemblerParameters: CloneAssemblerParameters
    private lateinit var ordering: CloneOrdering

    fun ensureParametersInitialized() {
        if (this::info.isInitialized) return
        VDJCAlignmentsReader(`in`, VDJCLibraryRegistry.getDefault()).use { reader ->
            genes = reader.usedGenes
            // Saving aligner parameters to correct assembler parameters
            info = reader.info
        }

        //set aligner parameters
        cloneAssemblerParameters = CloneAssemblerParametersPresets.getByName(assemblerParametersName)
            ?: throwValidationExceptionKotlin("Unknown parameters: $assemblerParametersName")
        cloneAssemblerParameters = cloneAssemblerParameters.updateFrom(info.alignerParameters)

        // Overriding JSON parameters
        if (overrides.isNotEmpty()) {
            cloneAssemblerParameters = JsonOverrider.override(
                cloneAssemblerParameters, CloneAssemblerParameters::class.java,
                overrides
            ) ?: throwValidationExceptionKotlin("Failed to override some parameter: $overrides")
        }
        ordering = if (sortBySequence) {
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
    }

    /**
     * Assemble report
     */
    private val reportBuilder = CloneAssemblerReportBuilder()

    override fun run0() {
        ensureParametersInitialized()
        // Saving initial timestamp
        val beginTimestamp = System.currentTimeMillis()

        // Will be used for several operations requiring disk offloading
        val tempDest = TempFileManager.smartTempDestination(out, "", useSystemTemp)

        // Checking consistency between actionParameters.doWriteClnA() value and file extension
        if (out.lowercase(Locale.getDefault())
                .endsWith(".clna") && !isClnaOutput || out.lowercase(Locale.getDefault())
                .endsWith(".clns") && isClnaOutput
        ) warn(
            """
    WARNING: Unexpected file extension, use .clns extension for clones-only (normal) output and
    .clna if -a / --write-alignments options specified.
    """.trimIndent()
        )
        VDJCAlignmentsReader(`in`).use { alignmentsReader ->
            CloneAssembler(
                cloneAssemblerParameters,
                isClnaOutput,
                genes,
                info.alignerParameters.featuresToAlignMap
            ).use { assembler ->
                // Creating event listener to collect run statistics
                reportBuilder.setStartMillis(beginTimestamp)
                reportBuilder.setInputFiles(`in`)
                reportBuilder.setOutputFiles(out)
                reportBuilder.commandLine = commandLineArguments
                assembler.setListener(reportBuilder)
                val preClones: PreCloneReader =
                    if (info.tagsInfo.hasTagsWithType(TagType.Cell) || info.tagsInfo.hasTagsWithType(TagType.Molecule)) {
                        val preClonesFile = tempDest.resolvePath("preclones.pc")
                        val params: PreCloneAssemblerParameters = run {
                            val defaultParameters = PreCloneAssemblerParameters.getDefaultParameters(cellLevel)
                            when {
                                preCloneAssemblerOverrides.isNotEmpty() -> {
                                    JsonOverrider.override(
                                        defaultParameters,
                                        PreCloneAssemblerParameters::class.java,
                                        preCloneAssemblerOverrides
                                    )
                                        ?: throwValidationExceptionKotlin("Failed to override some pre-clone assembler parameters: $preCloneAssemblerOverrides")
                                }
                                else -> defaultParameters
                            }
                        }
                        val assemblerRunner = PreCloneAssemblerRunner(
                            alignmentsReader,
                            if (cellLevel) TagType.Cell else TagType.Molecule,
                            cloneAssemblerParameters.assemblingFeatures,
                            params, preClonesFile, tempDest.addSuffix("pc.tmp")
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
                    assemblerRunner.getCloneSet(info.withAssemblerParameters(cloneAssemblerParameters)),
                    ordering
                )

                // Passing final cloneset to assemble last pieces of statistics for report
                reportBuilder.onClonesetFinished(cloneSet)
                assert(cloneSet.clones.size == reportBuilder.cloneCount)
                reportBuilder.setTotalReads(alignmentsReader.numberOfReads)


                // Writing results
                var report: CloneAssemblerReport
                if (isClnaOutput) {
                    ClnAWriter(out, tempDest, highCompression).use { writer ->

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
                    ClnsWriter(out).use { writer ->
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

    companion object {
        const val ASSEMBLE_COMMAND_NAME = "assemble"
    }
}
