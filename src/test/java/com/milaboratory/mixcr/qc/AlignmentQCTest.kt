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
package com.milaboratory.mixcr.qc

import com.milaboratory.mixcr.vdjaligners.VDJCAlignmentFailCause
import org.junit.Assert
import org.junit.Test

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
            VDJCAlignmentFailCause.SampleNotMatched
        )
        Assert.assertEquals(VDJCAlignmentFailCause.values().toSet(), all)
    }
}