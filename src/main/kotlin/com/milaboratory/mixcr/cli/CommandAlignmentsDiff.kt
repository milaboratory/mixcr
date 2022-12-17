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
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriterI.DummyWriter
import com.milaboratory.mixcr.cli.CommonDescriptions.Labels
import com.milaboratory.mixcr.util.VDJCAlignmentsDifferenceReader
import com.milaboratory.mixcr.util.VDJCAlignmentsDifferenceReader.DiffStatus.AlignmentPresentOnlyInFirst
import com.milaboratory.mixcr.util.VDJCAlignmentsDifferenceReader.DiffStatus.AlignmentPresentOnlyInSecond
import com.milaboratory.mixcr.util.VDJCAlignmentsDifferenceReader.DiffStatus.AlignmentsAreDifferent
import com.milaboratory.mixcr.util.VDJCAlignmentsDifferenceReader.DiffStatus.AlignmentsAreSame
import com.milaboratory.util.ReportHelper
import com.milaboratory.util.SmartProgressReporter
import com.milaboratory.util.exhaustive
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import picocli.CommandLine.Command
import picocli.CommandLine.Help.Visibility.ALWAYS
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.PrintStream
import java.nio.file.Path

@Command(
    description = ["Calculates the difference between two .vdjca files."]
)
class CommandAlignmentsDiff : MiXCRCommandWithOutputs() {
    @Parameters(paramLabel = "input_file1.vdjca", index = "0")
    lateinit var in1: Path

    @Parameters(paramLabel = "input_file2.vdjca", index = "1")
    lateinit var in2: Path

    @Parameters(
        description = ["Path where to write report. Will write to output if omitted."],
        paramLabel = "report.txt",
        index = "2",
        arity = "0..1"
    )
    var report: Path? = null

    @set:Option(
        names = ["-o1", "--only-in-first"],
        description = ["output for alignments contained only in the first .vdjca file"],
        paramLabel = "<path.vdjca>",
        order = OptionsOrder.main + 10_100
    )
    var onlyFirst: Path? = null
        set(value) {
            ValidationException.requireFileType(value, InputFileType.VDJCA)
            field = value
        }

    @set:Option(
        names = ["-o2", "--only-in-second"],
        description = ["output for alignments contained only in the second .vdjca file"],
        paramLabel = "<path.vdjca>",
        order = OptionsOrder.main + 10_200
    )
    var onlySecond: Path? = null
        set(value) {
            ValidationException.requireFileType(value, InputFileType.VDJCA)
            field = value
        }

    @set:Option(
        names = ["-d1", "--diff-from-first"],
        description = ["output for alignments from the first file that are different from those alignments in the second file"],
        paramLabel = "<path.vdjca>",
        order = OptionsOrder.main + 10_300
    )
    var diff1: Path? = null
        set(value) {
            ValidationException.requireFileType(value, InputFileType.VDJCA)
            field = value
        }

    @set:Option(
        names = ["-d2", "--diff-from-second"],
        description = ["output for alignments from the second file that are different from those alignments in the first file"],
        paramLabel = "<path.vdjca>",
        order = OptionsOrder.main + 10_400
    )
    var diff2: Path? = null
        set(value) {
            ValidationException.requireFileType(value, InputFileType.VDJCA)
            field = value
        }

    @Option(
        names = ["-g", "--gene-feature"],
        description = ["Specifies a gene feature to compare."],
        paramLabel = Labels.GENE_FEATURE,
        defaultValue = "CDR3",
        showDefaultValue = ALWAYS,
        order = OptionsOrder.main + 10_500
    )
    lateinit var geneFeatureToMatch: GeneFeature

    @Option(
        names = ["-l", "--top-hits-level"],
        description = ["Number of top hits to search for a match"],
        paramLabel = "<n>",
        order = OptionsOrder.main + 10_600
    )
    var hitsCompareLevel = 1

    override val inputFiles
        get() = listOf(in1, in2)

    override val outputFiles
        get() = listOfNotNull(report)

    override fun validate() {
        ValidationException.requireFileType(in1, InputFileType.VDJCA)
        ValidationException.requireFileType(in2, InputFileType.VDJCA)
    }

    override fun run1() {
        VDJCAlignmentsReader(in1).use { reader1 ->
            VDJCAlignmentsReader(in2).use { reader2 ->
                (onlyFirst?.let(::VDJCAlignmentsWriter) ?: DummyWriter).use { only1 ->
                    (onlySecond?.let(::VDJCAlignmentsWriter) ?: DummyWriter).use { only2 ->
                        (diff1?.let(::VDJCAlignmentsWriter) ?: DummyWriter).use { diff1 ->
                            (diff2?.let(::VDJCAlignmentsWriter) ?: DummyWriter).use { diff2 ->
                                (report?.let { PrintStream(it.toFile()) } ?: System.out).use { report ->
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
        input1.only.inheritHeaderAndFooterFrom(input1.reader)
        input1.diff.inheritHeaderAndFooterFrom(input1.reader)
        input2.only.inheritHeaderAndFooterFrom(input2.reader)
        input2.diff.inheritHeaderAndFooterFrom(input2.reader)
        val diffReader = VDJCAlignmentsDifferenceReader(
            input1.reader, input2.reader,
            geneFeatureToMatch, hitsCompareLevel
        )
        for (diff in CUtils.it(diffReader)) {
            @Suppress("IMPLICIT_CAST_TO_ANY")
            when (diff.status!!) {
                AlignmentsAreSame -> ++same
                AlignmentPresentOnlyInFirst -> {
                    ++onlyIn1
                    input1.only.write(diff.first)
                }
                AlignmentPresentOnlyInSecond -> {
                    ++onlyIn2
                    input2.only.write(diff.second)
                }
                AlignmentsAreDifferent -> {
                    ++justDiff
                    input1.diff.write(diff.first)
                    input2.diff.write(diff.second)
                    if (diff.reason.diffGeneFeature) ++diffFeature
                    for ((key, value) in diff.reason.diffHits) if (value) ++diffHits[key.ordinal]
                }
            }.exhaustive
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
