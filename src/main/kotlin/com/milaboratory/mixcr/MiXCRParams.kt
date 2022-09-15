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
package com.milaboratory.mixcr

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.milaboratory.mixcr.cli.*

// @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, )
interface MiXCRParams {
    @get:JsonIgnore
    val command: MiXCRCommand<*>
}

@JsonDeserialize(using = MiXCRCommand.Companion.JDeserializer::class)
sealed interface MiXCRCommand<T : MiXCRParams> {
    val command: String
    fun extractFromBundle(bundle: MiXCRParamsBundle): T?

    object align : MiXCRCommand<CommandAlign.Params> {
        @get:JsonValue
        override val command get() = "align"
        override fun extractFromBundle(bundle: MiXCRParamsBundle) = bundle.align
    }

    object exportAlignments : MiXCRCommand<CommandExportAlignments.Params> {
        @get:JsonValue
        override val command get() = "exportAlignments"
        override fun extractFromBundle(bundle: MiXCRParamsBundle) = bundle.exportAlignments
    }

    object extend : MiXCRCommand<CommandExtend.Params> {
        @get:JsonValue
        override val command get() = "extend"
        override fun extractFromBundle(bundle: MiXCRParamsBundle) = bundle.extend
    }

    object assemblePartial : MiXCRCommand<CommandAssemblePartial.Params> {
        @get:JsonValue
        override val command get() = "assemblePartial"
        override fun extractFromBundle(bundle: MiXCRParamsBundle) = bundle.assemblePartial
    }

    object assemble : MiXCRCommand<CommandAssemble.Params> {
        @get:JsonValue
        override val command get() = "assemble"
        override fun extractFromBundle(bundle: MiXCRParamsBundle) = bundle.assemble
    }

    object assembleContigs : MiXCRCommand<CommandAssembleContigs.Params> {
        @get:JsonValue
        override val command get() = "assembleContigs"
        override fun extractFromBundle(bundle: MiXCRParamsBundle) = bundle.assembleContigs
    }

    object exportClones : MiXCRCommand<CommandExportClones.Params> {
        @get:JsonValue
        override val command get() = "exportClones"
        override fun extractFromBundle(bundle: MiXCRParamsBundle) = bundle.exportClones
    }

    companion object {
        class JDeserializer : JsonDeserializer<MiXCRCommand<*>>() {
            override fun deserialize(p: JsonParser, ctxt: DeserializationContext): MiXCRCommand<*> = run {
                if (p.currentToken != JsonToken.VALUE_STRING)
                    throw ctxt.wrongTokenException(p, MiXCRCommand::class.java, JsonToken.VALUE_STRING, "")
                when (val str = p.text) {
                    "align" -> align
                    "exportAlignments" -> exportAlignments
                    "extend" -> extend
                    "assemblePartial" -> assemblePartial
                    "assemble" -> assemble
                    "assembleContigs" -> assembleContigs
                    "exportClones" -> exportClones
                    else -> throw ctxt.instantiationException(MiXCRCommand::class.java, "Unknown value: $str")
                }
            }
        }
    }
}

