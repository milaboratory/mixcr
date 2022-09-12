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

import com.milaboratory.mitool.helpers.group
import com.milaboratory.mitool.helpers.map
import com.milaboratory.mixcr.basictypes.VDJCAlignments
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter
import com.milaboratory.mixcr.basictypes.tag.TagTuple
import com.milaboratory.mixcr.basictypes.tag.TagType
import com.milaboratory.mixcr.partialassembler.PartialAlignmentsAssembler
import com.milaboratory.mixcr.partialassembler.PartialAlignmentsAssemblerParameters
import com.milaboratory.primitivio.forEach
import com.milaboratory.util.JsonOverrider
import com.milaboratory.util.ReportUtil
import com.milaboratory.util.SmartProgressReporter
import picocli.CommandLine

@CommandLine.Command(
    name = CommandAssemblePartialAlignments.ASSEMBLE_PARTIAL_COMMAND_NAME,
    sortOptions = true,
    separator = " ",
    description = ["Assembles partially aligned reads into longer sequences."]
)
class CommandAssemblePartialAlignments : MiXCRCommand() {
    @CommandLine.Parameters(description = ["alignments.vdjca"], index = "0")
    lateinit var `in`: String

    @CommandLine.Parameters(description = ["alignments.recovered.vdjca"], index = "1")
    lateinit var out: String

    @CommandLine.Option(names = ["-O"], description = ["Overrides default parameter values."])
    var overrides: Map<String, String> = mutableMapOf()

    @CommandLine.Option(description = [CommonDescriptions.REPORT], names = ["-r", "--report"])
    var reportFile: String? = null

    @CommandLine.Option(description = [CommonDescriptions.JSON_REPORT], names = ["-j", "--json-report"])
    var jsonReport: String? = null

    @CommandLine.Option(
        description = ["Write only overlapped sequences (needed for testing)."],
        names = ["-o", "--overlapped-only"]
    )
    var overlappedOnly = false

    @CommandLine.Option(
        description = ["Drop partial sequences which were not assembled. Can be used to reduce output file " +
                "size if no additional rounds of 'assemblePartial' are required."], names = ["-d", "--drop-partial"]
    )
    var dropPartial = false

    @CommandLine.Option(
        description = ["Overlap sequences on the cell level instead of UMIs for tagged data with molecular and cell barcodes"],
        names = ["--cell-level"]
    )
    var cellLevel = false

    override fun getInputFiles(): List<String> = listOf(`in`)

    override fun getOutputFiles(): List<String> = listOf(out)

    private val assemblerParameters: PartialAlignmentsAssemblerParameters by lazy {
        var result = PartialAlignmentsAssemblerParameters.getDefault()
        if (overrides.isNotEmpty()) {
            result = JsonOverrider.override(
                result,
                PartialAlignmentsAssemblerParameters::class.java, overrides
            ) ?: throwValidationExceptionKotlin("Failed to override some parameter.")
        }
        result
    }

    override fun run0() {
        // Saving initial timestamp
        val beginTimestamp = System.currentTimeMillis()
        VDJCAlignmentsReader(`in`).use { reader1 ->
            VDJCAlignmentsReader(`in`).use { reader2 ->
                VDJCAlignmentsWriter(out).use { writer ->
                    val groupingDepth = reader1.tagsInfo.getDepthFor(if (cellLevel) TagType.Cell else TagType.Molecule)
                    writer.header(
                        reader1.info // output data will be grouped only up to a groupingDepth
                            .updateTagInfo { ti -> ti.setSorted(groupingDepth) },
                        reader1.usedGenes
                    )
                    val assembler = PartialAlignmentsAssembler(
                        assemblerParameters, reader1.parameters,
                        reader1.usedGenes, !dropPartial, overlappedOnly
                    ) { alignment: VDJCAlignments -> writer.write(alignment) }

                    val reportBuilder = assembler
                        .setCommandLine(commandLineArguments)
                        .setStartMillis(beginTimestamp)
                        .setInputFiles(`in`)
                        .setOutputFiles(out)
                    if (reader1.tagsInfo != null && !reader1.tagsInfo.hasNoTags()) {
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
                        warn("WARNING: too many partial alignments detected, consider skipping assemblePartial (enriched library?). /leftPartsLimitReached/")
                    }
                    if (assembler.maxRightMatchesLimitReached()) {
                        warn("WARNING: too many partial alignments detected, consider skipping assemblePartial (enriched library?). /maxRightMatchesLimitReached/")
                    }
                    if (reportFile != null) ReportUtil.appendReport(reportFile, report)
                    if (jsonReport != null) ReportUtil.appendJsonReport(jsonReport, report)
                    writer.setNumberOfProcessedReads(reader1.numberOfReads - assembler.overlapped.get())
                    writer.writeFooter(reader1.reports(), report)
                }
            }
        }
    }

    companion object {
        const val ASSEMBLE_PARTIAL_COMMAND_NAME = "assemblePartial"
    }
}
