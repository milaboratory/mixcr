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
@file:Suppress("ClassName")

package com.milaboratory.mixcr

import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.milaboratory.mitool.helpers.K_OM
import com.milaboratory.mitool.helpers.K_YAML_OM
import com.milaboratory.mitool.helpers.readList
import com.milaboratory.mitool.helpers.writeList
import com.milaboratory.mitool.pattern.search.readObject
import com.milaboratory.mixcr.alleles.FindAllelesReport
import com.milaboratory.mixcr.assembler.fullseq.FullSeqAssemblerReport
import com.milaboratory.mixcr.cli.AbstractMiXCRCommandReport
import com.milaboratory.mixcr.cli.AlignerReport
import com.milaboratory.mixcr.cli.CloneAssemblerReport
import com.milaboratory.mixcr.cli.CommandAlign
import com.milaboratory.mixcr.cli.CommandAssemble
import com.milaboratory.mixcr.cli.CommandAssembleContigs
import com.milaboratory.mixcr.cli.CommandAssemblePartial
import com.milaboratory.mixcr.cli.CommandExportAlignments
import com.milaboratory.mixcr.cli.CommandExportClones
import com.milaboratory.mixcr.cli.CommandExtend
import com.milaboratory.mixcr.cli.CommandFindAlleles
import com.milaboratory.mixcr.cli.CommandFindShmTrees
import com.milaboratory.mixcr.cli.CommandRefineTagsAndSort
import com.milaboratory.mixcr.cli.MiXCRCommandReport
import com.milaboratory.mixcr.cli.RefineTagsAndSortReport
import com.milaboratory.mixcr.partialassembler.PartialAlignmentsAssemblerReport
import com.milaboratory.mixcr.trees.BuildSHMTreeReport
import com.milaboratory.mixcr.util.VDJCObjectExtenderReport
import com.milaboratory.primitivio.PrimitivI
import com.milaboratory.primitivio.PrimitivO
import com.milaboratory.primitivio.Serializer
import com.milaboratory.primitivio.annotations.Serializable
import com.milaboratory.util.ReportHelper
import kotlin.reflect.KClass

/** Marker class to indicate the command has no report class associated with it. */
class NoReport : AbstractMiXCRCommandReport(null, "", emptyArray(), emptyArray(), null, "") {
    override fun writeReport(helper: ReportHelper) {
        throw IllegalStateException()
    }

    override fun command() = ""
}

typealias AnyMiXCRCommand = MiXCRCommandDescriptor<*, *>

@JsonDeserialize(using = MiXCRCommandDescriptor.Companion.JDeserializer::class)
sealed class MiXCRCommandDescriptor<P : MiXCRParams, R : MiXCRCommandReport> : Comparable<AnyMiXCRCommand> {
    abstract val paramClass: KClass<P>
    abstract val reportClass: KClass<R>
    abstract val command: String
    abstract val order: Int
    open val allowMultipleRounds: Boolean get() = false

    abstract fun outputName(prefix: String, params: P, round: Int): String
    abstract fun reportName(prefix: String, params: P, round: Int): String?
    abstract fun jsonReportName(prefix: String, params: P, round: Int): String?

    abstract fun extractFromBundle(bundle: MiXCRParamsBundle): P?

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

    override fun compareTo(other: AnyMiXCRCommand) = order.compareTo(other.order)

    override fun toString() = command

    object align : MiXCRCommandDescriptor<CommandAlign.Params, AlignerReport>() {
        override val paramClass get() = CommandAlign.Params::class
        override val reportClass get() = AlignerReport::class

        @get:JsonValue
        override val command get() = CommandAlign.COMMAND_NAME
        override val order get() = 0

        override fun outputName(prefix: String, params: CommandAlign.Params, round: Int) =
            "${prefix.ifBlank { "alignments" }}.vdjca"

        override fun reportName(prefix: String, params: CommandAlign.Params, round: Int) =
            "${prefix.dotIfNotBlank()}align.report.txt"

        override fun jsonReportName(prefix: String, params: CommandAlign.Params, round: Int) =
            "${prefix.dotIfNotBlank()}align.report.json"

        override fun extractFromBundle(bundle: MiXCRParamsBundle) = bundle.align
    }

