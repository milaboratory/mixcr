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

import cc.redberry.pipe.util.asOutputPort
import cc.redberry.pipe.util.asSequence
import cc.redberry.pipe.util.filter
import cc.redberry.pipe.util.flatMap
import cc.redberry.pipe.util.map
import cc.redberry.pipe.util.toList
import com.milaboratory.app.InputFileType
import com.milaboratory.app.ValidationException
import com.milaboratory.app.logger
import com.milaboratory.mixcr.basictypes.CloneSet.Companion.divideClonesByTags
import com.milaboratory.mixcr.basictypes.VDJCSProperties
import com.milaboratory.mixcr.basictypes.tag.TagType
import com.milaboratory.mixcr.basictypes.tag.TagsInfo
import com.milaboratory.mixcr.cli.CommonDescriptions.Labels
import com.milaboratory.mixcr.export.ExportFieldDescription
import com.milaboratory.mixcr.export.InfoWriter
import com.milaboratory.mixcr.export.MetaForExport
import com.milaboratory.mixcr.export.RowMetaForExport
import com.milaboratory.mixcr.export.SplittedTreeNodeFieldsExtractorsFactory
import com.milaboratory.mixcr.export.SplittedTreeNodeFieldsExtractorsFactory.Wrapper
import com.milaboratory.mixcr.trees.SHMTreesReader
import com.milaboratory.mixcr.trees.constructStateBuilder
import com.milaboratory.mixcr.trees.forPostanalysis
import com.milaboratory.util.ComparatorWithHash
import com.milaboratory.util.TempFileDest
import com.milaboratory.util.TempFileManager
import com.milaboratory.util.groupByOnDisk
import gnu.trove.set.hash.TIntHashSet
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path
import kotlin.io.path.createDirectories

@Command(
    description = ["Export SHMTree as a table with a row for every node"]
)
class CommandExportShmTreesTableWithNodes : CommandExportShmTreesAbstract() {
    @set:Parameters(
        index = "1",
        arity = "0..1",
        paramLabel = "trees.tsv",
        description = ["Path where to write output export table. Print in stdout if omitted."]
    )
    var out: Path? = null
        set(value) {
            ValidationException.requireFileType(value, InputFileType.TSV)
            field = value
        }

    @Option(
        description = ["Don't print first header line, print only data"],
        names = ["--no-header"],
        order = OptionsOrder.exportOptions
    )
    var noHeader = false

    val addedFields: MutableList<ExportFieldDescription> = mutableListOf()

    @Option(
        description = ["Exclude nodes that was reconstructed by algorithm"],
        names = ["--only-observed"],
        order = OptionsOrder.main + 10_100
    )
    var onlyObserved: Boolean = false

    @Suppress("unused")
    @Option(
        names = ["--onlyObserved"],
        hidden = true
    )
    fun setOnlyObservedDeprecated(value: Boolean) {
        logger.warn("--onlyObserved is deprecated, use --only-observed instead")
        onlyObserved = value
    }

    @Option(
        description = ["Split clones by tag type. Will be calculated from export columns if not specified."],
        names = ["--split-by-tags"],
        paramLabel = Labels.TAG_TYPE,
        order = OptionsOrder.main + 10_200
    )
    private var splitByTagType: TagType? = null

    @Option(
        description = ["Export not covered regions as empty text."],
        names = ["--not-covered-as-empty"],
        arity = "0",
        order = OptionsOrder.exportOptions + 400
    )
    var notCoveredAsEmpty: Boolean = false

    @Mixin
    lateinit var useLocalTemp: UseLocalTempOption

    private val tempDest: TempFileDest by lazy {
        ValidationException.requireNotNull(out) {
            "With --use-local-temp explicit output path is required"
        }
        TempFileManager.smartTempDestination(out, ".build_trees", !useLocalTemp.value)
    }

    override val outputFiles
        get() = listOfNotNull(out)

