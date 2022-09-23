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
package com.milaboratory.mixcr.cli.postanalysis

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.milaboratory.mixcr.cli.ChainsUtil.name
import io.repseq.core.Chains

/**
 * Group of samples with specific metadata properties projected onto specific chains. Common downsampling is applied for
 * all samples in the group.
 */
data class IsolationGroup private constructor(
    /** Chains  */
    @field:JsonProperty("chains")
    val chains: Chains,
    /** Metadata field=value; always sorted by key  */
    @field:JsonProperty("groups")
    val group: Map<String, Any>?
) {
    fun toString(withChains: Boolean): String {
        val str = group?.entries?.joinToString(",") { (key, value) -> "$key=$value" } ?: ""
        return when {
            withChains -> "chains=${chains.name}${if (str.isEmpty()) "" else ",$str"}"
            else -> str
        }
    }


    override fun toString(): String = toString(true)

    /**
     * Generate file extension for specific group
     */
    fun extension(): String {
        val sb = StringBuilder()
        sb.append(".").append(chains.name)
        group?.values
            ?.filterNot { it.toString().equals(chains.name, ignoreCase = true) }
            ?.forEach { sb.append(".").append(it) }
        return sb.toString()
    }

    companion object {
        private fun sortByKey(group: Map<String, Any>): Map<String, Any> = group.entries
            .sortedBy { it.key }
            .associateTo(LinkedHashMap()) { it.key to it.value }

        @JsonCreator
        @JvmStatic
        operator fun invoke(
            @JsonProperty("chains") chains: Chains,
            @JsonProperty("groups") group: Map<String, Any>?
        ) = IsolationGroup(
            chains,
            if (group == null) null else sortByKey(group)
        )
    }
}