    object refineTagsAndSort : MiXCRCommandDescriptor<CommandRefineTagsAndSort.Params, RefineTagsAndSortReport>() {
        override val paramClass get() = CommandRefineTagsAndSort.Params::class
        override val reportClass get() = RefineTagsAndSortReport::class

        @get:JsonValue
        override val command get() = CommandRefineTagsAndSort.COMMAND_NAME
        override val order get() = 1

        override fun outputName(prefix: String, params: CommandRefineTagsAndSort.Params, round: Int) =
            "${prefix.ifBlank { "alignments" }}.refined.vdjca"

        override fun reportName(prefix: String, params: CommandRefineTagsAndSort.Params, round: Int) =
            "${prefix.dotIfNotBlank()}refine.report.txt"

        override fun jsonReportName(prefix: String, params: CommandRefineTagsAndSort.Params, round: Int) =
            "${prefix.dotIfNotBlank()}refine.report.json"

        override fun extractFromBundle(bundle: MiXCRParamsBundle) = bundle.refineTagsAndSort
    }

    object exportAlignments : MiXCRCommandDescriptor<CommandExportAlignments.Params, NoReport>() {
        override val paramClass get() = CommandExportAlignments.Params::class
        override val reportClass get() = NoReport::class

        @get:JsonValue
        override val command get() = CommandExportAlignments.COMMAND_NAME
        override val order get() = 2

        override fun outputName(prefix: String, params: CommandExportAlignments.Params, round: Int) =
            "${prefix.dotIfNotBlank()}alignments.tsv"

        override fun reportName(prefix: String, params: CommandExportAlignments.Params, round: Int) = null
        override fun jsonReportName(prefix: String, params: CommandExportAlignments.Params, round: Int) = null

        override fun extractFromBundle(bundle: MiXCRParamsBundle) = bundle.exportAlignments
    }

    object assemblePartial : MiXCRCommandDescriptor<CommandAssemblePartial.Params, PartialAlignmentsAssemblerReport>() {
        override val paramClass get() = CommandAssemblePartial.Params::class
        override val reportClass get() = PartialAlignmentsAssemblerReport::class

        @get:JsonValue
        override val command get() = CommandAssemblePartial.COMMAND_NAME
        override val order get() = 3
        override val allowMultipleRounds: Boolean get() = true

        override fun outputName(prefix: String, params: CommandAssemblePartial.Params, round: Int) =
            "${prefix.ifBlank { "alignments" }}.passembled.${round + 1}.vdjca"

        override fun reportName(prefix: String, params: CommandAssemblePartial.Params, round: Int) =
            "${prefix.dotIfNotBlank()}assemblePartial.report.txt"

        override fun jsonReportName(prefix: String, params: CommandAssemblePartial.Params, round: Int) =
            "${prefix.dotIfNotBlank()}assemblePartial.report.json"

        override fun extractFromBundle(bundle: MiXCRParamsBundle) = bundle.assemblePartial
    }

    object extend : MiXCRCommandDescriptor<CommandExtend.Params, VDJCObjectExtenderReport>() {
        override val paramClass get() = CommandExtend.Params::class
        override val reportClass get() = VDJCObjectExtenderReport::class

        @get:JsonValue
        override val command get() = CommandExtend.COMMAND_NAME
        override val order get() = 4

        override fun outputName(prefix: String, params: CommandExtend.Params, round: Int) =
            "${prefix.ifBlank { "alignments" }}.extended.vdjca"

        override fun reportName(prefix: String, params: CommandExtend.Params, round: Int) =
            "${prefix.dotIfNotBlank()}extend.report.txt"

        override fun jsonReportName(prefix: String, params: CommandExtend.Params, round: Int) =
            "${prefix.dotIfNotBlank()}extend.report.json"

        override fun extractFromBundle(bundle: MiXCRParamsBundle) = bundle.extend
    }

    object assemble : MiXCRCommandDescriptor<CommandAssemble.Params, CloneAssemblerReport>() {
        override val paramClass get() = CommandAssemble.Params::class
        override val reportClass get() = CloneAssemblerReport::class

        @get:JsonValue
        override val command get() = CommandAssemble.COMMAND_NAME
        override val order get() = 5

