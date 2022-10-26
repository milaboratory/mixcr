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
import picocli.CommandLine.Range

class FieldParameterless<T : Any>(
    override val priority: Int,
    override val cmdArgName: String,
    override val description: String,
    private val sHeader: String,
    override val deprecation: String? = null,
    private val extract: (T) -> String
) : Field<T>() {
    override val arity: Range = Range.valueOf("0")

    override fun create(
        headerData: MiXCRHeader,
        args: Array<String>
    ): FieldExtractor<T> = object : FieldExtractor<T> {
        override val header = sHeader
        override fun extractValue(obj: T): String = extract(obj)
    }

    override val metaVars: String = ""
}
