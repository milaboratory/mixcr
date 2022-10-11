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

import com.fasterxml.jackson.annotation.JsonMerge
import com.fasterxml.jackson.annotation.JsonProperty
import com.milaboratory.cli.POverridesBuilderOps
import com.milaboratory.mitool.helpers.group
import com.milaboratory.mitool.helpers.map
import com.milaboratory.mixcr.MiXCRCommand
import com.milaboratory.mixcr.MiXCRParams
import com.milaboratory.mixcr.MiXCRParamsBundle
import com.milaboratory.mixcr.basictypes.VDJCAlignments
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter
import com.milaboratory.mixcr.basictypes.tag.TagTuple
import com.milaboratory.mixcr.basictypes.tag.TagType
import com.milaboratory.mixcr.cli.CommonDescriptions.JSON_REPORT
import com.milaboratory.mixcr.cli.CommonDescriptions.REPORT
import com.milaboratory.mixcr.partialassembler.PartialAlignmentsAssembler
import com.milaboratory.mixcr.partialassembler.PartialAlignmentsAssemblerParameters
import com.milaboratory.primitivio.forEach
import com.milaboratory.util.ReportUtil
import com.milaboratory.util.SmartProgressReporter
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

object CommandAssemblePartial {
    const val COMMAND_NAME = "assemblePartial"

    data class Params(
        @JsonProperty("overlappedOnly") val overlappedOnly: Boolean,
        @JsonProperty("dropPartial") val dropPartial: Boolean,
        @JsonProperty("cellLevel") val cellLevel: Boolean,
        @JsonProperty("parameters") @JsonMerge val parameters: PartialAlignmentsAssemblerParameters
    ) : MiXCRParams {
        override val command = MiXCRCommand.assemblePartial
    }

    abstract class CmdBase : MiXCRPresetAwareCommand<Params>() {
        @Option(
            description = ["Write only overlapped sequences (needed for testing)."],
            names = ["-o", "--overlapped-only"]
        )
        private var overlappedOnly = false

        @Option(
            description = ["Drop partial sequences which were not assembled. Can be used to reduce output file " +
                    "size if no additional rounds of 'assemblePartial' are required."], names = ["-d", "--drop-partial"]
        )
        private var dropPartial = false

        @Option(
            description = ["Overlap sequences on the cell level instead of UMIs for tagged data with molecular and cell barcodes"],
            names = ["--cell-level"]
        )
        private var cellLevel = false

        @Option(names = ["-O"], description = ["Overrides default parameter values."])
        private var overrides: Map<String, String> = mutableMapOf()

        override val paramsResolver = object : MiXCRParamsResolver<Params>(MiXCRParamsBundle::assemblePartial) {
            override fun POverridesBuilderOps<Params>.paramsOverrides() {
                Params::overlappedOnly setIfTrue overlappedOnly
                Params::dropPartial setIfTrue dropPartial
                Params::cellLevel setIfTrue cellLevel
                Params::parameters jsonOverrideWith overrides
            }
        }
    }

    @Command(
        name = COMMAND_NAME,
        sortOptions = true,
        separator = " ",
        description = ["Assembles partially aligned reads into longer sequences."]
    )
    class Cmd : CmdBase() {
        @Parameters(description = ["alignments.vdjca"], index = "0")
        lateinit var inputFile: String

        @Parameters(description = ["alignments.recovered.vdjca"], index = "1")
        lateinit var outputFile: String

        @Option(description = [REPORT], names = ["-r", "--report"])
        var reportFile: String? = null

        @Option(description = [JSON_REPORT], names = ["-j", "--json-report"])
        var jsonReport: String? = null

        override val inputFiles: List<String>
            get() = listOf(inputFile)

        override val outputFiles: List<String>
            get() = listOf(outputFile)

        override fun run0() {
            // Saving initial timestamp
            val beginTimestamp = System.currentTimeMillis()
            val cmdParams: Params
            VDJCAlignmentsReader(inputFile).use { reader1 ->
                cmdParams = paramsResolver.resolve(reader1.header.paramsSpec).second
                VDJCAlignmentsReader(inputFile).use { reader2 ->
                    VDJCAlignmentsWriter(outputFile).use { writer ->
                        val groupingDepth =
                            reader1.header.tagsInfo.getDepthFor(if (cmdParams.cellLevel) TagType.Cell else TagType.Molecule)
                        writer.writeHeader(
                            reader1.header
                                .updateTagInfo { ti -> ti.setSorted(groupingDepth) } // output data will be grouped only up to a groupingDepth
                                .addStepParams(MiXCRCommand.assemblePartial, cmdParams),
                            reader1.usedGenes
                        )
                        val assembler = PartialAlignmentsAssembler(
                            cmdParams.parameters, reader1.parameters,
                            reader1.usedGenes, !cmdParams.dropPartial, cmdParams.overlappedOnly
                        ) { alignment: VDJCAlignments -> writer.write(alignment) }

                        @Suppress("UnnecessaryVariable")
                        val reportBuilder = assembler
                        reportBuilder.setStartMillis(beginTimestamp)
                        reportBuilder.setInputFiles(inputFile)
                        reportBuilder.setOutputFiles(outputFile)
                        reportBuilder.commandLine = commandLineArguments
                        if (!reader1.header.tagsInfo.hasNoTags()) {
                            SmartProgressReporter.startProgressReport("Running assemble partial", reader1)

                            // This processor strips all non-key information from the
                            val key: (VDJCAlignments) -> TagTuple = { al ->
                                al.tagCount.asKeyPrefixOrError(groupingDepth)
                            }
                            val groups1 = reader1
                                .map { it.ensureKeyTags() }
                                .group(key)
                            val groups2 = reader2
                                .map { it.ensureKeyTags() }
                                .group(key)
                            groups1.forEach { grp1 ->
                                assembler.buildLeftPartsIndex(grp1)
                                grp1.close() // Drain leftover alignments in the group if not yet done
                                groups2.take().use { grp2 ->
                                    assert(grp2.key == grp1.key) { grp1.key.toString() + " != " + grp2.key }
                                    assembler.searchOverlaps(grp2)
                                }
                            }
                        } else {
                            SmartProgressReporter.startProgressReport("Building index", reader1)
                            assembler.buildLeftPartsIndex(reader1)
                            SmartProgressReporter.startProgressReport("Searching for overlaps", reader2)
                            assembler.searchOverlaps(reader2)
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
                        if (reportFile != null) ReportUtil.appendReport(reportFile, report)
                        if (jsonReport != null) ReportUtil.appendJsonReport(jsonReport, report)
                        writer.setNumberOfProcessedReads(reader1.numberOfReads - assembler.overlapped.get())
                        writer.setFooter(reader1.footer.addStepReport(MiXCRCommand.assemblePartial, report))
                    }
                }
            }
        }
    }
}
