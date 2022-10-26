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
import com.milaboratory.mixcr.cli.ValidationException
import picocli.CommandLine.Range

abstract class FieldsCollectionWithParameters<T : Any, P> private constructor(
    override val priority: Int,
    override val cmdArgName: String,
    override val description: String,
    override val arity: Range,
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
        operator fun <T : Any, P1 : Any, P2 : Any> invoke(
            priority: Int,
            command: String,
            description: String,
            delegate: Field<T>,
            parameter1: CommandArgRequired<P1>,
            parameter2: CommandArgRequired<P2>,
            validateArgs: FieldsCollection<*>.(P1, P2) -> Unit = { _, _ -> },
            deprecation: String? = null,
            extract: MiXCRHeader.(P1, P2) -> List<Array<String>>
        ): FieldsCollection<T> = object :
            FieldsCollectionWithParameters<T, Pair<P1, P2>>(
                priority,
                command,
                description,
                Range.valueOf("2"),
                delegate,
                deprecation
            ) {
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

        operator fun <T : Any, P1 : Any, P2 : Any> invoke(
            priority: Int,
            command: String,
            description: String,
            delegate: Field<T>,
            parameter1: CommandArgOptional<P1?>,
            parameter2: CommandArgOptional<P2?>,
            validateArgs: FieldsCollection<*>.(P1?, P2?) -> Unit = { _, _ -> },
            deprecation: String? = null,
            extract: MiXCRHeader.(P1?, P2?) -> List<Array<String>>
        ): FieldsCollection<T> = object :
            FieldsCollectionWithParameters<T, Pair<P1?, P2?>>(
                priority,
                command,
                description,
                Range.valueOf("0..2"),
                delegate,
                deprecation
            ) {
            override val metaVars: String = "[" + parameter1.meta + " " + parameter2.meta + "]"

            override fun consumableArgs(args: List<String>): Int = when (args.size) {
                0, 1 -> 0
                else -> when {
                    !parameter1.canConsumeArg(args[0]) || !parameter2.canConsumeArg(args[1]) -> 0
                    else -> 2
                }
            }

            override fun getParameters(headerData: MiXCRHeader, args: Array<String>): Pair<P1?, P2?> {
                val arg1 = args.getOrNull(0)?.let { arg -> parameter1.decodeAndValidate(this, headerData, arg) }
                val arg2 = args.getOrNull(1)?.let { arg -> parameter2.decodeAndValidate(this, headerData, arg) }
                ValidationException.require((arg1 != null && arg2 != null) || (arg1 == null && arg2 == null)) {
                    "Both arguments must be set or both must be omitted, got ${args.joinToString(", ")}"
                }
                validateArgs(arg1, arg2)
                return arg1 to arg2
            }

            override fun argsSupplier(headerData: MiXCRHeader, parameters: Pair<P1?, P2?>): List<Array<String>> =
                extract(headerData, parameters.first, parameters.second)
        }
    }

}
