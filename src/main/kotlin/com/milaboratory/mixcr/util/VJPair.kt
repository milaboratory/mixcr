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
package com.milaboratory.mixcr.util

import io.repseq.core.GeneType
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable

data class VJPair<T>(
    val V: T,
    val J: T
) {
    operator fun get(geneType: GeneType): T = when (geneType) {
        Variable -> V
        Joining -> J
        else -> throw IllegalArgumentException()
    }

    fun <R> map(function: (T) -> R): VJPair<R> = VJPair(
        V = function(V),
        J = function(J),
    )

    override fun toString(): String {
        return "(V=$V, J=$J)"
    }
}
