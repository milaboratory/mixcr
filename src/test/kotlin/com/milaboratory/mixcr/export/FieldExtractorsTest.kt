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

import com.milaboratory.mixcr.MiXCRStepReports
import com.milaboratory.mixcr.basictypes.tag.TagsInfo
import com.milaboratory.mixcr.partialassembler.PartialAlignmentsAssemblerAligner
import com.milaboratory.mixcr.tests.MiXCRTestUtils
import com.milaboratory.mixcr.tests.TargetBuilder
import com.milaboratory.mixcr.vdjaligners.VDJCParametersPresets
import io.repseq.core.GeneType
import io.repseq.core.ReferencePoint
import io.repseq.core.VDJCLibraryRegistry
import org.apache.commons.math3.random.Well44497b
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test
import java.util.*
import kotlin.math.min

class FieldExtractorsTest {
    @Test
    fun testAnchorPoints1() {
        val print = false
        val rg = Well44497b(12312)
        val rnaSeqParams = VDJCParametersPresets.getByName("rna-seq")
        val aligner = PartialAlignmentsAssemblerAligner(rnaSeqParams)
        val lib = VDJCLibraryRegistry.getDefault().getLibrary("default", "hs")
        for (gene in VDJCLibraryRegistry.getDefault()
            .getLibrary("default", "hs").allGenes) if (gene.isFunctional) aligner.addGene(gene)
        val genes = TargetBuilder.VDJCGenes(
            lib,
            "TRBV12-3*00", "TRBD1*00", "TRBJ1-3*00", "TRBC2*00"
        )

        //                                 | 310  | 338   | 438
        // 250V + 60CDR3 (20V 7N 10D 3N 20J) + 28J + 100C + 100N
        // "{CDR3Begin(-250)}V*270 NNNNNNN {DBegin(0)}D*10 NNN {CDR3End(-20):FR4End} {CBegin}C*100 N*100"
        val extractor = VDJCAlignmentsFieldsExtractorsFactory.fields
            .filter { it.cmdArgName == "-defaultAnchorPoints" }
            .flatMap {
                it.createFields(MetaForExport(emptyList(), null, MiXCRStepReports()), emptyArray())
            }
            .first()
        val goAssert: F6 = object : F6 {
            override fun go(
                seq: String?,
                len: Int,
                offset1: Int,
                offset2: Int,
                offset3: Int,
                expected: String?
            ): Array<Array<Int?>> {
                val baseSeq = TargetBuilder.generateSequence(genes, seq, rg)
                val seq1 = baseSeq.getRange(offset1, min(baseSeq.size(), offset1 + len))
                val seq2 = when (offset2) {
                    -1 -> null
                    else -> baseSeq.getRange(offset2, min(baseSeq.size(), offset2 + len))
                }
                val seq3 = when (offset3) {
                    -1 -> null
                    else -> baseSeq.getRange(offset3, min(baseSeq.size(), offset3 + len))
                }
                val read = when {
                    offset3 != -1 -> MiXCRTestUtils.createMultiRead(seq1, seq2, seq3)
                    offset2 == -1 -> MiXCRTestUtils.createMultiRead(seq1)
                    else -> MiXCRTestUtils.createMultiRead(seq1, seq2)
                }
                val al = aligner.process(read.toTuple(), read)
                Assert.assertNotNull(al)
                if (print) {
                    MiXCRTestUtils.printAlignment(al)
                    println()
                    println("-------------------------------------------")
                    println()
                }
                val `val` = extractor.extractValue(
                    RowMetaForExport(
                        TagsInfo.NO_TAGS,
                        MetaForExport(emptyList(), null, MiXCRStepReports()),
                        false
                    ), al
                )
                if (print) println(`val`)
                val spl = `val`.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val result = Array(spl.size) { arrayOfNulls<Int>(ReferencePoint.DefaultReferencePoints.size) }
                for (i in spl.indices) {
                    val spl1 = spl[i].split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    for (j in spl1.indices) {
                        try {
                            result[i][j] = Integer.decode(spl1[j])
                        } catch (ignored: NumberFormatException) {
                        }
                    }
                }
                return result
            }
        }

        // No PSegments, just deletions
        var r = goAssert.go(
            "{CDR3Begin(-250):VEnd(-3)} 'CCAAA' {DBegin(0):DEnd(0)} 'AAA' {JBegin(2):FR4End} " +
                    "{CBegin}C*100 N*100",
            100, 230, 307, 450, ""
        )
        assertExportPoint(r[0], ReferencePoint.VEnd, -3)
        assertExportPoint(r[0], ReferencePoint.DBegin, 0)
        assertExportPoint(r[0], ReferencePoint.DEnd, 0)
        assertExportPoint(r[0], ReferencePoint.JBegin, -2)
        r = goAssert.go(
            "{CDR3Begin(-250):VEnd(0)} 'CCAAA' {DBegin(0):DEnd(-2)} 'AAA' {JBegin:FR4End} {CBegin}C*100 N*100",
            100, 240, 307, 450, ""
        )
        assertExportPoint(r[0], ReferencePoint.VEnd, 0)
        assertExportPoint(r[0], ReferencePoint.DBegin, 0)
        assertExportPoint(r[0], ReferencePoint.DEnd, -2)
        assertExportPoint(r[0], ReferencePoint.JBegin, 0)

        // With PSegments
        r = goAssert.go(
            "{CDR3Begin(-250):VEnd(0)} {VEnd:VEnd(-3)} 'CCAAA' {DBegin(3):DBegin} {DBegin:DEnd(-2)} 'AAA' " +
                    "{JBegin(2):JBegin} {JBegin:FR4End} {CBegin}C*100 N*100",
            100, 240, 307, 450, ""
        )
        assertExportPoint(r[0], ReferencePoint.VEnd, 3)
        assertExportPoint(r[0], ReferencePoint.DBegin, 3)
        assertExportPoint(r[0], ReferencePoint.DEnd, -2)
        assertExportPoint(r[0], ReferencePoint.JBegin, 2)
    }