        override fun outputName(prefix: String, params: CommandAssemble.Params, round: Int) =
            prefix.ifBlank { "clones" } + (if (params.clnaOutput) ".clna" else ".clns")

        override fun reportName(prefix: String, params: CommandAssemble.Params, round: Int) =
            "${prefix.dotIfNotBlank()}assemble.report.txt"

        override fun jsonReportName(prefix: String, params: CommandAssemble.Params, round: Int) =
            "${prefix.dotIfNotBlank()}assemble.report.json"

        override fun extractFromBundle(bundle: MiXCRParamsBundle) = bundle.assemble
    }

    object assembleContigs : MiXCRCommandDescriptor<CommandAssembleContigs.Params, FullSeqAssemblerReport>() {
        override val paramClass get() = CommandAssembleContigs.Params::class
        override val reportClass get() = FullSeqAssemblerReport::class

        @get:JsonValue
        override val command get() = CommandAssembleContigs.COMMAND_NAME
        override val order get() = 6

        override fun outputName(prefix: String, params: CommandAssembleContigs.Params, round: Int) =
            "${prefix.ifBlank { "clones" }}.contigs.clns"

        override fun reportName(prefix: String, params: CommandAssembleContigs.Params, round: Int) =
            "${prefix.dotIfNotBlank()}assembleContigs.report.txt"

        override fun jsonReportName(prefix: String, params: CommandAssembleContigs.Params, round: Int) =
            "${prefix.dotIfNotBlank()}assembleContigs.report.json"

        override fun extractFromBundle(bundle: MiXCRParamsBundle) = bundle.assembleContigs
    }

    object exportClones : MiXCRCommandDescriptor<CommandExportClones.Params, NoReport>() {
        override val paramClass get() = CommandExportClones.Params::class
        override val reportClass get() = NoReport::class

        @get:JsonValue
        override val command get() = CommandExportClones.COMMAND_NAME
        override val order get() = 7

        override fun outputName(prefix: String, params: CommandExportClones.Params, round: Int) =
            "${prefix.dotIfNotBlank()}clones.tsv"

        override fun reportName(prefix: String, params: CommandExportClones.Params, round: Int) = null
        override fun jsonReportName(prefix: String, params: CommandExportClones.Params, round: Int) = null

        override fun extractFromBundle(bundle: MiXCRParamsBundle) = bundle.exportClones
    }

    object findAlleles : MiXCRCommandDescriptor<CommandFindAlleles.Params, FindAllelesReport>() {
        override val paramClass get() = CommandFindAlleles.Params::class
        override val reportClass get() = FindAllelesReport::class

        @get:JsonValue
        override val command get() = CommandFindAlleles.COMMAND_NAME
        override val order get() = 8

        override fun outputName(prefix: String, params: CommandFindAlleles.Params, round: Int) = TODO()

        override fun reportName(prefix: String, params: CommandFindAlleles.Params, round: Int) = TODO()
        override fun jsonReportName(prefix: String, params: CommandFindAlleles.Params, round: Int) = TODO()

        override fun extractFromBundle(bundle: MiXCRParamsBundle) = TODO()
    }

    object findShmTrees : MiXCRCommandDescriptor<CommandFindShmTrees.Params, BuildSHMTreeReport>() {
        override val paramClass get() = CommandFindShmTrees.Params::class
        override val reportClass get() = BuildSHMTreeReport::class

        @get:JsonValue
        override val command get() = CommandFindShmTrees.COMMAND_NAME
        override val order get() = 9

        override fun outputName(prefix: String, params: CommandFindShmTrees.Params, round: Int) = TODO()

        override fun reportName(prefix: String, params: CommandFindShmTrees.Params, round: Int) = TODO()
        override fun jsonReportName(prefix: String, params: CommandFindShmTrees.Params, round: Int) = TODO()

        override fun extractFromBundle(bundle: MiXCRParamsBundle) = TODO()
    }

