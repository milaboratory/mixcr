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
import cc.redberry.pipe.util.StatusReporter
import com.fasterxml.jackson.annotation.JsonMerge
import com.fasterxml.jackson.annotation.JsonProperty
import com.milaboratory.cli.POverridesBuilderOps
import com.milaboratory.mitool.data.MinGroupsPerGroup
import com.milaboratory.mixcr.MiXCRCommandDescriptor
import com.milaboratory.mixcr.MiXCRParams
import com.milaboratory.mixcr.MiXCRParamsBundle
import com.milaboratory.mixcr.assembler.AlignmentsMappingMerger
import com.milaboratory.mixcr.assembler.CloneAssembler
import com.milaboratory.mixcr.assembler.CloneAssemblerParameters
import com.milaboratory.mixcr.assembler.CloneAssemblerRunner
import com.milaboratory.mixcr.assembler.ReadToCloneMapping.DROPPED_WITH_CLONE_MASK
import com.milaboratory.mixcr.assembler.preclone.PreCloneAssemblerParameters
import com.milaboratory.mixcr.assembler.preclone.PreCloneAssemblerRunner
import com.milaboratory.mixcr.assembler.preclone.PreCloneReader
import com.milaboratory.mixcr.basictypes.ClnAWriter
import com.milaboratory.mixcr.basictypes.ClnsWriter
import com.milaboratory.mixcr.basictypes.CloneSet
import com.milaboratory.mixcr.basictypes.VDJCAlignments
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader
import com.milaboratory.mixcr.basictypes.VDJCSProperties
import com.milaboratory.mixcr.basictypes.tag.TagCount
import com.milaboratory.mixcr.basictypes.tag.TagType
import com.milaboratory.mixcr.cli.CommonDescriptions.DEFAULT_VALUE_FROM_PRESET
import com.milaboratory.mixcr.cli.CommonDescriptions.Labels
import com.milaboratory.primitivio.map
import com.milaboratory.util.ArraysUtils
import com.milaboratory.util.ReportUtil
import com.milaboratory.util.SmartProgressReporter
import com.milaboratory.util.TempFileManager
import gnu.trove.map.hash.TIntObjectHashMap
import io.repseq.core.GeneFeature.CDR3
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path
import kotlin.io.path.extension

object CommandAssemble {
    const val COMMAND_NAME = "assemble"

    data class Params(
        @JsonProperty("sortBySequence") val sortBySequence: Boolean,
        @JsonProperty("clnaOutput") val clnaOutput: Boolean,
        @JsonProperty("cellLevel") val cellLevel: Boolean,
        @JsonProperty("consensusAssemblerParameters") @JsonMerge val consensusAssemblerParameters: PreCloneAssemblerParameters?,
        @JsonProperty("cloneAssemblerParameters") @JsonMerge val cloneAssemblerParameters: CloneAssemblerParameters,
        /** Try automatically infer threshold value for the minimal number of records per consensus from the
         * filtering metadata of tag-refinement step. Applied only if corresponding threshold equals to 0. */
        @JsonProperty("inferMinRecordsPerConsensus") val inferMinRecordsPerConsensus: Boolean,
    ) : MiXCRParams {
        override val command = MiXCRCommandDescriptor.assemble
    }

    abstract class CmdBase : MiXCRCommandWithOutputs(), MiXCRPresetAwareCommand<Params> {
        @Option(
            description = ["If this option is specified, output file will be written in \"Clones & " +
                    "Alignments\" format (*.clna), containing clones and all corresponding alignments. " +
                    "This file then can be used to build wider contigs for clonal sequence or extract original " +
                    "reads for each clone (if -OsaveOriginalReads=true was use on 'align' stage).",
                DEFAULT_VALUE_FROM_PRESET],
            names = ["-a", "--write-alignments"]
        )
        private var isClnaOutput = false

