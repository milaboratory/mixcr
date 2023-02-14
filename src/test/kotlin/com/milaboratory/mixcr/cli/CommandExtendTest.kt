package com.milaboratory.mixcr.cli

import com.milaboratory.test.TestUtil
import com.milaboratory.util.io.K_OM
import io.repseq.core.ReferencePoint
import org.junit.Test

class CommandExtendTest {
    @Test
    fun testSerialization() {
        TestUtil.assertJson(
            K_OM,
            CommandExtendParams(
                ReferencePoint.CDR3Begin, ReferencePoint.CDR3End, 70, 100
            ),
            true
        )
    }
}
