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

class FieldsCollectionParameterless<T : Any>(
    override val priority: Int,
    override val cmdArgName: String,
    override val description: String,
    private val delegate: Field<T>,
    override val deprecation: String? = null,
    private val argsSupplier: MiXCRHeader.() -> List<Array<String>>
) : FieldsCollection<T> {
    override val nArguments: Int = 0

    override fun createFields(
        headerData: MiXCRHeader,
        args: Array<String>
    ): List<FieldExtractor<T>> = argsSupplier(headerData).map { argsForDelegate ->
        delegate.create(headerData, argsForDelegate)
    }

    override val metaVars: String = ""
}
