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
package com.milaboratory.mixcr.basictypes

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.module.kotlin.readValue
import com.milaboratory.mitool.pattern.search.BasicSerializer
import com.milaboratory.primitivio.PrimitivI
import com.milaboratory.primitivio.PrimitivO
import com.milaboratory.primitivio.annotations.Serializable
import com.milaboratory.primitivio.readArray
import com.milaboratory.primitivio.writeArray
import com.milaboratory.util.GlobalObjectMappers
import io.repseq.core.GeneFeature

@Serializable(by = GeneFeatures.SerializerImpl::class)
class GeneFeatures(
    @get:JsonValue
    val features: Array<GeneFeature>
) {
    constructor(geneFeature: GeneFeature) : this(arrayOf(geneFeature))

    init {
        check(features.isNotEmpty())
    }

    fun intersection(other: GeneFeature): GeneFeatures? {
        val result = features.mapNotNull { GeneFeature.intersection(it, other) }
        if (result.isEmpty()) return null
        return GeneFeatures(result.toTypedArray())
    }

    operator fun plus(toAdd: GeneFeature): GeneFeatures =
        if (features.last().lastPoint == toAdd.firstPoint) {
            GeneFeatures(features.clone().also {
                it[features.size - 1] = features.last().append(toAdd)
            })
        } else {
            GeneFeatures(features + toAdd)
        }

    operator fun plus(toAdd: GeneFeatures): GeneFeatures =
        if (features.last().lastPoint == toAdd.features.first().firstPoint) {
            GeneFeatures(
                features.clone().also {
                    it[features.size - 1] = features.last().append(toAdd.features.first())
                } + toAdd.features.copyOfRange(1, toAdd.features.size)
            )
        } else {
            GeneFeatures(features + toAdd.features)
        }

    fun encode() = features.joinToString(",", "[", "]") { GeneFeature.encode(it) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GeneFeatures

        if (!features.contentEquals(other.features)) return false

        return true
    }

    override fun hashCode(): Int = features.contentHashCode()

    override fun toString(): String = encode()


    class SerializerImpl : BasicSerializer<GeneFeatures>() {
        override fun write(output: PrimitivO, obj: GeneFeatures) {
            output.writeArray(obj.features)
        }

        override fun read(input: PrimitivI): GeneFeatures {
            val features = input.readArray<GeneFeature>()
            return GeneFeatures(features)
        }

        override fun isReference(): Boolean = true
    }

    companion object {
        @JvmStatic
        @JsonCreator
        fun parse(value: String): GeneFeatures =
            if (value.startsWith("[")) {
                GeneFeatures(GlobalObjectMappers.getOneLine().readValue<Array<GeneFeature>>(value))
            } else {
                GeneFeatures(GeneFeature.parse(value))
            }
    }
}
