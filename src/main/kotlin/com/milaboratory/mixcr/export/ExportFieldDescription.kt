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

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY
import com.fasterxml.jackson.annotation.JsonProperty

data class ExportFieldDescription(
    @JsonProperty("field") val field: String,
    @JsonProperty("args") @JsonInclude(NON_EMPTY) val args: List<String> = emptyList(),
) {
    companion object {
        operator fun invoke(vararg args: String): ExportFieldDescription =
            ExportFieldDescription(args[0], args.copyOfRange(1, args.size).toList())
    }
}
