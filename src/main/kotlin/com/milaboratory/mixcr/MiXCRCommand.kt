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

import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.milaboratory.mixcr.cli.*


@JsonDeserialize(using = MiXCRCommand.Companion.JDeserializer::class)
sealed class MiXCRCommand<P : MiXCRParams> : Comparable<MiXCRCommand<*>> {
    abstract val command: String
    abstract val order: Int
    open val allowMultipleRounds: Boolean get() = false

    abstract fun outputName(prefix: String, params: P, round: Int): String
    abstract fun reportName(prefix: String, params: P, round: Int): String?
    abstract fun jsonReportName(prefix: String, params: P, round: Int): String?

    abstract fun extractFromBundle(bundle: MiXCRParamsBundle): P?

    abstract fun createCommand(): AbstractMiXCRCommand

    fun outputName(prefix: String, bundle: MiXCRParamsBundle, round: Int) =
        outputName(
            prefix,
            extractFromBundle(bundle) ?: throw RuntimeException("Parameters bundle has no parameters for $command"),
            round
        )

    fun reportName(prefix: String, bundle: MiXCRParamsBundle, round: Int) =
        reportName(
            prefix,
            extractFromBundle(bundle) ?: throw RuntimeException("Parameters bundle has no parameters for $command"),
            round
        )

    fun jsonReportName(prefix: String, bundle: MiXCRParamsBundle, round: Int) =
        jsonReportName(
            prefix,
            extractFromBundle(bundle) ?: throw RuntimeException("Parameters bundle has no parameters for $command"),
            round
        )

    override fun compareTo(other: MiXCRCommand<*>) = order.compareTo(other.order)

    override fun toString() = command

    object align : MiXCRCommand<CommandAlign.Params>() {
        @get:JsonValue
        override val command get() = "align"
        override val order get() = 0

        override fun outputName(prefix: String, params: CommandAlign.Params, round: Int) =
            "${prefix.ifBlank { "alignments" }}.vdjca"

        override fun reportName(prefix: String, params: CommandAlign.Params, round: Int) =
            "${prefix.dotIfNotBlank()}align.report.txt"

        override fun jsonReportName(prefix: String, params: CommandAlign.Params, round: Int) =
            "${prefix.dotIfNotBlank()}align.report.json"

        override fun extractFromBundle(bundle: MiXCRParamsBundle) = bundle.align

        override fun createCommand() = CommandAlign.Cmd()
    }

    object exportAlignments : MiXCRCommand<CommandExportAlignments.Params>() {
        @get:JsonValue
        override val command get() = "exportAlignments"
        override val order get() = 1

        override fun outputName(prefix: String, params: CommandExportAlignments.Params, round: Int) =
            "${prefix.dotIfNotBlank()}alignments.tsv"

        override fun reportName(prefix: String, params: CommandExportAlignments.Params, round: Int) = null
        override fun jsonReportName(prefix: String, params: CommandExportAlignments.Params, round: Int) = null

        override fun extractFromBundle(bundle: MiXCRParamsBundle) = bundle.exportAlignments

        override fun createCommand() = CommandExportAlignments.Cmd()
    }

    object extend : MiXCRCommand<CommandExtend.Params>() {
        @get:JsonValue
        override val command get() = "extend"
        override val order get() = 2

        override fun outputName(prefix: String, params: CommandExtend.Params, round: Int) =
            "${prefix.ifBlank { "alignments" }}.extended.vdjca"

        override fun reportName(prefix: String, params: CommandExtend.Params, round: Int) =
            "${prefix.dotIfNotBlank()}extend.report.txt"

        override fun jsonReportName(prefix: String, params: CommandExtend.Params, round: Int) =
            "${prefix.dotIfNotBlank()}extend.report.json"

        override fun extractFromBundle(bundle: MiXCRParamsBundle) = bundle.extend

        override fun createCommand() = CommandExtend.Cmd()
    }

    object assemblePartial : MiXCRCommand<CommandAssemblePartial.Params>() {
        @get:JsonValue
        override val command get() = "assemblePartial"
        override val order get() = 3
        override val allowMultipleRounds: Boolean get() = true

