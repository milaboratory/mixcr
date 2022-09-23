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
import com.milaboratory.cli.*
import com.milaboratory.mitool.helpers.KObjectMapperProvider
import com.milaboratory.mitool.helpers.K_YAML_OM
import com.milaboratory.mixcr.cli.*
import com.milaboratory.primitivio.annotations.Serializable
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.reflect.KProperty1

@Serializable(asJson = true, objectMapperBy = KObjectMapperProvider::class)
data class MiXCRParamsSpec(
    @JsonProperty("presetAddress") override val presetAddress: String,
    @JsonProperty("mixins") override val mixins: List<MiXCRMixin>,
) : ParamsBundleSpec<MiXCRParamsBundle> {
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
    // const val Species = "species"

    const val MaterialType = "materialType"
    const val LeftAlignmentMode = "leftSideAmplificationPrimer"
    const val RightAlignmentMode = "rightSideAmplificationPrimer"

    const val TagPattern = "tagPattern"

    val flagMessages = mapOf(
        MaterialType to
                "This preset requires to specify material type, \n" +
                "please use one of the following mix-ins: +dna, +rna",
        LeftAlignmentMode to
                "This preset requires to specify left side (V gene) alignment boundary mode, \n" +
                "please use one of the following mix-ins: \n" +
                "+floatingLeftAlignmentBoundary [optional_anchor_point]\n" +
                "+rigidLeftAlignmentBoundary [optional_anchor_point]",
        RightAlignmentMode to
                "This preset requires to specify left side (V gene) alignment boundary mode, \n" +
                "please use one of the following mix-ins: \n" +
                "+floatingRightAlignmentBoundary [optional_anchor_point]\n" +
                "+rigidRightAlignmentBoundary [optional_anchor_point]",

        TagPattern to
                "This preset requires to specify tag pattern, \n" +
                "please use +tagPattern mix-in to set it."
    )
}

object Presets {
    private val localPresetSearchPath = listOf(
        Path(System.getProperty("user.home"), ".mixcr", "presets"),
        Path(".")
    ).filter {
        try {
            it.exists()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private val files = listOf(
        "pipeline.yaml",
        "align.yaml",
        "assemble.yaml",
        "assembleContigs.yaml",
        "assemblePartial.yaml",
        "extend.yaml",
        "bundles.yaml",
        "refineTagsAndSort.yaml",
        "export.yaml",
        "test.yaml"
    )
    private val presetCollection: Map<String, MiXCRParamsBundleRaw> = run {
        val map = mutableMapOf<String, MiXCRParamsBundleRaw>()
        files.flatMap { file ->
            Presets.javaClass.getResourceAsStream("/mixcr_presets/$file")!!
                .use { stream -> K_YAML_OM.readValue<Map<String, MiXCRParamsBundleRaw>>(stream) }
                .toList()
        }.forEach { (k, v) ->
            if (map.put(k, v) != null)
                throw RuntimeException("Conflicting preset names in different preset files.")
        }
        map
    }

    val allPresetNames = presetCollection.keys

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
            throw IllegalArgumentException("Can't find local preset with name \"$name\"")
        } else
            return presetCollection[name] ?: throw IllegalArgumentException("No preset with name \"$name\"")
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
    ) : AbstractPresetBundleRaw<MiXCRParamsBundleRaw>

    fun resolveParamsBundle(presetName: String): MiXCRParamsBundle {
        val raw = rawResolve(presetName)
        val bundle = MiXCRParamsBundle(
            flags = raw!!.flags ?: emptySet(),
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
        val mixins = raw.mixins ?: emptyList()
        return mixins.apply(bundle)
    }
}