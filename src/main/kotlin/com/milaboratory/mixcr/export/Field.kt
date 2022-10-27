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
import picocli.CommandLine

abstract class Field<in T : Any> : FieldsCollection<T> {
    abstract fun create(headerData: MiXCRHeader, args: Array<String>): FieldExtractor<T>

    final override fun createFields(headerData: MiXCRHeader, args: Array<String>): List<FieldExtractor<T>> =
        listOf(create(headerData, args))

    companion object {
        operator fun <T : Any> invoke(
            priority: Int,
            command: String,
            description: String,
            sHeader: String,
            deprecation: String? = null,
            extract: (T) -> String
        ): Field<T> = FieldParameterless(priority, command, description, sHeader, deprecation, extract)

        operator fun <T : Any, P1 : Any> invoke(
            priority: Int,
            command: String,
            description: String,
            parameter1: CommandArgRequired<P1>,
            validateArgs: Field<T>.(P1) -> Unit = {},
            deprecation: String? = null,
            extract: (T, P1) -> String
        ): Field<T> = object :
            FieldWithParameters<T, P1>(priority, command, description, CommandLine.Range.valueOf("1"), deprecation) {
            override val metaVars: String = parameter1.meta

            override fun getParameters(headerData: MiXCRHeader, args: Array<String>): P1 {
                val arg1 = parameter1.decodeAndValidate(this, headerData, args[0])
                validateArgs(arg1)
                return arg1
            }

            override fun extractValue(`object`: T, parameters: P1): String =
                extract(`object`, parameters)

            override fun getHeader(parameters: P1): String = parameter1.sPrefix(parameters)
        }

        operator fun <T : Any, P1 : Any, P2 : Any> invoke(
            priority: Int,
            command: String,
            description: String,
            parameter1: CommandArgRequired<P1>,
            parameter2: CommandArgRequired<P2>,
            validateArgs: Field<T>.(P1, P2) -> Unit = { _, _ -> },
            deprecation: String? = null,
            extract: (T, P1, P2) -> String
        ): Field<T> = object : FieldWithParameters<T, Pair<P1, P2>>(
            priority,
            command,
            description,
            CommandLine.Range.valueOf("2"),
            deprecation
        ) {
            override val metaVars: String = parameter1.meta + " " + parameter2.meta

            override fun getParameters(headerData: MiXCRHeader, args: Array<String>): Pair<P1, P2> {
                val arg1 = parameter1.decodeAndValidate(this, headerData, args[0])
                val arg2 = parameter2.decodeAndValidate(this, headerData, args[1])
                validateArgs(arg1, arg2)
                return arg1 to arg2
            }

            override fun extractValue(`object`: T, parameters: Pair<P1, P2>): String =
                extract(`object`, parameters.first, parameters.second)

            override fun getHeader(parameters: Pair<P1, P2>): String =
                parameter1.sPrefix(parameters.first) + parameter2.sPrefix(parameters.second)
        }

        operator fun <T : Any, P1 : Any, P2 : Any> invoke(
            priority: Int,
            command: String,
            description: String,
            parameter1: CommandArgRequired<P1>,
            parameter2: CommandArgOptional<P2?>,
            validateArgs: Field<T>.(P1, P2?) -> Unit = { _, _ -> },
            deprecation: String? = null,
            extract: (T, P1, P2?) -> String
        ): Field<T> = object : FieldWithParameters<T, Pair<P1, P2?>>(
            priority,
            command,
            description,
            CommandLine.Range.valueOf("1..2"),
            deprecation
        ) {
            override val metaVars: String = parameter1.meta + " [" + parameter2.meta + "]"

            override fun consumableArgs(args: List<String>): Int = when (args.size) {
                0, 1 -> 1
                else -> when {
                    !parameter2.canConsumeArg(args[1]) -> 1
                    else -> 2
                }
            }

            override fun getParameters(headerData: MiXCRHeader, args: Array<String>): Pair<P1, P2?> {
                val arg1 = parameter1.decodeAndValidate(this, headerData, args[0])
                val arg2 = args.getOrNull(1)?.let { arg -> parameter2.decodeAndValidate(this, headerData, arg) }
                validateArgs(arg1, arg2)
                return arg1 to arg2
            }

            override fun extractValue(`object`: T, parameters: Pair<P1, P2?>): String =
                extract(`object`, parameters.first, parameters.second)

            override fun getHeader(parameters: Pair<P1, P2?>): String =
                parameter1.sPrefix(parameters.first) + parameter2.sPrefix(parameters.second)
        }

        operator fun <T : Any, P1 : Any, P2 : Any, P3 : Any> invoke(
            priority: Int,
            command: String,
            description: String,
            parameter1: CommandArgRequired<P1>,
            parameter2: CommandArgRequired<P2>,
            parameter3: CommandArgRequired<P3>,
            validateArgs: Field<T>.(P1, P2, P3) -> Unit = { _, _, _ -> },
            deprecation: String? = null,
            extract: (T, P1, P2, P3) -> String
        ): Field<T> =
            object : FieldWithParameters<T, Triple<P1, P2, P3>>(
                priority,
                command,
                description,
                CommandLine.Range.valueOf("3"),
                deprecation
            ) {
                override val metaVars: String = parameter1.meta + " " + parameter2.meta + " " + parameter3.meta

                override fun getParameters(headerData: MiXCRHeader, args: Array<String>): Triple<P1, P2, P3> {
                    val arg1 = parameter1.decodeAndValidate(this, headerData, args[0])
                    val arg2 = parameter2.decodeAndValidate(this, headerData, args[1])
                    val arg3 = parameter3.decodeAndValidate(this, headerData, args[2])
                    validateArgs(arg1, arg2, arg3)
                    return Triple(arg1, arg2, arg3)
                }

                override fun extractValue(`object`: T, parameters: Triple<P1, P2, P3>): String =
                    extract(`object`, parameters.first, parameters.second, parameters.third)

                override fun getHeader(parameters: Triple<P1, P2, P3>): String =
                    parameter1.sPrefix(parameters.first) +
                            parameter2.sPrefix(parameters.second) +
                            parameter3.sPrefix(parameters.third)
            }
    }
}