    companion object {
        private fun String.dotIfNotBlank() = if (isBlank()) this else "$this."

        fun fromStringOrNull(str: String): AnyMiXCRCommand? =
            when (str) {
                align.command -> align
                refineTagsAndSort.command -> refineTagsAndSort
                exportAlignments.command -> exportAlignments
                extend.command -> extend
                assemblePartial.command -> assemblePartial
                assemble.command -> assemble
                assembleContigs.command -> assembleContigs
                exportClones.command -> exportClones
                findAlleles.command -> findAlleles
                findShmTrees.command -> findShmTrees
                else -> null
            }

        fun fromString(str: String): AnyMiXCRCommand =
            fromStringOrNull(str) ?: throw IllegalArgumentException("Unknown command: $str")

        class JDeserializer : JsonDeserializer<AnyMiXCRCommand>() {
            override fun deserialize(p: JsonParser, ctxt: DeserializationContext): AnyMiXCRCommand = run {
                if (p.currentToken != JsonToken.VALUE_STRING)
                    throw ctxt.wrongTokenException(p, MiXCRCommandDescriptor::class.java, JsonToken.VALUE_STRING, "")
                fromStringOrNull(p.text) ?: throw ctxt.instantiationException(
                    MiXCRCommandDescriptor::class.java,
                    "Unknown value: ${p.text}"
                )
            }
        }
    }
}

interface IStepDataCollection {
    val upstreamCollections: List<IStepDataCollection>
    val dataMap: LinkedHashMap<String, List<ByteArray>>
    val steps: List<String>

    fun asTree(): JsonNode = run {
        val resMap = linkedMapOf<String, Any>()
        if (upstreamCollections.isNotEmpty())
            resMap["upstreams"] = upstreamCollections.map { it.asTree() }
        for (e in dataMap) {
            val valuesSet = e.value.map { K_OM.readTree(it) }.toSet()
            resMap[e.key] = (if (valuesSet.size == 1) valuesSet.first() else valuesSet)
        }
        K_OM.valueToTree(resMap)
    }

    fun getTrees(step: String): List<JsonNode>
    fun getTrees(step: AnyMiXCRCommand): List<JsonNode>

    fun getYamls(step: String): List<String> =
        getTrees(step).map { K_YAML_OM.writeValueAsString(step) }

    fun getYamls(step: AnyMiXCRCommand): List<String> =
        getTrees(step).map { K_YAML_OM.writeValueAsString(step) }

    fun getPrettyJsons(step: String): List<String> =
        getTrees(step).map { K_OM.writeValueAsString(step) }

    fun getPrettyJsons(step: AnyMiXCRCommand): List<String> =
        getTrees(step).map { K_OM.writeValueAsString(step) }

    fun getCompactJsons(step: String): List<String> =
        getTrees(step).map { K_OM.writeValueAsString(step) }

    fun getCompactJsons(step: AnyMiXCRCommand): List<String> =
        getTrees(step).map { K_OM.writeValueAsString(step) }
}

