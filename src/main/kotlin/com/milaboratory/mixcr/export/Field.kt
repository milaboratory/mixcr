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

interface Field<in T : Any> {
    val priority: Int
    val cmdArgName: String
    val description: String
    val deprecation: String?
    val nArguments: Int
    val metaVars: String
    fun create(outputMode: OutputMode, headerData: MiXCRHeader, args: Array<String>): FieldExtractor<T>
}

fun <T : Any, R : Any> Field<T>.fromProperty(
    headerMapper: (String) -> String = { it },
    property: R.() -> T?
): Field<R> = object : Field<R> {
    override val priority: Int = this@fromProperty.priority
    override val cmdArgName: String = this@fromProperty.cmdArgName
    override val description: String = this@fromProperty.description
    override val deprecation: String? = this@fromProperty.deprecation
    override val nArguments: Int = this@fromProperty.nArguments
    override val metaVars: String = this@fromProperty.metaVars

    override fun create(
        outputMode: OutputMode,
        headerData: MiXCRHeader,
        args: Array<String>
    ): FieldExtractor<R> {
        val delegate = this@fromProperty.create(outputMode, headerData, args)
        return object : FieldExtractor<R> {
            override val header: String = headerMapper(delegate.header)
            override fun extractValue(obj: R): String {
                val propertyVal = property(obj) ?: return NULL
                return delegate.extractValue(propertyVal)
            }
        }
    }
}
