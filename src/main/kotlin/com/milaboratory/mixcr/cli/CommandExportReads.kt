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

import cc.redberry.pipe.util.forEach
import com.milaboratory.app.ApplicationException
import com.milaboratory.app.InputFileType
import com.milaboratory.app.ValidationException
import com.milaboratory.app.logger
import com.milaboratory.core.io.sequence.PairedRead
import com.milaboratory.core.io.sequence.SequenceWriter
import com.milaboratory.core.io.sequence.SingleRead
import com.milaboratory.core.io.sequence.fastq.PairedFastqWriter
import com.milaboratory.core.io.sequence.fastq.SingleFastqWriter
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import java.nio.file.Path

@Command(
    description = ["Export original reads from vdjca file."]
)
class CommandExportReads : MiXCRCommandWithOutputs() {
    @Parameters(
        description = ["Path to input file with alignments"],
        index = "0",
        paramLabel = "input.vdjca",
        arity = "1"
    )
    lateinit var input: Path

    @Parameters(
        description = [
            "Path where to write reads from input alignments.",
            "Will write to output if omitted.",
            "Will write to single file if specified only one output file.",
            "Will write paired reads to two files if specified two output files.",
        ],
        index = "1",
        paramLabel = "[output_R1.fastq[.gz] [output_R2.fastq[.gz]]]",
        hideParamSyntax = true,
        arity = "0..2"
    )
    public override val outputFiles: List<Path> = mutableListOf()

    override val inputFiles
        get() = listOf(input)

    override fun validate() {
        ValidationException.requireFileType(input, InputFileType.VDJCA)
        outputFiles.forEach { output ->
            ValidationException.requireFileType(output, InputFileType.FASTQ)
        }
    }

    override fun initialize() {
        if (outputFiles.isEmpty())
            logger.redirectSysOutToSysErr()
    }

    override fun run1() {
        VDJCAlignmentsReader(inputFiles.first()).use { reader ->
            createWriter().use { writer ->
                reader
                    .reportProgress("Extracting reads")
                    .forEach { alignments ->
                        val reads = alignments.getOriginalReads()
                            ?: throw ApplicationException("VDJCA file doesn't contain original reads (perform align action with -g / --save-reads option).")
                        for (read in reads) {
                            when (writer) {
                                is PairedFastqWriter -> {
                                    if (read.numberOfReads() == 1)
                                        throw ValidationException("VDJCA file contains single-end reads, but two output files are specified.")
                                    writer.write(read as PairedRead)
                                }

                                is SingleFastqWriter -> {
                                    if (read.numberOfReads() == 2)
                                        throw ValidationException("VDJCA file contains paired-end reads, but only one / no output file is specified.")
                                    writer.write(read as SingleRead)
                                }
                            }
                        }
                    }
            }
        }
    }

    private fun createWriter(): SequenceWriter<*> {
        val outputFiles = outputFiles
        return when (outputFiles.size) {
            0 -> SingleFastqWriter(System.out)
            1 -> SingleFastqWriter(outputFiles[0].toFile())
            2 -> PairedFastqWriter(outputFiles[0].toFile(), outputFiles[1].toFile())
            else -> throw IllegalArgumentException()
        }
    }
}
