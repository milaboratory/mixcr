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
import com.milaboratory.cli.AbstractPresetSet
import com.milaboratory.cli.Preset
import com.milaboratory.cli.PresetResolver
import com.milaboratory.mitool.helpers.K_YAML_OM
import com.milaboratory.mixcr.cli.CommandAlign
import com.milaboratory.mixcr.cli.CommandAssemble
import com.milaboratory.mixcr.cli.CommandAssemblePartial
import com.milaboratory.mixcr.cli.CommandRefineTagsAndSort
import kotlin.reflect.KProperty1

object Presets {
    private val files = listOf(
        "align.yaml",
        "assemblePartial.yaml",
        "assemble.yaml",
        "pipelines.yaml",
        "refineTagsAndSort.yaml"
    )
    private val presetCollection: Map<String, MiXCRPresetSet> = run {
        val map = mutableMapOf<String, MiXCRPresetSet>()
        files.flatMap { file ->
            Presets.javaClass.getResourceAsStream("/mixcr_presets/$file")!!
                .use { stream -> K_YAML_OM.readValue<Map<String, MiXCRPresetSet>>(stream) }
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

    private fun <T : Any> getResolver(prop: KProperty1<MiXCRPresetSet, Preset<T>?>): PresetResolver<T> =
        object : PresetResolver<T>() {
            override fun invoke(name: String): T {
                return globalResolver(name).resolve(name, prop, globalResolver, this)
            }
        }

    val presets get() = presetCollection.keys

    val align = getResolver(MiXCRPresetSet::align)
    val refineTagsAndSort = getResolver(MiXCRPresetSet::refineTagsAndSort)
    val assemblePartial = getResolver(MiXCRPresetSet::assemblePartial)
    val assemble = getResolver(MiXCRPresetSet::assemble)

    private class MiXCRPresetSet(
        @JsonProperty("inheritFrom") override val inheritFrom: String? = null,
        @JsonProperty("align") val align: Preset<CommandAlign.Params>? = null,
        @JsonProperty("refineTagsAndSort") val refineTagsAndSort: Preset<CommandRefineTagsAndSort.Params>? = null,
        @JsonProperty("assemblePartial") val assemblePartial: Preset<CommandAssemblePartial.Params>? = null,
        @JsonProperty("assemble") val assemble: Preset<CommandAssemble.Params>? = null,
    ) : AbstractPresetSet<MiXCRPresetSet>
}