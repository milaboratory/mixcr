/*
 * Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved
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

import com.milaboratory.cli.POverridesBuilderOps
import com.milaboratory.cli.ParamsResolver
import com.milaboratory.mitool.helpers.K_YAML_OM
import com.milaboratory.mixcr.MiXCRParamsBundle
import com.milaboratory.mixcr.Presets
import picocli.CommandLine.*
import java.io.File

object CommandExportPreset {
    const val COMMAND_NAME = "exportPreset"

    @Command(
        name = COMMAND_NAME,
        sortOptions = false,
        description = ["Export a preset file given the preset name and a set of mix-ins"]
    )
    class Cmd : MiXCRPresetAwareCommand<Unit>() {
        @Parameters(
            arity = "1..2",
            hideParamSyntax = true,
            description = ["preset_name preset_file.(yaml|yml)"]
        )
        private val inOut: List<String> = mutableListOf()

        private val presetName get() = inOut[0]
        private val outputFile get() = if (inOut.size == 1) null else inOut[1]

        override fun getInputFiles() = mutableListOf<String>()

        override fun getOutputFiles() = outputFile?.let { mutableListOf(it) } ?: mutableListOf()

        @ArgGroup(validate = false, heading = "Analysis mix-ins")
        var mixins: AllMiXCRMixIns? = null

        override fun run0() {
            val (bundle, _) = paramsResolver.parse(Presets.resolveParamsBundle(presetName), printParameters = false)
            val of = outputFile
            if (of != null)
                K_YAML_OM.writeValue(File(of), bundle)
            else
                K_YAML_OM.writeValue(System.out, bundle)
        }

        override val paramsResolver: ParamsResolver<MiXCRParamsBundle, Unit>
            get() = object : MiXCRParamsResolver<Unit>(MiXCRParamsBundle::exportPreset) {
                override fun POverridesBuilderOps<MiXCRParamsBundle>.bundleOverrides() {
                    mixins?.bundleOverride?.let { addOverride(it) }
                }

                override fun POverridesBuilderOps<Unit>.paramsOverrides() {
                }
            }
    }
}