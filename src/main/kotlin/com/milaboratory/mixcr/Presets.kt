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
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.milaboratory.cli.AbstractPresetBundleRaw
import com.milaboratory.cli.ParamsBundleSpec
import com.milaboratory.cli.RawParams
import com.milaboratory.cli.Resolver
import com.milaboratory.cli.apply
import com.milaboratory.mitool.helpers.KObjectMapperProvider
import com.milaboratory.mitool.helpers.K_YAML_OM
import com.milaboratory.mixcr.AlignMixins.AlignmentBoundaryConstants
import com.milaboratory.mixcr.AlignMixins.MaterialTypeDNA
import com.milaboratory.mixcr.AlignMixins.MaterialTypeRNA
import com.milaboratory.mixcr.AlignMixins.SetSpecies
import com.milaboratory.mixcr.AlignMixins.SetTagPattern
import com.milaboratory.mixcr.cli.ApplicationException
import com.milaboratory.mixcr.cli.CommandAlign
import com.milaboratory.mixcr.cli.CommandAssemble
import com.milaboratory.mixcr.cli.CommandAssembleContigs
import com.milaboratory.mixcr.cli.CommandAssemblePartial
import com.milaboratory.mixcr.cli.CommandExportAlignments
import com.milaboratory.mixcr.cli.CommandExportClones
import com.milaboratory.mixcr.cli.CommandExtend
import com.milaboratory.mixcr.cli.CommandListPresets
import com.milaboratory.mixcr.cli.CommandRefineTagsAndSort
import com.milaboratory.mixcr.cli.CommonDescriptions.Labels
import com.milaboratory.mixcr.util.CosineSimilarity
import com.milaboratory.primitivio.annotations.Serializable
import org.apache.commons.io.IOUtils
import java.nio.charset.Charset
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.toPath
import kotlin.reflect.KProperty1

@Serializable(asJson = true, objectMapperBy = KObjectMapperProvider::class)
data class MiXCRParamsSpec(
    @JsonProperty("presetAddress") override val presetAddress: String,
    @JsonProperty("mixins") override val mixins: List<MiXCRMixin>,
) : ParamsBundleSpec<MiXCRParamsBundle> {

    fun addMixins(toAdd: List<MiXCRMixin>) = copy(
        mixins = mixins + toAdd
    )

    constructor(presetAddress: String, vararg mixins: MiXCRMixin) : this(presetAddress, listOf(*mixins))
}

@Serializable(asJson = true, objectMapperBy = KObjectMapperProvider::class)
data class MiXCRParamsBundle(
    @JsonProperty("flags") val flags: Set<String>,
    @JsonProperty("pipeline") val pipeline: MiXCRPipeline?,
    @JsonProperty("align") val align: CommandAlign.Params?,
    @JsonProperty("refineTagsAndSort") val refineTagsAndSort: CommandRefineTagsAndSort.Params?,
    @JsonProperty("assemblePartial") val assemblePartial: CommandAssemblePartial.Params?,
    @JsonProperty("extend") val extend: CommandExtend.Params?,
    @JsonProperty("assemble") val assemble: CommandAssemble.Params?,
    @JsonProperty("assembleContigs") val assembleContigs: CommandAssembleContigs.Params?,
    @JsonProperty("exportAlignments") val exportAlignments: CommandExportAlignments.Params?,
    @JsonProperty("exportClones") val exportClones: CommandExportClones.Params?,
    @JsonIgnore val exportPreset: Unit = Unit
)

object Flags {

    const val Species = "species"
    const val MaterialType = "materialType"
    const val LeftAlignmentMode = "leftAlignmentMode"
    const val RightAlignmentMode = "rightAlignmentMode"

    const val TagPattern = "tagPattern"

