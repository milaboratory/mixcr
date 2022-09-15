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
package com.milaboratory.mixcr

import com.fasterxml.jackson.annotation.JsonProperty

data class MiXCRPipelineElement(
    @JsonProperty("command") val command: MiXCRCommand<*>,
    @JsonProperty("outputSuffix") val outputSuffix: String,
    @JsonProperty("reportSuffix") val reportSuffix: String?,
    @JsonProperty("jsonReportSuffix") val jsonReportSuffix: String?,
)

typealias MiXCRPipeline = List<MiXCRPipelineElement>

// data class MiXCRPipeline(
//     @JsonValue val pipeline: List<MiXCRPipelineElement>
// )