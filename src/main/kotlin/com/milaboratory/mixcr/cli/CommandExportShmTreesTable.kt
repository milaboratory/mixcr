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

import com.milaboratory.mixcr.basictypes.tag.TagsInfo
import com.milaboratory.mixcr.export.ExportFieldDescription
import com.milaboratory.mixcr.export.HeaderForExport
import com.milaboratory.mixcr.export.InfoWriter
import com.milaboratory.mixcr.export.RowMetaForExport
import com.milaboratory.mixcr.export.SHMTreeFieldsExtractorsFactory
import com.milaboratory.mixcr.trees.SHMTreesReader
import com.milaboratory.mixcr.trees.forPostanalysis
import com.milaboratory.primitivio.filter
import com.milaboratory.primitivio.forEach
import com.milaboratory.primitivio.map
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path
import kotlin.io.path.createDirectories

@Command(
    description = ["Export SHMTree as a table with a row for every table"]
)
class CommandExportShmTreesTable : CommandExportShmTreesAbstract() {
    @Parameters(
        index = "1",
        arity = "0..1",
        paramLabel = "trees.tsv",
        description = ["Path to output table. Print in stdout if omitted."]
    )
    var out: Path? = null
        set(value) {
            ValidationException.requireFileType(out, InputFileType.TSV)
            field = value
        }

    @Option(
        description = ["Don't print first header line, print only data"],
        names = ["--no-header"],
        order = OptionsOrder.exportOptions
    )
    var noHeader = false

    val addedFields: MutableList<ExportFieldDescription> = mutableListOf()

    override val outputFiles
        get() = listOfNotNull(out)

    override fun run0() {
        out?.toAbsolutePath()?.parent?.createDirectories()
        SHMTreesReader(input, VDJCLibraryRegistry.getDefault()).use { reader ->
            val rowMetaForExport = RowMetaForExport(TagsInfo.NO_TAGS)
            InfoWriter.create(
                out,
                SHMTreeFieldsExtractorsFactory.createExtractors(
                    addedFields,
                    HeaderForExport(emptyList(), reader.header.allFullyCoveredBy)
                ),
                !noHeader,
            ) { rowMetaForExport }.use { output ->
                reader.readTrees()
                    .filter { treeFilter?.match(it.treeId) != false }
                    .map { shmTree ->
                        shmTree.forPostanalysis(reader.fileNames, reader.libraryRegistry)
                    }
                    .filter { treeFilter?.match(it) != false }
                    .forEach { output.put(it) }
            }
        }
    }

    companion object {
        fun mkCommandSpec(): CommandLine.Model.CommandSpec {
            val command = CommandExportShmTreesTable()
            val spec = CommandLine.Model.CommandSpec.forAnnotatedObject(command)
            command.spec = spec // inject spec manually
            SHMTreeFieldsExtractorsFactory.addOptionsToSpec(command.addedFields, spec)
            return spec
        }
    }
}
