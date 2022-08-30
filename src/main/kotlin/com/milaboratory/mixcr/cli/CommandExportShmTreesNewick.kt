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
    description = ["Export SHMTree as newick"]
)
class CommandExportShmTreesNewick : CommandExportShmTreesAbstract() {
    @Parameters(index = "1", description = ["output directory to write newick files"])
    override lateinit var out: String

    override fun run0() {
        val outputDir = Path(out)
        outputDir.toFile().mkdirs()

        val newickTreePrinter = NewickTreePrinter<SHMTreeForPostanalysis.BaseNode> {
            it.content.id.toString()
        }

        SHMTreesReader(`in`, VDJCLibraryRegistry.getDefault()).use { reader ->
            reader.readTrees().forEach { shmTree ->
                val shmTreeForPostanalysis = shmTree.forPostanalysis(
                    reader.fileNames,
                    reader.alignerParameters,
                    reader.libraryRegistry
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
