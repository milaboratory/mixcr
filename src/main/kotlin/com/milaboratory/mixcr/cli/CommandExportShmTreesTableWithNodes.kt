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

import com.milaboratory.mixcr.export.ExportFieldDescription
import com.milaboratory.mixcr.export.InfoWriter
import com.milaboratory.mixcr.export.SplittedTreeNodeFieldsExtractorsFactory
import com.milaboratory.mixcr.export.SplittedTreeNodeFieldsExtractorsFactory.Wrapper
import com.milaboratory.mixcr.trees.SHMTreesReader
import com.milaboratory.mixcr.trees.forPostanalysis
import com.milaboratory.primitivio.forEach
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path
import kotlin.io.path.createDirectories

@Command(
    description = ["Export SHMTree as a table with a row for every node"]
)
class CommandExportShmTreesTableWithNodes : CommandExportShmTreesAbstract() {
    @Parameters(
        index = "1",
        arity = "0..1",
        paramLabel = "trees.tsv",
        description = ["Path where to write output export table. Print in stdout if omitted."]
    )
    val out: Path? = null

    @Option(
        description = ["Don't print first header line, print only data"],
        names = ["--no-header"],
        order = 50_000 - 100
    )
    var noHeader = false

    val addedFields: MutableList<ExportFieldDescription> = mutableListOf()

    @Option(
        description = ["Exclude nodes that was reconstructed by algorithm"],
        names = ["--onlyObserved"],
    )
    var onlyObserved: Boolean = false

    override val outputFiles
        get() = listOfNotNull(out)

    override fun run0() {
        out?.toAbsolutePath()?.parent?.createDirectories()
        SHMTreesReader(input, VDJCLibraryRegistry.getDefault()).use { reader ->
            InfoWriter.create(
                out,
                SplittedTreeNodeFieldsExtractorsFactory.createExtractors(addedFields, reader.header),
                !noHeader,
            ).use { output ->
                reader.readTrees().forEach { shmTree ->
                    val shmTreeForPostanalysis = shmTree.forPostanalysis(
                        reader.fileNames,
                        reader.alignerParameters,
                        reader.libraryRegistry
                    )

                    shmTreeForPostanalysis.tree.allNodes()
                        .asSequence()
                        .filter { !onlyObserved || it.node.isLeaf() }
                        .flatMap { it.node.content.split() }
                        .forEach { node ->
                            output.put(Wrapper(shmTreeForPostanalysis, node))
                        }
                }
            }
        }
    }

    companion object {
        fun mkCommandSpec(): CommandSpec {
            val command = CommandExportShmTreesTableWithNodes()
            val spec = CommandSpec.forAnnotatedObject(command)
            command.spec = spec // inject spec manually
            SplittedTreeNodeFieldsExtractorsFactory.addOptionsToSpec(command.addedFields, spec)
            return spec
        }
    }
}
