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
import com.milaboratory.mixcr.trees.SHMTreesWriter.Companion.shmFileExtension
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
    @Parameters(arity = "2", description = ["trees.${shmFileExtension} trees.tsv"])
    override var inOut: List<String> = ArrayList()

    override fun run0() {
        InfoWriter<SplittedTreeNodeFieldsExtractorsFactory.Wrapper>(outputFiles.first()).use { output ->
            SHMTreesReader(inputFile, VDJCLibraryRegistry.getDefault()).use { reader ->
                val fieldExtractors =
                    SplittedTreeNodeFieldsExtractorsFactory.createExtractors(reader, spec.commandLine().parseResult)
                output.attachInfoProviders(fieldExtractors)
                output.ensureHeader()

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
