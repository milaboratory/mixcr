package com.milaboratory.mixcr.util

import com.milaboratory.mitool.refinement.TagCorrectionReport
import com.milaboratory.mitool.report.ParseCmdReport
import com.milaboratory.mitool.report.ParseReport
import com.milaboratory.mitool.report.TagCorrectionCmdReport
import com.milaboratory.mixcr.cli.MiToolReportsDelegate
import com.milaboratory.test.TestUtil
import com.milaboratory.util.K_OM
import org.junit.Test

class SerializationTest {
    @Test
    fun `mitool tag correction report`() {
        val report = MiToolReportsDelegate.RefineTags(
            TagCorrectionCmdReport(
                0,
                "mitool refineTags",
                listOf("input"),
                listOf("output"),
                "v1.0"
            ).also {
                it.correction = TagCorrectionReport(
                    5L,
                    4L,
                    emptyList(),
                    null
                )
            }
        )

        TestUtil.assertJson(K_OM, report, true)
    }

    @Test
    fun `mitool parse correction report`() {
        val report = MiToolReportsDelegate.Parse(
            ParseCmdReport(
                ParseReport(null, 5L, 1L, 0.5, emptyMap(), emptyList()),
                null,
                "mitool refineTags",
                listOf("input"),
                listOf("output"),
                "v1.0"
            )
        )

        TestUtil.assertJson(K_OM, report, true)
    }
}
