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
package com.milaboratory.mixcr.cli

import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name

enum class InputFileType {
    FASTQ {
        private val regex = Regex("\\.f(?:ast)?q(?:\\.gz)?$", RegexOption.IGNORE_CASE)
        override fun matches(path: Path) = path.name.contains(regex)
    },
    FASTA {
        private val regex = Regex("\\.f(?:ast)?a$", RegexOption.IGNORE_CASE)
        override fun matches(path: Path): Boolean = path.name.contains(regex)
    },
    BAM {
        private val regex = Regex("\\.[bs]am$", RegexOption.IGNORE_CASE)
        override fun matches(path: Path): Boolean = path.name.contains(regex)
    },
    VDJCA {
        override fun matches(path: Path) = path.extension == "vdjca"
    },
    CLNS {
        override fun matches(path: Path) = path.extension == "clns"
    },
    CLNA {
        override fun matches(path: Path) = path.extension == "clna"
    },
    CLNX {
        override fun matches(path: Path) = CLNS.matches(path) || CLNA.matches(path)
    },
    SHMT {
        override fun matches(path: Path) = path.extension == "shmt"
    },
    CSV {
        override fun matches(path: Path) = path.extension == "csv"
    },
    TSV {
        override fun matches(path: Path) = path.extension == "tsv"
    },
    XSV {
        override fun matches(path: Path) = TSV.matches(path) || CSV.matches(path)
    },
    JSON {
        override fun matches(path: Path) = path.extension == "json"
    },
    JSON_GZ {
        override fun matches(path: Path) = path.name.endsWith("json.gz")
    },
    PDF {
        override fun matches(path: Path) = path.extension == "pdf"
    },
    YAML {
        override fun matches(path: Path) = path.extension in arrayOf("yaml", "yml")
    },
    EPS {
        override fun matches(path: Path) = path.extension == "eps"
    },
    PNG {
        override fun matches(path: Path) = path.extension == "png"
    },
    SVG {
        override fun matches(path: Path) = path.extension == "svg"
    },
    JPEG {
        override fun matches(path: Path) = path.extension in arrayOf("jpg", "jpeg")
    }, ;

    abstract fun matches(path: Path): Boolean

    companion object {
        val exportTypes = arrayOf(PDF, SVG, EPS, PNG, JPEG)
        const val exportTypesLabel = "(pdf|eps|svg|png|jpeg)"
    }
}

fun Path.matches(inputFileType: InputFileType) = inputFileType.matches(this)
