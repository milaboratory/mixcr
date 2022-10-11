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

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*

abstract class AbstractMiXCRCommandReport(
    @field:JsonIgnore
    override val date: Date?,
    @field:JsonProperty("commandLine")
    override val commandLine: String,
    @field:JsonProperty("inputFiles")
    override val inputFiles: Array<String>,
    @field:JsonProperty("outputFiles")
    override val outputFiles: Array<String>,
    @field:JsonIgnore
    override val executionTimeMillis: Long?,
    @field:JsonProperty("version")
    override val version: String
) : MiXCRCommandReport {
    override fun toString(): String = asString()
}
