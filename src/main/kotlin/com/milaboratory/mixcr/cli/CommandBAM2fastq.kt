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

import com.milaboratory.core.io.sequence.PairedRead
import com.milaboratory.core.io.sequence.SingleRead
import com.milaboratory.core.io.sequence.fastq.PairedFastqWriter
import com.milaboratory.core.io.sequence.fastq.SingleFastqWriter
import com.milaboratory.mixcr.bam.BAMReader
import com.milaboratory.primitivio.forEach
import picocli.CommandLine

@CommandLine.Command(
    name = "BAM2fastq",
    hidden = true,
    description = ["Converts BAM/SAM file to paired/unpaired fastq files"]
)
class CommandBAM2fastq : AbstractMiXCRCommand() {
    @CommandLine.Option(names = ["-b", "--bam"], description = ["BAM files for conversion."], required = true)
    lateinit var bamFiles: Array<String>

    @CommandLine.Option(names = ["-r1"], description = ["File for first reads."], required = true)
    lateinit var fastq1: String

    @CommandLine.Option(names = ["-r2"], description = ["File for second reads."], required = true)
    lateinit var fastq2: String

    @CommandLine.Option(names = ["-u"], description = ["File for unpaired reads."], required = true)
    lateinit var fastqUnpaired: String

    @CommandLine.Option(
        names = ["--drop-non-vdj"],
        description = ["Drop reads from bam file mapped on human chromosomes except with VDJ region (2, 7, 14, 22)"]
    )
    var dropNonVDJ = false

    @CommandLine.Option(names = ["--keep-wildcards"], description = ["Keep sequences with wildcards in the output"])
    var keepWildcards = false

    override fun getInputFiles(): List<String> = bamFiles.toList()

    override fun getOutputFiles(): List<String> = listOf(fastq1, fastq2, fastqUnpaired)

    override fun run0() {
        BAMReader(bamFiles, dropNonVDJ, !keepWildcards).use { converter ->
            PairedFastqWriter(fastq1, fastq2).use { wr ->
                SingleFastqWriter(fastqUnpaired).use { swr ->
                    converter.forEach { read ->
                        when (read) {
                            is PairedRead -> wr.write(read)
                            is SingleRead -> swr.write(read)
                            else -> throw IllegalArgumentException()
                        }
                    }
                }
            }
            println("Your fastq files are ready.")
            println(converter.numberOfProcessedAlignments.toString() + " alignments processed.")
            println(converter.numberOfPairedReads.toString() + " paired reads.")
            println(converter.numberOfUnpairedReads.toString() + " unpaired reads.")
        }
    }
}
