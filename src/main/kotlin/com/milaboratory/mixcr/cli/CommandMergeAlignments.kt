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
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Parameters
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@Command(
    description = ["Merge several *.vdjca files with alignments into a single alignments file."]
)
class CommandMergeAlignments : MiXCRCommandWithOutputs() {
    companion object {
        private const val inputsLabel = "input.vdjca..."

        private const val outputLabel = "output.vdjca"

        fun mkCommandSpec(): CommandSpec = CommandSpec.forAnnotatedObject(CommandMergeAlignments::class.java)
            .addPositional(
                CommandLine.Model.PositionalParamSpec.builder()
                    .index("0")
                    .required(false)
                    .arity("0..*")
                    .type(Path::class.java)
                    .paramLabel(inputsLabel)
                    .hideParamSyntax(true)
                    .description("Paths to input files with alignments")
                    .build()
            )
            .addPositional(
                CommandLine.Model.PositionalParamSpec.builder()
                    .index("1")
                    .required(false)
                    .arity("0..*")
                    .type(Path::class.java)
                    .paramLabel(outputLabel)
                    .hideParamSyntax(true)
                    .description("Path where to write merged alignments")
                    .build()
            )
    }

    @Parameters(
        index = "0",
        arity = "2..*",
        paramLabel = "$inputsLabel $outputLabel",
        hideParamSyntax = true,
        //help is covered by mkCommandSpec
        hidden = true
    )
    var inOut: List<Path> = mutableListOf()

    private val output: Path get() = inOut.last()

    public override val inputFiles
        get() = inOut.dropLast(1)

    override val outputFiles
        get() = listOf(output)

    override fun validate() {
        inputFiles.forEach { input ->
            ValidationException.requireFileType(input, InputFileType.VDJCA)
        }
        ValidationException.requireFileType(output, InputFileType.VDJCA)
    }

    override fun run0() {
        MultiReader(inputFiles).use { reader ->
            VDJCAlignmentsWriter(output).use { writer ->
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
    private class MultiReader(val files: List<Path>) : OutputPort<VDJCAlignments>, CanReportProgress, AutoCloseable {
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
}
