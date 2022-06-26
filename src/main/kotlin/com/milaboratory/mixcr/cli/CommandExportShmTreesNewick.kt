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

import com.milaboratory.mixcr.trees.NewickTreePrinter
import com.milaboratory.mixcr.trees.SHMTreeForPostanalysis
import com.milaboratory.mixcr.trees.SHMTreesReader
import com.milaboratory.mixcr.trees.SHMTreesWriter.Companion.shmFileExtension
import com.milaboratory.mixcr.trees.forPostanalysis
import com.milaboratory.primitivio.forEach
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import kotlin.io.path.Path
import kotlin.io.path.writeText

@Command(
    name = CommandExportShmTreesNewick.COMMAND_NAME,
    sortOptions = false,
    separator = " ",
    description = ["Export SHMTree as a table with a row for every node"]
)
class CommandExportShmTreesNewick : CommandExportShmTreesAbstract() {
    @Parameters(arity = "2", description = ["trees.$shmFileExtension output_dir"])
    override var inOut: List<String> = ArrayList()

    override fun run0() {
        val outputDir = Path(outputFiles.first())
        outputDir.toFile().mkdirs()

        val newickTreePrinter = NewickTreePrinter<SHMTreeForPostanalysis.BaseNode> {
            it.content.id.toString()
        }

        val libraryRegistry = VDJCLibraryRegistry.getDefault()
        SHMTreesReader(inputFile, libraryRegistry).use { reader ->
            reader.readTrees().forEach { shmTree ->
                val shmTreeForPostanalysis = shmTree.forPostanalysis(
                    reader.fileNames,
                    reader.assemblerParameters,
                    reader.alignerParameters,
                    libraryRegistry
                )

                val newickFileOutput = outputDir.resolve("${shmTree.treeId}.tree")

                newickFileOutput.writeText(newickTreePrinter.print(shmTreeForPostanalysis.tree))
            }
        }
    }

    companion object {
        const val COMMAND_NAME = "exportShmTreesNewick"
    }
}
