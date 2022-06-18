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

import com.milaboratory.mixcr.basictypes.VDJCFileHeaderData

abstract class FieldParameterless<T> protected constructor(
    targetType: Class<T>,
    command: String,
    description: String,
    private val hHeader: String,
    private val sHeader: String
) : AbstractField<T>(targetType, command, description) {
    override fun nArguments(): Int = 0

    protected abstract fun extract(`object`: T): String

    fun getHeader(outputMode: OutputMode): String = when (outputMode) {
        OutputMode.HumanFriendly -> hHeader
        OutputMode.ScriptingFriendly -> sHeader
    }

    override fun create(
        outputMode: OutputMode,
        headerData: VDJCFileHeaderData,
        args: Array<String>
    ): FieldExtractor<T> = object : AbstractFieldExtractor<T>(getHeader(outputMode), this) {
        override fun extractValue(`object`: T): String = extract(`object`)
    }

    override fun metaVars(): String = ""

    companion object {
        inline operator fun <reified T : Any> invoke(
            command: String,
            description: String,
            hHeader: String,
            sHeader: String,
            noinline extractValue: (T) -> String
        ) = object : FieldParameterless<T>(
            T::class.java,
            command,
            description,
            hHeader,
            sHeader
        ) {
            override fun extract(`object`: T): String = extractValue(`object`)
        }
    }
}
