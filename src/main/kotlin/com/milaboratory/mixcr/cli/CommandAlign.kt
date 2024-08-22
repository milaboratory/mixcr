/*
 * Copyright (c) 2014-2024, MiLaboratories Inc. All Rights Reserved
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


import cc.redberry.pipe.util.buffered
import cc.redberry.pipe.util.chunked
import cc.redberry.pipe.util.forEach
import cc.redberry.pipe.util.mapChunksInParallel
import cc.redberry.pipe.util.mapUnchunked
import cc.redberry.pipe.util.ordered
import cc.redberry.pipe.util.synchronized
import cc.redberry.pipe.util.unchunked
import com.milaboratory.app.ApplicationException
import com.milaboratory.app.InputFileType
import com.milaboratory.app.ValidationException
import com.milaboratory.app.logger
import com.milaboratory.app.matches
import com.milaboratory.cli.FastqGroupReader
import com.milaboratory.cli.MultiSampleRun
import com.milaboratory.cli.MultiSampleRun.SAVE_OUTPUT_FILE_NAMES_OPTION
import com.milaboratory.cli.POverridesBuilderOps
import com.milaboratory.core.io.sequence.MultiRead
import com.milaboratory.core.io.sequence.PairedRead
import com.milaboratory.core.io.sequence.SequenceRead
import com.milaboratory.core.io.sequence.SequenceWriter
import com.milaboratory.core.io.sequence.SingleRead
import com.milaboratory.core.io.sequence.SingleReadImpl
import com.milaboratory.core.io.sequence.fasta.FastaReader
import com.milaboratory.core.io.sequence.fasta.FastaSequenceReaderWrapper
import com.milaboratory.core.io.sequence.fastq.MultiFastqWriter
import com.milaboratory.core.io.sequence.fastq.PairedFastqWriter
import com.milaboratory.core.io.sequence.fastq.SingleFastqWriter
import com.milaboratory.core.sequence.NSQTuple
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.core.sequence.quality.QualityTrimmerParameters
import com.milaboratory.core.sequence.quality.ReadTrimmerProcessor
import com.milaboratory.milm.MiXCRMain
import com.milaboratory.mitool.MiTollStepReports
import com.milaboratory.mitool.MiToolCommandDescriptor
import com.milaboratory.mitool.MiToolParams
import com.milaboratory.mitool.MiToolReport
import com.milaboratory.mitool.MiToolStepParams
import com.milaboratory.mitool.container.MicReader
import com.milaboratory.mitool.data.CriticalThresholdCollection
import com.milaboratory.mitool.pattern.search.ReadSearchMode
import com.milaboratory.mitool.pattern.search.ReadSearchPlan
import com.milaboratory.mitool.pattern.search.ReadSearchSettings
import com.milaboratory.mitool.pattern.search.SearchSettings
import com.milaboratory.mitool.report.ReadTrimmerReportBuilder
import com.milaboratory.mitool.tag.SequenceAndQualityTagValue
import com.milaboratory.mitool.tag.TagInfo
import com.milaboratory.mitool.tag.TagType
import com.milaboratory.mitool.tag.TagValueType
import com.milaboratory.mitool.tag.TechnicalTag.TAG_INPUT_IDX
import com.milaboratory.mixcr.bam.BAMReader
import com.milaboratory.mixcr.basictypes.MiXCRFooter
import com.milaboratory.mixcr.basictypes.MiXCRHeader
import com.milaboratory.mixcr.basictypes.SequenceHistory
import com.milaboratory.mixcr.basictypes.VDJCAlignments
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter
import com.milaboratory.mixcr.basictypes.VDJCHit
import com.milaboratory.mixcr.basictypes.tag.TagCount
import com.milaboratory.mixcr.basictypes.tag.TagTuple
import com.milaboratory.mixcr.cli.CommandAlign.Cmd.InputType.BAM
import com.milaboratory.mixcr.cli.CommandAlign.Cmd.InputType.Fasta
import com.milaboratory.mixcr.cli.CommandAlign.Cmd.InputType.MIC
import com.milaboratory.mixcr.cli.CommandAlign.Cmd.InputType.PairedEndFastq
import com.milaboratory.mixcr.cli.CommandAlign.Cmd.InputType.QuadEndFastq
import com.milaboratory.mixcr.cli.CommandAlign.Cmd.InputType.SingleEndFastq
import com.milaboratory.mixcr.cli.CommandAlign.Cmd.InputType.TripleEndFastq
import com.milaboratory.mixcr.cli.CommandAlignPipeline.ProcessingBundle
import com.milaboratory.mixcr.cli.CommandAlignPipeline.ProcessingBundleStatus.Good
import com.milaboratory.mixcr.cli.CommandAlignPipeline.ProcessingBundleStatus.NotAligned
import com.milaboratory.mixcr.cli.CommandAlignPipeline.ProcessingBundleStatus.NotMatched
import com.milaboratory.mixcr.cli.CommandAlignPipeline.ProcessingBundleStatus.NotParsed
import com.milaboratory.mixcr.cli.CommandAlignPipeline.cellSplitGroupLabel
import com.milaboratory.mixcr.cli.CommandAlignPipeline.getTagsExtractor
import com.milaboratory.mixcr.cli.CommonDescriptions.DEFAULT_VALUE_FROM_PRESET
import com.milaboratory.mixcr.cli.CommonDescriptions.Labels
import com.milaboratory.mixcr.cli.MiXCRCommand.OptionsOrder
import com.milaboratory.mixcr.presets.AlignMixins
import com.milaboratory.mixcr.presets.AlignMixins.LimitInput
import com.milaboratory.mixcr.presets.AnalyzeCommandDescriptor
import com.milaboratory.mixcr.presets.AnalyzeCommandDescriptor.Companion.dotAfterIfNotBlank
import com.milaboratory.mixcr.presets.AnalyzeCommandDescriptor.MiToolCommandDelegationDescriptor
import com.milaboratory.mixcr.presets.FullSampleSheetParsed
import com.milaboratory.mixcr.presets.MiXCRParamsBundle
import com.milaboratory.mixcr.presets.MiXCRParamsSpec
import com.milaboratory.mixcr.presets.MiXCRStepParams
import com.milaboratory.mixcr.presets.MiXCRStepReports
import com.milaboratory.mixcr.util.toHexString
import com.milaboratory.mixcr.vdjaligners.VDJCAligner
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters
import com.milaboratory.mixcr.vdjaligners.VDJCAlignmentFailCause
import com.milaboratory.primitivio.blocks.SemaphoreWithInfo
import com.milaboratory.util.FileGroup
import com.milaboratory.util.LightFileDescriptor
import com.milaboratory.util.OutputPortWithProgress
import com.milaboratory.util.PathPatternExpandException
import com.milaboratory.util.ReportHelper
import com.milaboratory.util.ReportUtil
import com.milaboratory.util.SmartProgressReporter
import com.milaboratory.util.TempFileManager
import com.milaboratory.util.limit
import com.milaboratory.util.parseAndRunAndCorrelateFSPattern
import com.milaboratory.util.use
import com.milaboratory.util.withExpectedSize
import io.repseq.core.Chains
import io.repseq.core.GeneFeature.VRegion
import io.repseq.core.GeneFeature.VRegionWithP
import io.repseq.core.GeneFeature.encode
import io.repseq.core.GeneType
import io.repseq.core.VDJCLibrary
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine.ArgGroup
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Model.PositionalParamSpec
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import java.util.regex.Pattern
import kotlin.collections.component1
import kotlin.collections.set
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.math.max

object CommandAlign {
    const val COMMAND_NAME = AnalyzeCommandDescriptor.align.name

    const val STRICT_SAMPLE_NAME_MATCHING_OPTION = "--strict-sample-sheet-matching"

    fun List<CommandAlignParams.SampleTable.Row>.bySampleName() = groupBy { it.sample }

    val FullSampleSheetParsed.inputFileGroups
        get() = inputs?.let { inputs ->
            InputFileGroups(
                inputs.mapIndexed { idx, paths ->
                    FileGroup(paths.map { basePath!!.resolve(it) }, listOf(TAG_INPUT_IDX to idx.toString()))
                })
        }

    class InputFileGroups(
        val fileGroups: List<FileGroup>
    ) {
        val allFiles: List<Path> = fileGroups.flatMap { it.files }

        /** List of tags (keys) available for each file group (i.e. CELL) */
        val tags: List<String> = fileGroups.first().tags.map { it.first }

        val inputType: Cmd.InputType by lazy {
            val first = fileGroups.first().files
            if (first.size == 1) {
                val f0 = first[0]
                when {
                    f0.matches(InputFileType.FASTQ) -> SingleEndFastq
                    f0.matches(InputFileType.FASTA) || f0.matches(InputFileType.FASTA_GZ) -> Fasta
                    f0.matches(InputFileType.BAM_SAM_CRAM) -> BAM
                    f0.matches(InputFileType.MIC) -> MIC(allTags = MicReader(f0).header.allTags)

                    else -> throw ValidationException("Unknown file type: $f0")
                }
            } else if (first.size <= 4) {
                first.forEach { f ->
                    if (!f.matches(InputFileType.FASTQ))
                        throw ValidationException("Only fastq supports multiple end inputs, can't recognise: $f")
                }
                when (first.size) {
                    2 -> PairedEndFastq
                    3 -> TripleEndFastq
                    4 -> QuadEndFastq
                    else -> throw ValidationException("Too many inputs")
                }
            } else
                throw ValidationException("Too many inputs")
        }
    }

    class PathsForNotAligned {
        companion object {
            val optionNames
                get() = arrayOf(
                    "--not-aligned-I1",
                    "--not-aligned-I2",
                    "--not-aligned-R1",
                    "--not-aligned-R2",
                    "--not-parsed-I1",
                    "--not-parsed-I2",
                    "--not-parsed-R1",
                    "--not-parsed-R2",
                )
        }

        @set:Option(
            description = ["Pipe not aligned I1 reads into separate file."],
            names = ["--not-aligned-I1"],
            paramLabel = "<path.fastq[.gz]>",
            order = OptionsOrder.notAligned + 101
        )
        var notAlignedI1: Path? = null
            set(value) {
                ValidationException.requireFileType(value, InputFileType.FASTQ)
                field = value
            }

        @set:Option(
            description = ["Pipe not aligned I2 reads into separate file."],
            names = ["--not-aligned-I2"],
            paramLabel = "<path.fastq[.gz]>",
            order = OptionsOrder.notAligned + 102
        )
        var notAlignedI2: Path? = null
            set(value) {
                ValidationException.requireFileType(value, InputFileType.FASTQ)
                field = value
            }

        @set:Option(
            description = ["Pipe not aligned R1 reads into separate file."],
            names = ["--not-aligned-R1"],
            paramLabel = "<path.fastq[.gz]>",
            order = OptionsOrder.notAligned + 103
        )
        var notAlignedR1: Path? = null
            set(value) {
                ValidationException.requireFileType(value, InputFileType.FASTQ)
                field = value
            }

        @set:Option(
            description = ["Pipe not aligned R2 reads into separate file."],
            names = ["--not-aligned-R2"],
            paramLabel = "<path.fastq[.gz]>",
            order = OptionsOrder.notAligned + 104
        )
        var notAlignedR2: Path? = null
            set(value) {
                ValidationException.requireFileType(value, InputFileType.FASTQ)
                field = value
            }

        @set:Option(
            description = ["Pipe not parsed I1 reads into separate file."],
            names = ["--not-parsed-I1"],
            paramLabel = "<path.fastq[.gz]>",
            order = OptionsOrder.notAligned + 201
        )
        var notParsedI1: Path? = null
            set(value) {
                ValidationException.requireFileType(value, InputFileType.FASTQ)
                field = value
            }

        @set:Option(
            description = ["Pipe not parsed I2 reads into separate file."],
            names = ["--not-parsed-I2"],
            paramLabel = "<path.fastq[.gz]>",
            order = OptionsOrder.notAligned + 202
        )
        var notParsedI2: Path? = null
            set(value) {
                ValidationException.requireFileType(value, InputFileType.FASTQ)
                field = value
            }

        @set:Option(
            description = ["Pipe not parsed R1 reads into separate file."],
            names = ["--not-parsed-R1"],
            paramLabel = "<path.fastq[.gz]>",
            order = OptionsOrder.notAligned + 203
        )
        var notParsedR1: Path? = null
            set(value) {
                ValidationException.requireFileType(value, InputFileType.FASTQ)
                field = value
            }

        @set:Option(
            description = ["Pipe not parsed R2 reads into separate file."],
            names = ["--not-parsed-R2"],
            paramLabel = "<path.fastq[.gz]>",
            order = OptionsOrder.notAligned + 204
        )
        var notParsedR2: Path? = null
            set(value) {
                ValidationException.requireFileType(value, InputFileType.FASTQ)
                field = value
            }

        private val allowedStates = listOf(
            listOf(),
            listOf("R1"),
            listOf("R1", "R2"),
            listOf("I1", "R1", "R2"),
            listOf("I1", "I2", "R1", "R2"),
        )

        val outputFiles
            get() = listOfNotNull(
                notAlignedI1,
                notAlignedI2,
                notAlignedR1,
                notAlignedR2,
                notParsedI1,
                notParsedI2,
                notParsedR1,
                notParsedR2,
            )

        fun fillWithDefaults(
            inputType: Cmd.InputType,
            outputDir: Path,
            prefix: String,
            addNotAligned: Boolean = true,
            addNotParsed: Boolean = true
        ) {
            fun fill(type: String) {
                when (type) {
                    "R1" -> {
                        if (addNotAligned)
                            notAlignedR1 = outputDir.resolve("${prefix.dotAfterIfNotBlank()}not_aligned.R1.fastq.gz")
                        if (addNotParsed)
                            notParsedR1 = outputDir.resolve("${prefix.dotAfterIfNotBlank()}not_parsed.R1.fastq.gz")
                    }

                    "R2" -> {
                        if (addNotAligned)
                            notAlignedR2 = outputDir.resolve("${prefix.dotAfterIfNotBlank()}not_aligned.R2.fastq.gz")
                        if (addNotParsed)
                            notParsedR2 = outputDir.resolve("${prefix.dotAfterIfNotBlank()}not_parsed.R2.fastq.gz")
                    }

                    "I1" -> {
                        if (addNotAligned)
                            notAlignedI1 = outputDir.resolve("${prefix.dotAfterIfNotBlank()}not_aligned.I1.fastq.gz")
                        if (addNotParsed)
                            notParsedI1 = outputDir.resolve("${prefix.dotAfterIfNotBlank()}not_parsed.I1.fastq.gz")
                    }

                    "I2" -> {
                        if (addNotAligned)
                            notAlignedI2 = outputDir.resolve("${prefix.dotAfterIfNotBlank()}not_aligned.I2.fastq.gz")
                        if (addNotParsed)
                            notParsedI2 = outputDir.resolve("${prefix.dotAfterIfNotBlank()}not_parsed.I2.fastq.gz")
                    }

                    else -> throw IllegalArgumentException()
                }
            }

            when (inputType) {
                SingleEndFastq -> {
                    fill("R1")
                }

                PairedEndFastq -> {
                    fill("R1")
                    fill("R2")
                }

                TripleEndFastq -> {
                    fill("I1")
                    fill("R1")
                    fill("R2")
                }

                QuadEndFastq -> {
                    fill("I1")
                    fill("I2")
                    fill("R1")
                    fill("R2")
                }

                is MIC -> when (inputType.readTags.size) {
                    1 -> fill("R1")
                    2 -> {
                        fill("R1")
                        fill("R2")
                    }

                    else -> throw ValidationException("Only 1 or 2 reads tags are supported, got ${inputType.readTags}")
                }

                Fasta -> throw ValidationException("Can't write not aligned and not parsed reads for fasta input")
                BAM -> {
                    fill("R1")
                    fill("R2")
                }
            }
        }

        fun argsOfNotAlignedForAlign(): List<String> = listOf(
            "--not-aligned-I1" to notAlignedI1,
            "--not-aligned-I2" to notAlignedI2,
            "--not-aligned-R1" to notAlignedR1,
            "--not-aligned-R2" to notAlignedR2,
        )
            .filter { it.second != null }
            .flatMap { listOf(it.first, it.second!!.toString()) }

        fun argsOfNotParsedForAlign(): List<String> = listOf(
            "--not-parsed-I1" to notParsedI1,
            "--not-parsed-I2" to notParsedI2,
            "--not-parsed-R1" to notParsedR1,
            "--not-parsed-R2" to notParsedR2,
        )
            .filter { it.second != null }
            .flatMap { listOf(it.first, it.second!!.toString()) }

        fun argsOfNotParsedForMiToolParse(): List<String> = listOfNotNull(
            notParsedI1,
            notParsedI2,
            notParsedR1,
            notParsedR2,
        ).flatMap { listOf("--unmatched", it.toString()) }

        fun validate(inputType: Cmd.InputType) {
            fun Any?.tl(value: String) = if (this == null) emptyList() else listOf(value)

            fun checkFailedReadsOptions(
                optionPrefix: String,
                i1: Path?, i2: Path?,
                r1: Path?, r2: Path?
            ) {
                val states = (i1.tl("I1") + i2.tl("I2") + r1.tl("R1") + r2.tl("R2"))

                ValidationException.require(states in allowedStates) {
                    "Unsupported combination of reads in ${optionPrefix}-*: found $states expected one of " +
                            allowedStates.joinToString(" / ") { it.joinToString(",") }
                }

                if (r1 != null) {
                    when {
                        inputType == SingleEndFastq || (inputType is MIC && inputType.readTags.size == 1) ->
                            ValidationException.require(r2 == null) {
                                "Option ${optionPrefix}-R2 is specified but single-end input data provided."
                            }

                        inputType == PairedEndFastq || (inputType is MIC && inputType.readTags.size == 2) ->
                            ValidationException.require(r2 != null) {
                                "Option ${optionPrefix}-R2 is not specified but paired-end input data provided."
                            }

                        else -> ValidationException.require(inputType.isFastq || inputType == BAM) {
                            "Option ${optionPrefix}-* options are supported for fastq data input only."
                        }
                    }
                }
            }
            checkFailedReadsOptions(
                "--not-aligned",
                notAlignedI1,
                notAlignedI2,
                notAlignedR1,
                notAlignedR2
            )
            checkFailedReadsOptions(
                "--not-parsed",
                notParsedI1,
                notParsedI2,
                notParsedR1,
                notParsedR2
            )
        }
    }

    fun checkInputTemplates(paths: List<Path>) {
        when (paths.size) {
            1 -> ValidationException.requireFileType(
                paths.first(),
                InputFileType.FASTQ,
                InputFileType.FASTA,
                InputFileType.FASTA_GZ,
                InputFileType.BAM_SAM_CRAM,
                InputFileType.TSV,
                InputFileType.MIC
            )

            2 -> {
                ValidationException.requireFileType(paths[0], InputFileType.FASTQ)
                ValidationException.requireFileType(paths[1], InputFileType.FASTQ)
            }

            3 -> {
                ValidationException.requireFileType(paths[0], InputFileType.FASTQ)
                ValidationException.requireFileType(paths[1], InputFileType.FASTQ)
                ValidationException.requireFileType(paths[2], InputFileType.FASTQ)
            }

            4 -> {
                ValidationException.requireFileType(paths[0], InputFileType.FASTQ)
                ValidationException.requireFileType(paths[1], InputFileType.FASTQ)
                ValidationException.requireFileType(paths[2], InputFileType.FASTQ)
                ValidationException.requireFileType(paths[3], InputFileType.FASTQ)
            }

            else -> throw ValidationException("Required 1 or 2 input files, got $paths")
        }
    }

    const val inputsLabel =
        "([file_I1.fastq[.gz] [file_I2.fastq[.gz]]] file_R1.fastq[.gz] [file_R2.fastq[.gz]]|file.(fasta[.gz]|bam|sam|cram))"

    val inputsDescription = arrayOf(
        "Two fastq files for paired reads or one file for single read data.",
        "Use {{n}} if you want to concatenate files from multiple lanes, like:",
        "my_file_L{{n}}_R1.fastq.gz my_file_L{{n}}_R2.fastq.gz"
    )

    private const val outputLabel = "alignments.vdjca"

    fun mkCommandSpec(): CommandSpec = CommandSpec.forAnnotatedObject(Cmd::class.java)
        .addPositional(
            PositionalParamSpec.builder()
                .index("0")
                .required(false)
                .arity("0..*")
                .type(Path::class.java)
                .paramLabel(inputsLabel)
                .hideParamSyntax(true)
                .description(*inputsDescription)
                .build()
        )
        .addPositional(
            PositionalParamSpec.builder()
                .index("1")
                .required(false)
                .arity("0..*")
                .type(Path::class.java)
                .paramLabel(outputLabel)
                .hideParamSyntax(true)
                .description("Path where to write output alignments")
                .build()
        )

    abstract class CmdBase : MiXCRCommandWithOutputs(), MiXCRPresetAwareCommand<CommandAlignParams> {
        @Option(
            names = ["-O"],
            description = ["Overrides aligner parameters from the selected preset"],
            paramLabel = Labels.OVERRIDES,
            order = OptionsOrder.overrides
        )
        private var overrides: Map<String, String> = mutableMapOf()

        @Option(
            description = [
                "Read pre-processing: trimming quality threshold. Zero value can be used to skip trimming.",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["--trimming-quality-threshold"],
            paramLabel = "<n>",
            order = OptionsOrder.main + 10_000
        )
        private var trimmingQualityThreshold: Byte? = null

        @Option(
            description = [
                "Read pre-processing: trimming window size.",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["--trimming-window-size"],
            paramLabel = "<n>",
            order = OptionsOrder.main + 10_100
        )
        private var trimmingWindowSize: Byte? = null

        @Suppress("unused", "UNUSED_PARAMETER")
        @Option(
            names = ["-c", "--chains"],
            hidden = true
        )
        fun setChains(ignored: String) {
            logger.warn(
                "Don't use --chains option on the alignment step. See --chains parameter in exportAlignments and " +
                        "exportClones actions to limit output to a subset of receptor chains."
            )
        }

        @Option(
            description = [
                "Drop reads from bam file mapped on human chromosomes except with VDJ region (2, 7, 14, 22)",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["--drop-non-vdj"],
            hidden = true
        )
        private var dropNonVDJ = false

        @Option(
            description = [
                "Write alignment results for all input reads (even if alignment failed).",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["--write-all"],
            order = OptionsOrder.main + 10_200
        )
        private var writeAllResults = false

        @set:Option(
            description = [
                "Read tag pattern from a file.",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["--tag-pattern-file"],
            paramLabel = "<path>",
            order = OptionsOrder.main + 10_300
        )
        var tagPatternFile: Path? = null
            set(value) {
                ValidationException.requireFileExists(value)
                field = value
            }

        @Option(
            description = [
                "If paired-end input is used, determines whether to try all combinations of mate-pairs or " +
                        "only match reads to the corresponding pattern sections (i.e. first file to first section etc).",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["--tag-parse-unstranded"],
            order = OptionsOrder.main + 10_400
        )
        private var tagUnstranded = false

        @Option(
            description = [
                "Maximal bit budget controlling mismatches (substitutions) in tag pattern. Higher values allows more substitutions in small letters.",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["--tag-max-budget"],
            paramLabel = "<n>",
            order = OptionsOrder.main + 10_500
        )
        private var tagMaxBudget: Double? = null

        @Option(
            description = [
                "Marks reads, coming from different files, but having the same positions in those files, " +
                        "as reads coming from the same cells. " +
                        "Main use-case is protocols with overlapped alpha-beta, gamma-delta or heavy-light cDNA molecules, " +
                        "where each side was sequenced by separate mate pairs in a paired-end sequencer. " +
                        "Use special expansion group $cellSplitGroupLabel instead of R index " +
                        "(i.e. \"my_file_R{{$cellSplitGroupLabel:n}}.fastq.gz\").",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["--read-id-as-cell-tag"],
            order = OptionsOrder.main + 10_600
        )
        private var readIdAsCellTag = false

        @Option(
            description = [
                "Copy original reads (sequences + qualities + descriptions) to .vdjca file.",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["-g", "--save-reads"],
            hidden = true
        )
        private var saveReads = false

        @Suppress("UNUSED_PARAMETER")
        @set:Option(
            description = [
                "Maximal number of reads to process",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["-n", "--limit"],
            paramLabel = "<n>",
            hidden = true
        )
        private var limit: Long? = null
            set(value) {
                throw ApplicationException("--limit and -n options are deprecated; use ${LimitInput.CMD_OPTION} instead.")
            }

        override val paramsResolver = object : MiXCRParamsResolver<CommandAlignParams>(MiXCRParamsBundle::align) {
            override fun POverridesBuilderOps<CommandAlignParams>.paramsOverrides() {
                if (overrides.isNotEmpty()) {
                    // Printing warning message for some common mistakes in parameter overrides
                    for ((key) in overrides) if ("Parameters.parameters.relativeMinScore" == key.substring(1)) logger.warn(
                        "most probably you want to change \"${key[0]}Parameters.relativeMinScore\" " +
                                "instead of \"${key[0]}Parameters.parameters.relativeMinScore\". " +
                                "The latter should be touched only in a very specific cases."
                    )
                    CommandAlignParams::parameters jsonOverrideWith overrides
                }

                CommandAlignParams::trimmingQualityThreshold setIfNotNull trimmingQualityThreshold
                CommandAlignParams::trimmingWindowSize setIfNotNull trimmingWindowSize
                CommandAlignParams::bamDropNonVDJ setIfTrue dropNonVDJ
                CommandAlignParams::writeFailedAlignments setIfTrue writeAllResults
                CommandAlignParams::tagPattern setIfNotNull tagPatternFile?.readText()
                CommandAlignParams::tagUnstranded setIfTrue tagUnstranded
                CommandAlignParams::tagMaxBudget setIfNotNull tagMaxBudget
                CommandAlignParams::readIdAsCellTag setIfTrue readIdAsCellTag

                if (saveReads)
                    CommandAlignParams::parameters.updateBy {
                        it.setSaveOriginalReads(true)
                    }

                CommandAlignParams::limit setIfNotNull limit
            }

            override fun validateParams(params: CommandAlignParams) {
                if (params.species.isEmpty())
                    throw ValidationException("Species not set, please use -s / --species option to specified it.")
            }
        }
    }

    @Command(
        description = ["Builds alignments with V,D,J and C genes for input sequencing reads."]
    )
    class Cmd : CmdBase() {
        @Option(
            description = ["Put temporary files in the same folder as the output files."],
            names = ["--use-local-temp"],
            order = OptionsOrder.localTemp
        )
        var useLocalTemp = false

        @Option(
            description = ["Analysis preset. Sets key parameters of this and all downstream analysis steps. " +
                    "It is critical to carefully select the most appropriate preset for the data you analyse."],
            names = ["-p", "--preset"],
            paramLabel = Labels.PRESET,
            required = true,
            order = OptionsOrder.main + 1000,
            completionCandidates = PresetsCandidates::class
        )
        lateinit var presetName: String

        @Option(
            description = [
                "Perform strict matching against input sample sheet (one substitution will be allowed by default).",
                "This option only valid if input file is *.tsv sample sheet."
            ],
            names = [STRICT_SAMPLE_NAME_MATCHING_OPTION],
            order = OptionsOrder.main + 1100,
        )
        private var strictMatching = false

        @ArgGroup(
            validate = false,
            multiplicity = "0..*",
            order = OptionsOrder.mixins.pipeline
        )
        var pipelineMixins: PipelineMiXCRMixinsHidden? = null

        @Mixin
        var alignMixins: AlignMiXCRMixins? = null

        @ArgGroup(
            validate = false,
            heading = RefineTagsAndSortMiXCRMixins.DESCRIPTION,
            multiplicity = "0..*",
            order = OptionsOrder.mixins.refineTagsAndSort
        )
        var refineTagsAndSortMixins: List<RefineTagsAndSortMiXCRMixins> = mutableListOf()

        @ArgGroup(
            validate = false,
            heading = AssembleMiXCRMixins.DESCRIPTION,
            multiplicity = "0..*",
            order = OptionsOrder.mixins.assemble
        )
        var assembleMixins: List<AssembleMiXCRMixins> = mutableListOf()

        @ArgGroup(
            validate = false,
            heading = AssembleContigsMiXCRMixins.DESCRIPTION,
            multiplicity = "0..*",
            order = OptionsOrder.mixins.assembleContigs
        )
        var assembleContigsMixins: List<AssembleContigsMiXCRMixins> = mutableListOf()

        @ArgGroup(
            validate = false,
            heading = ExportMiXCRMixins.DESCRIPTION,
            multiplicity = "0..*",
            order = OptionsOrder.mixins.exports
        )
        var exportMixins: List<ExportMiXCRMixins.All> = mutableListOf()

        @ArgGroup(
            multiplicity = "0..*",
            order = OptionsOrder.mixins.generic
        )
        var genericMixins: List<GenericMiXCRMixins> = mutableListOf()

        @ArgGroup(
            multiplicity = "0..*",
            order = OptionsOrder.mixins.qc
        )
        var qcMixins: List<QcChecksMixins> = mutableListOf()

        private val mixins: MiXCRMixinCollection
            get() = MiXCRMixinCollection.empty + pipelineMixins + alignMixins + refineTagsAndSortMixins +
                    assembleMixins + assembleContigsMixins + exportMixins + genericMixins + qcMixins

        @Parameters(
            index = "0",
            arity = "2..5",
            paramLabel = "$inputsLabel $outputLabel",
            hideParamSyntax = true,
            // help is covered by mkCommandSpec
            hidden = true
        )
        private val inOut: List<Path> = mutableListOf()

        private val outputFile get() = inOut.last()

        private val inputTemplates get() = inOut.dropLast(1)

        private val inputSampleSheet: FullSampleSheetParsed? by lazy {
            if (inputTemplates.size == 1 && inputTemplates[0].name.endsWith(".tsv"))
                FullSampleSheetParsed.parse(inputTemplates[0])
            else
                null
        }

        /** I.e. list of mate-pair files */
        private val inputFileGroups: InputFileGroups by lazy {
            try {
                inputSampleSheet
                    ?.inputFileGroups
                    ?: InputFileGroups(inputTemplates.parseAndRunAndCorrelateFSPattern())
            } catch (e: PathPatternExpandException) {
                throw ValidationException(e.message!!)
            }
        }

        override val inputFiles get() = inputFileGroups.allFiles + listOfNotNull(referenceForCram)

        override val outputFiles get() = listOf(outputFile) + pathsForNotAligned.outputFiles

        @Option(
            description = ["Size of buffer for FASTQ readers in bytes. Default: 4Mb"],
            names = ["--read-buffer"],
            paramLabel = "<n>",
            order = OptionsOrder.main + 10_700
        )
        var readBufferSize = 1 shl 22 // 4 Mb

        @Mixin
        lateinit var reportOptions: ReportOptions

        @Mixin
        lateinit var threads: ThreadsOption

        @Mixin
        lateinit var dontSavePresetOption: DontSavePresetOption

        @Option(
            description = ["Use higher compression for output file, 10~25%% slower, minus 30~50%% of file size."],
            names = ["--high-compression"],
            order = OptionsOrder.main + 10_800
        )
        var highCompression = false

        @Option(
            description = ["Align on all gene variants, not only that marked as primary."],
            names = ["--align-on-all-gene-variants"],
            hidden = true
        )
        var alignOnAllVariants = false

        @Option(
            names = [BAMReader.referenceForCramOption],
            description = ["Reference to the genome that was used for build a cram file"],
            order = OptionsOrder.main + 10_900,
            paramLabel = "genome.fasta[.gz]"
        )
        var referenceForCram: Path? = null

        @Mixin
        lateinit var pathsForNotAligned: PathsForNotAligned

        @Option(
            description = [
                "Using this option, the process will create a text file with the list of output *.vdjca files.",
                "Only file names are added, not full paths."
            ],
            names = [SAVE_OUTPUT_FILE_NAMES_OPTION],
            hidden = true
        )
        private var outputFileList: Path? = null

        private val tempDest by lazy {
            TempFileManager.smartTempDestination(outputFile, "", !useLocalTemp)
        }


        private val paramsSpec by lazy {
            MiXCRParamsSpec(
                presetName, mixins.mixins +
                        listOfNotNull(inputSampleSheet?.tagPattern?.let { AlignMixins.SetTagPattern(it) })
            )
        }

        /** Output file header will contain packed version of the parameter specs,
        i.e. all external presets and will be packed into the spec object.*/
        private val paramsSpecPacked by lazy { paramsSpec.pack() }

        private val bpPair by lazy { paramsResolver.resolve(paramsSpec) }

        private val cmdParams by lazy {
            var params = bpPair.second

            // If sample sheet is specified as an input, adding corresponding tag transformations,
            // and optionally overriding the tag pattern
            inputSampleSheet?.let { sampleSheet ->
                // tagPattern is set via mixin (see above)

                // Prepending tag transformation step
                val matchingTags = params.tagPattern?.let {
                    ReadSearchPlan.create(it, ReadSearchSettings(SearchSettings.Default, ReadSearchMode.Direct)).allTags
                } ?: emptySet()
                params = params.copy(
                    tagTransformationSteps = listOf(
                        sampleSheet.tagTransformation(
                            matchingTags,
                            !strictMatching
                        )
                    ) + params.tagTransformationSteps
                )
            }

            params
        }

        private val allInputFiles
            get() = when (inputFileGroups.inputType) {
                BAM, is MIC -> inputFileGroups.fileGroups.first().files
                Fasta -> listOf(inputFileGroups.fileGroups.first().files.first())
                else -> inputFileGroups.allFiles
            }

        val alignerParameters: VDJCAlignerParameters by lazy {
            val parameters = cmdParams.parameters
            // Detect if automatic featureToAlign correction is required
            var totalV = 0
            var totalVErrors = 0
            var hasVRegion = 0
            val correctingFeature = when {
                parameters.vAlignerParameters.geneFeatureToAlign.hasReversedRegions() -> VRegionWithP
                else -> VRegion
            }
            for (gene in getGenesForAligning()) {
                if (gene.geneType == GeneType.Variable) {
                    totalV++
                    if (!parameters.containsRequiredFeature(gene)) {
                        totalVErrors++
                        if (gene.referencePoints.isAvailable(correctingFeature)) hasVRegion++
                    }
                }
            }

            // Performing V featureToAlign correction if needed
            if (totalVErrors > totalV * 0.9 && hasVRegion > totalVErrors * 0.8) {
                val currentGenFeature = encode(parameters.vAlignerParameters.geneFeatureToAlign)
                logger.warn(
                    "forcing -OvParameters.geneFeatureToAlign=${encode(correctingFeature)} " +
                            "since current gene feature ($currentGenFeature) is absent in " +
                            "${ReportHelper.PERCENT_FORMAT.format(100.0 * totalVErrors / totalV)}% of V genes."
                )
                parameters.vAlignerParameters.geneFeatureToAlign = correctingFeature
            }

            parameters
        }

        private val vdjcLibrary: VDJCLibrary by lazy {
            val libraryName = libraryNameEnding.matcher(cmdParams.library).replaceAll("")
            VDJCLibraryRegistry.getDefault().getLibrary(libraryName, cmdParams.species)
        }

        private val inputHash: String? by lazy {
            LightFileDescriptor.calculateCommutativeLightHash(inputFileGroups.allFiles)
                ?.toHexString()
        }

        /** pairedRecords == null - means input files can't be directly used in analysis */
        sealed class InputType(val numberOfReads: Int, val isFastq: Boolean) {
            object SingleEndFastq : InputType(1, true)
            object PairedEndFastq : InputType(2, true)
            object TripleEndFastq : InputType(3, true)
            object QuadEndFastq : InputType(4, true)
            object Fasta : InputType(1, false)
            object BAM : InputType(-1 /* 1 or 2*/, false)
            data class MIC(
                val readTags: List<String>,
                val barcodes: List<String>,
            ) : InputType(readTags.size, false) {
                companion object {
                    operator fun invoke(allTags: Collection<String>): MIC {
                        val (barcodes, readTags) = allTags
                            .partition { tag -> TagType.isRecognisable(tag) }
                        return MIC(readTags, barcodes)
                    }
                }
            }
        }

        private fun createReader(pairedPatternPayload: Boolean?): OutputPortWithProgress<ProcessingBundle> {
            MiXCRMain.lm.reportApplicationInputs(
                true, false,
                allInputFiles,
                paramsSpecPacked.base.consistentHashString(),
                cmdParams.tagPattern.toString(),
                emptyList()
            )

            return when (val inputType = inputFileGroups.inputType) {
                BAM -> {
                    if (inputFileGroups.fileGroups.size != 1)
                        throw ValidationException("File concatenation supported only for fastq files.")
                    val files = inputFileGroups.fileGroups.first().files
                    val reader = BAMReader(
                        files,
                        cmdParams.bamDropNonVDJ,
                        cmdParams.replaceWildcards,
                        tempDest,
                        referenceForCram
                    ).map { ProcessingBundle.fromRead(it, it.weight()) }
                    when (pairedPatternPayload) {
                        null -> reader
                        true -> reader.onEach { record ->
                            ValidationException.require(record.read is PairedRead) {
                                "Tag pattern require BAM file to contain only paired reads"
                            }
                        }

                        false -> reader.onEach { record ->
                            ValidationException.require(record.read is SingleRead) {
                                "Tag pattern require BAM file to contain only single reads"
                            }
                        }
                    }
                }

                Fasta -> {
                    if (inputFileGroups.fileGroups.size != 1 || inputFileGroups.fileGroups.first().files.size != 1)
                        throw ValidationException("File concatenation not supported for fasta files.")
                    val inputFile = inputFileGroups.fileGroups.first().files.first()
                    FastaSequenceReaderWrapper(
                        FastaReader(inputFile.toFile(), NucleotideSequence.ALPHABET),
                        cmdParams.replaceWildcards
                    ).map { ProcessingBundle.fromRead(it, it.weight()) }
                }

                is MIC -> {
                    if (inputFileGroups.fileGroups.size != 1 || inputFileGroups.fileGroups.first().files.size != 1)
                        throw ValidationException("File concatenation not supported for MIC files.")
                    val inputFile = inputFileGroups.fileGroups.first().files.first()
                    val idGenerator = AtomicLong()
                    val reader = MicReader(inputFile)
                    val readTagsShortcuts = inputType.readTags.sorted().map { reader.tagShortcut(it) }
                    val barcodeShortcuts = inputType.barcodes.map { reader.tagShortcut(it) }
                    reader
                        .map { record ->
                            val readId = idGenerator.getAndIncrement()
                            ProcessingBundle.fromRead(
                                MultiRead(
                                    readTagsShortcuts
                                        .map { record.getTagValue(it) }
                                        .map { SingleReadImpl(readId, it.value, "$readId") }
                                        .toTypedArray()
                                ),
                                record.weight.toDouble(),
                                tags = TagTuple(
                                    *barcodeShortcuts.map {
                                        SequenceAndQualityTagValue(record.getTagValue(it).value)
                                    }.toTypedArray()
                                ),
                                originalReadId = readId
                            )
                        }
                        .synchronized()
                        .withExpectedSize(reader.recordCount)
                }

                else -> { // All fastq file types
                    assert(inputFileGroups.inputType.isFastq)
                    assert(inputFileGroups.fileGroups[0].files.size == inputFileGroups.inputType.numberOfReads)
                    FastqGroupReader(inputFileGroups.fileGroups, cmdParams.replaceWildcards, readBufferSize)
                        .map {
                            ProcessingBundle.fromRead(
                                it.read,
                                it.read.weight(),
                                fileTags = it.fileTags,
                                originalReadId = it.originalReadId
                            )
                        }
                }
            }
        }

        private fun SequenceRead.weight(): Double =
            when (val pattern = cmdParams.weightPatternInReadDescription) {
                null -> 1.0
                else -> {
                    val description = ValidationException.requireTheSame(map { it.description }) {
                        "Read descriptions should be the same for parsing weight from it"
                    }
                    val matcher = pattern.matcher(description)
                    ValidationException.require(matcher.find()) {
                        "Can't find weight in read description $description"
                    }
                    matcher.group(1).toDouble()
                }
            }


        override fun validate() {
            checkInputTemplates(inputTemplates)
            ValidationException.requireFileType(referenceForCram, InputFileType.FASTA, InputFileType.FASTA_GZ)
            if (referenceForCram != null) {
                ValidationException.require(inputTemplates.first().matches(InputFileType.CRAM)) {
                    "--reference-for-cram could be specified only with CRAM input"
                }
            }
            ValidationException.requireFileType(outputFile, InputFileType.VDJCA)
            pathsForNotAligned.validate(inputFileGroups.inputType)
            ValidationException.requireFileType(outputFileList, InputFileType.TSV)

            if (cmdParams.library.contains("/") || cmdParams.library.contains("\\")) {
                val libraryLocations = Paths.get(
                    System.getProperty("user.home"),
                    ".mixcr",
                    "libraries",
                    "mylibrary.json"
                ).toString()
                throw ValidationException(
                    "Library name can't be a path. Place your library to one of the library search locations " +
                            "(e.g. '$libraryLocations', and put just a library name as -b / --library option value (e.g. '--library mylibrary')."
                )
            }

            if (strictMatching && inputSampleSheet == null)
                throw ValidationException("$STRICT_SAMPLE_NAME_MATCHING_OPTION is valid only with sample sheet input, i.e. a *.tsv file.")
        }

        /**
         * Alignment report
         */
        private val reportBuilder = AlignerReportBuilder()
        private val qualityTrimmerParameters: QualityTrimmerParameters
            get() = QualityTrimmerParameters(
                cmdParams.trimmingQualityThreshold.toFloat(),
                cmdParams.trimmingWindowSize.toInt()
            )

        override fun run1() {
            // Saving initial timestamp
            val beginTimestamp = System.currentTimeMillis()

            // Printing library level warnings, if specified for the library
            if (!vdjcLibrary.warnings.isEmpty()) {
                logger.warnUnfomatted("Library warnings:")
                for (l in vdjcLibrary.warnings) logger.warnUnfomatted(l)
            }

            // Printing citation notice, if specified for the library
            if (!vdjcLibrary.citations.isEmpty()) {
                logger.warnUnfomatted("Please cite:")
                for (l in vdjcLibrary.citations) logger.warnUnfomatted(l)
            }

            // Tags
            val tagsExtractor = when (val inputType = inputFileGroups.inputType) {
                is MIC -> {
                    val barcodeTags = inputType.barcodes.mapIndexed { index, tag ->
                        TagInfo(
                            TagType.detectByTagName(tag)!!,
                            TagValueType.SequenceAndQuality,
                            tag,
                            index
                        )
                    }

                    getTagsExtractor(
                        cmdParams.copy(tagPattern = null),
                        inputFileGroups.tags,
                        barcodeTags
                    )
                }

                else -> getTagsExtractor(cmdParams, inputFileGroups.tags)
            }

            // Validating output tags if required
            for (tagsValidation in cmdParams.tagsValidations)
                tagsValidation.validate(tagsExtractor.tagsInfo)

            // Validating count of inputs with tag pattern
            tagsExtractor.usedReadsCount?.let { requiredInputs ->
                when (val inputType = inputFileGroups.inputType) {
                    BAM -> ValidationException.require(requiredInputs <= 2) {
                        "Can't use pattern with more than 2 reads with BAM input"
                    }

                    else -> ValidationException.require(inputType.numberOfReads == requiredInputs) {
                        "Tag pattern require $requiredInputs input ${if (requiredInputs == 1) "file" else "files"}, got ${inputType.numberOfReads}"
                    }
                }
            }

            // structure of final NSQTuple
            val readsCountInTuple = when (val inputType = inputFileGroups.inputType) {
                is MIC -> when (inputType.readTags.size) {
                    0 -> throw ValidationException("No read tags in pattern")
                    1 -> VDJCAligner.ReadsCount.ONE
                    2 -> VDJCAligner.ReadsCount.TWO
                    else -> throw ValidationException("More then 2 read tags in pattern")
                }

                else -> when (tagsExtractor.pairedPatternPayload) {
                    null -> when (inputFileGroups.inputType.numberOfReads) {
                        -1 -> {
                            check(inputFileGroups.inputType == BAM)
                            VDJCAligner.ReadsCount.ONE_OR_TWO
                        }

                        1 -> VDJCAligner.ReadsCount.ONE
                        2 -> VDJCAligner.ReadsCount.TWO
                        else -> throw ValidationException("Triple and quad fastq inputs require tag pattern for parsing.")
                    }

                    true -> VDJCAligner.ReadsCount.TWO
                    false -> VDJCAligner.ReadsCount.ONE
                }
            }

            // Creating aligner
            val aligner = VDJCAligner.createAligner(
                alignerParameters,
                readsCountInTuple,
                cmdParams.overlapPairedReads
            )
            var numberOfExcludedNFGenes = 0
            var numberOfExcludedFGenes = 0
            for (gene in getGenesForAligning()) {
                alignerParameters.getFeatureToAlign(gene.geneType) ?: continue

                val featureSequence = alignerParameters.extractFeatureToAlign(gene)

                var exclusionReason: String? = null
                if (featureSequence == null) exclusionReason =
                    "absent " + encode(alignerParameters.getFeatureToAlign(gene.geneType))
                else if (featureSequence.containsWildcards()) exclusionReason =
                    "wildcard symbols in " + encode(alignerParameters.getFeatureToAlign(gene.geneType))

                // exclusionReason is null ==> gene is not excluded
                if (exclusionReason == null)
                    aligner.addGene(gene) // If there are no reasons to exclude the gene, adding it to aligner
                else {
                    if (gene.isFunctional) {
                        ++numberOfExcludedFGenes
                        if (logger.verbose) logger.warn("Functional gene " + gene.name + " excluded due to " + exclusionReason)
                    } else ++numberOfExcludedNFGenes
                }
            }
            if (numberOfExcludedFGenes > 0) logger.warn(
                "$numberOfExcludedFGenes functional genes were excluded, " +
                        "re-run with --verbose option to see the list of excluded genes and exclusion reason."
            )
            if (logger.verbose && numberOfExcludedNFGenes > 0)
                logger.warn("$numberOfExcludedNFGenes non-functional genes excluded because they are not covering feature to align.")
            if (aligner.vGenesToAlign.isEmpty()) throw ApplicationException(
                "No V genes to align. Aborting execution. See warnings for more info " +
                        "(turn on verbose warnings by adding --verbose option)."
            )
            if (aligner.jGenesToAlign.isEmpty()) throw ApplicationException(
                "No J genes to align. Aborting execution. See warnings for more info " +
                        "(turn on verbose warnings by adding --verbose option)."
            )
            reportBuilder.setStartMillis(beginTimestamp)
            reportBuilder.setInputFiles(inputFiles)
            reportBuilder.setOutputFiles(outputFiles)
            reportBuilder.commandLine = commandLineArguments

            // Attaching report to aligner
            aligner.setEventsListener(reportBuilder)

            use(
                createReader(tagsExtractor.pairedPatternPayload),
                alignedWriter(outputFile, tagsExtractor.sampleTags),
                failedReadsWriter(
                    pathsForNotAligned.notAlignedI1,
                    pathsForNotAligned.notAlignedI2,
                    pathsForNotAligned.notAlignedR1,
                    pathsForNotAligned.notAlignedR2
                ),
                failedReadsWriter(
                    pathsForNotAligned.notParsedI1,
                    pathsForNotAligned.notParsedI2,
                    pathsForNotAligned.notParsedR1,
                    pathsForNotAligned.notParsedR2
                )
            ) { reader, writers, notAlignedWriter, notParsedWriter ->
                var paramsBefore = MiXCRStepParams()
                if (inputFileGroups.inputType is MIC) {
                    check(inputFileGroups.fileGroups.size == 1)
                    val inputFile = inputFileGroups.fileGroups.first().files.first()
                    val upstream = MicReader(inputFile).use { it.header.stepParams }

                    fun <P : MiToolParams> MiXCRStepParams.withMiToolParams(
                        source: MiToolStepParams, miToolCommand: MiToolCommandDescriptor<P, *>
                    ): MiXCRStepParams {
                        val mixcrCommand = MiToolCommandDelegationDescriptor.byMitoolCommand(miToolCommand)
                        var result = this
                        for (params in source[miToolCommand]) {
                            result = result.add(mixcrCommand, MiToolParamsDelegate(params))
                        }
                        return result
                    }

                    upstream.steps.forEach { step ->
                        paramsBefore = paramsBefore.withMiToolParams(upstream, MiToolCommandDescriptor.fromString(step))
                    }
                }
                val header = MiXCRHeader(
                    inputHash,
                    dontSavePresetOption.presetToSave(paramsSpecPacked),
                    paramsBefore.add(AnalyzeCommandDescriptor.align, cmdParams),
                    tagsExtractor.tagsInfo,
                    aligner.parameters,
                    aligner.parameters.featuresToAlignMap,
                    null,
                    null,
                    null,
                    false,
                    null
                )
                writers?.writeHeader { writer ->
                    writer.writeHeader(header, aligner.usedGenes)
                }
                val sReads = when {
                    cmdParams.limit != null -> reader.limit(cmdParams.limit!!)
                    else -> reader
                }

                // Shifting indels in homopolymers is effective only for alignments build with linear gap scoring,
                // consolidating some gaps, on the contrary, for alignments obtained with affine scoring such procedure
                // may break the alignment (gaps there are already consolidated as much as possible)
                val gtRequiringIndelShifts = alignerParameters.geneTypesWithLinearScoring
                val emptyHits = EnumMap<GeneType, Array<VDJCHit>>(GeneType::class.java)
                for (gt in GeneType.values()) if (alignerParameters.getGeneAlignerParameters(gt) != null) emptyHits[gt] =
                    emptyArray()
                val readsLayout = alignerParameters.readsLayout
                SmartProgressReporter.startProgressReport("Alignment", sReads)
                val mainInputReads = sReads
                    .chunked(64)
                    .buffered(max(16, threads.value))

                val step0 =
                    mainInputReads.mapUnchunked { bundle ->
                        val parsed = tagsExtractor.parse(bundle)
                        if (parsed.status == NotParsed)
                            reportBuilder.onFailedAlignment(VDJCAlignmentFailCause.NoBarcode, bundle.weight)
                        if (parsed.status == NotMatched)
                            reportBuilder.onFailedAlignment(VDJCAlignmentFailCause.SampleNotMatched, bundle.weight)
                        parsed
                    }

                val step1 = if (cmdParams.trimmingQualityThreshold > 0) {
                    val rep = ReadTrimmerReportBuilder()
                    val trimmerProcessor = ReadTrimmerProcessor(
                        qualityTrimmerParameters,
                        rep,
                        weightSupplier = { record -> record.weight }
                    ) { read: NSQTuple, mapper ->
                        read.map(mapper)
                    }
                    reportBuilder.setTrimmingReportBuilder(rep)
                    step0.mapUnchunked {
                        it.mapSequence(trimmerProcessor::process)
                    }
                } else
                    step0

                val step2 = step1.mapChunksInParallel(
                    bufferSize = max(16, threads.value),
                    threads = threads.value
                ) { bundle ->
                    if (bundle.ok) {
                        var alignment = aligner.process(bundle.sequence, bundle.read)
                            ?: return@mapChunksInParallel bundle.copy(status = NotAligned)
                        alignment = alignment
                            .withTagCount(TagCount(bundle.tags))
                            .shiftIndelsAtHomopolymers(gtRequiringIndelShifts)
                        if (cmdParams.parameters.isSaveOriginalReads)
                            alignment = alignment.withOriginalReads(arrayOf(bundle.read))
                        bundle.copy(alignment = alignment, status = Good)
                    } else
                        bundle
                }

                step2
                    .unchunked()
                    .ordered { it.read.id }
                    .forEach { bundle ->
                        if (bundle.status == NotParsed || bundle.status == NotMatched)
                            notParsedWriter?.write(bundle.read)
                        if (bundle.status == NotAligned)
                            notAlignedWriter?.write(bundle.read)

                        val alignment = when {
                            bundle.alignment != null -> bundle.alignment!!

                            cmdParams.writeFailedAlignments && bundle.status == NotAligned -> {
                                // Creating an empty alignment object if alignment for current read failed
                                val target = readsLayout.createTargets(bundle.sequence)[0]
                                VDJCAlignments(
                                    hits = emptyHits,
                                    tagCount = if (bundle.tags == TagTuple.NO_TAGS)
                                        TagCount.NO_TAGS else TagCount(bundle.tags),
                                    targets = target.targets,
                                    history = SequenceHistory.RawSequence.of(
                                        bundle.read.id,
                                        target,
                                        bundle.sequence.weight
                                    ),
                                    originalSequences = if (alignerParameters.isSaveOriginalSequence) arrayOf(bundle.sequence) else null,
                                    originalReads = if (alignerParameters.isSaveOriginalSequence) arrayOf(bundle.read) else null
                                )
                            }

                            else -> return@forEach
                        }

                        if (alignment.isChimera)
                            reportBuilder.onChimera(alignment.weight)

                        writers?.get(if (cmdParams.splitBySample) bundle.sample else emptyList())?.write(alignment)
                    }

                // Stats
                val stats = tagsExtractor.sampleStats.values.sortedBy { -it.reads.get() }
                val cumsum = stats.runningFold(0L) { acc, sampleStat -> acc + sampleStat.reads.get() }
                val cutOff =
                    cumsum.indexOfFirst { it >= cumsum.last() * 95 / 100 }.let { if (it < 0) stats.size else it }
                val cleanStats = stats.take(cutOff)
                MiXCRMain.lm.reportApplicationInputs(
                    false, true,
                    allInputFiles,
                    paramsSpecPacked.base.consistentHashString(),
                    cmdParams.tagPattern.toString(),
                    cleanStats.map { it.hash.get().toString() }
                )

                // If nothing was written, writing empty file with empty key
                if (writers?.keys?.isEmpty() == true)
                    writers[emptyList()]

                writers?.keys?.forEach { sample ->
                    writers[sample].setNumberOfProcessedReads(
                        if (sample.isEmpty())
                            tagsExtractor.inputReads.get()
                        else
                            tagsExtractor.sampleStats[sample]!!.reads.get()
                    )
                }

                reportBuilder.setFinishMillis(System.currentTimeMillis())

                if (tagsExtractor.reportAgg != null) reportBuilder.setTagReport(tagsExtractor.reportAgg!!.report)
                reportBuilder.setNotMatchedByHeader(tagsExtractor.notMatchedByHeader.get())
                reportBuilder.setTransformerReports(tagsExtractor.transformerReports)

                val report = reportBuilder.buildReport()
                var reportsBefore = MiXCRStepReports()
                val thresholds: CriticalThresholdCollection
                if (inputFileGroups.inputType is MIC) {
                    check(inputFileGroups.fileGroups.size == 1)
                    val inputFile = inputFileGroups.fileGroups.first().files.first()
                    val footer = MicReader(inputFile).use { it.footer }

                    fun <R : MiToolReport> MiXCRStepReports.withMiToolReports(
                        source: MiTollStepReports, miToolCommand: MiToolCommandDescriptor<*, R>
                    ): MiXCRStepReports {
                        val mixcrCommand = MiToolCommandDelegationDescriptor.byMitoolCommand(miToolCommand)
                        var result = this
                        for (params in source[miToolCommand]) {
                            result = result.add(mixcrCommand, MiToolReportsDelegate(params))
                        }
                        return result
                    }

                    footer.reports.steps.forEach { step ->
                        reportsBefore = reportsBefore.withMiToolReports(
                            footer.reports, MiToolCommandDescriptor.fromString(step)
                        )
                    }
                    thresholds = footer.thresholds
                } else {
                    thresholds = CriticalThresholdCollection()
                }

                val footer =
                    MiXCRFooter(reportsBefore, thresholds).addStepReport(AnalyzeCommandDescriptor.align, report)
                writers?.setFooter { it.setFooter(footer) }

                // Writing report to stout
                ReportUtil.writeReportToStdout(report)
                reportOptions.appendToFiles(report)
            }
        }

        private fun getGenesForAligning() =
            if (alignOnAllVariants) {
                vdjcLibrary.getAllGenes(Chains.parse(cmdParams.chains))
                    .filterNot { it.data.isAlias }
            } else {
                vdjcLibrary.getPrimaryGenes(Chains.parse(cmdParams.chains))
            }

        @Suppress("UNCHECKED_CAST")
        private fun failedReadsWriter(i1: Path?, i2: Path?, r1: Path?, r2: Path?): SequenceWriter<SequenceRead>? =
            when (r1) {
                null -> null
                else -> when (val inputType = inputFileGroups.inputType) {
                    SingleEndFastq -> SingleFastqWriter(r1.toFile())
                    PairedEndFastq -> PairedFastqWriter(r1.toFile(), r2!!.toFile())
                    TripleEndFastq -> MultiFastqWriter(false, i1!!, r1, r2!!)
                    QuadEndFastq -> MultiFastqWriter(false, i1!!, i2!!, r1, r2!!)
                    BAM -> MultiFastqWriter(true, r1, r2!!)
                    is MIC -> when (inputType.readTags.size) {
                        1 -> MultiFastqWriter(false, r1.toFile())
                        2 -> MultiFastqWriter(false, r1.toFile(), r2!!.toFile())
                        else -> throw IllegalArgumentException("Can't create writer for more than 2 fastq files")
                    }

                    else -> throw ValidationException(
                        "Export of reads for which alignment / parsing failed allowed only for fastq inputs."
                    ) // must never happen because of parameters validation
                } as SequenceWriter<SequenceRead>
            }

        private fun alignedWriter(outputFile: Path, sampleTags: List<TagInfo>) =
            when (outputFile.toString()) {
                "." -> null
                else -> MultiSampleRun.writer(
                    outputFile,
                    outputFileList?.let {
                        MultiSampleRun.SampleNameWriter(it, sampleTags.map { tagInfo -> tagInfo.name })
                    }
                ) { path ->
                    VDJCAlignmentsWriter(
                        path,
                        SemaphoreWithInfo(max(1, threads.value / 8)),
                        VDJCAlignmentsWriter.DEFAULT_ALIGNMENTS_IN_BLOCK,
                        highCompression
                    )
                }
            }

        companion object {
            private val libraryNameEnding: Pattern = Pattern.compile("\\.json(?:\\.gz|)$")
        }
    }
}
