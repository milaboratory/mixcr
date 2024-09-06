/*
 * Copyright (c) 2014-2024, MiLaboratories Inc. All Rights Reserved
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
import com.milaboratory.mitool.tag.TagType
import com.milaboratory.mitool.tag.TagsInfo
import com.milaboratory.mixcr.clonegrouping.CellType
import com.milaboratory.mixcr.export.ExportFieldDescription
import com.milaboratory.mixcr.export.InfoWriter
import com.milaboratory.mixcr.export.MetaForExport
import com.milaboratory.mixcr.export.RowMetaForExport
import com.milaboratory.mixcr.export.SHMSingleCellTreeNodeFieldsExtractorsFactory
import com.milaboratory.mixcr.export.SHMSingleCellTreeNodeFieldsExtractorsFactory.Wrapper
import com.milaboratory.mixcr.trees.SHMTreesReader
import com.milaboratory.mixcr.trees.forPostanalysis
import com.milaboratory.mixcr.trees.splitToChains
import io.repseq.core.Chains
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Model
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path

@Command(
    description = ["Export SHM trees with one row per node. Tree may contain several roots, that will be exported in separate columns. " +
            "Initial data for building tree should contain cell data."]
)
class CommandExportShmSingleCellTreesTable : CommandExportShmTreesAbstract() {
    @Option(
        description = ["Export trees with only one chain, i.e. without single cell data. By default exporting only trees that have several roots"],
        names = ["--include-one-chain-trees"],
        order = OptionsOrder.main,
        arity = "0"
    )
    var includeOneChainTrees: Boolean = false

    @Option(
        description = ["Export SHM trees for given cell type. By default all will be exported. Possible values: IGHK, IGHL"],
        names = ["--cell-type"],
        order = OptionsOrder.mixins.exports + 5_100,
        paramLabel = "<cell_type>",
        arity = "1..*"
    )
    val exportCloneGroupsForCellTypes: List<CellType> = mutableListOf()

    @Option(
        description = ["Don't show columns for secondary chains in export for cell groups."],
        names = ["--dont-show-secondary-chain"],
        order = OptionsOrder.mixins.exports + 5_201,
        arity = "0",
    )
    var dontShowSecondaryChain: Boolean = false

    @Option(
        description = [
            "How to sort subtrees for determination of the second chain.",
            "Clones - by count of clones, Read - by sum of reads count, Molecule - by sum of count of UMI tags."
        ],
        names = ["--sort-chains-by"],
        order = OptionsOrder.mixins.exports + 5_300,
        paramLabel = "(Clones|Read|Molecule)",
        showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
        arity = "1"
    )
    var sortChainsBy: SortChainsBy = SortChainsBy.Clones

    enum class SortChainsBy {
        Clones,
        Read,
        Molecule
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

    @Option(
        description = ["Export nodes that have at least one clone."],
        names = ["--only-observed"],
        order = OptionsOrder.main + 10_100
    )
    var onlyObserved: Boolean = false

    @set:Parameters(
        description = ["Path where to write output export table. Print in stdout if omitted."],
        paramLabel = "trees.tsv",
        index = "1",
        arity = "0..1"
    )
    var outputFile: Path? = null
        set(value) {
            ValidationException.requireFileType(value, InputFileType.TSV)
            field = value
        }

    val addedFields: MutableList<ExportFieldDescription> = mutableListOf()

    override val inputFiles
        get() = listOf(input)

    override val outputFiles
        get() = listOfNotNull(outputFile)

    override fun validate() {
        ValidationException.requireFileType(input, InputFileType.SHMT)
        ValidationException.requireEmpty(exportCloneGroupsForCellTypes.filter { it.vdjChain !in Chains.IG }) {
            "Can't export not IG chains"
        }
    }

    override fun initialize() {
        if (outputFile == null)
            logger.redirectSysOutToSysErr()
    }

    override fun run1() {
        val reader = SHMTreesReader(input, VDJCLibraryRegistry.getDefault())
        ValidationException.require(reader.header.calculatedCloneGroups) {
            "SHM trees build without single cell data. Run `${CommandAssembleCells.COMMAND_NAME}` on sources of trees."
        }

        val headerForExport = MetaForExport(
            reader.cloneSetInfos.map { it.tagsInfo },
            reader.header.allFullyCoveredBy,
            reader.footer,
            reader.header.calculatedCloneGroups,
            reader.library
        )
        val fieldExtractors = SHMSingleCellTreeNodeFieldsExtractorsFactory(
            exportCloneGroupsForCellTypes.ifEmpty { CellType.values().toList().filter { it.vdjChain in Chains.IG } },
            !dontShowSecondaryChain
        ).createExtractors(addedFields, headerForExport)

        val rowMetaForExport = RowMetaForExport(TagsInfo.NO_TAGS, headerForExport, notCoveredAsEmpty)
        InfoWriter.create(
            outputFile,
            fieldExtractors,
            !noHeader
        ) { rowMetaForExport }.use { writer ->
            reader.readTrees()
                .asSequence()
                .filter { tree -> includeOneChainTrees || tree.rootInfos.size > 1 }
                .filter { treeFilter?.match(it.treeId) != false }
                .map {
                    it.forPostanalysis(reader.fileNames, reader.library)
                }
                .filter { treeFilter?.match(it) != false }
                .flatMap { forPostanalysis ->
                    val chains = forPostanalysis.splitToChains()
                    val subtreeIds = chains.groupBy { it.meta.chains }.mapValues { (_, subtrees) ->
                        val sortedSubtrees = subtrees.sortedByDescending { subtree ->
                            when (sortChainsBy) {
                                SortChainsBy.Clones -> subtree.clonesWithChain.size.toDouble()
                                SortChainsBy.Read -> subtree.clonesWithChain.sumOf { it.clone.count }
                                SortChainsBy.Molecule -> subtree.clonesWithChain.sumOf { clone ->
                                    val tagsInfo = headerForExport.allTagsInfo[clone.datasetId]
                                    val tag = tagsInfo.allTagsOfType(TagType.Molecule)
                                        .maxByOrNull { it.index } ?: return@sumOf 0
                                    clone.clone.getTagDiversity(tag)
                                }.toDouble()
                            }
                        }
                        Wrapper.SubtreeIdPair(
                            sortedSubtrees.first().subtreeId,
                            sortedSubtrees.getOrNull(1)?.subtreeId
                        )
                    }
                    val treeData = chains.first().treeData
                    val metaPerSubtree = chains.map { it.meta }
                    val clonesPerSubtree = chains.map { it.clonesWithChain }
                    forPostanalysis.tree.allNodes()
                        .filter { !onlyObserved || it.node.content.hasClones() }
                        .map { node ->
                            Wrapper(
                                subtreeIds,
                                treeData,
                                metaPerSubtree,
                                clonesPerSubtree,
                                node.node.content
                            )
                        }
                }
                .forEach { writer.put(it) }
        }
    }

    companion object {
        const val COMMAND_NAME = SHMSingleCellTreeNodeFieldsExtractorsFactory.COMMAND_NAME

        fun mkSpec(): Model.CommandSpec {
            val cmd = CommandExportShmSingleCellTreesTable()
            val spec = Model.CommandSpec.forAnnotatedObject(cmd)
            cmd.spec = spec // inject spec manually
            SHMSingleCellTreeNodeFieldsExtractorsFactory.forAllChains.addOptionsToSpec(cmd.addedFields, spec)
            return spec
        }
    }
}
