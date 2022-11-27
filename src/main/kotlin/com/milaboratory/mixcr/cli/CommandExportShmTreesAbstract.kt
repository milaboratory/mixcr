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

import com.milaboratory.mixcr.postanalysis.plots.SeqPattern
import com.milaboratory.mixcr.postanalysis.plots.TreeFilter
import com.milaboratory.mixcr.trees.SHMTreesWriter.Companion.shmFileExtension
import io.repseq.core.GeneFeature
import picocli.CommandLine
import picocli.CommandLine.ArgGroup
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path

abstract class CommandExportShmTreesAbstract : MiXCRCommandWithOutputs() {
    @Parameters(
        index = "0",
        paramLabel = "trees.$shmFileExtension",
        description = ["Input file produced by '${CommandFindShmTrees.COMMAND_NAME}' command."]
    )
    lateinit var input: Path

    @set:Option(
        names = ["--filter-min-nodes"],
        description = ["Minimal number of nodes in tree"],
        paramLabel = "<n>",
        order = OptionsOrder.main + 30_000
    )
    private var minNodes: Int? = null
        set(value) {
            ValidationException.require(value == null || value > 0) { "value must be greater then 0" }
            field = value
        }

    @set:Option(
        names = ["--filter-min-height"],
        description = ["Minimal height of the tree "],
        paramLabel = "<n>",
        order = OptionsOrder.main + 30_100
    )
    private var minHeight: Int? = null
        set(value) {
            ValidationException.require(value == null || value > 0) { "value must be greater then 0" }
            field = value
        }

    @Option(
        names = ["--ids"],
        description = ["Filter specific trees by id"],
        split = ",",
        paramLabel = "<id>",
        order = OptionsOrder.main + 30_200
    )
    private var treeIds: Set<Int>? = null

    class PatternOptions {
        class PatternChoice {
            @Option(
                names = ["--filter-aa-pattern"],
                description = ["Filter specific trees by aa pattern."],
                paramLabel = "<pattern>",
                order = 101
            )
            var seqAa: String? = null

            @Option(
                names = ["--filter-nt-pattern"],
                description = ["Filter specific trees by nt pattern."],
                paramLabel = "<pattern>",
                order = 102
            )
            var seqNt: String? = null
        }

        @ArgGroup(
            multiplicity = "1",
            exclusive = true,
            order = 1
        )
        lateinit var pattern: PatternChoice

        @Option(
            names = ["--filter-in-feature"],
            description = ["Match pattern inside specified gene feature."],
            paramLabel = CommonDescriptions.Labels.GENE_FEATURE,
            defaultValue = "CDR3",
            showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
            order = 2
        )
        lateinit var inFeature: GeneFeature

        @Option(
            names = ["--pattern-max-errors"],
            description = ["Max allowed subs & indels."],
            paramLabel = "<n>",
            showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
            defaultValue = "0",
            order = 3
        )
        var maxErrors: Int = 0
    }

    @ArgGroup(
        heading = "Filter by pattern\n",
        exclusive = false,
        multiplicity = "0..1",
        order = OptionsOrder.main + 30_300
    )
    private var patternOptions: PatternOptions? = null

    private val pattern by lazy {
        patternOptions?.let { options ->
            if (options.pattern.seqNt != null)
                SeqPattern(options.pattern.seqNt!!, false, options.inFeature, options.maxErrors)
            else
                SeqPattern(options.pattern.seqAa!!, true, options.inFeature, options.maxErrors)
        }
    }

    protected val treeFilter by lazy {
        if (minNodes == null && minHeight == null && treeIds == null && pattern == null)
            null
        else
            TreeFilter(
                minNodes = minNodes,
                minHeight = minHeight,
                treeIds = treeIds,
                seqPattern = pattern
            )
    }

    override val inputFiles
        get() = listOf(input)

    override fun validate() {
        ValidationException.requireFileType(input, InputFileType.SHMT)
    }
}
