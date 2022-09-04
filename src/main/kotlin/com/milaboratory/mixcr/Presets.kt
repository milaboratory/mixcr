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
import com.milaboratory.mixcr.cli.RefineTagsAndSort
import kotlin.reflect.KProperty1

object Presets {
    private val presetCollection: Map<String, MiXCRPresetSet> =
        object {}.javaClass.getResourceAsStream("/mixcr_presets.yaml")!!
            .use { stream -> K_YAML_OM.readValue(stream) }

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

    private class MiXCRPresetSet(
        @JsonProperty("inheritFrom") override val inheritFrom: String? = null,
        @JsonProperty("align") val align: Preset<CommandAlign.Params>? = null,
        @JsonProperty("refineTagsAndSort") val refineTagsAndSort: Preset<RefineTagsAndSort.Params>? = null,
    ) : AbstractPresetSet<MiXCRPresetSet>
}