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
import com.milaboratory.mixcr.MiXCRCommand
import com.milaboratory.mixcr.MiXCRParams
import com.milaboratory.mixcr.MiXCRParamsBundle
import com.milaboratory.mixcr.MiXCRParamsSpec
import com.milaboratory.mixcr.basictypes.*
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType
import com.milaboratory.mixcr.util.VDJCObjectExtender
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters
import com.milaboratory.primitivio.asSequence
import com.milaboratory.primitivio.port
import com.milaboratory.util.ReportUtil
import com.milaboratory.util.SmartProgressReporter
import io.repseq.core.Chains
import io.repseq.core.ReferencePoint
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine.*
import java.nio.file.Paths

object CommandExtend {
    const val COMMAND_NAME = "extend"

    data class Params(
        @JsonProperty("vAnchor") val vAnchor: ReferencePoint,
        @JsonProperty("jAnchor") val jAnchor: ReferencePoint,
        @JsonProperty("minimalVScore") val minimalVScore: Int,
        @JsonProperty("minimalJScore") val minimalJScore: Int
    ) : MiXCRParams {
        override val command = MiXCRCommand.extend
    }

    abstract class CmdBase : MiXCRPresetAwareCommand<Params>() {
        @Option(description = ["V extension anchor point."], names = ["--v-anchor"])
        private var vAnchorPoint: String? = null

        @Option(description = ["J extension anchor point."], names = ["--j-anchor"])
        private var jAnchorPoint: String? = null

        @Option(description = ["Minimal V hit score to perform left extension."], names = ["--min-v-score"])
        private var minimalVScore: Int? = null

        @Option(description = ["Minimal J hit score to perform right extension."], names = ["--min-j-score"])
        private var minimalJScore: Int? = null

        override val paramsResolver = object : MiXCRParamsResolver<Params>(this, MiXCRParamsBundle::extend) {
            override fun POverridesBuilderOps<Params>.paramsOverrides() {
                Params::vAnchor setIfNotNull vAnchorPoint?.let(ReferencePoint::parse)
                Params::jAnchor setIfNotNull jAnchorPoint?.let(ReferencePoint::parse)
                Params::minimalVScore setIfNotNull minimalVScore
                Params::minimalJScore setIfNotNull minimalJScore
            }
        }
    }

    @Command(
        name = COMMAND_NAME,
        sortOptions = true,
        separator = " ",
        description = ["Impute alignments or clones with germline sequences."]
    )
    class Cmd : CmdBase() {
        @Parameters(description = ["data.[vdjca|clns|clna]"], index = "0")
        lateinit var inputFile: String

        @Parameters(description = ["extendeed.[vdjca|clns|clna]"], index = "1")
        lateinit var outputFile: String

        @Option(
            description = ["Apply procedure only to alignments with specific immunological-receptor chains."],
            names = ["-c", "--chains"]
        )
        var chains = "TCR"

        @Option(description = [CommonDescriptions.REPORT], names = ["-r", "--report"])
        var reportFile: String? = null

        @Option(description = [CommonDescriptions.JSON_REPORT], names = ["-j", "--json-report"])
        var jsonReport: String? = null

        @Option(description = ["Quality score value to assign imputed sequences"], names = ["-q", "--quality"])
        var extensionQuality: Byte = 30

        @Option(description = ["Processing threads"], names = ["-t", "--threads"])
        var threads = Runtime.getRuntime().availableProcessors()
            set(value) {
                if (value <= 0) throwValidationExceptionKotlin("-t / --threads must be positive")
                field = value
            }

        override fun getInputFiles(): List<String> = listOf(inputFile)

        override fun getOutputFiles(): List<String> = listOf(outputFile)

        override fun run0() {
            when (IOUtil.extractFileType(Paths.get(inputFile))!!) {
                MiXCRFileType.VDJCA -> processVDJCA()
                MiXCRFileType.CLNS -> processClns()
                MiXCRFileType.CLNA -> throwValidationException("Operation is not supported for ClnA files.")
                MiXCRFileType.SHMT -> throwValidationException("Operation is not supported for SHMT files.")
            }
        }

        private fun processClns() {
            ClnsReader(inputFile, VDJCLibraryRegistry.getDefault()).use { reader ->
                val cloneSet = reader.cloneSet
                val outputPort = cloneSet.port
                val process = processWrapper(outputPort, reader.header.paramsSpec, cloneSet.alignmentParameters)

                val clones = process.output
                    .asSequence()
                    .map { clone -> clone.resetParentCloneSet() }
                    .sortedBy { it.id }
                    .toList()
                val newCloneSet =
                    CloneSet(clones, cloneSet.usedGenes, cloneSet.header, cloneSet.footer, cloneSet.ordering)
                ClnsWriter(outputFile).use { writer ->
                    writer.writeCloneSet(newCloneSet)
                    val report = process.finish()
                    writer.setFooter(reader.footer.addReport(report))
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
                    writer.setFooter(reader.footer.addReport(report))
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
                Chains.parse(chains), extensionQuality,
                alignerParameters.vAlignerParameters.scoring,
                alignerParameters.jAlignerParameters.scoring,
                cmdParams.minimalVScore, cmdParams.minimalJScore,
                cmdParams.vAnchor, cmdParams.jAnchor
            )
            val output = ParallelProcessor(input, extender, threads)
            extender.setStartMillis(System.currentTimeMillis())
            extender.setInputFiles(inputFile)
            extender.setOutputFiles(outputFile)
            extender.commandLine = commandLineArguments
            return ProcessWrapper(extender, output)
        }

        private inner class ProcessWrapper<T : VDJCObject>(
            val reportBuilder: VDJCObjectExtender<T>,
            val output: ParallelProcessor<T, T>
        ) {
            fun finish(): MiXCRCommandReport {
                reportBuilder.setFinishMillis(System.currentTimeMillis())
                val report = reportBuilder.buildReport()!!
                // Writing report to stout
                ReportUtil.writeReportToStdout(report)
                if (reportFile != null) ReportUtil.appendReport(reportFile, report)
                if (jsonReport != null) ReportUtil.appendJsonReport(jsonReport, report)
                return report
            }
        }
    }
}
