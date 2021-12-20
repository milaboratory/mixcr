package com.milaboratory.mixcr.postanalysis.dataframe

import com.milaboratory.mixcr.cli.CommandPostanalysis
import com.milaboratory.mixcr.postanalysis.dataframe.SimpleStatistics.plotPDF
import com.milaboratory.mixcr.postanalysis.dataframe.SimpleStatistics.withMetadata
import com.milaboratory.util.GlobalObjectMappers
import io.repseq.core.Chains
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.io.read
import org.junit.Test
import java.nio.file.Paths


/**
 *
 */
internal class SimpleStatisticsTest {
    @Test
    fun test1() {
        val meta = DataFrame.read(javaClass.getResource("/postanalysis/metadata.csv")!!)
        val pa = GlobalObjectMappers.PRETTY.readValue(
            javaClass.getResource("/postanalysis/sample_pa.json"),
            CommandPostanalysis.PaResult::class.java
        )

        val igh = pa.results[Chains.IGH_NAMED]!!

        var df = SimpleStatistics.dataFrame(igh.result.forGroup(igh.schema.getGroup<Any>("biophysics")), null)

        df = df.withMetadata(meta)

        df.plotPDF(
            Paths.get("scratch/pa/diversity.pdf"),
            SimpleStatistics.BoxPlotSettings(primaryGroup = "Cat3", applyHolmBonferroni = true)
        )
    }
}