/** Represents a collection of reports / parameters for steps executed for the dataset. Each report encoded as Json. */
@Serializable(by = StepDataCollection.Companion.SerializerImpl::class)
class StepDataCollection(
    override val upstreamCollections: List<StepDataCollection> = emptyList(),
    private val perStepData: List<Pair<String, ByteArray>> = emptyList()
) : IStepDataCollection {
    override val dataMap by lazy {
        val res = LinkedHashMap<String, List<ByteArray>>()
        for (el in perStepData)
            res.compute(el.first) { _, v -> (v ?: emptyList()) + el.second }
        res
    }

    override val steps by lazy { dataMap.keys.toList() }

    fun add(step: String, report: ByteArray) = StepDataCollection(upstreamCollections, perStepData + (step to report))
    fun <D> add(step: AnyMiXCRCommand, report: D) = add(step.command, K_OM.writeValueAsBytes(report))
    fun getBytes(step: String): List<ByteArray> = perStepData.filter { it.first == step }.map { it.second }
    fun getBytes(step: AnyMiXCRCommand): List<ByteArray> = getBytes(step.command)
    fun <D : Any> get(step: AnyMiXCRCommand, type: KClass<D>) = getBytes(step).map { K_OM.readValue(it, type.java) }
    override fun getTrees(step: String) = getBytes(step).map { K_OM.readTree(it) }
    override fun getTrees(step: AnyMiXCRCommand) = getBytes(step).map { K_OM.readTree(it) }

    fun <D : Any, P : MiXCRParams, R : MiXCRCommandReport> get(
        step: MiXCRCommandDescriptor<P, R>,
        typeExtractor: (MiXCRCommandDescriptor<P, R>) -> KClass<D>
    ) =
        get(step, typeExtractor(step))

    fun getMap(typeExtractor: (AnyMiXCRCommand) -> KClass<*>) =
        dataMap.map { e ->
            val cmd = MiXCRCommandDescriptor.fromString(e.key)
            val type = typeExtractor(cmd)
            cmd to e.value.map { K_OM.readValue(it, type.java) }
        }.toMap()

    companion object {
        class SerializerImpl : Serializer<StepDataCollection> {
            override fun write(output: PrimitivO, obj: StepDataCollection) {
                output.writeList(obj.upstreamCollections) {
                    writeObject(it) // recurrent call to the same serializer
                }
                output.writeList(obj.perStepData) {
                    writeUTF(it.first)
                    writeObject(it.second)
                }
            }

            override fun read(input: PrimitivI): StepDataCollection {
                val upstreams = input.readList {
                    readObject<StepDataCollection>() // recurrent call to the same serializer
                }
                val steps = input.readList {
                    val step = readUTF()
                    val content = readObject<ByteArray>()
                    step to content
                }
                return StepDataCollection(upstreams, steps)
            }

            override fun isReference() = false
            override fun handlesReference() = false
        }
    }
}

@Serializable(by = MiXCRStepReports.Companion.SerializerImpl::class)
class MiXCRStepReports(val collection: StepDataCollection = StepDataCollection()) :
    IStepDataCollection by collection {
    val upstreams by lazy { collection.upstreamCollections.map { MiXCRStepReports(it) } }

    val map by lazy {
        @Suppress("UNCHECKED_CAST")
        collection.getMap { it.reportClass } as Map<AnyMiXCRCommand, List<MiXCRCommandReport>>
    }

    fun <R : MiXCRCommandReport> add(step: MiXCRCommandDescriptor<*, R>, report: R) =
        MiXCRStepReports(collection.add(step, report))

    operator fun <R : MiXCRCommandReport> get(step: MiXCRCommandDescriptor<*, R>): List<R> =
        collection.get(step) { it.reportClass }

    companion object {
        fun mergeUpstreams(upstreams: List<MiXCRStepReports>) =
            MiXCRStepReports(StepDataCollection(upstreams.map { it.collection }))

        class SerializerImpl : Serializer<MiXCRStepReports> {
            override fun write(output: PrimitivO, obj: MiXCRStepReports) = output.writeObject(obj.collection)
            override fun read(input: PrimitivI) = MiXCRStepReports(input.readObject<StepDataCollection>())
            override fun isReference() = false
            override fun handlesReference() = false
        }
    }
}

@Serializable(by = MiXCRStepParams.Companion.SerializerImpl::class)
class MiXCRStepParams(private val collection: StepDataCollection = StepDataCollection()) :
    IStepDataCollection by collection {
    val upstreams by lazy { collection.upstreamCollections.map { MiXCRStepParams(it) } }

    val map by lazy {
        @Suppress("UNCHECKED_CAST")
        collection.getMap { it.paramClass } as Map<AnyMiXCRCommand, List<MiXCRParams>>
    }

    fun <P : MiXCRParams> add(step: MiXCRCommandDescriptor<P, *>, params: P) =
        MiXCRStepParams(collection.add(step, params))

    operator fun <P : MiXCRParams> get(step: MiXCRCommandDescriptor<P, *>): List<P> =
        collection.get(step) { it.paramClass }

    companion object {
        fun mergeUpstreams(upstreams: List<MiXCRStepParams>) =
            MiXCRStepParams(StepDataCollection(upstreams.map { it.collection }))

        class SerializerImpl : Serializer<MiXCRStepParams> {
            override fun write(output: PrimitivO, obj: MiXCRStepParams) = output.writeObject(obj.collection)
            override fun read(input: PrimitivI) = MiXCRStepParams(input.readObject<StepDataCollection>())
            override fun isReference() = false
            override fun handlesReference() = false
        }
    }
}
