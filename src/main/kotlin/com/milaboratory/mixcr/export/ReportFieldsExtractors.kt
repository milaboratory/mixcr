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
import com.milaboratory.mixcr.cli.CommandExportReportsAsTable
import com.milaboratory.mixcr.cli.MiXCRCommandReport
import com.milaboratory.mixcr.export.ReportFieldsExtractors.ReportWithSource
import java.nio.file.Path

object ReportFieldsExtractors : FieldExtractorsFactoryWithPresets<ReportWithSource>() {
    override val presets: Map<String, List<ExportFieldDescription>> = buildMap {
        this["full"] = listOf(
            ExportFieldDescription("-fileName"),
            ExportFieldDescription("-commandName"),
            ExportFieldDescription("-commandLine"),
            ExportFieldDescription("-MiXCRVersion"),
        )
    }
    override val defaultPreset: String = "full"

    override fun allAvailableFields(): List<Field<ReportWithSource>> = buildList {
        this += Field(
            100,
            "-fileName",
            "File name as it was specified in command `${CommandExportReportsAsTable.COMMAND_NAME}`",
            "fileName"
        ) { reportWithSource: ReportWithSource ->
            reportWithSource.source.toString()
        }
        this += Field(
            200,
            "-commandName",
            "Name of command",
            "commandName"
        ) { reportWithSource: ReportWithSource ->
            reportWithSource.command.command
        }
        this += Field(
            300,
            "-commandLine",
            "Command line arguments",
            "commandLine"
        ) { (report: MiXCRCommandReport) ->
            report.commandLine
        }
        this += Field(
            400,
            "-inputFiles",
            "Input files for command",
            "inputFiles"
        ) { (report: MiXCRCommandReport) ->
            report.inputFiles.joinToString(",")
        }
        this += Field(
            500,
            "-outputFiles",
            "Output files of command",
            "outputFiles"
        ) { (report: MiXCRCommandReport) ->
            report.inputFiles.joinToString(",")
        }
        this += Field(
            600,
            "-MiXCRVersion",
            "Version of MiXCR",
            "MiXCRVersion"
        ) { (report: MiXCRCommandReport) ->
            report.version
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
