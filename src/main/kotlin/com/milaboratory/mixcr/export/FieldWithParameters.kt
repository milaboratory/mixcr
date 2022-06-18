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

abstract class FieldWithParameters<T, P>(
    targetType: Class<T>,
    command: String,
    description: String,
    val nArguments: Int
) : AbstractField<T>(targetType, command, description) {
    override fun nArguments(): Int = nArguments

    protected abstract fun getParameters(args: Array<String>): P
    protected abstract fun getHeader(outputMode: OutputMode, parameters: P): String
    protected abstract fun extractValue(`object`: T, parameters: P): String

    override fun create(
        outputMode: OutputMode,
        headerData: VDJCFileHeaderData,
        args: Array<String>
    ): FieldExtractor<T> {
        require(args.size == nArguments) {
            "$command requires $nArguments arguments, got ${args.joinToString(" ")}"
        }
        val params = getParameters(args)
        val header = getHeader(outputMode, params)
        return object : AbstractFieldExtractor<T>(header, this) {
            override fun extractValue(`object`: T): String {
                return this@FieldWithParameters.extractValue(`object`, params)
            }
        }
    }

    class CommandArg<T>(
        val meta: String,
        val decode: (String) -> T,
        val hPrefix: (T) -> String,
        val sPrefix: (T) -> String
    )

    companion object {
        inline operator fun <reified T : Any, P1 : Any> invoke(
            command: String,
            description: String,
            parameter1: CommandArg<P1>,
            noinline validateArgs: AbstractField<T>.(P1) -> Unit = {},
            noinline extract: (T, P1) -> String
        ): Field<T> = object : FieldWithParameters<T, P1>(T::class.java, command, description, 1) {
            override fun metaVars(): String = parameter1.meta

            override fun getParameters(args: Array<String>): P1 {
                val arg1 = parameter1.decode(args.first())
                validateArgs(arg1)
                return arg1
            }

            override fun extractValue(`object`: T, parameters: P1): String =
                extract(`object`, parameters)

            override fun getHeader(outputMode: OutputMode, parameters: P1): String =
                FieldExtractors.choose(
                    outputMode,
                    parameter1.hPrefix(parameters),
                    parameter1.sPrefix(parameters)
                )
        }

        inline operator fun <reified T : Any, P1 : Any, P2 : Any> invoke(
            command: String,
            description: String,
            parameter1: CommandArg<P1>,
            parameter2: CommandArg<P2>,
            noinline validateArgs: AbstractField<T>.(P1, P2) -> Unit = { _, _ -> },
            noinline extract: (T, P1, P2) -> String
        ): Field<T> = object : FieldWithParameters<T, Pair<P1, P2>>(T::class.java, command, description, 2) {
            override fun metaVars(): String = parameter1.meta + " " + parameter2.meta

            override fun getParameters(args: Array<String>): Pair<P1, P2> {
                val arg1 = parameter1.decode(args[0])
                val arg2 = parameter2.decode(args[1])
                validateArgs(arg1, arg2)
                return arg1 to arg2
            }

            override fun extractValue(`object`: T, parameters: Pair<P1, P2>): String =
                extract(`object`, parameters.first, parameters.second)

            override fun getHeader(outputMode: OutputMode, parameters: Pair<P1, P2>): String =
                FieldExtractors.choose(
                    outputMode,
                    parameter1.hPrefix(parameters.first) + " " + parameter2.hPrefix(parameters.second),
                    parameter1.sPrefix(parameters.first) + parameter2.sPrefix(parameters.second),
                )
        }

        inline operator fun <reified T : Any, P1 : Any, P2 : Any, P3 : Any> invoke(
            command: String,
            description: String,
            parameter1: CommandArg<P1>,
            parameter2: CommandArg<P2>,
            parameter3: CommandArg<P3>,
            noinline validateArgs: AbstractField<T>.(P1, P2, P3) -> Unit = { _, _, _ -> },
            noinline extract: (T, P1, P2, P3) -> String
        ): Field<T> = object : FieldWithParameters<T, Triple<P1, P2, P3>>(T::class.java, command, description, 3) {
            override fun metaVars(): String = parameter1.meta + " " + parameter2.meta + " " + parameter3.meta

            override fun getParameters(args: Array<String>): Triple<P1, P2, P3> {
                val arg1 = parameter1.decode(args[0])
                val arg2 = parameter2.decode(args[1])
                val arg3 = parameter3.decode(args[2])
                validateArgs(arg1, arg2, arg3)
                return Triple(arg1, arg2, arg3)
            }

            override fun extractValue(`object`: T, parameters: Triple<P1, P2, P3>): String =
                extract(`object`, parameters.first, parameters.second, parameters.third)

            override fun getHeader(outputMode: OutputMode, parameters: Triple<P1, P2, P3>): String =
                FieldExtractors.choose(
                    outputMode,
                    parameter1.hPrefix(parameters.first)
                        + " " + parameter2.hPrefix(parameters.second)
                        + " " + parameter3.hPrefix(parameters.third),
                    parameter1.sPrefix(parameters.first)
                        + parameter2.sPrefix(parameters.second)
                        + parameter3.sPrefix(parameters.third),
                )
        }
    }

}