private class FieldParameterless<T : Any>(
    override val priority: Int,
    override val cmdArgName: String,
    override val description: String,
    private val sHeader: String,
    override val deprecation: String? = null,
    private val extract: (T) -> String
) : Field<T>() {
    override val arity: CommandLine.Range = CommandLine.Range.valueOf("0")

    override fun create(
        headerData: MiXCRHeader,
        args: Array<String>
    ): FieldExtractor<T> = object : FieldExtractor<T> {
        override val header = sHeader
        override fun extractValue(obj: T): String = extract(obj)
    }

    override val metaVars: String = ""
}

private abstract class FieldWithParameters<T : Any, P>(
    override val priority: Int,
    override val cmdArgName: String,
    override val description: String,
    override val arity: CommandLine.Range,
    override val deprecation: String? = null
) : Field<T>() {
    protected abstract fun getParameters(headerData: MiXCRHeader, args: Array<String>): P
    protected abstract fun getHeader(parameters: P): String
    protected abstract fun extractValue(`object`: T, parameters: P): String

    override fun create(
        headerData: MiXCRHeader,
        args: Array<String>
    ): FieldExtractor<T> {
        require(arity.min() <= args.size && args.size <= arity.max()) {
            "$cmdArgName requires $arity arguments, got ${args.joinToString(" ")}"
        }
        val params = getParameters(headerData, args)
        return object : FieldExtractor<T> {
            override val header = getHeader(params)
            override fun extractValue(obj: T): String = this@FieldWithParameters.extractValue(obj, params)
        }
    }
}
