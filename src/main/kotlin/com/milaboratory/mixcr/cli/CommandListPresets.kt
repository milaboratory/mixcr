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

import com.milaboratory.mixcr.cli.CommonDescriptions.Labels
import com.milaboratory.mixcr.presets.AlignMixins
import com.milaboratory.mixcr.presets.Flags
import com.milaboratory.mixcr.presets.Presets
import picocli.CommandLine
import picocli.CommandLine.Command

@Command(
    description = ["Show all available presets"]
)
class CommandListPresets : MiXCRCommand() {
    override fun run0() {
        val maxPresetNameLength = Presets.visiblePresets.maxOf { it.length }
        val table = CommandLine.Help.TextTable.forColumns(
            spec.commandLine().colorScheme,
            CommandLine.Help.Column(maxPresetNameLength, 0, CommandLine.Help.Column.Overflow.WRAP),
            CommandLine.Help.Column(
                flagOptions.values.flatMap { message -> message.split("\n").map { it.length } }.max() + 2,
                2,
                CommandLine.Help.Column.Overflow.SPAN
            )
        )
        table.addRowValues("PresetName", "Required mix-ins")

        Presets.visiblePresets.sorted().forEach { presetName ->
            val preset = Presets.MiXCRBundleResolver.resolvePreset(presetName)
            table.addRowValues(
                presetName,
                preset.flags.joinToString("\n") { flagOptions[it]!! }
            )
        }
        println(table.toString())
    }
}

private val flagOptions = mapOf(
    Flags.Species to "${AlignMixins.SetSpecies.CMD_OPTION} <name>",
    Flags.MaterialType to "(${AlignMixins.MaterialTypeDNA.CMD_OPTION}|${AlignMixins.MaterialTypeRNA.CMD_OPTION})",
    Flags.LeftAlignmentMode to
            "(${AlignMixins.AlignmentBoundaryConstants.LEFT_FLOATING_CMD_OPTION} [${Labels.ANCHOR_POINT}]|\n" +
            "${AlignMixins.AlignmentBoundaryConstants.LEFT_RIGID_CMD_OPTION} [${Labels.ANCHOR_POINT}])",
    Flags.RightAlignmentMode to
            "(${AlignMixins.AlignmentBoundaryConstants.RIGHT_FLOATING_CMD_OPTION} (${Labels.GENE_TYPE}|${Labels.ANCHOR_POINT})|\n" +
            "${AlignMixins.AlignmentBoundaryConstants.RIGHT_RIGID_CMD_OPTION} [(${Labels.GENE_TYPE}|${Labels.ANCHOR_POINT})])",
    Flags.TagPattern to "${AlignMixins.SetTagPattern.CMD_OPTION} <pattern>",
    Flags.SampleTable to
            "${AlignMixins.SetSampleSheet.CMD_OPTION_FUZZY} sample_table.tsv\n" +
            "${AlignMixins.SetSampleSheet.CMD_OPTION_STRICT} sample_table.tsv",
)
