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
@file:Suppress("EnumEntryName", "ClassName")

package com.milaboratory.mixcr.cli.analyze

enum class StartingMaterial {
    /** RNA */
    rna,

    /** Genomic DNA */
    dna
}

enum class `5End` {
    /** No V gene primers (e.g. 5’RACE with template switch oligo or a like) */
    `no-v-primers`,

    /** V gene single primer / multiplex */
    `v-primers`
}

enum class `3End` {
    /** J gene single primer / multiplex */
    `j-primers`,

    /** J-C intron single primer / multiplex */
    `j-c-intron-primers`,

    /** C gene single primer / multiplex (e.g. IGHC primers specific to different immunoglobulin isotypes) */
    `c-primers`
}

enum class Adapters {
    /** May be present */
    `adapters-present`,

    /** Absent / nearly absent / trimmed */
    `no-adapters`
}