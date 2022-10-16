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

import com.milaboratory.util.Report
import com.milaboratory.util.ReportUtil
import picocli.CommandLine.Option
import java.nio.file.Path

class ReportOptions {
    @Option(
        description = ["Report file (human readable version, see -j / --json-report for machine readable report)."],
        names = ["-r", "--report"],
        paramLabel = "<path>",
        order = 1_000_000 - 7
    )
    private var plain: Path? = null

    @Option(
        description = ["JSON formatted report file."],
        names = ["-j", "--json-report"],
        paramLabel = "<path>",
        order = 1_000_000 - 6
    )
    private var json: Path? = null

    fun appendToFiles(report: Report) {
        if (plain != null) ReportUtil.appendReport(plain, report)
        if (json != null) ReportUtil.appendJsonReport(json, report)
    }
}
