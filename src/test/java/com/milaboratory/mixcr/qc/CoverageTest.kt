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
import org.junit.Test
import java.nio.file.Paths

class CoverageTest {
    @Test
    fun test1() {
        val path =
            Paths.get("/Users/poslavskysv/Projects/milab/mixcr-test-data/results/als.vdjca")

        writePDF(
            Paths.get("scratch/bp.pdf"),
            Coverage.coveragePlot(path),
        )
    }
}