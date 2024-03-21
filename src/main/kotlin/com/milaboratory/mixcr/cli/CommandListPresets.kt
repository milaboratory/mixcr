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

import com.milaboratory.mixcr.cli.CommonDescriptions.Labels
import com.milaboratory.mixcr.presets.AlignMixins
import com.milaboratory.mixcr.presets.AssembleContigsMixins
import com.milaboratory.mixcr.presets.AssembleMixins
import com.milaboratory.mixcr.presets.Flags
import com.milaboratory.mixcr.presets.MiXCRPresetCategory
import com.milaboratory.mixcr.presets.PipelineMixins
import com.milaboratory.mixcr.presets.PresetSpecification
import com.milaboratory.mixcr.presets.Presets
import org.apache.logging.log4j.core.tools.picocli.CommandLine.Help.Ansi
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import java.io.PrintWriter

@Command(
    description = ["Show all available presets"]
)
class CommandListPresets : MiXCRCommand() {
    @Parameters(
        description = ["Will export only presets that contains a specified parameter."],
        paramLabel = "<query>",
        arity = "0..1"
    )
    var query: String? = null

    override fun run0() {
        val forExport = when {
            query != null -> Presets.visiblePresets.filter { it.contains(query!!) }
            else -> Presets.visiblePresets
        }
        val presetsByCategory = forExport
            .map { presetName -> PresetSpecification.ForUI.build(presetName) }
            .groupBy { it.category }

        val ansi = Ansi.AUTO
        PrintWriter(System.out).use { writer ->
            presetsByCategory[MiXCRPresetCategory.generic]?.let { genericPresets ->
                writer.println("Generic presets:")
                genericPresets.forEach { preset ->
                    writer.printPresetInfo(preset, ansi)
                    writer.println()
                }
            }

            presetsByCategory[MiXCRPresetCategory.`non-generic`]?.groupBy { it.vendor!! }?.let { byVendors ->
                byVendors.forEach { (vendor, presets) ->
                    if (byVendors.size != 1) {
                        writer.println("-----$vendor-----")
                    }
                    presets.forEach { preset ->
                        writer.printPresetInfo(preset, ansi)
                        writer.println()
                    }
                }
            }
        }
    }

    private fun PrintWriter.printPresetInfo(preset: PresetSpecification.ForUI, ansi: Ansi) {
        println(ansi.Text("@|bold ${preset.presetName}|@ @|faint (${preset.label})|@").toString())
        if (preset.requiredFlags.isNotEmpty()) {
            println("  Required args:")
            preset.requiredFlags.forEach { flag ->
                println("    ${flagOptions[flag]!!.replace("\n", "")}")
            }
        }
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
    Flags.AssembleClonesBy to "${AssembleMixins.SetClonotypeAssemblingFeatures.CMD_OPTION} ${Labels.GENE_FEATURES}",
    Flags.AssembleContigsBy to "${AssembleContigsMixins.SetContigAssemblingFeatures.CMD_OPTION} ${Labels.GENE_FEATURES}",
    Flags.AssembleContigsByOrMaxLength to "(${AssembleContigsMixins.SetContigAssemblingFeatures.CMD_OPTION} ${Labels.GENE_FEATURES}|${AssembleContigsMixins.AssembleContigsWithMaxLength.CMD_OPTION})",
    Flags.AssembleContigsByOrByCell to "(${AssembleContigsMixins.SetContigAssemblingFeatures.CMD_OPTION} ${Labels.GENE_FEATURES}|${PipelineMixins.AssembleContigsByCells.CMD_OPTION})",
)
