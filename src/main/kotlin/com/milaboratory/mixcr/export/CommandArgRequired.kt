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

sealed interface CommandArg<T> {
    val meta: String
    val decodeAndValidate: FieldsCollection<*>.(MiXCRHeader, String) -> T
    val sPrefix: (T) -> String
}

class CommandArgRequired<T : Any>(
    override val meta: String,
    override val decodeAndValidate: FieldsCollection<*>.(MiXCRHeader, String) -> T,
    override val sPrefix: (T) -> String
) : CommandArg<T>

class CommandArgOptional<T>(
    override val meta: String,
    val canConsumeArg: (String) -> Boolean,
    override val decodeAndValidate: FieldsCollection<*>.(MiXCRHeader, String) -> T,
    override val sPrefix: (T) -> String
) : CommandArg<T>
