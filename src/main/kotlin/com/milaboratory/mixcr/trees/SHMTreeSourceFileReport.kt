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
package com.milaboratory.mixcr.trees

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.milaboratory.mixcr.cli.MiXCRCommandReport
import com.milaboratory.util.ReportHelper

class SHMTreeSourceFileReport @JsonCreator constructor(
    @param:JsonProperty("fileName")
    val fileName: String,
    @param:JsonProperty("report")
    val report: MiXCRCommandReport
) : MiXCRCommandReport by report {
    override fun writeReport(helper: ReportHelper) {
        helper.writeNotNullField("fileName", fileName)
        report.writeReport(helper)
    }

    override fun toString(): String = asString()
}