        @Option(
            description = [
                "If tags are present, do assemble pre-clones on the cell level rather than on the molecular level.",
                "If there are no molecular tags in the data, but cell tags are present, this option will be used by default.",
                "This option has no effect on the data without tags.",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["--cell-level"]
        )
        private var cellLevel = false

        @Option(
            description = [
                "Sort by sequence. Clones in the output file will be sorted by clonal sequence," +
                        "which allows to build overlaps between clonesets.",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["-s", "--sort-by-sequence"]
        )
        private var sortBySequence = false

        @Option(
            names = ["-O"],
            description = ["Overrides default parameter values."],
            paramLabel = Labels.OVERRIDES,
            order = 100_000
        )
        private val cloneAssemblerOverrides: Map<String, String> = mutableMapOf()

        @Option(
            names = ["-P"],
            description = ["Overrides default pre-clone assembler parameter values."],
            paramLabel = Labels.OVERRIDES,
            order = 100_000 + 1
        )
        private val consensusAssemblerOverrides: Map<String, String> = mutableMapOf()

        @Option(
            description = [
                "Turns off automatic inference of minRecordsPerConsensus parameter.",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["--dont-infer-threshold"]
        )
        private var dontInferThreshold = false

        @Mixin
        private var mixins: AssembleMiXCRMixins? = null

        protected val mixinsToAdd get() = mixins?.mixins ?: emptyList()

        override val paramsResolver = object : MiXCRParamsResolver<Params>(MiXCRParamsBundle::assemble) {
            override fun POverridesBuilderOps<Params>.paramsOverrides() {
                Params::clnaOutput setIfTrue isClnaOutput
                Params::cellLevel setIfTrue cellLevel
                Params::sortBySequence setIfTrue sortBySequence
                Params::cloneAssemblerParameters jsonOverrideWith cloneAssemblerOverrides
                Params::consensusAssemblerParameters jsonOverrideWith consensusAssemblerOverrides
                Params::inferMinRecordsPerConsensus resetIfTrue dontInferThreshold
            }
        }
    }

    @Command(
        name = COMMAND_NAME,
        description = ["Assemble clones."]
    )
    class Cmd : CmdBase() {
        @Parameters(
            description = ["Path to input file with alignments."],
            paramLabel = "alignments.vdjca",
            index = "0"
        )
        lateinit var inputFile: Path

        @Parameters(
            description = ["Path where to write assembled clones."],
            paramLabel = "clones.[clns|clna]",
            index = "1"
        )
        lateinit var outputFile: Path

        @Suppress("unused", "UNUSED_PARAMETER")
        @Option(
            description = ["Use system temp folder for temporary files."],
            names = ["--use-system-temp"],
            hidden = true
        )
        fun useSystemTemp(value: Boolean) {
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
            description = ["Use higher compression for output file."],
            names = ["--high-compression"]
        )
        var highCompression = false

        @Mixin
        lateinit var reportOptions: ReportOptions

        @Option(
            description = ["Show buffer statistics."],
            names = ["--buffers"],
            hidden = true
        )
        var reportBuffers = false

        override val inputFiles
            get() = listOf(inputFile)

        override val outputFiles
            get() = listOf(outputFile)

        /**
         * Assemble report
         */
        private val reportBuilder = CloneAssemblerReportBuilder()

        override fun run0() {
            // Saving initial timestamp
            val beginTimestamp = System.currentTimeMillis()

            val numberOfAlignments: Long

            val cmdParam: Params
            VDJCAlignmentsReader(inputFile).use { alignmentsReader ->
                val inputHeader = alignmentsReader.header
                val inputFooter = alignmentsReader.footer
                numberOfAlignments = alignmentsReader.numberOfAlignments

                cmdParam = paramsResolver.resolve(inputHeader.paramsSpec.addMixins(mixinsToAdd)) { cp ->
                    if (!cp.inferMinRecordsPerConsensus || cp.consensusAssemblerParameters == null)
                        return@resolve cp

                    if (cp.consensusAssemblerParameters.assembler.minRecordsPerConsensus != 0) {
                        println(
                            "WARNING: minRecordsPerConsensus has non default value (not equal to 0), the automatic " +
                                    "inference of this parameter was canceled."
                        )
                        return@resolve cp
                    }

                    val groupingLevel = if (cp.cellLevel) TagType.Cell else TagType.Molecule
                    val groupingTags = (0 until inputHeader.tagsInfo.getDepthFor(groupingLevel))
                        .map { i -> inputHeader.tagsInfo[i].name }

                    val threshold = inputFooter.thresholds[MinGroupsPerGroup(groupingTags, null)]
                    if (threshold == null) {
                        println(
                            "No data to automatically infer minRecordsPerConsensus. Using default value: " +
                                    cp.consensusAssemblerParameters.assembler.minRecordsPerConsensus
                        )
                        cp
                    } else {
                        println("Value for minRecordsPerConsensus was automatically inferred and set to ${threshold.toInt()}")
                        cp.copy(
                            consensusAssemblerParameters = cp.consensusAssemblerParameters
                                .mapAssembler { it.withMinRecordsPerConsensus(threshold.toInt()) }
                        )
                    }
                }.second

                // Checking consistency between actionParameters.doWriteClnA() value and file extension
                if ((outputFile.extension == "clna" && !cmdParam.clnaOutput) ||
                    (outputFile.extension == "clns" && cmdParam.clnaOutput)
                ) logger.warn("Unexpected file extension, use .clns extension for clones-only (normal) output and .clna if -a / --write-alignments options specified.")

                // set aligner parameters
                val cloneAssemblerParameters =
                    cmdParam.cloneAssemblerParameters.updateFrom(inputHeader.alignerParameters)

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
                    inputHeader.tagsInfo,
                    cloneAssemblerParameters,
                    cmdParam.clnaOutput,
                    alignmentsReader.usedGenes,
                    inputHeader.alignerParameters.featuresToAlignMap
                ).use { assembler ->
                    // Creating event listener to collect run statistics
                    reportBuilder.setStartMillis(beginTimestamp)
                    reportBuilder.setInputFiles(inputFile)
                    reportBuilder.setOutputFiles(outputFile)
                    reportBuilder.commandLine = commandLineArguments
                    assembler.setListener(reportBuilder)
                    val preClones: PreCloneReader =
                        if (
                            inputHeader.tagsInfo.hasTagsWithType(TagType.Cell) ||
                            inputHeader.tagsInfo.hasTagsWithType(TagType.Molecule)
                        ) {
                            val preClonesFile = tempDest.resolvePath("preclones.pc")

                            val groupingLevel = if (cmdParam.cellLevel) TagType.Cell else TagType.Molecule
                            val assemblerRunner = PreCloneAssemblerRunner(
                                alignmentsReader,
                                groupingLevel,
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
                        assemblerRunner.getCloneSet(
                            inputHeader
                                .withAssemblerParameters(cloneAssemblerParameters)
                                .addStepParams(MiXCRCommandDescriptor.assemble, cmdParam),
                            inputFooter
                        ),
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

                            // Preparing a cloneId -> tag count map for filtering
                            // (read below)
                            val cloneTagCounts = TIntObjectHashMap<TagCount>(cloneSet.size())
                            for (clone in cloneSet)
                                if (cloneTagCounts.put(clone.id, clone.tagCount) != null)
                                    throw IllegalStateException("Repeated clone id.")

                            AlignmentsMappingMerger(preClones.readAlignments(), assembler.assembledReadsPort)
                                .use { merged ->
                                    // Because clone's tag counts may be additionally filtered in post filtering,
                                    // it is important to take this into account here, to drop those alignments
                                    // with dropped tags / tag prefixes
                                    val filteredByTags: OutputPort<VDJCAlignments> = merged.map {
                                        if (it.cloneIndex == -1L)
                                            return@map it
                                        val cloneTagCount = cloneTagCounts.get(it.cloneIndex.toInt())!!
                                        val prefixes = it.tagCount.reduceToLevel(cloneTagCount.depth())
                                        if (!cloneTagCount.containsAll(prefixes.tuples())) {
                                            reportBuilder.onAlignmentFilteredByPrefix(it)
                                            // Dropped with clone semantically fits the case the most
                                            return@map it.withCloneIndexAndMappingType(-1, DROPPED_WITH_CLONE_MASK)
                                        }
                                        it
                                    }
                                    writer.collateAlignments(filteredByTags, numberOfAlignments)
                                }
                            reportBuilder.setFinishMillis(System.currentTimeMillis())
                            report = reportBuilder.buildReport()
                            writer.setFooter(
                                alignmentsReader.footer.addStepReport(
                                    MiXCRCommandDescriptor.assemble,
                                    report
                                )
                            )
                            writer.writeAlignmentsAndIndex()
                        }
                    } else {
                        ClnsWriter(outputFile).use { writer ->
                            writer.writeCloneSet(cloneSet)
                            reportBuilder.setFinishMillis(System.currentTimeMillis())
                            report = reportBuilder.buildReport()
                            writer.setFooter(
                                alignmentsReader.footer.addStepReport(
                                    MiXCRCommandDescriptor.assemble,
                                    report
                                )
                            )
                        }
                    }

                    // Writing report to stout
                    ReportUtil.writeReportToStdout(report)
                    reportOptions.appendToFiles(report)
                }
            }
        }
    }
}

