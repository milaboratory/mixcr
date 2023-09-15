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
import cc.redberry.pipe.blocks.ParallelProcessor
import cc.redberry.pipe.util.asOutputPort
import cc.redberry.pipe.util.asSequence
import cc.redberry.pipe.util.toList
import com.milaboratory.app.InputFileType
import com.milaboratory.app.ValidationException
import com.milaboratory.cli.POverridesBuilderOps
import com.milaboratory.mixcr.basictypes.ClnsReader
import com.milaboratory.mixcr.basictypes.ClnsWriter
import com.milaboratory.mixcr.basictypes.CloneSet
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.CLNA
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.CLNS
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.SHMT
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.VDJCA
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter
import com.milaboratory.mixcr.basictypes.VDJCObject
import com.milaboratory.mixcr.cli.CommonDescriptions.DEFAULT_VALUE_FROM_PRESET
import com.milaboratory.mixcr.cli.CommonDescriptions.Labels
import com.milaboratory.mixcr.presets.MiXCRCommandDescriptor
import com.milaboratory.mixcr.presets.MiXCRParamsBundle
import com.milaboratory.mixcr.presets.MiXCRParamsSpec
import com.milaboratory.mixcr.util.VDJCObjectExtender
import com.milaboratory.mixcr.util.VDJCObjectExtenderReport
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters
import com.milaboratory.util.ReportUtil
import com.milaboratory.util.SmartProgressReporter
import io.repseq.core.Chains
import io.repseq.core.ReferencePoint
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine.Command
import picocli.CommandLine.Help.Visibility.ALWAYS
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path

object CommandExtend {
    const val COMMAND_NAME = MiXCRCommandDescriptor.extend.name

    abstract class CmdBase : MiXCRCommandWithOutputs(), MiXCRPresetAwareCommand<CommandExtendParams> {
        @Option(
            description = ["V extension anchor point.", DEFAULT_VALUE_FROM_PRESET],
            names = ["--v-anchor"],
            paramLabel = Labels.ANCHOR_POINT,
            order = OptionsOrder.main + 10_100,
            completionCandidates = ReferencePointsCandidates::class
        )
        private var vAnchorPoint: ReferencePoint? = null

        @Option(
            description = ["J extension anchor point.", DEFAULT_VALUE_FROM_PRESET],
            names = ["--j-anchor"],
            paramLabel = Labels.ANCHOR_POINT,
            order = OptionsOrder.main + 10_200,
            completionCandidates = ReferencePointsCandidates::class
        )
        private var jAnchorPoint: ReferencePoint? = null

        @Option(
            description = ["Minimal V hit score to perform left extension.", DEFAULT_VALUE_FROM_PRESET],
            names = ["--min-v-score"],
            paramLabel = "<n>",
            order = OptionsOrder.main + 10_300
        )
        private var minimalVScore: Int? = null

        @Option(
            description = ["Minimal J hit score to perform right extension.", DEFAULT_VALUE_FROM_PRESET],
            names = ["--min-j-score"],
            paramLabel = "<n>",
            order = OptionsOrder.main + 10_400
        )
        private var minimalJScore: Int? = null

        override val paramsResolver = object : MiXCRParamsResolver<CommandExtendParams>(MiXCRParamsBundle::extend) {
            override fun POverridesBuilderOps<CommandExtendParams>.paramsOverrides() {
                CommandExtendParams::vAnchor setIfNotNull vAnchorPoint
                CommandExtendParams::jAnchor setIfNotNull jAnchorPoint
                CommandExtendParams::minimalVScore setIfNotNull minimalVScore
                CommandExtendParams::minimalJScore setIfNotNull minimalJScore
            }
        }
    }

    @Command(
        description = ["Impute alignments or clones with germline sequences."]
    )
    class Cmd : CmdBase() {
        @Parameters(
            description = ["Path to input file."],
            paramLabel = "data.(vdjca|clns|clna)",
            index = "0"
        )
        lateinit var inputFile: Path

        @Parameters(
            description = ["Path where to write output. Will have the same file type."],
            paramLabel = "extendeed.(vdjca|clns|clna)",
            index = "1"
        )
        lateinit var outputFile: Path

        @Option(
            description = ["Apply procedure only to alignments with specific immunological-receptor chains."],
            names = ["-c", "--chains"],
            paramLabel = Labels.CHAINS,
            showDefaultValue = ALWAYS,
            order = OptionsOrder.main + 10_500,
            completionCandidates = ChainsCandidates::class
        )
        var chains: Chains = Chains.TCR

        @Mixin
        lateinit var reportOptions: ReportOptions

        @Option(
            description = ["Quality score value to assign imputed sequences."],
            names = ["-q", "--quality"],
            paramLabel = "<n>",
            showDefaultValue = ALWAYS,
            order = OptionsOrder.main + 10_600
        )
        var extensionQuality: Byte = 30

        @Mixin
        lateinit var threadsOption: ThreadsOption

        @Mixin
        lateinit var resetPreset: ResetPresetOptions

