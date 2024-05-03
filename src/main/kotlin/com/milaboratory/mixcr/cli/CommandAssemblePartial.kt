/*
 * Copyright (c) 2014-2024, MiLaboratories Inc. All Rights Reserved
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

import cc.redberry.pipe.InputPort
import cc.redberry.pipe.util.forEach
import com.milaboratory.app.InputFileType
import com.milaboratory.app.ValidationException
import com.milaboratory.app.logger
import com.milaboratory.cli.POverridesBuilderOps
import com.milaboratory.mixcr.basictypes.VDJCAlignments
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter
import com.milaboratory.mixcr.basictypes.tag.TagTuple
import com.milaboratory.mixcr.basictypes.tag.TagType
import com.milaboratory.mixcr.cli.CommonDescriptions.DEFAULT_VALUE_FROM_PRESET
import com.milaboratory.mixcr.cli.CommonDescriptions.Labels
import com.milaboratory.mixcr.partialassembler.PartialAlignmentsAssembler
import com.milaboratory.mixcr.presets.AnalyzeCommandDescriptor
import com.milaboratory.mixcr.presets.MiXCRParamsBundle
import com.milaboratory.util.ReportUtil
import com.milaboratory.util.SmartProgressReporter
import com.milaboratory.util.groupAlreadySorted
import com.milaboratory.util.use
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path

object CommandAssemblePartial {
    const val COMMAND_NAME = AnalyzeCommandDescriptor.assemblePartial.name

    abstract class CmdBase : MiXCRCommandWithOutputs(), MiXCRPresetAwareCommand<CommandAssemblePartialParams> {
        @Option(
            description = [
                "Write only overlapped sequences (needed for testing).",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["-o", "--overlapped-only"],
            order = OptionsOrder.main + 10_100
        )
        private var overlappedOnly = false

        @Option(
            description = [
                "Drop partial sequences which were not assembled. Can be used to reduce output file " +
                        "size if no additional rounds of `assemblePartial` are required.",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["-d", "--drop-partial"],
            order = OptionsOrder.main + 10_200
        )
        private var dropPartial = false

        @Option(
            description = [
                "Overlap sequences on the cell level instead of UMIs for tagged data with molecular and cell barcodes.",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["--cell-level"],
            order = OptionsOrder.main + 10_300
        )
        private var cellLevel = false

        @Option(
            names = ["-O"],
            description = ["Overrides default parameter values."],
            paramLabel = Labels.OVERRIDES,
            order = OptionsOrder.overrides
        )
        private var overrides: Map<String, String> = mutableMapOf()

        override val paramsResolver =
            object : MiXCRParamsResolver<CommandAssemblePartialParams>(MiXCRParamsBundle::assemblePartial) {
                override fun POverridesBuilderOps<CommandAssemblePartialParams>.paramsOverrides() {
                    CommandAssemblePartialParams::overlappedOnly setIfTrue overlappedOnly
                    CommandAssemblePartialParams::dropPartial setIfTrue dropPartial
                    CommandAssemblePartialParams::cellLevel setIfTrue cellLevel
                    CommandAssemblePartialParams::parameters jsonOverrideWith overrides
                }
            }
    }

    @Command(
        description = ["Assembles partially aligned reads into longer sequences."]
    )
    class Cmd : CmdBase() {
        @Parameters(
            description = ["Path to input alignments file."],
            paramLabel = "alignments.vdjca",
            index = "0"
        )
        lateinit var inputFile: Path

        @Parameters(
            description = ["Path where to write recovered alignments."],
            paramLabel = "alignments.recovered.vdjca",
            index = "1"
        )
        lateinit var outputFile: Path

        @Mixin
        lateinit var reportOptions: ReportOptions

        @Mixin
        lateinit var resetPreset: ResetPresetOptions

        @Mixin
        lateinit var dontSavePresetOption: DontSavePresetOption

        override val inputFiles
            get() = listOf(inputFile)

        override val outputFiles
            get() = listOf(outputFile)

        override fun validate() {
            ValidationException.requireFileType(inputFile, InputFileType.VDJCA)
            ValidationException.requireFileType(outputFile, InputFileType.VDJCA)
        }

        override fun run1() {
            // Saving initial timestamp
            val beginTimestamp = System.currentTimeMillis()
            use(
                VDJCAlignmentsReader(inputFile),
                VDJCAlignmentsWriter(outputFile)
            ) { reader, writer ->
                val header = reader.header
                val paramsSpec = resetPreset.overridePreset(header.paramsSpec)
                val cmdParams = paramsResolver.resolve(paramsSpec).second
                val groupingDepth =
                    header.tagsInfo.getDepthFor(if (cmdParams.cellLevel) TagType.Cell else TagType.Molecule)
                writer.writeHeader(
                    header
                        .updateTagInfo { ti -> ti.setSorted(groupingDepth) } // output data will be grouped only up to a groupingDepth
                        .addStepParams(AnalyzeCommandDescriptor.assemblePartial, cmdParams)
                        .copy(paramsSpec = dontSavePresetOption.presetToSave(paramsSpec)),
                    reader.usedGenes
                )
                val assembler = PartialAlignmentsAssembler(
                    cmdParams.parameters, reader.parameters,
                    reader.usedGenes, !cmdParams.dropPartial, cmdParams.overlappedOnly,
                    groupingDepth,
                    InputPort { alignment -> writer.write(alignment) }
                )

                @Suppress("UnnecessaryVariable")
                val reportBuilder = assembler
                reportBuilder.setStartMillis(beginTimestamp)
                reportBuilder.setInputFiles(inputFile)
                reportBuilder.setOutputFiles(outputFile)
                reportBuilder.commandLine = commandLineArguments

                use(reader.readAlignments(), reader.readAlignments()) { reader1, reader2 ->
                    if (!header.tagsInfo.hasNoTags()) {

                        SmartProgressReporter.startProgressReport("Running assemble partial", reader1)

                        // This processor strips all non-key information from the
                        val key: (VDJCAlignments) -> TagTuple = { al ->
                            al.tagCount.asKeyPrefixOrError(groupingDepth)
                        }
                        val groups1 = reader1
                            .map { it.ensureKeyTags() }
                            .groupAlreadySorted(key)
                        val groups2 = reader2
                            .map { it.ensureKeyTags() }
                            .groupAlreadySorted(key)
                        groups1.forEach { grp1 ->
                            assembler.buildLeftPartsIndex(grp1)
                            grp1.close() // Drain leftover alignments in the group if not yet done
                            groups2.take().use { grp2 ->
                                assert(grp2!!.key == grp1.key) { grp1.key.toString() + " != " + grp2.key }
                                assembler.searchOverlaps(grp2)
                            }
                        }
                    } else {
                        SmartProgressReporter.startProgressReport("Building index", reader1)
                        assembler.buildLeftPartsIndex(reader1)
                        SmartProgressReporter.startProgressReport("Searching for overlaps", reader2)
                        assembler.searchOverlaps(reader2)
                    }
                }
                reportBuilder.setFinishMillis(System.currentTimeMillis())
                val report = reportBuilder.buildReport()
                // Writing report to stout
                ReportUtil.writeReportToStdout(report)
                if (assembler.leftPartsLimitReached()) {
                    logger.warn("too many partial alignments detected, consider skipping assemblePartial (enriched library?). /leftPartsLimitReached/")
                }
                if (assembler.maxRightMatchesLimitReached()) {
                    logger.warn("too many partial alignments detected, consider skipping assemblePartial (enriched library?). /maxRightMatchesLimitReached/")
                }
                reportOptions.appendToFiles(report)
                writer.setNumberOfProcessedReads(reader.numberOfReads - assembler.overlapped.get())
                writer.setFooter(reader.footer.addStepReport(AnalyzeCommandDescriptor.assemblePartial, report))
            }
        }
    }
}
