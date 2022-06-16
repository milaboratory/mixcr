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
package com.milaboratory.mixcr.trees

import com.fasterxml.jackson.core.type.TypeReference
import com.milaboratory.util.GlobalObjectMappers

object SHMTreeBuilderParametersPresets {
    private var knownParameters: Map<String, SHMTreeBuilderParameters>? = null
    private fun ensureInitialized() {
        if (knownParameters == null) synchronized(SHMTreeBuilderParametersPresets::class.java) {
            if (knownParameters == null) {
                val `is` =
                    SHMTreeBuilderParameters::class.java.classLoader.getResourceAsStream("parameters/shm_tree_parameters.json")
                val typeRef: TypeReference<HashMap<String, SHMTreeBuilderParameters>> =
                    object : TypeReference<HashMap<String, SHMTreeBuilderParameters>>() {}
                knownParameters = GlobalObjectMappers.getOneLine().readValue(`is`, typeRef)
            }
        }
    }

    val availableParameterNames: Set<String>
        get() {
            ensureInitialized()
            return knownParameters!!.keys
        }

    fun getByName(name: String): SHMTreeBuilderParameters? {
        ensureInitialized()
        val params = knownParameters!![name] ?: return null
        return params.copy()
    }
}