        override fun outputName(prefix: String, params: CommandAssemblePartial.Params, round: Int) =
            "${prefix.ifBlank { "alignments" }}.passembled.${round}.vdjca"

        override fun reportName(prefix: String, params: CommandAssemblePartial.Params, round: Int) =
            "${prefix.dotIfNotBlank()}assemblePartial.report.txt"

        override fun jsonReportName(prefix: String, params: CommandAssemblePartial.Params, round: Int) =
            "${prefix.dotIfNotBlank()}assemblePartial.report.json"

        override fun extractFromBundle(bundle: MiXCRParamsBundle) = bundle.assemblePartial

        override fun createCommand() = CommandAssemblePartial.Cmd()
    }

    object assemble : MiXCRCommand<CommandAssemble.Params>() {
        @get:JsonValue
        override val command get() = "assemble"
        override val order get() = 4

        override fun outputName(prefix: String, params: CommandAssemble.Params, round: Int) =
            prefix.ifBlank { "clones" } + (if (params.clnaOutput) ".clna" else ".clns")

        override fun reportName(prefix: String, params: CommandAssemble.Params, round: Int) =
            "${prefix.dotIfNotBlank()}assemble.report.txt"

        override fun jsonReportName(prefix: String, params: CommandAssemble.Params, round: Int) =
            "${prefix.dotIfNotBlank()}assemble.report.json"

        override fun extractFromBundle(bundle: MiXCRParamsBundle) = bundle.assemble

        override fun createCommand() = CommandAssemble.Cmd()
    }

    object assembleContigs : MiXCRCommand<CommandAssembleContigs.Params>() {
        @get:JsonValue
        override val command get() = "assembleContigs"
        override val order get() = 5

        override fun outputName(prefix: String, params: CommandAssembleContigs.Params, round: Int) =
            "${prefix.ifBlank { "clones" }}.contigs.clns"

        override fun reportName(prefix: String, params: CommandAssembleContigs.Params, round: Int) =
            "${prefix.dotIfNotBlank()}assembleContigs.report.txt"

        override fun jsonReportName(prefix: String, params: CommandAssembleContigs.Params, round: Int) =
            "${prefix.dotIfNotBlank()}assembleContigs.report.json"

        override fun extractFromBundle(bundle: MiXCRParamsBundle) = bundle.assembleContigs

        override fun createCommand() = CommandAssembleContigs.Cmd()
    }

    object exportClones : MiXCRCommand<CommandExportClones.Params>() {
        @get:JsonValue
        override val command get() = "exportClones"
        override val order get() = 6

        override fun outputName(prefix: String, params: CommandExportClones.Params, round: Int) =
            "${prefix.dotIfNotBlank()}clones.tsv"

        override fun reportName(prefix: String, params: CommandExportClones.Params, round: Int) = null
        override fun jsonReportName(prefix: String, params: CommandExportClones.Params, round: Int) = null

        override fun extractFromBundle(bundle: MiXCRParamsBundle) = bundle.exportClones

        override fun createCommand() = CommandExportClones.Cmd()
    }

    companion object {
        private fun String.dotIfNotBlank() = if (isBlank()) this else "$this."

        fun fromStringOrNull(str: String): MiXCRCommand<*>? =
            when (str) {
                "align" -> align
                "exportAlignments" -> exportAlignments
                "extend" -> extend
                "assemblePartial" -> assemblePartial
                "assemble" -> assemble
                "assembleContigs" -> assembleContigs
                "exportClones" -> exportClones
                else -> null
            }

        fun fromString(str: String): MiXCRCommand<*> =
            fromStringOrNull(str) ?: throw IllegalArgumentException("Unknown command: $str")

        class JDeserializer : JsonDeserializer<MiXCRCommand<*>>() {
            override fun deserialize(p: JsonParser, ctxt: DeserializationContext): MiXCRCommand<*> = run {
                if (p.currentToken != JsonToken.VALUE_STRING)
                    throw ctxt.wrongTokenException(p, MiXCRCommand::class.java, JsonToken.VALUE_STRING, "")
                fromStringOrNull(p.text) ?: throw ctxt.instantiationException(
                    MiXCRCommand::class.java,
                    "Unknown value: ${p.text}"
                )
            }
        }
    }
}