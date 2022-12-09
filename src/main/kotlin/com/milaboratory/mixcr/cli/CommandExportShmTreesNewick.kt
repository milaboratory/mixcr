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

import cc.redberry.pipe.util.filter
import cc.redberry.pipe.util.forEach
import cc.redberry.pipe.util.map
import com.milaboratory.mixcr.trees.NewickTreePrinter
import com.milaboratory.mixcr.trees.SHMTreeForPostanalysis
import com.milaboratory.mixcr.trees.SHMTreesReader
import com.milaboratory.mixcr.trees.forPostanalysis
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

@Command(
    description = ["Export SHMTree as newick"]
)
class CommandExportShmTreesNewick : CommandExportShmTreesAbstract() {
    @Parameters(
        index = "1",
        paramLabel = "outputDir",
        description = ["Output directory to write newick files. Separate file for every tree will be created"]
    )
    lateinit var out: Path

    override val outputFiles
        get() = listOf(out)

    override fun validate() {
        ValidationException.requireNoExtension(out.toString())
    }

    override fun run0() {
        out.createDirectories()

        val newickTreePrinter = NewickTreePrinter<SHMTreeForPostanalysis.BaseNode> {
            it.content.id.toString()
        }

        SHMTreesReader(input, VDJCLibraryRegistry.getDefault()).use { reader ->
            reader.readTrees()
                .filter { treeFilter?.match(it.treeId) != false }
                .map {
                    it.forPostanalysis(reader.fileNames, reader.libraryRegistry)
                }
                .filter { treeFilter?.match(it) != false }
                .forEach { shmTree ->
                    val newickFileOutput = out.resolve("${shmTree.meta.treeId}.tree")
                    newickFileOutput.writeText(newickTreePrinter.print(shmTree.tree))
                }
        }
    }

}
