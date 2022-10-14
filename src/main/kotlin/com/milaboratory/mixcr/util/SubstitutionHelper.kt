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

import com.milaboratory.mitool.helpers.repeatCollect

object SubstitutionHelper {
    private data class SubstitutionValue(val index: Int, val value: String)

    class SubstitutionValues {
        private var counter = 0
        private val values = mutableMapOf<String, SubstitutionValue>()

        fun add(value: String, vararg keys: String): SubstitutionValues {
            val idx = counter++
            val v = SubstitutionValue(idx, value)
            for (key in keys) {
                require(!values.containsKey(key)) { "key $key already associated with the value" }
                values[key] = v
            }
            return this
        }

        fun getValues(keys: List<String>) =
            keys
                .map { key -> values[key] ?: throw IllegalArgumentException("No value for key: $key") }
                .also { values ->
                    if (values.map { it.index }.toSet().size != counter)
                        throw IllegalArgumentException("Some substitutions were not used.")
                }
                .map { it.value }
    }

    class SubstitutionReadyString(
        private val elements: List<String>,
        private val groupNames: List<String>
    ) {
        init {
            require(elements.size - 1 == groupNames.size)
        }

        fun render(values: SubstitutionValues) =
            values.getValues(groupNames)
                .mapIndexed { i, groupValue -> elements[i] + groupValue }
                .joinToString("") +
                    elements.last()
    }

    private val substitutionGroupPattern = Regex("\\{\\{([^}]+)}}")

    fun parseFileName(
        fileName: String,
        fallbackNumberOfGroups: Int,
        fallbackSeparator: String = "_"
    ): SubstitutionReadyString =
        if (fileName.contains(substitutionGroupPattern)) { // file name contains substitution points
            var previousPosition = 0
            val elements = mutableListOf<String>()
            val groupNames = mutableListOf<String>()
            substitutionGroupPattern.findAll(fileName).forEach { m ->
                elements.add(fileName.substring(previousPosition, m.range.first))
                groupNames.add(m.groups[1]!!.value)
                previousPosition = m.range.last + 1
            }
            elements.add(fileName.substring(previousPosition))
            SubstitutionReadyString(elements, groupNames)
        } else {
            if (fallbackNumberOfGroups == 0)
                SubstitutionReadyString(listOf(fileName), emptyList())
            else {
                val extensionBegin = fileName.lastIndexOf('.')
                val fallbackGroupNames = fallbackNumberOfGroups.repeatCollect { "${it + 1}" }
                if (extensionBegin == -1)
                    SubstitutionReadyString(
                        listOf("${fileName}$fallbackSeparator")
                                + (fallbackNumberOfGroups - 1).repeatCollect { fallbackSeparator }
                                + listOf(""),
                        fallbackGroupNames
                    )
                else
                    SubstitutionReadyString(
                        listOf("${fileName.substring(0, extensionBegin)}$fallbackSeparator")
                                + (fallbackNumberOfGroups - 1).repeatCollect { fallbackSeparator }
                                + listOf(fileName.substring(extensionBegin)),
                        fallbackGroupNames
                    )

            }
        }

}