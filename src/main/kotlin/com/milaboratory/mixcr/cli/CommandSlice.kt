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

import cc.redberry.pipe.OutputPort
import com.milaboratory.mitool.exhaustive
import com.milaboratory.mitool.helpers.filter
import com.milaboratory.mitool.helpers.it
import com.milaboratory.mitool.helpers.map
import com.milaboratory.mixcr.basictypes.*
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.*
import com.milaboratory.mixcr.trees.SHMTreesReader
import com.milaboratory.mixcr.trees.SHMTreesWriter
import com.milaboratory.mixcr.util.Concurrency
import com.milaboratory.primitivio.flatten
import com.milaboratory.primitivio.forEach
import com.milaboratory.util.TempFileManager
import gnu.trove.map.hash.TIntIntHashMap
import gnu.trove.set.hash.TLongHashSet
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine.*
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.path.readLines

@Command(
    name = CommandSlice.SLICE_COMMAND_NAME,
    sortOptions = true,
    separator = " ",
    description = ["Slice vdjca|clns|clna|shmt file."]
)
class CommandSlice : AbstractMiXCRCommand() {
    @Parameters(description = ["data.[vdjca|clns|clna|shmt]"], index = "0")
    lateinit var `in`: String

    @Parameters(description = ["data_sliced"], index = "1")
    lateinit var out: String

    @ArgGroup(exclusive = true, multiplicity = "1")
    lateinit var idsOptions: IdsFilterOptions

    class IdsFilterOptions {
        @Option(
            description = ["List of read (for .vdjca) / clone (for .clns/.clna) / tree (for .shmt) ids to export."],
            names = ["-i", "--id"],
            required = true
        )
        var ids: List<Long>? = null

        @Option(
            description = [
                "File with list of read (for .vdjca) / clone (for .clns/.clna) / tree (for .shmt) ids to export.",
                "Every id on separate line"
            ],
            names = ["--ids-file"],
            required = true
        )
        var fileWithIds: Path? = null
    }

    private val ids: List<Long> by lazy {
        val result = idsOptions.ids ?: idsOptions.fileWithIds!!.readLines().map { it.toLong() }
        result.sorted()
    }

    override fun getInputFiles(): List<String> = listOf(`in`)

    override fun getOutputFiles(): List<String> = listOf(out)

    override fun run0() {
        when (IOUtil.extractFileType(Paths.get(`in`))) {
            VDJCA -> sliceVDJCA()
            CLNS -> sliceClns()
            CLNA -> sliceClnA()
            SHMT -> sliceShmt()
        }.exhaustive
    }

    private fun sliceVDJCA() {
        val set = TLongHashSet(ids)
        VDJCAlignmentsReader(`in`).use { reader ->
            VDJCAlignmentsWriter(out).use { writer ->
                writer.inheritHeaderAndFooterFrom(reader)
                for (alignments in reader.it) {
                    if (set.removeAll(alignments.readIds)) writer.write(alignments)
                    if (set.isEmpty) break
                }
                writer.setFooter(reader.footer)
            }
        }
    }

    private fun sliceClnA() {
        ClnAReader(`in`, VDJCLibraryRegistry.getDefault(), Concurrency.noMoreThan(4)).use { reader ->
            ClnAWriter(out, TempFileManager.smartTempDestination(out, "", false)).use { writer ->
                // Getting full clone set
                val cloneSet = reader.readCloneSet()

                // old clone id -> new clone id
                val idMapping = TIntIntHashMap()
                var newNumberOfAlignments: Long = 0

                // Creating new cloneset
                val clones = mutableListOf<Clone>()
                val allAlignmentsList = mutableListOf<OutputPort<VDJCAlignments>>()
                for ((i, cloneId_) in ids.withIndex()) {
                    val cloneId = cloneId_.toInt()
                    newNumberOfAlignments += reader.numberOfAlignmentsInClone(cloneId)
                    val clone = cloneSet[cloneId]
                    idMapping.put(clone.id, i)
                    clones.add(clone.setId(i).resetParentCloneSet())
                    allAlignmentsList += reader
                        .readAlignmentsOfClone(cloneId)
                        .map { it.withCloneIndex(i.toLong()) }
                }
                val newCloneSet =
                    CloneSet(clones, cloneSet.usedGenes, cloneSet.header, cloneSet.footer, cloneSet.ordering)
                val idGen = AtomicLong()
                val allAlignmentsPort = allAlignmentsList
                    .flatten()
                    .map { it.setAlignmentsIndex(idGen.getAndIncrement()) }
                writer.writeClones(newCloneSet)
                writer.collateAlignments(allAlignmentsPort, newNumberOfAlignments)
                writer.setFooter(reader.footer)
                writer.writeAlignmentsAndIndex()
            }
        }
    }

    private fun sliceClns() {
        ClnsReader(`in`, VDJCLibraryRegistry.getDefault()).use { reader ->
            ClnsWriter(out).use { writer ->
                // Getting full clone set
                val cloneSet = reader.cloneSet

                // Creating new cloneset
                val clones = mutableListOf<Clone>()
                for ((i, cloneId_) in ids.withIndex()) {
                    val cloneId = cloneId_.toInt()
                    val clone = cloneSet[cloneId]
                    clones.add(clone.setId(i).resetParentCloneSet())
                }
                val newCloneSet =
                    CloneSet(clones, cloneSet.usedGenes, cloneSet.header, cloneSet.footer, cloneSet.ordering)
                writer.writeCloneSet(newCloneSet)
            }
        }
    }

    private fun sliceShmt() {
        SHMTreesReader(Paths.get(`in`), VDJCLibraryRegistry.getDefault()).use { reader ->
            SHMTreesWriter(out).use { writer ->
                writer.writeHeader(reader.originHeaders, reader.header, reader.fileNames, reader.userGenes)

                val treesWriter = writer.treesWriter()
                reader.readTrees()
                    .filter { it.treeId.toLong() in ids }
                    .forEach { treesWriter.put(it) }

                writer.setFooter(reader.footer)
            }
        }
    }

    companion object {
        const val SLICE_COMMAND_NAME = "slice"
    }
}
