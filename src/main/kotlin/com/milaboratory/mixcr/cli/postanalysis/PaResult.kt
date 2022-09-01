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

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.milaboratory.util.GlobalObjectMappers
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * PA results (written to disk)
 */
@JsonAutoDetect
class PaResult @JsonCreator constructor(
    /** Metadata. Null if was not specified  */
    @param:JsonProperty("metadata") val metadata: Map<String, List<Any>>?,
    /** Metadata categories used to isolate samples into groups  */
    @param:JsonProperty("isolationGroups") val isolationGroups: List<String>,
    /** Results for groups  */
    @param:JsonProperty("results") val results: List<PaResultByGroup>
) {
    fun writeJson(path: Path) {
        when {
            path.fileName.toString().endsWith(".json") ->
                GlobalObjectMappers.getPretty().writeValue(path.toFile(), this)
            path.fileName.toString().endsWith(".json.gz") ->
                FileOutputStream(path.toFile()).use { fs ->
                    GZIPOutputStream(BufferedOutputStream(fs)).use { zs ->
                        GlobalObjectMappers.getOneLine().writeValue(zs, this)
                    }
                }
            else -> throw IllegalArgumentException("path should ends with .json.gz or .json but was $path")
        }
    }

    companion object {
        fun readJson(path: Path): PaResult = when {
            path.fileName.toString().endsWith(".json") ->
                GlobalObjectMappers.getPretty().readValue(path.toFile())
            path.fileName.toString().endsWith(".json.gz") ->
                FileInputStream(path.toFile()).use { fs ->
                    GZIPInputStream(BufferedInputStream(fs)).use { zs ->
                        GlobalObjectMappers.getOneLine().readValue(zs)
                    }
                }
            else -> throw IllegalArgumentException("path should ends with .json.gz or .json")
        }
    }
}
