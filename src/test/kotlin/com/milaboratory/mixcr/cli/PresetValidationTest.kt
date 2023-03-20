package com.milaboratory.mixcr.cli

import com.milaboratory.util.TempFileManager
import org.junit.Test

class PresetValidationTest {
    @Test
    fun `validate tag pattern`() {
        val output = TempFileManager.newTempDir().toPath().resolve("output.yaml").toFile()
        TestMain.shouldFail("exportPreset --preset-name 10x-vdj-tcr-qc-test --tag-pattern ^(UMI:N{10})\\^(R2:*) $output")
    }
}
