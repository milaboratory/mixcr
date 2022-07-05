/*
 *
 * Copyright (c) 2022, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/miplots/blob/main/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
package com.milaboratory.mixcr.qc

import com.milaboratory.miplots.writePDF
import com.milaboratory.mixcr.vdjaligners.VDJCAlignmentFailCause
import org.junit.Assert
import org.junit.Test
import java.nio.file.Paths
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

class AlignmentQCTest {
    @Test
    fun testAllFailReasonsAccounted() {
        val all = setOf(
            VDJCAlignmentFailCause.NoHits,
            VDJCAlignmentFailCause.NoCDR3Parts,
            VDJCAlignmentFailCause.NoVHits,
            VDJCAlignmentFailCause.NoJHits,
            VDJCAlignmentFailCause.VAndJOnDifferentTargets,
            VDJCAlignmentFailCause.LowTotalScore,
            VDJCAlignmentFailCause.NoBarcode,
            VDJCAlignmentFailCause.BarcodeNotInWhitelist
        )
        Assert.assertEquals(VDJCAlignmentFailCause.values().toSet(), all)
    }

    @Test
    fun test1() {
        val files =
            Paths.get("/Users/poslavskysv/Projects/milab/mixcr/src/test/resources/sequences/big/yf_sample_data/")
                .listDirectoryEntries()
                .filter { it.name.endsWith("clns") }
                .flatMap { listOf(it) }

        writePDF(
            Paths.get("scratch/bp.pdf"),
            AlignmentQC.alignQc(files, percent = false),
            AlignmentQC.alignQc(files, percent = true),

            ChainUsage.chainUsageAlign(files, percent = false),
            ChainUsage.chainUsageAlign(files, percent = true),

            ChainUsage.chainUsageAssemble(files, percent = false),
            ChainUsage.chainUsageAssemble(files, percent = true),
        )
    }
}