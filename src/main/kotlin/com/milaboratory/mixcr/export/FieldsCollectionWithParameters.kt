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

abstract class FieldsCollectionWithParameters<T : Any, P> private constructor(
    override val priority: Int,
    override val cmdArgName: String,
    override val description: String,
    override val nArguments: Int,
    private val delegate: Field<T>,
    override val deprecation: String? = null
) : FieldsCollection<T> {
    protected abstract fun getParameters(headerData: MiXCRHeader, args: Array<String>): P
    protected abstract fun argsSupplier(headerData: MiXCRHeader, parameters: P): List<Array<String>>

    override fun createFields(
        headerData: MiXCRHeader,
        args: Array<String>
    ): List<FieldExtractor<T>> = argsSupplier(headerData, getParameters(headerData, args)).map { argsForDelegate ->
        delegate.create(headerData, argsForDelegate)
    }

    companion object {
        operator fun <T : Any, P1, P2> invoke(
            priority: Int,
            command: String,
            description: String,
            delegate: Field<T>,
            parameter1: CommandArg<P1>,
            parameter2: CommandArg<P2>,
            validateArgs: FieldsCollection<*>.(P1, P2) -> Unit = { _, _ -> },
            deprecation: String? = null,
            extract: MiXCRHeader.(P1, P2) -> List<Array<String>>
        ): FieldsCollection<T> = object :
            FieldsCollectionWithParameters<T, Pair<P1, P2>>(priority, command, description, 2, delegate, deprecation) {
            override val metaVars: String = parameter1.meta + " " + parameter2.meta

            override fun getParameters(headerData: MiXCRHeader, args: Array<String>): Pair<P1, P2> {
                val arg1 = parameter1.decodeAndValidate(this, headerData, args[0])
                val arg2 = parameter2.decodeAndValidate(this, headerData, args[1])
                validateArgs(arg1, arg2)
                return arg1 to arg2
            }

            override fun argsSupplier(headerData: MiXCRHeader, parameters: Pair<P1, P2>): List<Array<String>> =
                extract(headerData, parameters.first, parameters.second)
        }
    }

}
