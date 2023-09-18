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

import com.milaboratory.app.InputFileType
import com.milaboratory.app.ValidationException
import com.milaboratory.mixcr.presets.MiXCRPresetCategory
import com.milaboratory.mixcr.presets.PresetSpecification
import com.milaboratory.mixcr.presets.Presets
import com.milaboratory.util.K_OM
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import java.nio.file.Path

@Command(hidden = true)
class CommandListPresetSpecificationsForUI : MiXCRCommand() {
    @Parameters(index = "0", arity = "1")
    lateinit var output: Path

    @Parameters(index = "1", arity = "0..1")
    var category: MiXCRPresetCategory? = null

    override fun validate() {
        ValidationException.requireFileType(output, InputFileType.JSON)
    }

    override fun run0() {
        output.toAbsolutePath().parent.toFile().mkdirs()
        val specifications = Presets.visiblePresets
            .map { presetName -> PresetSpecification.ForUI.build(presetName) }
            .filter { specification -> category == null || specification.category == category }
        K_OM.writeValue(output.toFile(), specifications)
    }
}
