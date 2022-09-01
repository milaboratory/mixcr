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
import com.milaboratory.mitool.helpers.it
import com.milaboratory.mitool.helpers.map
import com.milaboratory.mixcr.basictypes.ClnAReader
import com.milaboratory.mixcr.basictypes.ClnAWriter
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.CloneSet
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.CLNA
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.CLNS
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.VDJCA
import com.milaboratory.mixcr.basictypes.VDJCAlignments
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter
import com.milaboratory.mixcr.util.Concurrency
import com.milaboratory.primitivio.flatten
import com.milaboratory.util.TempFileManager
import gnu.trove.map.hash.TIntIntHashMap
import gnu.trove.set.hash.TLongHashSet
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicLong

@CommandLine.Command(
    name = CommandSlice.SLICE_COMMAND_NAME,
    sortOptions = true,
    separator = " ",
    description = ["Slice ClnA file."]
)
class CommandSlice : MiXCRCommand() {
    @CommandLine.Parameters(description = ["data.[vdjca|clns|clna]"], index = "0")
    lateinit var `in`: String

    @CommandLine.Parameters(description = ["data_sliced"], index = "1")
    lateinit var out: String

    @CommandLine.Option(
        description = ["List of read (for .vdjca) / clone (for .clns/.clna) ids to export."],
        names = ["-i", "--id"]
    )
    var ids: List<Long> = mutableListOf()

    override fun getInputFiles(): List<String> = listOf(`in`)

    override fun getOutputFiles(): List<String> = listOf(out)

    override fun run0() {
        ids = ids.sorted()
        when (IOUtil.extractFileType(Paths.get(`in`))!!) {
            VDJCA -> sliceVDJCA()
            CLNS -> throwValidationException("Operation is not yet supported for Clns files.")
            CLNA -> sliceClnA()
        }
    }

    private fun sliceVDJCA() {
        val set = TLongHashSet(ids)
        VDJCAlignmentsReader(`in`).use { reader ->
            VDJCAlignmentsWriter(out).use { writer ->
                writer.header(reader)
                for (alignments in reader.it) {
                    if (set.removeAll(alignments.readIds)) writer.write(alignments)
                    if (set.isEmpty) break
                }
                writer.writeFooter(reader.reports(), null)
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
                val newCloneSet = CloneSet(clones, cloneSet.usedGenes, cloneSet.info, cloneSet.ordering)
                val idGen = AtomicLong()
                val allAlignmentsPort = allAlignmentsList
                    .flatten()
                    .map { it.setAlignmentsIndex(idGen.getAndIncrement()) }
                writer.writeClones(newCloneSet)
                writer.collateAlignments(allAlignmentsPort, newNumberOfAlignments)
                writer.writeFooter(reader.reports(), null)
                writer.writeAlignmentsAndIndex()
            }
        }
    }

    companion object {
        const val SLICE_COMMAND_NAME = "slice"
    }
}
