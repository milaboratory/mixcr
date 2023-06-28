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

import cc.redberry.pipe.OutputPort
import cc.redberry.pipe.util.StatusReporter
import cc.redberry.pipe.util.map
import com.milaboratory.app.InputFileType
import com.milaboratory.app.ValidationException
import com.milaboratory.app.logger
import com.milaboratory.cli.POverridesBuilderOps
import com.milaboratory.core.Range
import com.milaboratory.mitool.consensus.ConsensusResult
import com.milaboratory.mitool.data.MinGroupsPerGroup
import com.milaboratory.mixcr.assembler.AlignmentsMappingMerger
import com.milaboratory.mixcr.assembler.CloneAssembler
import com.milaboratory.mixcr.assembler.CloneAssemblerRunner
import com.milaboratory.mixcr.assembler.ReadToCloneMapping.DROPPED_WITH_CLONE_MASK
import com.milaboratory.mixcr.assembler.preclone.PreCloneAssembler
import com.milaboratory.mixcr.assembler.preclone.PreCloneAssemblerRunner
import com.milaboratory.mixcr.assembler.preclone.PreCloneRawConsensusListener
import com.milaboratory.mixcr.assembler.preclone.PreCloneReader
import com.milaboratory.mixcr.basictypes.ClnAWriter
import com.milaboratory.mixcr.basictypes.ClnsWriter
import com.milaboratory.mixcr.basictypes.CloneSet.Companion.reorder
import com.milaboratory.mixcr.basictypes.HasFeatureToAlign
import com.milaboratory.mixcr.basictypes.VDJCAlignments
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader
import com.milaboratory.mixcr.basictypes.VDJCSProperties
import com.milaboratory.mixcr.basictypes.tag.TagCount
import com.milaboratory.mixcr.basictypes.tag.TagTuple
import com.milaboratory.mixcr.basictypes.tag.TagType
import com.milaboratory.mixcr.basictypes.validateCompositeFeatures
import com.milaboratory.mixcr.cli.CommonDescriptions.DEFAULT_VALUE_FROM_PRESET
import com.milaboratory.mixcr.cli.CommonDescriptions.Labels
import com.milaboratory.mixcr.presets.MiXCRCommandDescriptor
import com.milaboratory.mixcr.presets.MiXCRParamsBundle
import com.milaboratory.util.ArraysUtils
import com.milaboratory.util.HashFunctions
import com.milaboratory.util.ReportUtil
import com.milaboratory.util.SmartProgressReporter
import com.milaboratory.util.TempFileManager
import com.milaboratory.util.use
import gnu.trove.map.hash.TIntObjectHashMap
import io.repseq.core.GeneFeature.CDR3
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import org.apache.commons.math3.random.RandomDataGenerator
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.BufferedWriter
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.path.bufferedWriter
import kotlin.io.path.extension

object CommandAssemble {
    const val COMMAND_NAME = MiXCRCommandDescriptor.assemble.name

    abstract class CmdBase : MiXCRCommandWithOutputs(), MiXCRPresetAwareCommand<CommandAssembleParams> {
        @Option(
            description = ["If this option is specified, output file will be written in \"Clones & " +
                    "Alignments\" format (*.clna), containing clones and all corresponding alignments. " +
                    "This file then can be used to build wider contigs for clonal sequence or extract original " +
                    "reads for each clone (if -OsaveOriginalReads=true was use on 'align' stage).",
                DEFAULT_VALUE_FROM_PRESET],
            names = ["-a", "--write-alignments"],
            order = OptionsOrder.main + 10_100
        )
        private var isClnaOutput = false

