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
import com.milaboratory.mixcr.export.OutputMode.HumanFriendly
import com.milaboratory.mixcr.export.OutputMode.ScriptingFriendly

abstract class FieldParameterless<T : Any> protected constructor(
    override val priority: Int,
    override val cmdArgName: String,
    override val description: String,
    private val hHeader: String,
    private val sHeader: String,
    override val deprecation: String? = null
) : AbstractField<T>() {
    override val nArguments: Int = 0

    protected abstract fun extract(`object`: T): String

    fun getHeader(outputMode: OutputMode): String = when (outputMode) {
        HumanFriendly -> hHeader
        ScriptingFriendly -> sHeader
    }

    override fun create1(
        outputMode: OutputMode,
        headerData: MiXCRHeader,
        args: Array<String>
    ): FieldExtractor<T> = object : FieldExtractor<T> {
        override val header = getHeader(outputMode)
        override fun extractValue(obj: T): String = extract(obj)
    }

    override val metaVars: String = ""

    companion object {
        operator fun <T : Any> invoke(
            priority: Int,
            command: String,
            description: String,
            hHeader: String,
            sHeader: String,
            deprecation: String? = null,
            extractValue: (T) -> String
        ) = object : FieldParameterless<T>(
            priority,
            command,
            description,
            hHeader,
            sHeader,
            deprecation
        ) {
            override fun extract(`object`: T): String = extractValue(`object`)
        }
    }
}