        @Mixin
        lateinit var dontSavePresetOption: DontSavePresetOption

        override val inputFiles
            get() = listOf(inputFile)

        override val outputFiles
            get() = listOf(outputFile)

        override fun validate() {
            ValidationException.requireTheSameFileType(
                inputFile,
                outputFile,
                InputFileType.VDJCA, InputFileType.CLNS, InputFileType.CLNA
            )
        }

        override fun run1() {
            when (IOUtil.extractFileType(inputFile)) {
                VDJCA -> processVDJCA()
                CLNS -> processClns()
                CLNA -> throw ValidationException("Operation is not supported for ClnA files.")
                SHMT -> throw ValidationException("Operation is not supported for SHMT files.")
            }
        }

        private fun processClns() {
            ClnsReader(inputFile, VDJCLibraryRegistry.getDefault()).use { reader ->
                val cloneSet = reader.readCloneSet()

                ValidationException.chainsExist(chains, cloneSet.usedGenes)
                val outputPort = cloneSet.asOutputPort()
                val paramsSpec = resetPreset.overridePreset(reader.header.paramsSpec)
                val process = processWrapper(outputPort, paramsSpec, cloneSet.header.alignerParameters!!)

                val newCloneSet = CloneSet.Builder(
                    process.output.toList(),
                    cloneSet.usedGenes,
                    cloneSet.header
                        .addStepParams(MiXCRCommandDescriptor.extend, process.params)
                        .copy(allFullyCoveredBy = null)
                        .copy(paramsSpec = dontSavePresetOption.presetToSave(paramsSpec))
                )
                    .sort(cloneSet.ordering)
                    .withTotalCounts(cloneSet.counts)
                    .build()
                ClnsWriter(outputFile).use { writer ->
                    writer.writeCloneSet(newCloneSet)
                    val report = process.finish()
                    writer.setFooter(reader.footer.addStepReport(MiXCRCommandDescriptor.extend, report))
                }
            }
        }

        private fun processVDJCA() {
            VDJCAlignmentsReader(inputFile).use { reader ->
                VDJCAlignmentsWriter(outputFile).use { writer ->
                    SmartProgressReporter.startProgressReport("Extending alignments", reader)
                    val paramsSpec = resetPreset.overridePreset(reader.header.paramsSpec)
                    val process = processWrapper(reader, paramsSpec, reader.parameters)
                    writer.writeHeader(
                        reader.header
                            .copy(paramsSpec = dontSavePresetOption.presetToSave(paramsSpec))
                            .addStepParams(MiXCRCommandDescriptor.extend, process.params),
                        reader.usedGenes
                    )
                    writer.setFooter(reader.footer)

                    // Shifting indels in homopolymers is effective only for alignments build with linear gap scoring,
                    // consolidating some gaps, on the contrary, for alignments obtained with affine scoring such procedure
                    // may break the alignment (gaps there are already consolidated as much as possible)
                    val gtRequiringIndelShifts = reader.parameters.geneTypesWithLinearScoring
                    process.output
                        .asSequence()
                        .sortedBy { it.alignmentsIndex }
                        .forEach { alignments ->
                            writer.write(alignments.shiftIndelsAtHomopolymers(gtRequiringIndelShifts))
                        }
                    writer.setNumberOfProcessedReads(reader.numberOfReads)
                    val report = process.finish()
                    writer.setFooter(reader.footer.addStepReport(MiXCRCommandDescriptor.extend, report))
                }
            }
        }

        private fun <T : VDJCObject> processWrapper(
            input: OutputPort<T>,
            paramsSpec: MiXCRParamsSpec,
            alignerParameters: VDJCAlignerParameters
        ): ProcessWrapper<T> {
            val (_, cmdParams) = paramsResolver.resolve(paramsSpec)

            val extender = VDJCObjectExtender<T>(
                chains, extensionQuality,
                alignerParameters.vAlignerParameters.scoring,
                alignerParameters.jAlignerParameters.scoring,
                cmdParams.minimalVScore, cmdParams.minimalJScore,
                cmdParams.vAnchor, cmdParams.jAnchor
            )
            val output = ParallelProcessor(input, extender, threadsOption.value)
            extender.setStartMillis(System.currentTimeMillis())
            extender.setInputFiles(inputFile)
            extender.setOutputFiles(outputFile)
            extender.commandLine = commandLineArguments
            return ProcessWrapper(extender, output, cmdParams)
        }

        private inner class ProcessWrapper<T : VDJCObject>(
            val reportBuilder: VDJCObjectExtender<T>,
            val output: ParallelProcessor<T, T>,
            val params: CommandExtendParams,
        ) {
            fun finish(): VDJCObjectExtenderReport {
                reportBuilder.setFinishMillis(System.currentTimeMillis())
                val report = reportBuilder.buildReport()!!
                // Writing report to stout
                ReportUtil.writeReportToStdout(report)
                reportOptions.appendToFiles(report)
                return report
            }
        }
    }
}
