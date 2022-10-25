package com.milaboratory.mixcr.trees

import com.fasterxml.jackson.module.kotlin.readValue
import com.milaboratory.mitool.helpers.K_OM
import com.milaboratory.mixcr.cli.MiXCRCommandReport
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.instanceOf
import org.junit.Test
import java.util.*

class BuildSHMTreeReportTest {
    @Test
    fun serialization() {
        val report = BuildSHMTreeReport(
            Date(),
            "from test",
            arrayOf("in.clns"),
            arrayOf("out.shmt"),
            10L,
            "version",
            listOf(
                BuildSHMTreeReport.StepResult(
                    BuildSHMTreeStep.BuildingInitialTrees(
                        SHMTreeBuilderParameters.ClusterizationAlgorithm.SingleLinkage(
                            5,
                            1.0
                        )
                    ),
                    10,
                    10,
                    2,
                    statsSample(),
                    statsSample(),
                    statsSample(),
                    statsSample(),
                    statsSample(),
                    statsSample()
                )
            )
        )

        val asJson = K_OM.writeValueAsString(report)
        val deserialized = K_OM.readValue<BuildSHMTreeReport>(asJson)
        deserialized.commandLine shouldBe "from test"
        deserialized.stepResults.first().step shouldBe instanceOf<BuildSHMTreeStep.BuildingInitialTrees>()
        deserialized.stepResults.first().clonesCountInTrees.size shouldBe 10L
        deserialized.date shouldBe null
        deserialized.executionTimeMillis shouldBe null
    }

    private fun statsSample() =
        MiXCRCommandReport.StatsWithQuantiles(10L, 10.0, 2.0, 0.5, 15.0, 1.0, 3.0, 1.25, 1.9, 2.5)
}
