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

import com.milaboratory.mixcr.export.InfoWriter
import com.milaboratory.mixcr.export.SplittedTreeNodeFieldsExtractorsFactory
import com.milaboratory.mixcr.trees.SHMTreesReader
import com.milaboratory.mixcr.trees.forPostanalysis
import com.milaboratory.primitivio.forEach
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters

@Command(
    name = CommandExportShmTreesTableWithNodes.COMMAND_NAME,
    sortOptions = false,
    separator = " ",
    description = ["Export SHMTree as a table with a row for every node"]
)
class CommandExportShmTreesTableWithNodes : CommandExportShmTreesAbstract() {
    @Parameters(index = "1", description = ["trees.tsv"])
    override lateinit var out: String

    override fun run0() {
        SHMTreesReader(`in`, VDJCLibraryRegistry.getDefault()).use { reader ->
            InfoWriter.create(
                out,
                SplittedTreeNodeFieldsExtractorsFactory,
                spec.commandLine().parseResult,
                reader
            ).use { output ->
                reader.readTrees().forEach { shmTree ->
                    val shmTreeForPostanalysis = shmTree.forPostanalysis(
                        reader.fileNames,
                        reader.alignerParameters,
                        reader.libraryRegistry
                    )

                    shmTreeForPostanalysis.tree.allNodes()
                        .asSequence()
                        .flatMap { it.node.content.split() }
                        .forEach { node ->
                            output.put(SplittedTreeNodeFieldsExtractorsFactory.Wrapper(shmTreeForPostanalysis, node))
                        }
                }
            }
        }
    }

    companion object {
        const val COMMAND_NAME = "exportShmTreesWithNodes"

        @JvmStatic
        fun mkCommandSpec(): CommandLine.Model.CommandSpec {
            val command = CommandExportShmTreesTableWithNodes()
            val spec = CommandLine.Model.CommandSpec.forAnnotatedObject(command)
            command.spec = spec // inject spec manually
            SplittedTreeNodeFieldsExtractorsFactory.addOptionsToSpec(spec, true)
            return spec
        }
    }
}