    override fun run1() {
        out?.toAbsolutePath()?.parent?.createDirectories()
        SHMTreesReader(input, VDJCLibraryRegistry.getDefault()).use { reader ->
            val headerForExport = MetaForExport(
                reader.cloneSetInfos.map { it.tagsInfo },
                reader.header.allFullyCoveredBy,
                reader.footer.reports
            )

            val splitByTagType = if (splitByTagType != null) {
                splitByTagType
            } else {
                val tagsExportedByGroups = addedFields
                    .filter {
                        it.field.equals("-allTags", ignoreCase = true) ||
                                it.field.equals("-tags", ignoreCase = true)
                    }
                    .map { TagType.valueOfCaseInsensitiveOrNull(it.args[0])!! }
                val newSpitBy = tagsExportedByGroups.maxOrNull()
                if (newSpitBy != null && out != null) {
                    println("Clone splitting by ${newSpitBy.name} added automatically because -tags ${newSpitBy.name} field is present in the list.")
                }
                newSpitBy
            }
            if (splitByTagType != null && headerForExport.allTagsInfo.none { it.hasTagsWithType(splitByTagType) }) {
                logger.warn("Input has no tags with type $splitByTagType")
            }
            val splitByTags = reader.cloneSetInfos
                .map { it.tagsInfo }
                .map { tagsInfo ->
                    when {
                        splitByTagType == null -> null
                        !tagsInfo.hasTagsWithType(splitByTagType) -> null
                        else -> tagsInfo.getDepthFor(splitByTagType)
                    }
                }

            val datasetsToSplit = TIntHashSet(splitByTags.withIndex().filter { it.value != null }.map { it.index })

            val splitClones = if (!datasetsToSplit.isEmpty) {
                reader.readTrees()
                    .flatMap { result ->
                        result.tree.allNodes()
                            .flatMap { it.node.content.clones }
                            .iterator().asOutputPort()
                    }
                    .filter { it.datasetId in datasetsToSplit }
                    .groupByOnDisk(
                        comparator = ComparatorWithHash.compareBy { it.datasetId },
                        tempDest,
                        suffixForTempDest = "sort_for_divide_by_tags",
                        stateBuilder = reader.alignerParameters.constructStateBuilder(reader.usedGenes)
                    )
                    .asSequence()
                    .associate { group ->
                        val datasetId = group.key
                        val depth = splitByTags[datasetId]!!
                        datasetId to reader.cloneSetInfos[datasetId]
                            .divideClonesByTags(
                                group.map { it.clone }.toList(),
                                depth,
                                reader.usedGenes,
                                VDJCSProperties.CO_BY_COUNT
                            )
                            .clones.groupBy { it.id }
                    }
            } else {
                emptyMap()
            }


            InfoWriter.create(
                out,
                SplittedTreeNodeFieldsExtractorsFactory.createExtractors(addedFields, headerForExport),
                !noHeader,
            ) { (_, node) ->
                val tagsInfo = node.clone?.datasetId?.let { reader.cloneSetInfos[it].tagsInfo } ?: TagsInfo.NO_TAGS
                RowMetaForExport(tagsInfo, headerForExport, notCoveredAsEmpty)
            }.use { output ->
                reader.readTrees()
                    .asSequence()
                    .filter { treeFilter?.match(it.treeId) != false }
                    .map {
                        it.forPostanalysis(reader.fileNames, reader.libraryRegistry)
                    }
                    .filter { treeFilter?.match(it) != false }
                    .flatMap { shmTreeForPostanalysis ->
                        shmTreeForPostanalysis.tree.allNodes()
                            .asSequence()
                            .filter { !onlyObserved || it.node.content.clones.isNotEmpty() }
                            .flatMap { it.node.content.split(splitClones) }
                            .map { node -> Wrapper(shmTreeForPostanalysis, node) }
                    }
                    .forEach {
                        output.put(it)
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
