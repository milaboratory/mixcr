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
@file:Suppress("LocalVariableName")

package com.milaboratory.mixcr.util

import com.milaboratory.primitivio.PrimitivI
import com.milaboratory.primitivio.PrimitivO
import com.milaboratory.primitivio.readObjectRequired
import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE
import io.repseq.core.GeneType
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable

@JsonAutoDetect(
    fieldVisibility = ANY,
    isGetterVisibility = NONE,
    getterVisibility = NONE
)
data class VJPair<T : Any>(
    val V: T,
    val J: T
) {
    operator fun get(geneType: GeneType): T = when (geneType) {
        Variable -> V
        Joining -> J
        else -> throw IllegalArgumentException()
    }

    fun <R : Any> map(function: (T) -> R): VJPair<R> = VJPair(
        V = function(V),
        J = function(J),
    )

    override fun toString(): String = "(V=$V, J=$J)"
}

fun <T : Any> PrimitivO.writePair(pair: VJPair<T>) {
    writeObject(pair.V)
    writeObject(pair.J)
}

inline fun <reified T : Any> PrimitivI.readPair(): VJPair<T> {
    val V = readObjectRequired<T>()
    val J = readObjectRequired<T>()
    return VJPair(V, J)
}
