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

import com.milaboratory.mixcr.basictypes.MiXCRHeader

abstract class AbstractField<T : Any> : Field<T> {
    protected abstract fun create1(
        outputMode: OutputMode,
        headerData: MiXCRHeader,
        args: Array<String>
    ): FieldExtractor<T>

    final override fun create(
        outputMode: OutputMode,
        headerData: MiXCRHeader,
        args: Array<String>
    ): FieldExtractor<T> {
        if (deprecation != null) {
            println("WARNING: $deprecation")
        }
        return create1(outputMode, headerData, args)
    }
}
