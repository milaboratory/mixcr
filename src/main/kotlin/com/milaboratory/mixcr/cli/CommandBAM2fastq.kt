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
import com.milaboratory.app.logger
import com.milaboratory.core.io.sequence.PairedRead
import com.milaboratory.core.io.sequence.SingleRead
import com.milaboratory.core.io.sequence.fastq.PairedFastqWriter
import com.milaboratory.core.io.sequence.fastq.SingleFastqWriter
import com.milaboratory.mixcr.bam.BAMReader
import com.milaboratory.util.TempFileManager
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.nio.file.Path

@Command(
    name = "BAM2fastq",
    hidden = true,
    description = ["Converts BAM/SAM file to paired/unpaired fastq files"]
)
class CommandBAM2fastq : MiXCRCommandWithOutputs() {
    @Option(
        description = ["Put temporary files in the same folder as the output files."],
        names = ["--use-local-temp"],
        order = OptionsOrder.localTemp
    )
    var useLocalTemp = false

    @Option(
        names = ["-b", "--bam"],
        description = ["BAM files for conversion."],
        required = true,
        paramLabel = "<path>",
        order = OptionsOrder.main + 10_100
    )
    lateinit var bamFiles: List<Path>

    @Option(
        names = ["-r1"],
        description = ["File for first reads."],
        required = true,
        paramLabel = "<path>",
        order = OptionsOrder.main + 10_200
    )
    lateinit var fastq1: Path

    @Option(
        names = ["-r2"],
        description = ["File for second reads."],
        required = true,
        paramLabel = "<path>",
        order = OptionsOrder.main + 10_300
    )
    lateinit var fastq2: Path

    @Option(
        names = ["-u"],
        description = ["File for unpaired reads."],
        required = true,
        paramLabel = "<path>",
        order = OptionsOrder.main + 10_400
    )
    lateinit var fastqUnpaired: Path

    @Option(
        names = ["--drop-non-vdj"],
        description = ["Drop reads from bam file mapped on human chromosomes except with VDJ region (2, 7, 14, 22)"],
        order = OptionsOrder.main + 10_500
    )
    var dropNonVDJ = false

    @Option(
        names = ["--keep-wildcards"],
        description = ["Keep sequences with wildcards in the output"],
        order = OptionsOrder.main + 10_600
    )
    var keepWildcards = false

    @Option(
        names = [BAMReader.referenceForCramOption],
        description = ["Reference for genome that was used for build a cram file"],
        order = OptionsOrder.main + 10_700
    )
    var referenceForCram: Path? = null

    override val inputFiles
        get() = bamFiles.toList()

    override val outputFiles
        get() = listOf(fastq1, fastq2, fastqUnpaired)

    override fun run1() {
        val tempFileDest = TempFileManager.smartTempDestination(fastq1, "", !useLocalTemp)
        BAMReader(bamFiles, dropNonVDJ, !keepWildcards, tempFileDest, referenceForCram).use { converter ->
            PairedFastqWriter(fastq1.toFile(), fastq2.toFile()).use { wr ->
                SingleFastqWriter(fastqUnpaired.toFile()).use { swr ->
                    converter.forEach { read ->
                        when (read) {
                            is PairedRead -> wr.write(read)
                            is SingleRead -> swr.write(read)
                            else -> throw IllegalArgumentException()
                        }
                    }
                }
            }
            logger.log("Your fastq files are ready.")
            logger.log("${converter.numberOfProcessedAlignments()} alignments processed.")
            logger.log("${converter.numberOfPairedReads()} paired reads.")
            logger.log("${converter.numberOfUnpairedReads()} unpaired reads.")
        }
    }
}
