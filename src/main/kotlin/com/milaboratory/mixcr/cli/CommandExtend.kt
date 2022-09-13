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
import com.milaboratory.mitool.exhaustive
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
import com.milaboratory.mixcr.util.VDJCObjectExtender
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters
import com.milaboratory.primitivio.asSequence
import com.milaboratory.primitivio.port
import com.milaboratory.util.ReportUtil
import com.milaboratory.util.SmartProgressReporter
import io.repseq.core.Chains
import io.repseq.core.ReferencePoint
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine
import java.nio.file.Paths

@CommandLine.Command(
    name = CommandExtend.EXTEND_COMMAND_NAME,
    sortOptions = true,
    separator = " ",
    description = ["Impute alignments or clones with germline sequences."]
)
class CommandExtend : MiXCRCommand() {
    @CommandLine.Parameters(description = ["data.[vdjca|clns]"], index = "0")
    lateinit var `in`: String

    @CommandLine.Parameters(description = ["extendeed.[vdjca|clns]"], index = "1")
    lateinit var out: String

    @CommandLine.Option(
        description = ["Apply procedure only to alignments with specific immunological-receptor chains."],
        names = ["-c", "--chains"]
    )
    var chains = "TCR"

    @CommandLine.Option(description = [CommonDescriptions.REPORT], names = ["-r", "--report"])
    var reportFile: String? = null

    @CommandLine.Option(description = [CommonDescriptions.JSON_REPORT], names = ["-j", "--json-report"])
    var jsonReport: String? = null

    @CommandLine.Option(description = ["Quality score value to assign imputed sequences"], names = ["-q", "--quality"])
    var extensionQuality: Byte = 30

    @CommandLine.Option(description = ["Processing threads"], names = ["-t", "--threads"])
    var threads = Runtime.getRuntime().availableProcessors()
        set(value) {
            if (value <= 0) throwValidationExceptionKotlin("-t / --threads must be positive")
            field = value
        }

    @CommandLine.Option(description = ["V extension anchor point."], names = ["--v-anchor"])
    var vAnchorPoint = "CDR3Begin"

    @CommandLine.Option(description = ["J extension anchor point."], names = ["--j-anchor"])
    var jAnchorPoint = "CDR3End"

    @CommandLine.Option(description = ["Minimal V hit score to perform left extension."], names = ["--min-v-score"])
    var minimalVScore = 100

    @CommandLine.Option(
        description = ["Minimal J hit score alignment to perform right extension."],
        names = ["--min-j-score"]
    )
    var minimalJScore = 70

    override fun getInputFiles(): List<String> = listOf(`in`)

    override fun getOutputFiles(): List<String> = listOf(out)

    override fun run0() {
        when (IOUtil.extractFileType(Paths.get(`in`))) {
            VDJCA -> processVDJCA()
            CLNS -> processClns()
            CLNA -> throwValidationExceptionKotlin("Operation is not supported for ClnA files.")
            SHMT -> throwValidationExceptionKotlin("Operation is not supported for shmt files.")
        }.exhaustive
    }

    private fun processClns() {
        ClnsReader(`in`, VDJCLibraryRegistry.getDefault()).use { reader ->
            val cloneSet = reader.cloneSet
            val outputPort = cloneSet.port
            val process = processWrapper(outputPort, cloneSet.alignmentParameters)

            val clones = process.output
                .asSequence()
                .map { clone -> clone.resetParentCloneSet() }
                .sortedBy { it.id }
                .toList()
            val newCloneSet = CloneSet(clones, cloneSet.usedGenes, cloneSet.info, cloneSet.ordering)
            ClnsWriter(out).use { writer ->
                writer.writeCloneSet(newCloneSet)
                val report = process.finish()
                writer.writeFooter(reader.reports(), report)
            }
        }
    }

    private fun processVDJCA() {
        VDJCAlignmentsReader(`in`).use { reader ->
            VDJCAlignmentsWriter(out).use { writer ->
                SmartProgressReporter.startProgressReport("Extending alignments", reader)
                writer.header(reader)
                val process = processWrapper(reader, reader.parameters)

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
                writer.writeFooter(reader.reports(), report)
            }
        }
    }

    private fun <T : VDJCObject> processWrapper(
        input: OutputPort<T>,
        alignerParameters: VDJCAlignerParameters
    ): ProcessWrapper<T> {
        val extender = VDJCObjectExtender<T>(
            Chains.parse(chains), extensionQuality,
            alignerParameters.vAlignerParameters.scoring,
            alignerParameters.jAlignerParameters.scoring,
            minimalVScore, minimalJScore,
            ReferencePoint.parse(vAnchorPoint),
            ReferencePoint.parse(jAnchorPoint)
        )
        val output = ParallelProcessor(input, extender, threads)
        extender
            .setStartMillis(System.currentTimeMillis())
            .setCommandLine(commandLineArguments)
            .setInputFiles(`in`)
            .setOutputFiles(out)
        return ProcessWrapper(extender, output)
    }

    private inner class ProcessWrapper<T : VDJCObject>(
        val reportBuilder: AbstractCommandReportBuilder<*>,
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

    companion object {
        const val EXTEND_COMMAND_NAME = "extend"
    }
}
