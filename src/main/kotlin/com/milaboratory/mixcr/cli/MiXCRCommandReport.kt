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

import com.milaboratory.util.FormatUtils
import com.milaboratory.util.ReportHelper
import java.io.ByteArrayOutputStream
import java.util.*

interface MiXCRCommandReport : MiXCRReport {
    val date: Date?
    val commandLine: String
    val inputFiles: Array<String>
    val outputFiles: Array<String>
    val executionTimeMillis: Long?
    val version: String

    fun command(): String

    fun writeHeader(helper: ReportHelper) {
        val command = command().substring(0, 1).uppercase(Locale.getDefault()) + command().substring(1)
        helper.writeLine("============== $command Report ==============")
    }

    fun writeSuperReport(helper: ReportHelper) {
        if (helper.isStdout) {
            writeHeader(helper)
        } else {
            helper.writeNotNullField("Analysis date", date)
                .writeField("Input file(s)", inputFiles.joinToString(","))
                .writeField("Output file(s)", outputFiles.joinToString(","))
                .writeField("Version", version)
                .writeField("Command line arguments", commandLine)
        }
        executionTimeMillis?.let { executionTimeMillis ->
            helper.writeNotNullField("Analysis time", FormatUtils.nanoTimeToString(executionTimeMillis * 1000000))
        }
    }

    fun asString(): String {
        val os = ByteArrayOutputStream()
        writeReport(ReportHelper(os, true))
        return os.toString()
    }
}
