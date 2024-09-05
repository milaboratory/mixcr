/*
 * Copyright (c) 2014-2024, MiLaboratories Inc. All Rights Reserved
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

import com.fasterxml.jackson.module.kotlin.readValue
import com.milaboratory.app.InputFileType
import com.milaboratory.app.ValidationException
import com.milaboratory.cli.POverridesBuilderOps
import com.milaboratory.cli.ParamsResolver
import com.milaboratory.mixcr.presets.MiXCRParamsBundle
import com.milaboratory.mixcr.presets.MiXCRParamsSpec
import com.milaboratory.mixcr.presets.PresetSpecification
import com.milaboratory.util.K_OM
import com.milaboratory.util.K_YAML_OM
import com.mixcr.util.K_JSON_OM
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import java.nio.file.Path

@Command(hidden = true)
class CommandPresetSpecificationsForBack : MiXCRCommand(), MiXCRPresetAwareCommand<Unit> {
    @Parameters(index = "0", arity = "1")
    lateinit var input: String

    @Parameters(index = "1", arity = "1")
    lateinit var output: Path

    override fun validate() {
        ValidationException.requireFileType(output, InputFileType.JSON)
    }

    override fun run0() {
        output.toAbsolutePath().parent.toFile().mkdirs()
        val preset = if (InputFileType.YAML.matches(Path.of(input))) {
            K_YAML_OM.readValue(Path.of(input).toFile())
        } else if (InputFileType.JSON.matches(Path.of(input))) {
            K_JSON_OM.readValue(Path.of(input).toFile())
        } else {
            paramsResolver.resolve(
                MiXCRParamsSpec(input),
                printParameters = false,
                validate = false
            ).first
        }
        K_OM.writeValue(output.toFile(), PresetSpecification.ForBackend.build(preset, emptyList()))
    }

    override val paramsResolver: ParamsResolver<MiXCRParamsBundle, Unit>
        get() = object : MiXCRParamsResolver<Unit>(MiXCRParamsBundle::exportPreset) {
            override fun POverridesBuilderOps<Unit>.paramsOverrides() {
            }
        }
}
