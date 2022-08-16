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

import com.milaboratory.mixcr.cli.CommandExport.FieldData
import com.milaboratory.mixcr.export.InfoWriter
import com.milaboratory.mixcr.export.OutputMode
import com.milaboratory.mixcr.export.SHNTreeFieldsExtractor
import com.milaboratory.mixcr.trees.SHMTreeForPostanalysis
import com.milaboratory.mixcr.trees.SHMTreesReader
import com.milaboratory.mixcr.trees.SHMTreesWriter.Companion.shmFileExtension
import com.milaboratory.mixcr.trees.forPostanalysis
import com.milaboratory.primitivio.forEach
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

@Command(
    name = CommandExportShmTreesTable.COMMAND_NAME,
    sortOptions = false,
    separator = " ",
    description = ["Export SHMTree as a table with a row for every table"]
)
class CommandExportShmTreesTable : CommandExportShmTreesAbstract() {
    @Parameters(arity = "2", description = ["trees.${shmFileExtension} trees.tsv"])
    override var inOut: List<String> = ArrayList()

    @Option(description = ["Output column headers with spaces."], names = ["-v", "--with-spaces"])
    var humanReadable = false

    override fun run0() {
        InfoWriter<SHMTreeForPostanalysis>(outputFiles.first()).use { output ->

            val oMode = when {
                humanReadable -> OutputMode.HumanFriendly
                else -> OutputMode.ScriptingFriendly
            }

            val libraryRegistry = VDJCLibraryRegistry.getDefault()
            SHMTreesReader(inputFile, libraryRegistry).use { reader ->
                val treeExtractors = listOf(
                    FieldData.mk("-treeId"),
                    FieldData.mk("-uniqClonesCount"),
                    FieldData.mk("-totalClonesCount"),
                    FieldData.mk("-wildcardsScore"),
                    FieldData.mk("-ndnOfMRCA"),
                    FieldData.mk("-vHit"),
                    FieldData.mk("-jHit"),
                )

                output.attachInfoProviders(
                    treeExtractors
                        .flatMap {
                            SHNTreeFieldsExtractor.extract(
                                it,
                                SHMTreeForPostanalysis::class.java,
                                reader,
                                oMode
                            )
                        }
                )

                output.ensureHeader()

                reader.readTrees().forEach { shmTree ->
                    output.put(
                        shmTree.forPostanalysis(
                            reader.fileNames,
                            reader.alignerParameters,
                            libraryRegistry
                        )
                    )
                }
            }
        }
    }

    companion object {
        const val COMMAND_NAME = "exportShmTrees"
    }
}