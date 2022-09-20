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
import com.milaboratory.mixcr.basictypes.VDJCAlignments
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter
import com.milaboratory.primitivio.forEach
import com.milaboratory.util.CanReportProgress
import com.milaboratory.util.SmartProgressReporter
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@CommandLine.Command(
    name = CommandMergeAlignments.MERGE_ALIGNMENTS_COMMAND_NAME,
    sortOptions = true,
    separator = " ",
    description = ["Merge several *.vdjca files with alignments into a single alignments file."]
)
class CommandMergeAlignments : AbstractMiXCRCommand() {
    @CommandLine.Parameters(
        description = ["[input_file1.vdjca [input_file2.vdjca ....]] output_file.vdjca"],
        arity = "2..*"
    )
    var input: List<String> = mutableListOf()

    public override fun getInputFiles(): List<String> = input.subList(0, input.size - 1)

    override fun getOutputFiles(): List<String> = listOf(input.last())

    override fun run0() {
        MultiReader(inputFiles).use { reader ->
            VDJCAlignmentsWriter(outputFiles[0]).use { writer ->
                SmartProgressReporter.startProgressReport("Merging", reader)
                // FIXME shouldn't be something changed in the header ?
                writer.inheritHeaderAndFooterFrom(reader.currentInnerReader)
                reader.forEach { record ->
                    writer.write(record)
                }
                writer.setNumberOfProcessedReads(reader.readIdOffset.get())
            }
        }
    }

    // Not thread-safe !
    private class MultiReader(val files: List<String>) : OutputPort<VDJCAlignments>, CanReportProgress, AutoCloseable {
        init {
            require(files.isNotEmpty())
        }

        private val registry: VDJCLibraryRegistry = VDJCLibraryRegistry.getDefault()
        private val fileId = AtomicInteger(0)
        private val recordId = AtomicLong()
        val readIdOffset = AtomicLong()

        @Volatile
        private var progress = 0.0
        var currentInnerReader: VDJCAlignmentsReader = createNewReader()!!

        private fun updateProgress() {
            var p = 1.0 * (fileId.get() - 1) / files.size
            p += currentInnerReader.progress / files.size
            progress = p
        }

        override fun getProgress(): Double = progress

        override fun isFinished(): Boolean = fileId.get() > files.size

        private fun createNewReader(): VDJCAlignmentsReader? {
            val idToCreate = fileId.getAndIncrement()
            return when {
                idToCreate >= files.size -> null
                else -> VDJCAlignmentsReader(
                    files[idToCreate],
                    registry
                )
            }
        }

        override fun take(): VDJCAlignments? {
            var record = currentInnerReader.take()
            if (record == null) {
                readIdOffset.addAndGet(currentInnerReader.numberOfReads)
                currentInnerReader.close()
                val newReader = createNewReader() ?: return null
                currentInnerReader = newReader
                record = currentInnerReader.take()
            }
            updateProgress()
            return record.shiftReadId(recordId.incrementAndGet(), readIdOffset.get())
        }

        override fun close() {
            currentInnerReader.close()
        }
    }

    companion object {
        const val MERGE_ALIGNMENTS_COMMAND_NAME = "mergeAlignments"
    }
}
