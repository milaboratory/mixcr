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
import cc.redberry.pipe.blocks.ParallelProcessor
import com.fasterxml.jackson.annotation.JsonProperty
import com.milaboratory.cli.POverridesBuilderOps
import com.milaboratory.mixcr.MiXCRCommandDescriptor
import com.milaboratory.mixcr.MiXCRParams
import com.milaboratory.mixcr.MiXCRParamsBundle
import com.milaboratory.mixcr.MiXCRParamsSpec
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
import com.milaboratory.mixcr.util.VDJCObjectExtender
import com.milaboratory.mixcr.util.VDJCObjectExtenderReport
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters
import com.milaboratory.primitivio.asSequence
import com.milaboratory.primitivio.port
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
    const val COMMAND_NAME = "extend"

    data class Params(
        @JsonProperty("vAnchor") val vAnchor: ReferencePoint,
        @JsonProperty("jAnchor") val jAnchor: ReferencePoint,
        @JsonProperty("minimalVScore") val minimalVScore: Int,
        @JsonProperty("minimalJScore") val minimalJScore: Int
    ) : MiXCRParams {
        override val command = MiXCRCommandDescriptor.extend
    }

    abstract class CmdBase : MiXCRCommandWithOutputs(), MiXCRPresetAwareCommand<Params> {
        @Option(
            description = ["V extension anchor point.", DEFAULT_VALUE_FROM_PRESET],
            names = ["--v-anchor"],
            paramLabel = Labels.ANCHOR_POINT
        )
        private var vAnchorPoint: ReferencePoint? = null

        @Option(
            description = ["J extension anchor point.", DEFAULT_VALUE_FROM_PRESET],
            names = ["--j-anchor"],
            paramLabel = Labels.ANCHOR_POINT
        )
        private var jAnchorPoint: ReferencePoint? = null

        @Option(
            description = ["Minimal V hit score to perform left extension.", DEFAULT_VALUE_FROM_PRESET],
            names = ["--min-v-score"],
            paramLabel = "<n>"
        )
        private var minimalVScore: Int? = null

        @Option(
            description = ["Minimal J hit score to perform right extension.", DEFAULT_VALUE_FROM_PRESET],
            names = ["--min-j-score"],
            paramLabel = "<n>"
        )
        private var minimalJScore: Int? = null

        override val paramsResolver = object : MiXCRParamsResolver<Params>(MiXCRParamsBundle::extend) {
            override fun POverridesBuilderOps<Params>.paramsOverrides() {
                Params::vAnchor setIfNotNull vAnchorPoint
                Params::jAnchor setIfNotNull jAnchorPoint
                Params::minimalVScore setIfNotNull minimalVScore
                Params::minimalJScore setIfNotNull minimalJScore
            }
        }
    }

    @Command(
        description = ["Impute alignments or clones with germline sequences."]
    )
    class Cmd : CmdBase() {
        @Parameters(
            description = ["Path to input file."],
            paramLabel = "data.[vdjca|clns|clna]",
            index = "0"
        )
        lateinit var inputFile: Path

        @Parameters(
            description = ["Path where to write output. Will have the same file type."],
            paramLabel = "extendeed.[vdjca|clns|clna]",
            index = "1"
        )
        lateinit var outputFile: Path

        @Option(
            description = ["Apply procedure only to alignments with specific immunological-receptor chains."],
            names = ["-c", "--chains"],
            paramLabel = Labels.CHAINS,
            showDefaultValue = ALWAYS
        )
        var chains: Chains = Chains.TCR

        @Mixin
        lateinit var reportOptions: ReportOptions

        @Option(
            description = ["Quality score value to assign imputed sequences."],
            names = ["-q", "--quality"],
            paramLabel = "<n>",
            showDefaultValue = ALWAYS
        )
        var extensionQuality: Byte = 30

        @Mixin
        lateinit var threadsOption: ThreadsOption

        override val inputFiles
            get() = listOf(inputFile)

        override val outputFiles
            get() = listOf(outputFile)

        override fun run0() {
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
                val outputPort = cloneSet.port
                val process = processWrapper(outputPort, reader.header.paramsSpec, cloneSet.alignmentParameters)

                val clones = process.output
                    .asSequence()
                    .map { clone -> clone.resetParentCloneSet() }
                    .sortedBy { it.id }
                    .toList()
                val newCloneSet =
                    CloneSet(
                        clones,
                        cloneSet.usedGenes,
                        cloneSet.header
                            .addStepParams(MiXCRCommandDescriptor.extend, process.params)
                            .copy(allFullyCoveredBy = null),
                        cloneSet.footer,
                        cloneSet.ordering
                    )
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
                    writer.inheritHeaderAndFooterFrom(reader)
                    val process = processWrapper(reader, reader.header.paramsSpec, reader.parameters)

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
            val (_, cmdParams) = paramsResolver.resolve(paramsSpec, printParameters = logger.verbose)

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
            val params: Params,
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
