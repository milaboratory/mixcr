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
import picocli.CommandLine

interface FieldsCollection<in T : Any> {
    val priority: Int
    val cmdArgName: String
    val description: String
    val deprecation: String?
    val arity: CommandLine.Range
    val metaVars: String
    fun consumableArgs(args: List<String>): Int = arity.max()

    fun createFields(headerData: MiXCRHeader, args: Array<String>): List<FieldExtractor<T>>

    companion object {
        operator fun <T : Any> invoke(
            priority: Int,
            command: String,
            description: String,
            delegate: Field<T>,
            deprecation: String? = null,
            extract: MiXCRHeader.() -> List<Array<String>>
        ): FieldsCollection<T> = FieldsCollectionParameterless(
            priority,
            command,
            description,
            delegate,
            deprecation,
            extract
        )

        operator fun <T : Any, P1 : Any> invoke(
            priority: Int,
            command: String,
            description: String,
            delegate: Field<T>,
            parameter1: CommandArgRequired<P1>,
            validateArgs: FieldsCollection<*>.(P1) -> Unit = { _ -> },
            deprecation: String? = null,
            extract: MiXCRHeader.(P1) -> List<Array<String>>
        ): FieldsCollection<T> = object : FieldsCollectionWithParameters<T, P1>(
            priority,
            command,
            description,
            CommandLine.Range.valueOf("1"),
            listOf(delegate),
            deprecation
        ) {
            override val metaVars: String = parameter1.meta

            override fun getParameters(headerData: MiXCRHeader, args: Array<String>): P1 {
                val arg1 = parameter1.decodeAndValidate(this, headerData, args[0])
                validateArgs(arg1)
                return arg1
            }

            override fun argsSupplier(headerData: MiXCRHeader, parameters: P1): List<Array<String>> =
                extract(headerData, parameters)
        }

        operator fun <T : Any, P1 : Any> invoke(
            priority: Int,
            command: String,
            description: String,
            delegate: Field<T>,
            parameter1: CommandArgOptional<P1?>,
            validateArgs: FieldsCollection<*>.(P1?) -> Unit = { _ -> },
            deprecation: String? = null,
            extract: MiXCRHeader.(P1?) -> List<Array<String>>
        ): FieldsCollection<T> = object : FieldsCollectionWithParameters<T, P1?>(
            priority,
            command,
            description,
            CommandLine.Range.valueOf("0..1"),
            listOf(delegate),
            deprecation
        ) {
            override val metaVars: String = "[" + parameter1.meta + "]"

            override fun getParameters(headerData: MiXCRHeader, args: Array<String>): P1? {
                val arg1 = args.getOrNull(0)?.let { arg -> parameter1.decodeAndValidate(this, headerData, arg) }
                validateArgs(arg1)
                return arg1
            }

            override fun argsSupplier(headerData: MiXCRHeader, parameters: P1?): List<Array<String>> =
                extract(headerData, parameters)
        }

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
        ): FieldsCollection<T> = object : FieldsCollectionWithParameters<T, Pair<P1, P2>>(
            priority,
            command,
            description,
            CommandLine.Range.valueOf("2"),
            listOf(delegate),
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
        ): FieldsCollection<T> = invoke(
            priority,
            command,
            description,
            listOf(delegate),
            parameter1,
            parameter2,
            validateArgs,
            deprecation,
            extract
        )

