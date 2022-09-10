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

import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.milaboratory.util.GlobalObjectMappers
import com.milaboratory.util.ReportHelper
import io.repseq.core.Chains
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class ChainUsageStatsBuilderTest {
    @Before
    fun before() {
        GlobalObjectMappers.addModifier {
            it.registerModule(kotlinModule())

        }
    }

    @Test
    fun serializationTest() {
        val stats = ChainUsageStatsBuilder()
        stats.total.addAndGet(2)
        stats.total.incrementAndGet()
        stats.chimeras.incrementAndGet()

        stats.getRecordBuilder(Chains.TRB)!!.total.incrementAndGet()
        stats.getRecordBuilder(Chains.TRB)!!.oof.incrementAndGet()
        stats.getRecordBuilder(Chains.TRB)!!.nf.incrementAndGet()
        stats.getRecordBuilder(Chains.TRA)!!.total.incrementAndGet()
        val expected = stats.buildReport()
        expected.writeReport(ReportHelper(System.out, true))
        val str = GlobalObjectMappers.getPretty().writeValueAsString(expected)
        println(str)
        val actual = GlobalObjectMappers.getPretty().readValue(str, ChainUsageStats::class.java)
        Assert.assertEquals(expected, actual)
    }
}