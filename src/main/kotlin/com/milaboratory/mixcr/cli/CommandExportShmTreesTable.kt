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
import com.milaboratory.mixcr.export.SHMTreeFieldsExtractorsFactory
import com.milaboratory.mixcr.trees.SHMTreesReader
import com.milaboratory.mixcr.trees.forPostanalysis
import com.milaboratory.primitivio.forEach
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import java.nio.file.Path
import kotlin.io.path.createDirectories

@Command(
    name = CommandExportShmTreesTable.COMMAND_NAME,
    sortOptions = false,
    separator = " ",
    description = ["Export SHMTree as a table with a row for every table"]
)
class CommandExportShmTreesTable : CommandExportShmTreesAbstract() {
    @Parameters(
        index = "1",
        arity = "0..1",
        paramLabel = "trees.tsv",
        description = ["Path to output table. Print in stdout if omitted."]
    )
    val out: Path? = null

    override fun getOutputFiles(): List<String> = listOfNotNull(out?.toString())

    override fun run0() {
        out?.toAbsolutePath()?.parent?.createDirectories()
        SHMTreesReader(`in`, VDJCLibraryRegistry.getDefault()).use { reader ->
            InfoWriter.create(
                out,
                SHMTreeFieldsExtractorsFactory,
                spec.commandLine().parseResult,
                reader.header,
                true,
            ).use { output ->
                reader.readTrees().forEach { shmTree ->
                    output.put(
                        shmTree.forPostanalysis(
                            reader.fileNames,
                            reader.alignerParameters,
                            reader.libraryRegistry
                        )
                    )
                }
            }
        }
    }

    companion object {
        const val COMMAND_NAME = "exportShmTrees"

        @JvmStatic
        fun mkCommandSpec(): CommandLine.Model.CommandSpec {
            val command = CommandExportShmTreesTable()
            val spec = CommandLine.Model.CommandSpec.forAnnotatedObject(command)
            command.spec = spec // inject spec manually
            SHMTreeFieldsExtractorsFactory.addOptionsToSpec(spec, true)
            return spec
        }
    }
}
