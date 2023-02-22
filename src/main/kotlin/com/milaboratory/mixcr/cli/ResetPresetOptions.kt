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

import com.milaboratory.mixcr.MiXCRParamsSpec
import com.milaboratory.mixcr.cli.CommonDescriptions.Labels
import picocli.CommandLine.Option

class ResetPresetOptions {
    @Option(
        description = ["Reset preset from input file to new value. All previous mix-ins will be removed"],
        names = ["--reset-preset"],
        paramLabel = Labels.PRESET,
        order = 1,
        hidden = true,
        completionCandidates = PresetsCandidates::class
    )
    var resetPreset: String? = null

    private var resetMixins = true

    @Suppress("unused")
    @Option(
        description = ["Reset preset from input file to new value. All previous mix-ins will be removed"],
        names = ["--reset-preset-keep-mixins"],
        paramLabel = Labels.PRESET,
        order = 2,
        hidden = true,
        completionCandidates = PresetsCandidates::class
    )
    fun resetPresetAndKeepMixins(presetName: String) {
        resetPreset = presetName
        resetMixins = false
    }

    fun overridePreset(originalPreset: MiXCRParamsSpec): MiXCRParamsSpec =
        when {
            resetPreset != null -> when {
                resetMixins -> MiXCRParamsSpec(resetPreset!!)
                else -> MiXCRParamsSpec(resetPreset!!, originalPreset.mixins)
            }

            else -> originalPreset
        }
}