        @Option(
            description = [
                "If tags are present, do assemble pre-clones on the cell level rather than on the molecular level.",
                "If there are no molecular tags in the data, but cell tags are present, this option will be used by default.",
                "This option has no effect on the data without tags.",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["--cell-level"],
            order = OptionsOrder.main + 10_200
        )
        private var cellLevel = false

        @Option(
            description = [
                "Sort by sequence. Clones in the output file will be sorted by clonal sequence," +
                        "which allows to build overlaps between clonesets.",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["-s", "--sort-by-sequence"],
            order = OptionsOrder.main + 10_300
        )
        private var sortBySequence = false

        @Option(
            names = ["-O"],
            description = ["Overrides default parameter values."],
            paramLabel = Labels.OVERRIDES,
            order = OptionsOrder.overrides
        )
        private val cloneAssemblerOverrides: Map<String, String> = mutableMapOf()

        @Option(
            names = ["-P"],
            description = ["Overrides default pre-clone assembler parameter values."],
            paramLabel = Labels.OVERRIDES,
            order = OptionsOrder.overrides + 100
        )
        private val consensusAssemblerOverrides: Map<String, String> = mutableMapOf()

        @Option(
            description = [
                "Turns off automatic inference of minRecordsPerConsensus parameter.",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["--dont-infer-threshold"],
            order = OptionsOrder.main + 10_400
        )
        private var dontInferThreshold = false

        override val paramsResolver = object : MiXCRParamsResolver<CommandAssembleParams>(MiXCRParamsBundle::assemble) {
            override fun POverridesBuilderOps<CommandAssembleParams>.paramsOverrides() {
                CommandAssembleParams::clnaOutput setIfTrue isClnaOutput
                CommandAssembleParams::cellLevel setIfTrue cellLevel
                CommandAssembleParams::sortBySequence setIfTrue sortBySequence
                CommandAssembleParams::cloneAssemblerParameters jsonOverrideWith cloneAssemblerOverrides
                CommandAssembleParams::consensusAssemblerParameters jsonOverrideWith consensusAssemblerOverrides
                CommandAssembleParams::inferMinRecordsPerConsensus resetIfTrue dontInferThreshold
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
            paramLabel = "clones.(clns|clna)",
            index = "1"
        )
        lateinit var outputFile: Path

        @Mixin
        lateinit var useLocalTemp: UseLocalTempOption

        private val tempDest by lazy {
            TempFileManager.smartTempDestination(outputFile, "", !useLocalTemp.value)
        }

        @Option(
            description = ["Use higher compression for output file."],
            names = ["--high-compression"],
            order = OptionsOrder.main + 10_500
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

        @Option(
            description = ["Write consensus alignments"],
            names = ["--consensus-alignments"],
            hidden = true,
        )
        var consensusAlignments: Path? = null

        @Option(
            description = ["Write consensus state statistics"],
            names = ["--consensus-state-stat"],
            hidden = true,
        )
        var consensusStateStats: Path? = null

        @Option(
            description = ["Write consensus state statistics"],
            names = ["--downsample-consensus-state-stat"],
            hidden = true,
        )
        var consensusStateStatsDownsampling: Double? = null

        @Mixin
        lateinit var resetPreset: ResetPresetOptions

        @Mixin
        lateinit var dontSavePresetOption: DontSavePresetOption

        @Mixin
        private var assembleMixins: AssembleMiXCRMixins? = null

        private val mixins get() = assembleMixins?.mixins ?: emptyList()

        override val inputFiles
            get() = listOf(inputFile)

        override val outputFiles
            get() = listOf(outputFile)

        /**
         * Assemble report
         */
        private val reportBuilder = CloneAssemblerReportBuilder()

        override fun validate() {
            ValidationException.requireFileType(inputFile, InputFileType.VDJCA)
            ValidationException.requireFileType(outputFile, InputFileType.CLNX)
        }

        override fun run1() {
            // Saving initial timestamp
            val beginTimestamp = System.currentTimeMillis()

            VDJCAlignmentsReader(inputFile).use { alignmentsReader ->
                val inputHeader = alignmentsReader.header
                val inputFooter = alignmentsReader.footer
                val numberOfAlignments = alignmentsReader.numberOfAlignments

                val paramSpec = resetPreset.overridePreset(inputHeader.paramsSpec).addMixins(mixins)
                val (_, cmdParam) = paramsResolver.resolve(paramSpec, printParameters = logger.verbose) { cp ->
                    if (!cp.inferMinRecordsPerConsensus || cp.consensusAssemblerParameters == null)
                        return@resolve cp

                    // All the code below executed only if inferMinRecordsPerConsensus == true

                    if (cp.consensusAssemblerParameters!!.assembler.minRecordsPerConsensus != 0) {
                        println(
                            "WARNING: minRecordsPerConsensus has non default value (not equal to 0), the automatic " +
                                    "inference of this parameter was canceled."
                        )
                        return@resolve cp
                    }

                    // Here we search specifically for "reads per UMI" threshold, even if we do cell-level assembly,
                    // as the most meaningful estimation for the minRecordsPerConsensus parameter
                    val groupingTags = (0 until inputHeader.tagsInfo.getDepthFor(TagType.Molecule))
                        .map { i -> inputHeader.tagsInfo[i].name }
                    val threshold = inputFooter.thresholds[MinGroupsPerGroup(groupingTags, null)]
                    if (threshold == null) {
                        println(
                            "No data to automatically infer minRecordsPerConsensus. Using default value: " +
                                    cp.consensusAssemblerParameters!!.assembler.minRecordsPerConsensus
                        )
                        cp
                    } else {
                        println("Value for minRecordsPerConsensus was automatically inferred and set to ${threshold.toInt()}")
                        cp.copy(
                            consensusAssemblerParameters = cp.consensusAssemblerParameters!!
                                .mapAssembler { it.withMinRecordsPerConsensus(threshold.toInt()) }
                        )
                    }
                }

                validateParams(cmdParam, inputHeader)

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
                    inputHeader.featuresToAlignMap
                ).use { assembler ->
                    // Creating event listener to collect run statistics
                    reportBuilder.setStartMillis(beginTimestamp)
                    reportBuilder.setInputFiles(inputFile)
                    reportBuilder.setOutputFiles(outputFile)
                    reportBuilder.commandLine = commandLineArguments
                    assembler.setListener(reportBuilder)
                    val preClones: PreCloneReader =
                        if (
                            (inputHeader.tagsInfo.hasTagsWithType(TagType.Cell) ||
                                    inputHeader.tagsInfo.hasTagsWithType(TagType.Molecule)) &&
                            cmdParam.consensusAssemblerParameters != null
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

                            // Optionally writing additional information about consensus assembly process
                            use(
                                consensusAlignments?.bufferedWriter(bufferSize = 1048576),
                                consensusStateStats?.bufferedWriter(bufferSize = 1048576)
                            ) { cAlignmentsWriter, cStateStatsWriter ->

                                // If any auxiliary output was requested, adding raw consensus listener to pre-clone assembler
                                if (cAlignmentsWriter != null || cStateStatsWriter != null) {
                                    val formatter = ConsensusAlignmentDataFormatter(
                                        cAlignmentsWriter,
                                        cStateStatsWriter,
                                        consensusStateStatsDownsampling
                                    )
                                    formatter.writeHeaders()
                                    assemblerRunner.setRawConsensusListener(true, formatter)
                                }

                                // Pre-clone assembly happens here (file with pre-clones and alignments written as a result)
                                assemblerRunner.run()
                            }

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
                    val resultHeader = inputHeader
                        .withAssemblerParameters(cloneAssemblerParameters)
                        .addStepParams(
                            MiXCRCommandDescriptor.assemble,
                            paramsResolver.resolve(paramSpec, printParameters = logger.verbose).second
                        )
                        .copy(paramsSpec = dontSavePresetOption.presetToSave(paramSpec))
                    val cloneSet = assemblerRunner
                        .getCloneSet(resultHeader)
                        .reorder(ordering)

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

    class ConsensusAlignmentDataFormatter(
        private val cAlignmentsWriter: BufferedWriter?,
        private val cStateStatsWriter: BufferedWriter?,
        private val statDownsample: Double?
    ) : PreCloneRawConsensusListener {
        fun writeHeaders() {
            cStateStatsWriter?.appendLine(
                "TagGroupIdx\tConsensusIdx\tSubConsensusIdx\tReadIdx\tConsensusPosition\t" +
                        "ConsensusLetter\tConsensusQuality\tReadLetter\tReadQuality"
            )
        }

        private val consensusCounterA = AtomicLong()
        private val consensusCounterS = AtomicLong()
        private val tagGroupCounter = AtomicLong()

        override fun onRawConsensuses(
            key: TagTuple,
            consensuses: MutableList<ConsensusResult>,
            alignmentInfos: MutableList<PreCloneAssembler.AlignmentInfo>
        ) {
            val tagGroupIdx = tagGroupCounter.getAndIncrement()
            if (cAlignmentsWriter != null) {
                cAlignmentsWriter.appendLine("=============================")
                cAlignmentsWriter.appendLine("For tags: $key ($tagGroupIdx)")
                for (c in consensuses) {
                    cAlignmentsWriter.appendLine()
                    cAlignmentsWriter.appendLine("Consensus #${consensusCounterA.getAndIncrement()}")
                    c.consensuses.forEachIndexed { subRead, sc ->
                        cAlignmentsWriter.appendLine()
                        if (c.consensuses.size > 1)
                            cAlignmentsWriter.appendLine("SubRead #$subRead")
                        cAlignmentsWriter.appendLine("Consensus:")
                        cAlignmentsWriter.appendLine(sc.consensus.sequence.toString())
                        cAlignmentsWriter.appendLine(sc.consensus.quality.toString())
                        cAlignmentsWriter.appendLine()
                        cAlignmentsWriter.appendLine("Alignments:")
                        for (al in sc.alignments) {
                            if (!c.recordsUsed[al.recordId])
                                continue
                            cAlignmentsWriter.appendLine("Read #${alignmentInfos[al.recordId].minReadId}")
                            cAlignmentsWriter.appendLine(
                                al.alignment.alignmentHelper.toStringWithSeq2Quality(al.sequenceWithQuality.quality)
                            )
                            cAlignmentsWriter.appendLine()
                        }
                    }
                }
            }

            if (cStateStatsWriter != null) {
                val random = RandomDataGenerator()
                random.reSeed(HashFunctions.JenkinWang64shift(tagGroupIdx)) // for reproducible downsampling
                val downsampling = statDownsample ?: 2.0
                for (c in consensuses) {
                    val consensusIdx = consensusCounterS.getAndIncrement()
                    c.consensuses.forEachIndexed { subRead, sc ->
                        val consensusSeq = sc.consensus.sequence.toString()
                        val consensusQual = sc.consensus.quality.toString()
                        for (al in sc.alignments) {
                            if (!c.recordsUsed[al.recordId])
                                continue
                            for (i in al.alignment.sequence1Range.lower until al.alignment.sequence1Range.upper) {
                                if (random.nextUniform(0.0, 1.0, true) >= downsampling)
                                    continue
                                val s1r = Range.createFromOffsetAndLength(i, 1)
                                val s2r = al.alignment.convertToSeq2Range(s1r)
                                // cStateStatsWriter.append(tagTuple.toString()).append('\t')
                                cStateStatsWriter.append(tagGroupIdx.toString())
                                    .append('\t')
                                cStateStatsWriter.append(consensusIdx.toString())
                                    .append('\t')
                                cStateStatsWriter.append(subRead.toString()).append('\t')
                                cStateStatsWriter.append(
                                    alignmentInfos[al.recordId].minReadId
                                        .toString()
                                ).append('\t')
                                cStateStatsWriter.append(i.toString()).append('\t')
                                cStateStatsWriter.append(consensusSeq[i]).append('\t')
                                cStateStatsWriter.append(consensusQual[i]).append('\t')
                                val readState = al.sequenceWithQuality.getRange(s2r)
                                cStateStatsWriter.append(readState.sequence.toString())
                                    .append('\t')
                                cStateStatsWriter.append(readState.quality.toString())
                                    .append('\t')
                                cStateStatsWriter.appendLine()
                            }
                        }
                    }
                }
            }
        }
    }

    fun validateParams(cmdParam: CommandAssembleParams, featureToAlign: HasFeatureToAlign) {
        featureToAlign.validateCompositeFeatures(*cmdParam.cloneAssemblerParameters.assemblingFeatures)
    }
}

