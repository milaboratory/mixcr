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
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriterI
import com.milaboratory.mixcr.util.VDJCAlignmentsDifferenceReader
import com.milaboratory.mixcr.util.VDJCAlignmentsDifferenceReader.DiffStatus
import com.milaboratory.util.ReportHelper
import com.milaboratory.util.SmartProgressReporter
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import picocli.CommandLine
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Paths

@CommandLine.Command(
    name = "alignmentsDiff",
    sortOptions = false,
    separator = " ",
    description = ["Calculates the difference between two .vdjca files."]
)
class CommandAlignmentsDiff : MiXCRCommand() {
    @CommandLine.Parameters(description = ["input_file1"], index = "0")
    lateinit var in1: String

    @CommandLine.Parameters(description = ["input_file2"], index = "1")
    lateinit var in2: String

    @CommandLine.Parameters(description = ["report"], index = "2", arity = "0..1")
    var report: String? = null

    @CommandLine.Option(
        names = ["-o1", "--only-in-first"], description = ["output for alignments contained only " +
                "in the first .vdjca file"]
    )
    var onlyFirst: String? = null

    @CommandLine.Option(
        names = ["-o2", "--only-in-second"], description = ["output for alignments contained only " +
                "in the second .vdjca file"]
    )
    var onlySecond: String? = null

    @CommandLine.Option(
        names = ["-d1", "--diff-from-first"], description = ["output for alignments from the first file " +
                "that are different from those alignments in the second file"]
    )
    var diff1: String? = null

    @CommandLine.Option(
        names = ["-d2", "--diff-from-second"], description = ["output for alignments from the second file " +
                "that are different from those alignments in the first file"]
    )
    var diff2: String? = null

    @CommandLine.Option(names = ["-g", "--gene-feature"], description = ["Specifies a gene feature to compare"])
    var geneFeatureToMatch = "CDR3"

    @CommandLine.Option(names = ["-l", "--top-hits-level"], description = ["Number of top hits to search for a match"])
    var hitsCompareLevel = 1
    val feature: GeneFeature by lazy {
        GeneFeature.parse(geneFeatureToMatch)
    }

    override fun getInputFiles(): List<String> = listOf(in1, in2)

    override fun getOutputFiles(): List<String> = listOfNotNull(report)

    override fun run0() {
        VDJCAlignmentsReader(in1).use { reader1 ->
            VDJCAlignmentsReader(in2).use { reader2 ->
                (if (onlyFirst == null) VDJCAlignmentsWriterI.DummyWriter else VDJCAlignmentsWriter(onlyFirst)).use { only1 ->
                    if (onlySecond == null) VDJCAlignmentsWriterI.DummyWriter else VDJCAlignmentsWriter(onlySecond).use { only2 ->
                        if (diff1 == null) VDJCAlignmentsWriterI.DummyWriter else VDJCAlignmentsWriter(diff1).use { diff1 ->
                            if (diff2 == null) VDJCAlignmentsWriterI.DummyWriter else VDJCAlignmentsWriter(diff2).use { diff2 ->
                                when (report) {
                                    null -> System.out
                                    else -> PrintStream(Files.newOutputStream(Paths.get(report!!)))
                                }.use { report ->
                                    val readerForProgress = when {
                                        reader1.numberOfReads > reader2.numberOfReads -> reader1
                                        else -> reader2
                                    }
                                    SmartProgressReporter.startProgressReport("Analyzing diff", readerForProgress)
                                    val input1 = InputWrapper(reader1, only1, diff1)
                                    val input2 = InputWrapper(reader2, only2, diff2)
                                    writeDiff(input1, input2, report)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun writeDiff(
        input1: InputWrapper,
        input2: InputWrapper,
        report: PrintStream
    ) {
        var same: Long = 0
        var onlyIn1: Long = 0
        var onlyIn2: Long = 0
        var diffFeature: Long = 0
        var justDiff: Long = 0
        val diffHits = LongArray(GeneType.NUMBER_OF_TYPES)
        input1.only.header(input1.reader)
        input1.diff.header(input1.reader)
        input2.only.header(input2.reader)
        input2.diff.header(input2.reader)
        val diffReader = VDJCAlignmentsDifferenceReader(
            input1.reader, input2.reader,
            feature, hitsCompareLevel
        )
        for (diff in CUtils.it(diffReader)) {
            when (diff.status!!) {
                DiffStatus.AlignmentsAreSame -> ++same
                DiffStatus.AlignmentPresentOnlyInFirst -> {
                    ++onlyIn1
                    input1.only.write(diff.first)
                }
                DiffStatus.AlignmentPresentOnlyInSecond -> {
                    ++onlyIn2
                    input2.only.write(diff.second)
                }
                DiffStatus.AlignmentsAreDifferent -> {
                    ++justDiff
                    input1.diff.write(diff.first)
                    input2.diff.write(diff.second)
                    if (diff.reason.diffGeneFeature) ++diffFeature
                    for ((key, value) in diff.reason.diffHits) if (value) ++diffHits[key.ordinal]
                }
            }
        }
        input1.only.setNumberOfProcessedReads(onlyIn1)
        input2.only.setNumberOfProcessedReads(onlyIn2)
        input1.diff.setNumberOfProcessedReads(justDiff)
        input2.diff.setNumberOfProcessedReads(justDiff)
        report.println("First  file: $in1")
        report.println("Second file: $in2")
        report.println("Completely same reads: $same")
        report.println(
            "Aligned reads present only in the FIRST  file: $onlyIn1 " +
                    "(${ReportHelper.PERCENT_FORMAT.format(100.0 * onlyIn1 / input1.reader.numberOfReads)})%"
        )
        report.println(
            "Aligned reads present only in the SECOND file: $onlyIn2 " +
                    "(${ReportHelper.PERCENT_FORMAT.format(100.0 * onlyIn2 / input2.reader.numberOfReads)})%"
        )
        report.println("Total number of different reads: $justDiff")
        report.println("Reads with not same $geneFeatureToMatch: $diffFeature")
        for (geneType in GeneType.VDJC_REFERENCE) {
            report.println("Reads with not same ${geneType.name} hits: ${diffHits[geneType.ordinal]}")
        }
    }

    private class InputWrapper(
        val reader: VDJCAlignmentsReader,
        val only: VDJCAlignmentsWriterI,
        val diff: VDJCAlignmentsWriterI
    )
}