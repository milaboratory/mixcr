package com.milaboratory.mixcr.cli

import com.milaboratory.mitool.helpers.K_OM
import com.milaboratory.test.TestUtil
import io.repseq.core.ReferencePoint
import org.junit.Test

class CommandExtendTest {
    @Test
    fun testSerialization() {
        TestUtil.assertJson(
            K_OM,
            CommandExtend.Params(
                ReferencePoint.CDR3Begin, ReferencePoint.CDR3End, 70, 100
            ),
            true
        )
    }
}