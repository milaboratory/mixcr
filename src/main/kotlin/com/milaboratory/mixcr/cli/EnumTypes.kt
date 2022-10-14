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
package com.milaboratory.mixcr.cli

import com.milaboratory.miplots.StandardPlots
import com.milaboratory.miplots.stat.util.PValueCorrection
import picocli.CommandLine

private inline fun <reified T : Enum<T>> candidatesByField(cliName: (T) -> String): List<String> =
    enumValues<T>().map(cliName)

private inline fun <reified T : Enum<T>> candidatesWithNone(): List<String> =
    listOf("none") + enumValues<T>().map { it.name }

object EnumTypes {
    abstract class ConverterWithNone<T : Enum<T>>(
        private val converter: (String) -> T
    ) : CommandLine.ITypeConverter<T> {
        override fun convert(value: String?): T? = when {
            value.isNullOrBlank() -> null
            value == "none" -> null
            else -> converter(value)
        }
    }

    class PlotTypeCandidates : TypeCandidates(candidatesByField<StandardPlots.PlotType> { it.cliName })

    class PValueCorrectionMethodCandidatesWithNone : TypeCandidates(candidatesWithNone<PValueCorrection.Method>())

    class PValueCorrectionMethodConverterWithNone :
        ConverterWithNone<PValueCorrection.Method>({ PValueCorrection.Method.valueOf(it) })
}
