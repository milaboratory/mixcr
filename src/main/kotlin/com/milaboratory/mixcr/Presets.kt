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

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.milaboratory.cli.AbstractPresetBundleRaw
import com.milaboratory.cli.RawParams
import com.milaboratory.cli.Resolver
import com.milaboratory.mitool.helpers.KObjectMapperProvider
import com.milaboratory.mitool.helpers.K_YAML_OM
import com.milaboratory.mixcr.cli.*
import com.milaboratory.primitivio.annotations.Serializable
import kotlin.reflect.KProperty1

@Serializable(asJson = true, objectMapperBy = KObjectMapperProvider::class)
data class MiXCRParamsBundle(
    @JsonProperty("flags") val flags: Set<String>,
    @JsonProperty("align") val align: CommandAlign.Params?,
    @JsonProperty("refineTagsAndSort") val refineTagsAndSort: CommandRefineTagsAndSort.Params?,
    @JsonProperty("assemblePartial") val assemblePartial: CommandAssemblePartial.Params?,
    @JsonProperty("extend") val extend: CommandExtend.Params?,
    @JsonProperty("assemble") val assemble: CommandAssemble.Params?,
    @JsonProperty("assembleContigs") val assembleContigs: CommandAssembleContigs.Params?
)

object Flags {
    const val MaterialType = "materialType"
    const val LeftAlignmentMode = "leftSideAmplificationPrimer"
    const val RightAlignmentMode = "rightSideAmplificationPrimer"

    val flagMessages = mapOf(
        MaterialType to
                "This preset requires to specify material type, \n" +
                "please use one of the following mixins: +dna, +rna",
        LeftAlignmentMode to
                "This preset requires to specify left side (V gene) alignment boundary mode, \n" +
                "please use one of the following mixins: \n" +
                "+floatingLeftAlignmentBoundary [optional_anchor_point]\n" +
                "+rigidLeftAlignmentBoundary [optional_anchor_point]",
        RightAlignmentMode to
                "This preset requires to specify left side (V gene) alignment boundary mode, \n" +
                "please use one of the following mixins: \n" +
                "+floatingRightAlignmentBoundary [optional_anchor_point]\n" +
                "+rigidRightAlignmentBoundary [optional_anchor_point]",
    )
}

object Presets {
    private val files = listOf(
        "align.yaml",
        "assemble.yaml",
        "assembleContigs.yaml",
        "assemblePartial.yaml",
        "extend.yaml",
        "pipelines.yaml",
        "refineTagsAndSort.yaml",
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

    private val globalResolver = { name: String ->
        presetCollection[name] ?: throw IllegalArgumentException("No preset with name \"$name\"")
    }

    private fun <T : Any> getResolver(prop: KProperty1<MiXCRParamsBundleRaw, RawParams<T>?>): Resolver<T> =
        object : Resolver<T>() {
            override fun invoke(name: String): T? {
                return globalResolver(name).resolve(name, prop, globalResolver, nonNullResolver)
            }

            override fun nullErrorMessage(name: String) = "No value for ${prop.name} in $name"
        }

    val presets get() = presetCollection.keys

    private val align = getResolver(MiXCRParamsBundleRaw::align)
    private val refineTagsAndSort = getResolver(MiXCRParamsBundleRaw::refineTagsAndSort)
    private val assemblePartial = getResolver(MiXCRParamsBundleRaw::assemblePartial)
    private val assemble = getResolver(MiXCRParamsBundleRaw::assemble)
    private val assembleContigs = getResolver(MiXCRParamsBundleRaw::assembleContigs)
    private val extend = getResolver(MiXCRParamsBundleRaw::extend)

    private class MiXCRParamsBundleRaw(
        @JsonProperty("inheritFrom") override val inheritFrom: String? = null,
        @JsonProperty("flags") val flags: Set<String>?,
        @JsonProperty("align") val align: RawParams<CommandAlign.Params>? = null,
        @JsonProperty("refineTagsAndSort") val refineTagsAndSort: RawParams<CommandRefineTagsAndSort.Params>? = null,
        @JsonProperty("assemblePartial") val assemblePartial: RawParams<CommandAssemblePartial.Params>? = null,
        @JsonProperty("extend") val extend: RawParams<CommandExtend.Params>? = null,
        @JsonProperty("assemble") val assemble: RawParams<CommandAssemble.Params>? = null,
        @JsonProperty("assembleContigs") val assembleContigs: RawParams<CommandAssembleContigs.Params>? = null,
    ) : AbstractPresetBundleRaw<MiXCRParamsBundleRaw>

    fun resolveParamsBundle(presetName: String) = MiXCRParamsBundle(
        flags = presetCollection[presetName]!!.flags ?: emptySet(),
        align = align(presetName),
        refineTagsAndSort = refineTagsAndSort(presetName),
        assemblePartial = assemblePartial(presetName),
        extend = extend(presetName),
        assemble = assemble(presetName),
        assembleContigs = assembleContigs(presetName)
    )
}