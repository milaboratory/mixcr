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

import java.nio.file.Path
import kotlin.io.path.extension

interface MultipleMetricsInOneFile {
    fun validateNonPdf(out: Path, metrics: List<String>?) {
        if (out.extension != "pdf" && metrics.isNullOrEmpty()) {
            val ext = out.extension
            throw ValidationException("For export in $ext Use --metric option to specify only one metric to export. Or use PDF format for export.")
        }
    }
}
