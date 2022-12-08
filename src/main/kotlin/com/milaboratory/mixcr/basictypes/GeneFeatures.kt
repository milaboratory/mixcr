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
import com.milaboratory.mitool.pattern.search.BasicSerializer
import com.milaboratory.primitivio.PrimitivI
import com.milaboratory.primitivio.PrimitivO
import com.milaboratory.primitivio.annotations.Serializable
import com.milaboratory.primitivio.readList
import com.milaboratory.primitivio.readObjectRequired
import com.milaboratory.primitivio.writeCollection
import io.repseq.core.GeneFeature

@Serializable(by = GeneFeatures.SerializerImpl::class)
data class GeneFeatures @JsonCreator constructor(
    @JsonValue val features: List<GeneFeature>
) {
    constructor(geneFeature: GeneFeature) : this(listOf(geneFeature))

    init {
        check(features.isNotEmpty())
        for (i in (1 until features.size)) {
            require(features[i - 1].lastPoint <= features[i].firstPoint) {
                features.map { GeneFeature.encode(it) } + " are not ordered"
            }
        }
    }

    fun intersection(other: GeneFeature): GeneFeatures? {
        val result = features.mapNotNull { GeneFeature.intersection(it, other) }
        if (result.isEmpty()) return null
        return GeneFeatures(result)
    }

    operator fun plus(toAdd: GeneFeature): GeneFeatures {
        val lastFeature = features.last()
        return if (lastFeature.firstPoint < lastFeature.lastPoint && lastFeature.lastPoint == toAdd.firstPoint) {
            GeneFeatures(features.dropLast(1) + listOf(features.last().append(toAdd)))
        } else {
            GeneFeatures(features + listOf(toAdd))
        }
    }

    operator fun plus(toAdd: GeneFeatures): GeneFeatures =
        if (features.last().lastPoint == toAdd.features.first().firstPoint) {
            GeneFeatures(
                features.dropLast(1)
                        + listOf(features.last().append(toAdd.features.first()))
                        + toAdd.features.drop(1)
            )
        } else {
            GeneFeatures(features + toAdd.features)
        }

    fun encode(): String =
        if (features.size == 1)
            GeneFeature.encode(features[0])
        else
            features.joinToString(",", "[", "]") { GeneFeature.encode(it) }

    override fun toString() = encode()

    class SerializerImpl : BasicSerializer<GeneFeatures>() {
        override fun write(output: PrimitivO, obj: GeneFeatures) {
            output.writeCollection(obj.features) {
                writeObject(it)
            }
        }

        override fun read(input: PrimitivI): GeneFeatures {
            val features: List<GeneFeature> = input.readList {
                readObjectRequired()
            }
            return GeneFeatures(features)
        }

        override fun isReference(): Boolean = true
    }

    companion object {
        @JvmStatic
        @JsonCreator // for JsonOverrider
        fun parse(value: String): GeneFeatures = if (value.startsWith("[")) {
            if (!value.endsWith("]"))
                throw IllegalArgumentException("Malformed GeneFeatures: $value")
            GeneFeatures(value.substring(1, value.length - 1).split(",").map { GeneFeature.parse(it) })
        } else
            GeneFeatures(GeneFeature.parse(value))

        @JvmStatic
        fun parse(value: Array<String>): GeneFeatures =
            GeneFeatures(value.map { GeneFeature.parse(it) })
    }
}