    interface F6 {
        fun go(seq: String?, len: Int, offset1: Int, offset2: Int, offset3: Int, expected: String?): Array<Array<Int?>>
    }

    @Ignore
    @Test
    fun bestHits() {
        for (type in GeneType.values()) {
            val u = type.name.substring(0, 1).uppercase(Locale.getDefault())
            val l = u.lowercase(Locale.getDefault())
            println(
                """@ExtractorInfo(type = VDJCObject.class,
            command = "-${l}Hit",
            header = "Best $type hit",
            description = "Export best $type hit")"""
            )
            println("public static final FieldExtractorFactory<VDJCObject> EXTRACT_BEST_" + u + "_HIT = extractBestHit(GeneType." + type + ");")
            println()
        }
    }

    @Ignore
    @Test
    fun hits() {
        for (type in GeneType.values()) {
            val u = type.name.substring(0, 1).uppercase(Locale.getDefault())
            val l = u.lowercase(Locale.getDefault())
            println(
                """@ExtractorInfo(type = VDJCObject.class,
            command = "-${l}Hits",
            header = "$type hits",
            description = "Export $type hits")"""
            )
            println("public static final FieldExtractorFactory<VDJCObject> EXTRACT_" + u + "_HITS = extractHits(GeneType." + type + ");")
            println()
        }
    }

    @Ignore
    @Test
    fun bestAlignments() {
        for (type in GeneType.values()) {
            val u = type.name.substring(0, 1).uppercase(Locale.getDefault())
            val l = u.lowercase(Locale.getDefault())
            println(
                """@ExtractorInfo(type = VDJCObject.class,
            command = "-${l}Alignment",
            header = "Best $type alignment",
            description = "Export best $type alignment")"""
            )
            println("public static final FieldExtractorFactory<VDJCObject> EXTRACT_BEST_" + u + "_ALIGNMENT = extractBestAlignments(GeneType." + type + ");")
            println()
        }
    }

    @Ignore
    @Test
    fun alignments() {
        for (type in GeneType.values()) {
            val u = type.name.substring(0, 1).uppercase(Locale.getDefault())
            val l = u.lowercase(Locale.getDefault())
            println(
                """@ExtractorInfo(type = VDJCObject.class,
            command = "-${l}Alignments",
            header = "$type alignments",
            description = "Export $type alignments")"""
            )
            println("public static final FieldExtractorFactory<VDJCObject> EXTRACT_" + u + "_ALIGNMENTS = extractAlignments(GeneType." + type + ");")
            println()
        }
    }

    companion object {
        fun assertExportPoint(r: Array<Int?>, rp: ReferencePoint, value: Int?) {
            for (i in ReferencePoint.DefaultReferencePoints.indices) if (ReferencePoint.DefaultReferencePoints[i] == rp) {
                Assert.assertEquals(value, r[i])
                return
            }
            Assert.fail()
        }
    }
}
