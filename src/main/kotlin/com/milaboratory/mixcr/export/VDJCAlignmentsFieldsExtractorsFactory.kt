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
package com.milaboratory.mixcr.export

import com.milaboratory.mixcr.basictypes.VDJCAlignments
import com.milaboratory.util.GlobalObjectMappers

object VDJCAlignmentsFieldsExtractorsFactory : FieldExtractorsFactory<VDJCAlignments>() {
    override fun allAvailableFields(): List<FieldsCollection<VDJCAlignments>> =
        VDJCObjectFieldExtractors.vdjcObjectFields(forTreesExport = false) + vdjcAlignmentsFields()

    private fun vdjcAlignmentsFields(): List<FieldsCollection<VDJCAlignments>> = buildList {
        this += Field(
            Order.readIds + 100,
            "-readId",
            "Export id of read corresponding to alignment (deprecated)",
            "readId",
            deprecation = "-readId is deprecated. Use -readIds"
        ) { obj: VDJCAlignments ->
            obj.minReadId.toString()
        }
        this += Field(
            Order.readIds + 200,
            "-readIds",
            "Export id(s) of read(s) corresponding to alignment",
            "readId"
        ) { `object`: VDJCAlignments ->
            val readIds = `object`.readIds
            val sb = StringBuilder()
            var i = 0
            while (true) {
                sb.append(readIds[i])
                if (i == readIds.size - 1) break
                sb.append(",")
                i++
            }
            sb.toString()
        }
        this += Field(
            Order.readDescriptions + 100,
            "-descrR1",
            "Export description line from initial .fasta or .fastq file (deprecated)",
            "descrR1",
            deprecation = "-descrR1 is deprecated. Use -descrsR1"
        ) { vdjcAlignments: VDJCAlignments ->
            val reads = vdjcAlignments.originalReads
                ?: throw IllegalArgumentException(
                    """
                    Error for option '-descrR1':
                    No description available for read: either re-run align action with -OsaveOriginalReads=true option or don't use '-descrR1' in exportAlignments
                    """.trimIndent()
                )
            reads[0].getRead(0).description
        }
        this += Field(
            Order.readDescriptions + 200,
            "-descrR2",
            "Export description line from initial .fasta or .fastq file (deprecated)",
            "descrR2",
            deprecation = "-descrR2 is deprecated. Use -descrsR2"
        ) { vdjcAlignments: VDJCAlignments ->
            val reads = vdjcAlignments.originalReads
                ?: throw IllegalArgumentException(
                    """
                    Error for option '-descrR1':
                    No description available for read: either re-run align action with -OsaveOriginalReads=true option or don't use '-descrR1' in exportAlignments
                    """.trimIndent()
                )
            val read = reads[0]
            require(read.numberOfReads() >= 2) {
                """
                Error for option '-descrR2':
                No description available for second read: your input data was single-end
                """.trimIndent()
            }
            read.getRead(1).description
        }
        this += Field(
            Order.readDescriptions + 300,
            "-descrsR1",
            "Export description lines from initial .fasta or .fastq file " +
                    "for R1 reads (only available if -OsaveOriginalReads=true was used in align command)",
            "descrsR1"
        ) { vdjcAlignments: VDJCAlignments ->
            val reads = vdjcAlignments.originalReads
                ?: throw IllegalArgumentException(
                    """
                    Error for option '-descrR1':
                    No description available for read: either re-run align action with -OsaveOriginalReads option or don't use '-descrR1' in exportAlignments
                    """.trimIndent()
                )
            val sb = StringBuilder()
            var i = 0
            while (true) {
                sb.append(reads[i].getRead(0).description)
                if (i == reads.size - 1) break
                sb.append(",")
                i++
            }
            sb.toString()
        }
        this += Field(
            Order.readDescriptions + 400,
            "-descrsR2",
            "Export description lines from initial .fastq file " +
                    "for R2 reads (only available if -OsaveOriginalReads=true was used in align command)",
            "descrsR2"
        ) { vdjcAlignments: VDJCAlignments ->
            val reads = vdjcAlignments.originalReads
                ?: throw IllegalArgumentException(
                    """
                    Error for option '-descrR1':
                    No description available for read: either re-run align action with -OsaveOriginalReads option or don't use '-descrR1' in exportAlignments
                    """.trimIndent()
                )
            val sb = StringBuilder()
            var i = 0
            while (true) {
                val read = reads[i]
                require(read.numberOfReads() >= 2) {
                    """
                    Error for option '-descrsR2':
                    No description available for second read: your input data was single-end
                    """.trimIndent()
                }
                sb.append(read.getRead(1).description)
                if (i == reads.size - 1) break
                sb.append(",")
                i++
            }
            sb.toString()
        }
        this += Field(
            Order.readDescriptions + 500,
            "-readHistory",
            "Export read history",
            "readHistory"
        ) { vdjcAlignments: VDJCAlignments ->
            GlobalObjectMappers.toOneLine(vdjcAlignments.history)
        }
        this += Field(
            Order.alignmentCloneIds + 100,
            "-cloneId",
            "To which clone alignment was attached (make sure using .clna file as input for exportAlignments)",
            "cloneId"
        ) { vdjcAlignments: VDJCAlignments ->
            vdjcAlignments.cloneIndex.toString()
        }
        this += Field(
            Order.alignmentCloneIds + 200,
            "-cloneIdWithMappingType",
            "To which clone alignment was attached with additional info on mapping type (make sure using .clna file as input for exportAlignments)",
            "cloneMapping"
        ) { vdjcAlignments: VDJCAlignments ->
            "${vdjcAlignments.cloneIndex}:${vdjcAlignments.mappingType}"
        }
    }

}
