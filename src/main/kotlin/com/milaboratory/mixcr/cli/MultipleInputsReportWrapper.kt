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

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE
import com.milaboratory.util.ReportHelper

@JsonAutoDetect(
    fieldVisibility = ANY,
    isGetterVisibility = NONE,
    getterVisibility = NONE
)
data class MultipleInputsReportWrapper(
    val inputIndex: Int,
    val inputName: String,
    val report: MiXCRCommandReport
) : MiXCRCommandReport by report {
    override fun writeReport(helper: ReportHelper) {
        helper.println("Report for input #${inputIndex} (${inputName})")
        report.writeReport(helper.indentedHelper())
    }

    override fun toString(): String = asString()
}