        operator fun <T : Any, P1 : Any, P2 : Any> invoke(
            priority: Int,
            command: String,
            description: String,
            delegates: List<Field<T>>,
            parameter1: CommandArgOptional<P1?>,
            parameter2: CommandArgOptional<P2?>,
            validateArgs: FieldsCollection<*>.(P1?, P2?) -> Unit = { _, _ -> },
            deprecation: String? = null,
            extract: MiXCRHeader.(P1?, P2?) -> List<Array<String>>
        ): FieldsCollection<T> = object : FieldsCollectionWithParameters<T, Pair<P1?, P2?>>(
            priority,
            command,
            description,
            CommandLine.Range.valueOf("0..2"),
            delegates,
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

        operator fun <T : Any, P1 : Any, P2 : Any, P3 : Any> invoke(
            priority: Int,
            command: String,
            description: String,
            delegate: Field<T>,
            parameter1: CommandArgRequired<P1>,
            parameter2: CommandArgOptional<P2?>,
            parameter3: CommandArgOptional<P3?>,
            validateArgs: FieldsCollection<*>.(P1, P2?, P3?) -> Unit = { _, _, _ -> },
            deprecation: String? = null,
            extract: MiXCRHeader.(P1, P2?, P3?) -> List<Array<String>>
        ): FieldsCollection<T> = object : FieldsCollectionWithParameters<T, Triple<P1, P2?, P3?>>(
            priority,
            command,
            description,
            CommandLine.Range.valueOf("1..3"),
            listOf(delegate),
            deprecation
        ) {
            override val metaVars: String = parameter1.meta + " [" + parameter2.meta + " " + parameter3.meta + "]"

            override fun consumableArgs(args: List<String>): Int = when (args.size) {
                0, 1, 2 -> 1
                else -> when {
                    !parameter2.canConsumeArg(args[1]) || !parameter3.canConsumeArg(args[2]) -> 1
                    else -> 3
                }
            }

            override fun getParameters(headerData: MiXCRHeader, args: Array<String>): Triple<P1, P2?, P3?> {
                val arg1 = parameter1.decodeAndValidate(this, headerData, args[0])
                val arg2 = args.getOrNull(1)?.let { arg -> parameter2.decodeAndValidate(this, headerData, arg) }
                val arg3 = args.getOrNull(2)?.let { arg -> parameter3.decodeAndValidate(this, headerData, arg) }
                ValidationException.require((arg2 != null && arg3 != null) || (arg2 == null && arg3 == null)) {
                    "Both second and third arguments must be set or both must be omitted, got ${args.joinToString(", ")}"
                }
                validateArgs(arg1, arg2, arg3)
                return Triple(arg1, arg2, arg3)
            }

            override fun argsSupplier(headerData: MiXCRHeader, parameters: Triple<P1, P2?, P3?>): List<Array<String>> =
                extract(headerData, parameters.first, parameters.second, parameters.third)
        }
    }
}

private abstract class FieldsCollectionWithParameters<T : Any, P>(
    override val priority: Int,
    override val cmdArgName: String,
    override val description: String,
    override val arity: CommandLine.Range,
    private val delegates: List<Field<T>>,
    override val deprecation: String? = null
) : FieldsCollection<T> {
    protected abstract fun getParameters(headerData: MiXCRHeader, args: Array<String>): P
    protected abstract fun argsSupplier(headerData: MiXCRHeader, parameters: P): List<Array<String>>

    override fun createFields(
        headerData: MiXCRHeader,
        args: Array<String>
    ): List<FieldExtractor<T>> = argsSupplier(headerData, getParameters(headerData, args))
        .flatMap { argsForDelegate ->
            delegates.map { delegate -> delegate.create(headerData, argsForDelegate) }
        }
}

private class FieldsCollectionParameterless<T : Any>(
    override val priority: Int,
    override val cmdArgName: String,
    override val description: String,
    private val delegate: Field<T>,
    override val deprecation: String? = null,
    private val argsSupplier: MiXCRHeader.() -> List<Array<String>>
) : FieldsCollection<T> {
    override val arity: CommandLine.Range = CommandLine.Range.valueOf("0")

    override fun createFields(
        headerData: MiXCRHeader,
        args: Array<String>
    ): List<FieldExtractor<T>> = argsSupplier(headerData).map { argsForDelegate ->
        delegate.create(headerData, argsForDelegate)
    }

    override val metaVars: String = ""
}


fun <T : Any, R : Any> FieldsCollection<T>.fromProperty(
    descriptionMapper: (String) -> String = { it },
    property: R.() -> T?
): FieldsCollection<R> {
    val that = this@fromProperty
    return object : FieldsCollection<R> {
        override val priority = that.priority
        override val cmdArgName = that.cmdArgName
        override val description = descriptionMapper(that.description)
        override val deprecation = that.deprecation
        override val arity = that.arity
        override val metaVars = that.metaVars

        override fun consumableArgs(args: List<String>) = that.consumableArgs(args)

        override fun createFields(
            headerData: MiXCRHeader,
            args: Array<String>
        ): List<FieldExtractor<R>> {
            val delegates = that.createFields(headerData, args)
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
}
