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

import cc.redberry.pipe.OutputPort
import cc.redberry.pipe.util.filter
import cc.redberry.pipe.util.flatten
import cc.redberry.pipe.util.forEach
import cc.redberry.pipe.util.it
import cc.redberry.pipe.util.map
import cc.redberry.pipe.util.use
import com.milaboratory.app.InputFileType
import com.milaboratory.app.ValidationException
import com.milaboratory.mixcr.basictypes.ClnAReader
import com.milaboratory.mixcr.basictypes.ClnAWriter
import com.milaboratory.mixcr.basictypes.ClnsReader
import com.milaboratory.mixcr.basictypes.ClnsWriter
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.CloneSet
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.CLNA
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.CLNS
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.SHMT
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.VDJCA
import com.milaboratory.mixcr.basictypes.VDJCAlignments
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter
import com.milaboratory.mixcr.trees.SHMTreesReader
import com.milaboratory.mixcr.trees.SHMTreesWriter
import com.milaboratory.mixcr.util.Concurrency
import com.milaboratory.util.TempFileManager
import com.milaboratory.util.exhaustive
import gnu.trove.map.hash.TIntIntHashMap
import gnu.trove.set.hash.TLongHashSet
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine.ArgGroup
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.path.readLines

@Command(
    name = CommandSlice.SLICE_COMMAND_NAME,
    description = ["Slice vdjca|clns|clna|shmt file."]
)
class CommandSlice : MiXCRCommandWithOutputs() {
    @Parameters(
        description = ["Input data to filter by ids"],
        paramLabel = "data.(vdjca|clns|clna|shmt)",
        index = "0"
    )
    lateinit var input: Path

    @Parameters(
        description = ["Output file with filtered data"],
        paramLabel = "data_sliced.(vdjca|clns|clna|shmt)",
        index = "1"
    )
    lateinit var out: Path

    @ArgGroup(
        exclusive = true,
        multiplicity = "1",
        order = OptionsOrder.main + 10_100
    )
    lateinit var idsOptions: IdsFilterOptions

    @Mixin
    lateinit var useLocalTemp: UseLocalTempOption

    class IdsFilterOptions {
        @Option(
            description = ["List of read (for .vdjca) / clone (for .clns/.clna) / tree (for .shmt) ids to export."],
            names = ["-i", "--id"],
            required = true,
            paramLabel = "<id>",
            order = 1
        )
        var ids: List<Long>? = null

        @Option(
            description = [
                "File with list of read (for .vdjca) / clone (for .clns/.clna) / tree (for .shmt) ids to export.",
                "Every id on separate line"
            ],
            names = ["--ids-file"],
            required = true,
            paramLabel = "<path>",
            order = 2
        )
        var fileWithIds: Path? = null
    }

    private val ids: List<Long> by lazy {
        val result = idsOptions.ids ?: idsOptions.fileWithIds!!.readLines().map { it.toLong() }
        result.sorted()
    }

    override val inputFiles
        get() = listOf(input) + listOfNotNull(idsOptions.fileWithIds)

    override val outputFiles
        get() = listOf(out)

    override fun validate() {
        ValidationException.requireTheSameFileType(
            input,
            out,
            InputFileType.VDJCA,
            InputFileType.CLNS,
            InputFileType.CLNA,
            InputFileType.SHMT
        )
    }

    override fun run1() {
        when (IOUtil.extractFileType(input)) {
            VDJCA -> sliceVDJCA()
            CLNS -> sliceClns()
            CLNA -> sliceClnA()
            SHMT -> sliceShmt()
        }.exhaustive
    }

    private fun sliceVDJCA() {
        val set = TLongHashSet(ids)
        VDJCAlignmentsReader(input).use { reader ->
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
        ClnAReader(input, VDJCLibraryRegistry.getDefault(), Concurrency.noMoreThan(4)).use { reader ->
            ClnAWriter(out.toFile(), TempFileManager.smartTempDestination(out, "", !useLocalTemp.value)).use { writer ->
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
                    clones.add(clone.withId(i).withTotalCountsReset())
                    allAlignmentsList += reader
                        .readAlignmentsOfClone(cloneId)
                        .map { it.withCloneIndex(i.toLong()) }
                }
                val newCloneSet = CloneSet.Builder(clones, cloneSet.usedGenes, cloneSet.header)
                    .sort(cloneSet.ordering)
                    .recalculateRanks()
                    .calculateTotalCounts()
                    .build()
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
        ClnsReader(input, VDJCLibraryRegistry.getDefault()).use { reader ->
            ClnsWriter(out.toFile()).use { writer ->
                // Getting full clone set
                val cloneSet = reader.readCloneSet()

                // Creating new cloneset
                val clones = mutableListOf<Clone>()
                for ((i, cloneId_) in ids.withIndex()) {
                    val cloneId = cloneId_.toInt()
                    val clone = cloneSet[cloneId]
                    clones.add(clone.withId(i).withTotalCountsReset())
                }
                val newCloneSet = CloneSet.Builder(clones, cloneSet.usedGenes, cloneSet.header)
                    .sort(cloneSet.ordering)
                    .recalculateRanks()
                    .calculateTotalCounts()
                    .build()
                writer.writeCloneSet(newCloneSet)
                writer.setFooter(reader.footer)
            }
        }
    }

    private fun sliceShmt() {
        SHMTreesReader(input, VDJCLibraryRegistry.getDefault()).use { reader ->
            SHMTreesWriter(out).use { writer ->
                writer.copyHeaderFrom(reader)

                writer.treesWriter().use { treesWriter ->
                    reader.readTrees()
                        .filter { it.treeId.toLong() in ids }
                        .forEach { treesWriter.put(it) }
                }

                writer.setFooter(reader.footer)
            }
        }
    }

    companion object {
        const val SLICE_COMMAND_NAME = "slice"
    }
}