    val flagMessages = mapOf(
        Species to
                "This preset requires to specify species, \n" +
                "please use the following mix-in: ${SetSpecies.CMD_OPTION} <name>",
        MaterialType to
                "This preset requires to specify material type, \n" +
                "please use one of the following mix-ins: ${MaterialTypeDNA.CMD_OPTION}, ${MaterialTypeRNA.CMD_OPTION}",
        LeftAlignmentMode to
                "This preset requires to specify left side (V gene) alignment boundary mode, \n" +
                "please use one of the following mix-ins: \n" +
                "${AlignmentBoundaryConstants.LEFT_FLOATING_CMD_OPTION} [${Labels.ANCHOR_POINT}]\n" +
                "${AlignmentBoundaryConstants.LEFT_RIGID_CMD_OPTION} [${Labels.ANCHOR_POINT}]",
        RightAlignmentMode to
                "This preset requires to specify left side (V gene) alignment boundary mode, \n" +
                "please use one of the following mix-ins: \n" +
                "${AlignmentBoundaryConstants.RIGHT_FLOATING_CMD_OPTION} (${Labels.GENE_TYPE}|${Labels.ANCHOR_POINT})\n" +
                "${AlignmentBoundaryConstants.RIGHT_RIGID_CMD_OPTION} [(${Labels.GENE_TYPE}|${Labels.ANCHOR_POINT})]",

        TagPattern to
                "This preset requires to specify tag pattern, \n" +
                "please use ${SetTagPattern.CMD_OPTION} mix-in to set it."
    )
}

