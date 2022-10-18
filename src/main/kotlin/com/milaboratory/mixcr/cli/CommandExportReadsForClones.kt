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

import cc.redberry.pipe.CUtils
import com.milaboratory.core.io.sequence.PairedRead
import com.milaboratory.core.io.sequence.SequenceWriter
import com.milaboratory.core.io.sequence.SingleRead
import com.milaboratory.core.io.sequence.fasta.FastaSequenceWriterWrapper
import com.milaboratory.core.io.sequence.fastq.PairedFastqWriter
import com.milaboratory.core.io.sequence.fastq.SingleFastqWriter
import com.milaboratory.mixcr.basictypes.ClnAReader
import com.milaboratory.mixcr.basictypes.VDJCAlignments
import com.milaboratory.mixcr.util.Concurrency
import com.milaboratory.util.CanReportProgress
import com.milaboratory.util.SmartProgressReporter
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.stream.IntStream

@Command(
    description = [
        "Export reads for particular clones from \"clones & alignments\" (*.clna) file.",
        "Note that such export is possible only from `.clna` files, produced by MiXCR `assemble` command with option `--write-alignments`."
    ]
)
class CommandExportReadsForClones : MiXCRCommandWithOutputs() {
    @Parameters(
        description = ["Path to input file"],
        index = "0",
        paramLabel = "input.clna"
    )
    lateinit var input: Path

    @Parameters(
        description = ["Output file name will be transformed into `_R1`/`_R2` pair in case of paired end reads."],
        index = "1",
        paramLabel = "output.(fastq[.gz]|fasta)"
    )
    lateinit var out: Path

    @Option(
        description = [
            "Clone ids to export.",
            "If no clone ids are specified all reads assigned to clonotypes will be exported.",
            "Use --id -1 to export alignments not assigned to any clone (not assembled)."
        ],
        names = ["--id"],
        paramLabel = "<id>",
        arity = "0..*"
    )
    var cloneIds: List<Int> = mutableListOf()

    public override val inputFiles
        get() = listOf(input)

    override val outputFiles
        get() = listOf(out)

    @Option(
        description = ["Create separate files for each clone. File or pair of `_R1`/`_R2` files, with `_clnN` suffix, " +
                "where N is clone index, will be created for each clone index."],
        names = ["-s", "--separate"]
    )
    var separate = false

    override fun run0() {
        ClnAReader(input, VDJCLibraryRegistry.getDefault(), Concurrency.noMoreThan(4)).use { clna ->
            val firstAlignment: VDJCAlignments = clna.readAllAlignments()
                .use { dummyP -> dummyP.take() } ?: return
            ValidationException.requireNotNull(firstAlignment.originalReads) {
                "Error: original reads were not saved in original .vdjca file: " +
                        "re-run 'align' with '-OsaveOriginalReads=true' option."
            }
            val cloneIds: () -> IntStream = {
                when {
                    cloneIds.isEmpty() -> IntStream.range(0, clna.numberOfClones())
                    else -> cloneIds.stream().mapToInt { it }
                }
            }
            val totalAlignments = cloneIds()
                .mapToLong { cloneIndex -> clna.numberOfAlignmentsInClone(cloneIndex) }
                .sum()
            val alignmentsWritten = AtomicLong()
            val finished = AtomicBoolean(false)
            SmartProgressReporter.startProgressReport("Writing reads", object : CanReportProgress {
                override fun getProgress(): Double {
                    return 1.0 * alignmentsWritten.get() / totalAlignments
                }

                override fun isFinished(): Boolean {
                    return finished.get()
                }
            })
            val paired = firstAlignment.originalReads[0].numberOfReads() == 2
            when {
                separate -> null
                else -> createWriter(paired, out.toString())
            }.use { globalWriter ->
                cloneIds().forEach { cloneId ->
                    when (globalWriter) {
                        null -> createWriter(paired, cloneFile(out.toString(), cloneId))
                        else -> null
                    }.use { individualWriter ->
                        val actualWriter = globalWriter ?: individualWriter!!
                        for (alignments in CUtils.it(clna.readAlignmentsOfClone(cloneId))) {
                            for (read in alignments.originalReads) {
                                when (actualWriter) {
                                    is PairedFastqWriter -> actualWriter.write(read as PairedRead)
                                    is SingleFastqWriter -> actualWriter.write(read as SingleRead)
                                }
                            }
                            alignmentsWritten.incrementAndGet()
                        }
                    }
                }
            }
        }
    }

    companion object {
        private fun cloneFile(fileName: String, id: Int): String = when {
            fileName.contains(".fast") -> fileName.replace(".fast", "_cln$id.fast")
            else -> fileName + id
        }

        private fun createWriter(paired: Boolean, fileName: String): SequenceWriter<*> {
            val split = fileName.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val ext = when {
                split.last() == "gz" -> split[split.size - 2]
                else -> split.last()
            }
            return when (ext) {
                "fasta" -> {
                    require(!paired) { "Fasta does not support paired reads." }
                    FastaSequenceWriterWrapper(fileName)
                }
                "fastq" -> when {
                    paired -> PairedFastqWriter(
                        fileName.replace(".fastq", "_R1.fastq"),
                        fileName.replace(".fastq", "_R2.fastq")
                    )
                    else -> SingleFastqWriter(fileName)
                }
                else -> when {
                    paired -> PairedFastqWriter(
                        fileName + "_R1.fastq.gz",
                        fileName + "_R2.fastq.gz"
                    )
                    else -> SingleFastqWriter("$fileName.fastq.gz")
                }
            }
        }
    }
}
