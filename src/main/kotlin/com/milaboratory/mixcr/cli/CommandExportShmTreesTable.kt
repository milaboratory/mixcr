/*
 * Copyright (c) 2014-2023, MiLaboratories Inc. All Rights Reserved
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

import cc.redberry.pipe.util.asSequence
import com.milaboratory.app.InputFileType
import com.milaboratory.app.ValidationException
import com.milaboratory.app.logger
import com.milaboratory.mixcr.basictypes.tag.TagsInfo
import com.milaboratory.mixcr.export.ExportFieldDescription
import com.milaboratory.mixcr.export.InfoWriter
import com.milaboratory.mixcr.export.MetaForExport
import com.milaboratory.mixcr.export.RowMetaForExport
import com.milaboratory.mixcr.export.SHMTreeFieldsExtractorsFactory
import com.milaboratory.mixcr.trees.SHMTreesReader
import com.milaboratory.mixcr.trees.forPostanalysis
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path
import kotlin.io.path.createDirectories

@Command(
    description = ["Export SHMTree as a table with a row for every SHM root in a table (single row if no single cell data)"]
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

    @Option(
        description = ["Export not covered regions as empty text."],
        names = ["--not-covered-as-empty"],
        arity = "0",
        order = OptionsOrder.exportOptions + 400
    )
    var notCoveredAsEmpty: Boolean = false

    val addedFields: MutableList<ExportFieldDescription> = mutableListOf()

    override val outputFiles
        get() = listOfNotNull(out)

    override fun initialize() {
        if (out == null) {
            logger.redirectSysOutToSysErr()
        }
    }

    override fun run1() {
        out?.toAbsolutePath()?.parent?.createDirectories()
        SHMTreesReader(input, VDJCLibraryRegistry.getDefault()).use { reader ->
            val headerForExport = MetaForExport(
                reader.cloneSetInfos.map { it.tagsInfo },
                reader.header.allFullyCoveredBy,
                reader.footer,
                reader.header.calculatedCloneGroups
            )
            val rowMetaForExport = RowMetaForExport(TagsInfo.NO_TAGS, headerForExport, notCoveredAsEmpty)
            InfoWriter.create(
                out,
                SHMTreeFieldsExtractorsFactory.createExtractors(addedFields, headerForExport),
                !noHeader,
            ) { rowMetaForExport }.use { output ->
                reader.readTrees()
                    .asSequence()
                    .filter { treeFilter?.match(it.treeId) != false }
                    .map { shmTree ->
                        shmTree.forPostanalysis(reader.fileNames, reader.library)
                    }
                    .filter { treeFilter?.match(it) != false }
                    .flatMap { it.splitToChains() }
                    .forEach { output.put(it) }
            }
        }
    }

    companion object {
        val COMMAND_NAME = SHMTreeFieldsExtractorsFactory.commandForUsage
        fun mkCommandSpec(): CommandLine.Model.CommandSpec {
            val command = CommandExportShmTreesTable()
            val spec = CommandLine.Model.CommandSpec.forAnnotatedObject(command)
            command.spec = spec // inject spec manually
            SHMTreeFieldsExtractorsFactory.addOptionsToSpec(command.addedFields, spec)
            return spec
        }
    }
}
