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

class VJPair<T>(
    val V: T,
    val J: T
) {
    operator fun get(geneType: GeneType): T = when (geneType) {
        Variable -> V
        Joining -> J
        else -> throw IllegalArgumentException()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VJPair<*>

        if (V != other.V) return false
        if (J != other.J) return false

        return true
    }

    override fun hashCode(): Int {
        var result = V?.hashCode() ?: 0
        result = 31 * result + (J?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "(V=$V, J=$J)"
    }
}
