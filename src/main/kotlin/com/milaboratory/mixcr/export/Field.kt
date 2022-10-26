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

abstract class Field<in T : Any> : FieldsCollection<T> {
    abstract fun create(headerData: MiXCRHeader, args: Array<String>): FieldExtractor<T>

    final override fun createFields(headerData: MiXCRHeader, args: Array<String>): List<FieldExtractor<T>> =
        listOf(create(headerData, args))
}

interface FieldsCollection<in T : Any> {
    val priority: Int
    val cmdArgName: String
    val description: String
    val deprecation: String?
    val nArguments: Int
    val metaVars: String
    fun createFields(headerData: MiXCRHeader, args: Array<String>): List<FieldExtractor<T>>
}

fun <T : Any, R : Any> FieldsCollection<T>.fromProperty(
    descriptionMapper: (String) -> String = { it },
    property: R.() -> T?
): FieldsCollection<R> = object : FieldsCollection<R> {
    override val priority: Int = this@fromProperty.priority
    override val cmdArgName: String = this@fromProperty.cmdArgName
    override val description: String = descriptionMapper(this@fromProperty.description)
    override val deprecation: String? = this@fromProperty.deprecation
    override val nArguments: Int = this@fromProperty.nArguments
    override val metaVars: String = this@fromProperty.metaVars

    override fun createFields(
        headerData: MiXCRHeader,
        args: Array<String>
    ): List<FieldExtractor<R>> {
        val delegates = this@fromProperty.createFields(headerData, args)
        return delegates.map { delegate ->
            object : FieldExtractor<R> {
                override val header: String = delegate.header
                override fun extractValue(obj: R): String {
                    val propertyVal = property(obj) ?: return NULL
                    return delegate.extractValue(propertyVal)
                }
            }
        }
    }
}
