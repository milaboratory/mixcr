/*
 * Copyright (c) 2014-2023, MiLaboratories Inc. All Rights Reserved
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

import com.milaboratory.cli.DummyParamsBundleSpec
import com.milaboratory.mixcr.presets.MiXCRParamsSpec
import picocli.CommandLine.Option

class DontSavePresetOption {
    @Option(
        names = ["--dont-save-preset"],
        hidden = true
    )
    private var dontSavePreset: Boolean = false

    fun presetToSave(originalPreset: MiXCRParamsSpec): MiXCRParamsSpec = if (dontSavePreset) {
        MiXCRParamsSpec(base = DummyParamsBundleSpec(), mixins = emptyList())
    } else {
        originalPreset
    }
}
