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

import com.milaboratory.mixcr.Flags
import com.milaboratory.mixcr.Presets
import picocli.CommandLine
import picocli.CommandLine.Command

@Command(
    description = ["Show all available presets"]
)
class CommandListPresets : MiXCRCommand() {
    companion object {
        const val COMMAND_NAME = "listPresets"
    }

    override fun run0() {
        val maxPresetNameLength = Presets.visiblePresets.maxOf { it.length }
        val table = CommandLine.Help.TextTable.forColumns(
            spec.commandLine().colorScheme,
            CommandLine.Help.Column(maxPresetNameLength, 0, CommandLine.Help.Column.Overflow.WRAP),
            CommandLine.Help.Column(
                Flags.flagOptions.values.flatMap { message -> message.split("\n").map { it.length } }.max() + 2,
                2,
                CommandLine.Help.Column.Overflow.SPAN
            )
        )
        table.addRowValues("PresetName", "Required mix-ins")

        Presets.visiblePresets.sorted().forEach { presetName ->
            val preset = Presets.MiXCRBundleResolver.resolvePreset(presetName)
            table.addRowValues(
                presetName,
                preset.flags.joinToString("\n") { Flags.flagOptions[it]!! }
            )
        }
        println(table.toString())
    }
}
