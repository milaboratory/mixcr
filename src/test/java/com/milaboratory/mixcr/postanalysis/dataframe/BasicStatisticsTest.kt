package com.milaboratory.mixcr.postanalysis.dataframe

import com.milaboratory.miplots.writePDF
import com.milaboratory.mixcr.cli.CommandPa
import com.milaboratory.util.GlobalObjectMappers
import io.repseq.core.Chains
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.filter
import org.jetbrains.kotlinx.dataframe.io.read
import org.junit.Test
import java.nio.file.Paths


/**
 *
 */
internal class BasicStatisticsTest {
    @Test
    fun test1() {
        val meta = DataFrame.read(javaClass.getResource("/postanalysis/metadata.csv")!!)
        val pa = GlobalObjectMappers.PRETTY.readValue(
            javaClass.getResource("/postanalysis/sample_pa.json"),
            CommandPa.PaResult::class.java
        )

        val igh = pa.results[Chains.IGH_NAMED]!!


        var df = BasicStatistics.dataFrame(igh.result.forGroup(igh.schema.getGroup<Any>("biophysics")), null)
        df = df.withMetadata(meta)

        df = df.filter { metric == "ntLengthOfCDR3" }

        val plt = BasicStatistics.plot(
            df, BasicStatistics.PlotParameters(
                primaryGroup = "Cat3",
                secondaryGroup = "Cat2",
                facetBy = null
            )
        )

        writePDF(
            Paths.get("scratch/pa/diversity.pdf"),
            plt
        )
    }
}
