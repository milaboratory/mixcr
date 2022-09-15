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

import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.util.GlobalObjectMappers
import com.milaboratory.util.ReportHelper
import com.milaboratory.util.ReportUtil
import picocli.CommandLine
import java.nio.file.Path

@CommandLine.Command(
    name = CommandExportReports.EXPORT_REPORTS_COMMAND_NAME,
    separator = " ",
    description = ["Export MiXCR reports."]
)
class CommandExportReports : MiXCRCommand() {
    @CommandLine.Parameters(description = ["data.[vdjca|clns|clna]"], index = "0")
    lateinit var `in`: Path

    @CommandLine.Parameters(description = ["report.[txt|jsonl]"], index = "1", arity = "0..1")
    var out: String? = null

    @CommandLine.Option(names = ["--json"], description = ["Export as json lines"])
    var json = false

    override fun getInputFiles(): List<String> = listOf(`in`.toString())

    override fun getOutputFiles(): List<String> = listOfNotNull(out)

    override fun run0() {
        val reports = IOUtil.extractReports(`in`)
        if (json) {
            if (out != null) {
                for (report in reports) {
                    ReportUtil.appendJsonReport(out!!, report)
                }
            } else {
                println(GlobalObjectMappers.getPretty().writeValueAsString(reports))
            }
        } else {
            val helper = when {
                out != null -> ReportHelper(out!!)
                else -> ReportHelper(System.out, false)
            }
            for (report in reports) {
                report.writeHeader(helper)
                report.writeReport(helper)
                if (out == null) println()
            }
        }
    }

    companion object {
        const val EXPORT_REPORTS_COMMAND_NAME = "exportReports"
    }
}
