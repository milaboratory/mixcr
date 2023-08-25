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
import com.milaboratory.cli.POverridesBuilderOps
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.cli.CommonDescriptions.DEFAULT_VALUE_FROM_PRESET
import com.milaboratory.mixcr.clonegrouping.CellType
import com.milaboratory.mixcr.export.CloneFieldsExtractorsFactory
import com.milaboratory.mixcr.presets.MiXCRCommandDescriptor
import com.milaboratory.mixcr.presets.MiXCRParamsBundle
import io.repseq.core.Chains
import io.repseq.core.GeneFeature
import io.repseq.core.GeneFeature.CDR3
import io.repseq.core.GeneType
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Model
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path

object CommandExportCloneGroups {
    const val COMMAND_NAME = MiXCRCommandDescriptor.exportCloneGroups.name

    private fun CommandExportCloneGroupsParams.test(
        clone: Clone,
        assemblingFeatures: Array<GeneFeature>,
        chains: Chains
    ): Boolean {
        if (filterOutOfFrames)
            if (clone.isOutOfFrameOrAbsent(CDR3)) return false

        if (filterStops)
            for (assemblingFeature in assemblingFeatures)
                if (clone.containsStopsOrAbsent(assemblingFeature)) return false

        if (chains == Chains.ALL)
            return true

        return GeneType.VJC_REFERENCE.any {
            val hit = clone.getBestHit(it)
            hit != null && chains.intersects(hit.gene.chains)
        }
    }

    abstract class CmdBase : MiXCRCommandWithOutputs(), MiXCRPresetAwareCommand<CommandExportCloneGroupsParams> {
        @Option(
            description = [
                "Limit export to specific cell types. Possible values: \${COMPLETION-CANDIDATES}.",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["--types"],
            paramLabel = "<type>",
            order = OptionsOrder.main + 10_100,
            completionCandidates = ChainsCandidates::class
        )
        private var types: List<CellType> = emptyList()

        @Option(
            description = [
                "Exclude clones with out-of-frame clone sequences (fractions will be recalculated).",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["-o", "--filter-out-of-frames"],
            order = OptionsOrder.main + 10_200
        )
        private var filterOutOfFrames = false

        @Option(
            description = [
                "Exclude sequences containing stop codons (fractions will be recalculated).",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["-t", "--filter-stops"],
            order = OptionsOrder.main + 10_300
        )
        private var filterStops = false

        @Option(
            description = [
                "Exclude groups containing only one .",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["--filter-groups-with-one-chain"],
            order = OptionsOrder.main + 10_400
        )
        private var filterOutGroupsWithOneClone = false

        @Mixin
        lateinit var exportDefaults: ExportDefaultOptions

        @Mixin
        lateinit var resetPreset: ResetPresetOptions


        override val paramsResolver =
            object : MiXCRParamsResolver<CommandExportCloneGroupsParams>(MiXCRParamsBundle::exportCloneGroups) {
                override fun POverridesBuilderOps<CommandExportCloneGroupsParams>.paramsOverrides() {
                    CommandExportCloneGroupsParams::types setIfNotEmpty types
                    CommandExportCloneGroupsParams::filterOutOfFrames setIfTrue filterOutOfFrames
                    CommandExportCloneGroupsParams::filterStops setIfTrue filterStops
                    CommandExportCloneGroupsParams::filterOutGroupsWithOneClone setIfTrue filterOutGroupsWithOneClone
                    CommandExportCloneGroupsParams::noHeader setIfTrue exportDefaults.noHeader
                    CommandExportCloneGroupsParams::fields updateBy exportDefaults
                }
            }
    }

    @Command(
        description = ["Export assembled clones into tab delimited file."]
    )
    class Cmd : CmdBase() {
        @Parameters(
            description = ["Path to input file with clones"],
            paramLabel = "data.clns",
            index = "0"
        )
        lateinit var inputFile: Path

        @set:Parameters(
            description = ["Path where to write export table."],
            paramLabel = "table.tsv",
            index = "1",
            arity = "0..1"
        )
        var outputFile: Path? = null
            set(value) {
                ValidationException.requireFileType(value, InputFileType.TSV)
                field = value
            }

        @Mixin
        lateinit var exportMixins: ExportMiXCRMixins.CommandSpecificExportClones

        override val inputFiles
            get() = listOf(inputFile)

        override val outputFiles
            get() = listOfNotNull(outputFile)

        override fun validate() {
            ValidationException.requireFileType(inputFile, InputFileType.CLNS)
        }

        override fun run1() {
        }
    }

    @JvmStatic
    fun mkSpec(): Model.CommandSpec {
        val cmd = Cmd()
        val spec = Model.CommandSpec.forAnnotatedObject(cmd)
        cmd.spec = spec // inject spec manually
        CloneFieldsExtractorsFactory.addOptionsToSpec(cmd.exportDefaults.addedFields, spec)
        return spec
    }
}