object Presets {
    private val localPresetSearchPath = listOfNotNull(
        Path(System.getProperty("user.home"), ".mixcr", "presets"),
        Presets::class.java.protectionDomain.codeSource?.location?.toURI()?.toPath(),
        Path(".")
    ).filter {
        try {
            it.exists()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private val presetCollection: Map<String, MiXCRParamsBundleRaw> = buildMap {
        val files = (Presets.javaClass.getResourceAsStream("/mixcr_presets/file_list.txt")
            ?: throw IllegalStateException("No preset file list")).use { stream ->
            IOUtils.readLines(stream, Charset.defaultCharset())
        }
        files.flatMap { file ->
            (Presets.javaClass.getResourceAsStream("/mixcr_presets/$file") ?: throw IllegalStateException("No $file"))
                .use { stream -> K_YAML_OM.readValue<Map<String, MiXCRParamsBundleRaw>>(stream) }
                .toList()
        }.forEach { (k, v) ->
            if (put(k, v) != null)
                throw RuntimeException("Conflicting preset names in different preset files.")
        }
    }

    val allPresetNames = presetCollection.keys

    val nonAbstractPresetNames = presetCollection.filter { !it.value.abstract }.keys

    val visiblePresets = nonAbstractPresetNames.filter { "test" !in it && "legacy" !in it }

    private fun rawResolve(name: String): MiXCRParamsBundleRaw {
        if (name.startsWith("local:")) {
            val lName = name.removePrefix("local:")
            localPresetSearchPath.forEach { folder ->
                listOf(".yaml", ".yml").forEach { ext ->
                    val presetPath = folder.resolve(lName + ext)
                    if (presetPath.exists())
                        return K_YAML_OM.readValue(presetPath.toFile())
                }
            }
            throw ApplicationException("Can't find local preset with name \"$name\"")
        } else {
            val result = presetCollection[name]
            if (result == null) {
                val limits = 3..6
                var candidates: List<String>? = null
                for (i in (1..10).reversed()) {
                    val withThreshold = CosineSimilarity.mostSimilar(name, visiblePresets, i / 10.0)
                    if (withThreshold.size in limits) {
                        candidates = withThreshold
                        break
                    }
                }
                if (candidates == null) {
                    candidates = CosineSimilarity.mostSimilar(name, visiblePresets).take(limits.last)
                }
                throw ApplicationException("No preset with name \"$name\". Did you mean: ${candidates.joinToString(" or ")}?\nTo list all built-in presets run `mixcr ${CommandListPresets.COMMAND_NAME}`.")
            }
            return result
        }
    }

    private fun <T : Any> getResolver(prop: KProperty1<MiXCRParamsBundleRaw, RawParams<T>?>): Resolver<T> =
        object : Resolver<T>() {
            override fun invoke(name: String): T? {
                return rawResolve(name).resolve(name, prop, ::rawResolve, nonNullResolver)
            }

            override fun nullErrorMessage(name: String) = "No value for ${prop.name} in $name"
        }

    val presets get() = presetCollection.keys

    internal val pipeline = getResolver(MiXCRParamsBundleRaw::pipeline)
    internal val align = getResolver(MiXCRParamsBundleRaw::align)
    internal val refineTagsAndSort = getResolver(MiXCRParamsBundleRaw::refineTagsAndSort)
    internal val assemblePartial = getResolver(MiXCRParamsBundleRaw::assemblePartial)
    internal val assemble = getResolver(MiXCRParamsBundleRaw::assemble)
    internal val assembleContigs = getResolver(MiXCRParamsBundleRaw::assembleContigs)
    internal val extend = getResolver(MiXCRParamsBundleRaw::extend)
    internal val exportAlignments = getResolver(MiXCRParamsBundleRaw::exportAlignments)
    internal val exportClones = getResolver(MiXCRParamsBundleRaw::exportClones)

    private class MiXCRParamsBundleRaw(
        @JsonProperty("abstract") val abstract: Boolean = false,
        @JsonProperty("inheritFrom") override val inheritFrom: String? = null,
        @JsonProperty("mixins") val mixins: List<MiXCRMixin>?,
        @JsonProperty("flags") val flags: Set<String>?,
        @JsonProperty("pipeline") val pipeline: RawParams<MiXCRPipeline>?,
        @JsonProperty("align") val align: RawParams<CommandAlign.Params>? = null,
        @JsonProperty("refineTagsAndSort") val refineTagsAndSort: RawParams<CommandRefineTagsAndSort.Params>? = null,
        @JsonProperty("assemblePartial") val assemblePartial: RawParams<CommandAssemblePartial.Params>? = null,
        @JsonProperty("extend") val extend: RawParams<CommandExtend.Params>? = null,
        @JsonProperty("assemble") val assemble: RawParams<CommandAssemble.Params>? = null,
        @JsonProperty("assembleContigs") val assembleContigs: RawParams<CommandAssembleContigs.Params>? = null,
        @JsonProperty("exportAlignments") val exportAlignments: RawParams<CommandExportAlignments.Params>?,
        @JsonProperty("exportClones") val exportClones: RawParams<CommandExportClones.Params>?,
    ) : AbstractPresetBundleRaw<MiXCRParamsBundleRaw> {
        val rawParent by lazy { inheritFrom?.let { rawResolve(it) } }

        // flags and mixins are aggregated and applied on the very step of resolution process

        val resolvedFlags: Set<String> by lazy {
            (flags ?: emptySet()) + (rawParent?.resolvedFlags ?: emptySet())
        }
        val resolvedMixins: List<MiXCRMixin> by lazy {
            (mixins ?: emptyList()) + (rawParent?.resolvedMixins ?: emptyList())
        }
    }

    fun resolveParamsBundle(presetName: String): MiXCRParamsBundle {
        val raw = rawResolve(presetName)
        if (raw.abstract)
            throw ApplicationException("Preset $presetName is abstract and not intended to be used directly.")
        val bundle = MiXCRParamsBundle(
            flags = raw.resolvedFlags,
            pipeline = pipeline(presetName),
            align = align(presetName),
            refineTagsAndSort = refineTagsAndSort(presetName),
            assemblePartial = assemblePartial(presetName),
            extend = extend(presetName),
            assemble = assemble(presetName),
            assembleContigs = assembleContigs(presetName),
            exportAlignments = exportAlignments(presetName),
            exportClones = exportClones(presetName),
        )
        return raw.resolvedMixins.apply(bundle)
    }
}
