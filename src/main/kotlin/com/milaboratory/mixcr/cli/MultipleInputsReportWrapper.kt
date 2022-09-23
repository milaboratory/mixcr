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

import com.fasterxml.jackson.annotation.JsonProperty
import com.milaboratory.util.ReportHelper

data class MultipleInputsReportWrapper(
    @JsonProperty("inputIndex") val inputIndex: Int,
    @JsonProperty("inputName") val inputName: String,
    @JsonProperty("report") val report: MiXCRCommandReport
) : MiXCRCommandReport by report {
    override fun writeReport(helper: ReportHelper) {
        helper.println("Report for input #${inputIndex} (${inputName})")
        report.writeReport(helper.indentedHelper())
    }

    override fun toString(): String = asString()
}
