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
package com.milaboratory.mixcr.export

import com.fasterxml.jackson.core.JsonPointer
import com.fasterxml.jackson.databind.JsonNode
import com.milaboratory.mixcr.AnyMiXCRCommand
import com.milaboratory.mixcr.alleles.FindAllelesReport
import com.milaboratory.mixcr.assembler.fullseq.FullSeqAssemblerReport
import com.milaboratory.mixcr.cli.AlignerReport
import com.milaboratory.mixcr.cli.CloneAssemblerReport
import com.milaboratory.mixcr.cli.CommandAlign
import com.milaboratory.mixcr.cli.CommandAssemble
import com.milaboratory.mixcr.cli.CommandAssembleContigs
import com.milaboratory.mixcr.cli.CommandAssemblePartial
import com.milaboratory.mixcr.cli.CommandExportReportsAsTable
import com.milaboratory.mixcr.cli.CommandExtend
import com.milaboratory.mixcr.cli.CommandFindAlleles
import com.milaboratory.mixcr.cli.CommandFindShmTrees
import com.milaboratory.mixcr.cli.CommandRefineTagsAndSort
import com.milaboratory.mixcr.cli.MiXCRCommandReport
import com.milaboratory.mixcr.cli.RefineTagsAndSortReport
import com.milaboratory.mixcr.export.ReportFieldsExtractors.ReportWithSource
import com.milaboratory.mixcr.partialassembler.PartialAlignmentsAssemblerReport
import com.milaboratory.mixcr.trees.BuildSHMTreeReport
import com.milaboratory.mixcr.util.VDJCObjectExtenderReport
import java.nio.file.Path

object ReportFieldsExtractors : FieldExtractorsFactoryWithPresets<ReportWithSource>() {
    override val presets: Map<String, List<ExportFieldDescription>> = buildMap {
        this["min"] = listOf(
            ExportFieldDescription("-fileName"),
            ExportFieldDescription("-commandName"),
            ExportFieldDescription("-commandLine"),
            ExportFieldDescription("-MiXCRVersion"),
        )
        this["full"] = listOf(
            ExportFieldDescription("-fileName"),
            ExportFieldDescription("-commandName"),
            ExportFieldDescription("-commandLine"),
            ExportFieldDescription("-inputFiles"),
            ExportFieldDescription("-MiXCRVersion"),
            ExportFieldDescription("-totalProcessedRecords"),
            ExportFieldDescription("-totalOutputRecords"),
        )
    }
    override val defaultPreset: String = "full"

    override fun allAvailableFields(): List<Field<ReportWithSource>> = buildList {
        this += Field(
            100,
            "-fileName",
            "File name as it was specified in command `${CommandExportReportsAsTable.COMMAND_NAME}`.",
            "fileName"
        ) { reportWithSource: ReportWithSource ->
            reportWithSource.source.toString()
        }
        this += Field(
            200,
            "-commandName",
            "Name of command.",
            "commandName"
        ) { reportWithSource: ReportWithSource ->
            reportWithSource.command.command
        }
        this += Field(
            300,
            "-commandLine",
            "Command line arguments.",
            "commandLine"
        ) { (report: MiXCRCommandReport) ->
            report.commandLine
        }
        this += Field(
            400,
            "-inputFiles",
            "Input files for command.",
            "inputFiles"
        ) { (report: MiXCRCommandReport) ->
            report.inputFiles.joinToString(",")
        }
        this += Field(
            500,
            "-outputFiles",
            "Output files of command.",
            "outputFiles"
        ) { (report: MiXCRCommandReport) ->
            report.inputFiles.joinToString(",")
        }
        this += Field(
            600,
            "-MiXCRVersion",
            "Version of MiXCR.",
            "MiXCRVersion"
        ) { (report: MiXCRCommandReport) ->
            report.version
        }
        this += Field(
            700,
            "-totalProcessedRecords",
            "Total number of processed records. Meaning depends on command:%n" +
                    "`${CommandAlign.COMMAND_NAME}`, `${CommandRefineTagsAndSort.COMMAND_NAME}`, `${CommandAssemble.COMMAND_NAME}` and `${CommandAssembleContigs.COMMAND_NAME}`- reads count,%n" +
                    "`${CommandAssemblePartial.COMMAND_NAME}` - alignments count,%n" +
                    "`${CommandExtend.COMMAND_NAME}` - depends on input,%n" +
                    "`${CommandFindAlleles.COMMAND_NAME}` and `${CommandFindShmTrees.COMMAND_NAME}` - clones count.",
            "totalProcessedRecords"
        ) { (report: MiXCRCommandReport) ->
            //TODO sealed interface
            when (report) {
                is AlignerReport -> report.totalReadsProcessed
                is RefineTagsAndSortReport -> report.correctionReport.inputRecords
                is PartialAlignmentsAssemblerReport -> report.totalProcessed
                is VDJCObjectExtenderReport -> report.totalProcessed
                is CloneAssemblerReport -> report.totalReadsProcessed
                is FullSeqAssemblerReport -> report.totalReadsProcessed
                is FindAllelesReport -> report.totalClonesCount()
                is BuildSHMTreeReport -> report.totalClonesProcessed
                else -> null
            }?.toString() ?: ""
        }
        this += Field(
            800,
            "-totalOutputRecords",
            "Total number of successfully processed or changed records. Meaning depends on command:%n" +
                    "`${CommandAlign.COMMAND_NAME}`, `${CommandRefineTagsAndSort.COMMAND_NAME}`, `${CommandAssemble.COMMAND_NAME}` and `${CommandAssembleContigs.COMMAND_NAME}`- reads count,%n" +
                    "`${CommandAssemblePartial.COMMAND_NAME}` - alignments count,%n" +
                    "`${CommandExtend.COMMAND_NAME}` - extended records, record type depends on input,%n" +
                    "`${CommandFindAlleles.COMMAND_NAME}` - count of clones with changed best hits%n.",
            "`${CommandFindShmTrees.COMMAND_NAME}` - clones count grouped in trees.",
            "totalOutputRecords"
        ) { (report: MiXCRCommandReport) ->
            //TODO sealed interface
            when (report) {
                is AlignerReport -> report.aligned
                is RefineTagsAndSortReport -> report.correctionReport.outputRecords
                is PartialAlignmentsAssemblerReport -> report.outputAlignments
                is VDJCObjectExtenderReport -> report.totalExtended
                is CloneAssemblerReport -> report.readsInClones
                is FullSeqAssemblerReport -> report.totalDividedVariantReads
                is FindAllelesReport -> report.changedClonesCount()
                is BuildSHMTreeReport -> report.totalClonesCountInTrees()
                else -> null
            }?.toString() ?: ""
        }
        this += Field(
            50_000,
            "-reportJsonPart",
            "Part of report by specified json path. Examples:%n" +
                    "`-reportJsonPart /notAlignedReasons/NoHits`%n" +
                    "`-reportJsonPart /clonalChainUsage/chains/IGH/total`%n",
            CommandArgRequired(
                "<json_path>",
                { JsonPointer.compile(it) },
                { "report$it" }
            )
        ) { reportWithSource: ReportWithSource, jsonPath: JsonPointer ->
            reportWithSource.json.at(jsonPath)?.toString() ?: ""
        }
    }

    data class ReportWithSource(
        val report: MiXCRCommandReport,
        val json: JsonNode,
        val command: AnyMiXCRCommand,
        val source: Path
    )
}
