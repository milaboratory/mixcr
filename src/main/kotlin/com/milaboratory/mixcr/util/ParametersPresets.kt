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
package com.milaboratory.mixcr.util

import com.fasterxml.jackson.core.type.TypeReference
import com.milaboratory.util.GlobalObjectMappers

class ParametersPresets<T : Any>(
    private val fileName: String,
    private val typeRef: TypeReference<Map<String, T>>
) {
    private val knownParameters: Map<String, T> by lazy {
        val `is` = ParametersPresets::class.java.classLoader.getResourceAsStream("parameters/$fileName.json")
        GlobalObjectMappers.getOneLine().readValue(`is`, typeRef)
    }

    val availableParameterNames: Set<String>
        get() = knownParameters.keys

    fun getByName(name: String): T? = knownParameters[name]

    companion object {
        inline operator fun <reified T : Any> invoke(fileName: String) = ParametersPresets(
            fileName,
            object : TypeReference<Map<String, T>>() {}
        )
    }
}
