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


import cc.redberry.pipe.util.buffered
import cc.redberry.pipe.util.chunked
import cc.redberry.pipe.util.forEach
import cc.redberry.pipe.util.mapChunksInParallel
import cc.redberry.pipe.util.mapUnchunked
import cc.redberry.pipe.util.ordered
import cc.redberry.pipe.util.unchunked
import com.fasterxml.jackson.annotation.JsonMerge
import com.fasterxml.jackson.annotation.JsonProperty
import com.milaboratory.app.ApplicationException
import com.milaboratory.app.InputFileType
import com.milaboratory.app.ValidationException
import com.milaboratory.app.logger
import com.milaboratory.app.matches
import com.milaboratory.cli.POverridesBuilderOps
import com.milaboratory.core.io.CompressionType
import com.milaboratory.core.io.sequence.MultiReader
import com.milaboratory.core.io.sequence.SequenceRead
import com.milaboratory.core.io.sequence.SequenceReadUtil
import com.milaboratory.core.io.sequence.SequenceReader
import com.milaboratory.core.io.sequence.SequenceWriter
import com.milaboratory.core.io.sequence.fasta.FastaReader
import com.milaboratory.core.io.sequence.fasta.FastaSequenceReaderWrapper
import com.milaboratory.core.io.sequence.fastq.MultiFastqWriter
import com.milaboratory.core.io.sequence.fastq.PairedFastqReader
import com.milaboratory.core.io.sequence.fastq.PairedFastqWriter
import com.milaboratory.core.io.sequence.fastq.SingleFastqReader
import com.milaboratory.core.io.sequence.fastq.SingleFastqWriter
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.core.sequence.quality.QualityTrimmerParameters
import com.milaboratory.core.sequence.quality.ReadTrimmerProcessor
import com.milaboratory.milm.MiXCRMain
import com.milaboratory.mitool.helpers.FileGroup
import com.milaboratory.mitool.helpers.PathPatternExpandException
import com.milaboratory.mitool.helpers.parseAndRunAndCorrelateFSPattern
import com.milaboratory.mitool.use
import com.milaboratory.mixcr.AlignMixins.LimitInput
import com.milaboratory.mixcr.MiXCRCommandDescriptor
import com.milaboratory.mixcr.MiXCRParams
import com.milaboratory.mixcr.MiXCRParamsBundle
import com.milaboratory.mixcr.MiXCRParamsSpec
import com.milaboratory.mixcr.MiXCRStepParams
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
import com.milaboratory.mixcr.cli.CommandAlign.Cmd.InputType.PairedEndFastq
import com.milaboratory.mixcr.cli.CommandAlign.Cmd.InputType.QuadEndFastq
import com.milaboratory.mixcr.cli.CommandAlign.Cmd.InputType.SingleEndFastq
import com.milaboratory.mixcr.cli.CommandAlign.Cmd.InputType.TripleEndFastq
import com.milaboratory.mixcr.cli.CommandAlignPipeline.ProcessingBundle
import com.milaboratory.mixcr.cli.CommandAlignPipeline.ProcessingBundleStatus.Good
import com.milaboratory.mixcr.cli.CommandAlignPipeline.ProcessingBundleStatus.NotAligned
import com.milaboratory.mixcr.cli.CommandAlignPipeline.ProcessingBundleStatus.NotParsed
import com.milaboratory.mixcr.cli.CommandAlignPipeline.ProcessingBundleStatus.SampleNotMatched
import com.milaboratory.mixcr.cli.CommandAlignPipeline.cellSplitGroupLabel
import com.milaboratory.mixcr.cli.CommandAlignPipeline.getTagsExtractor
import com.milaboratory.mixcr.cli.CommonDescriptions.DEFAULT_VALUE_FROM_PRESET
import com.milaboratory.mixcr.cli.CommonDescriptions.Labels
import com.milaboratory.mixcr.cli.MiXCRCommand.OptionsOrder
import com.milaboratory.mixcr.util.toHexString
import com.milaboratory.mixcr.vdjaligners.VDJCAligner
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters
import com.milaboratory.mixcr.vdjaligners.VDJCAlignmentFailCause
import com.milaboratory.util.CanReportProgress
import com.milaboratory.util.LightFileDescriptor
import com.milaboratory.util.OutputPortWithProgress
import com.milaboratory.util.ReportHelper
import com.milaboratory.util.ReportUtil
import com.milaboratory.util.SmartProgressReporter
import com.milaboratory.util.limit
import io.repseq.core.Chains
import io.repseq.core.GeneFeature.VRegion
import io.repseq.core.GeneFeature.VRegionWithP
import io.repseq.core.GeneFeature.encode
import io.repseq.core.GeneType
import io.repseq.core.VDJCGene
import io.repseq.core.VDJCLibrary
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine.ArgGroup
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Model.PositionalParamSpec
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.FileInputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.Semaphore
import java.util.regex.Pattern
import kotlin.collections.component1
import kotlin.collections.set
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.math.max

object CommandAlign {
    const val COMMAND_NAME = "align"

    /** Defines specific mapping between tag values and sample name (i.e. one row from sample table) */
    data class SampleTableRow(
        @JsonProperty("matchTags") val matchTags: SortedMap<String, String> = TreeMap(),
        @JsonProperty("matchVariantId") val matchVariantId: Int? = null,
        @JsonProperty("sampleName") val sampleName: String
    ) {
        init {
            require(matchTags.isNotEmpty() || matchVariantId != null)
        }

        fun matchingInfoString() =
            matchVariantId.toString() + " " + matchTags.entries.joinToString("|") { it.key + "-" + it.value }

        override fun toString() =
            sampleName + " " + matchingInfoString()
    }

    /** Whole set of sample tag values to sample name mappings (i.e. sample table) */
    data class SampleTable(
        @JsonProperty("sampleTagName") val sampleTagName: String,
        @JsonProperty("samples") val samples: List<SampleTableRow>
    )

    fun List<SampleTableRow>.bySampleName() = groupBy { it.sampleName }

    data class Params(
        @JsonProperty("species") val species: String = "",
        @JsonProperty("libraryName") val library: String = "default",
        @JsonProperty("trimmingQualityThreshold") val trimmingQualityThreshold: Byte,
        @JsonProperty("trimmingWindowSize") val trimmingWindowSize: Byte,
        @JsonProperty("chains") val chains: String = "ALL",
        @JsonProperty("replaceWildcards") val replaceWildcards: Boolean = true,
        @JsonProperty("overlapPairedReads") val overlapPairedReads: Boolean = true,
        @JsonProperty("bamDropNonVDJ") val bamDropNonVDJ: Boolean = false,
        @JsonProperty("writeFailedAlignments") val writeFailedAlignments: Boolean = false,
        @JsonProperty("tagPattern") val tagPattern: String? = null,
        @JsonProperty("tagUnstranded") val tagUnstranded: Boolean = false,
        @JsonProperty("tagMaxBudget") val tagMaxBudget: Double,
        @JsonProperty("sampleTable") val sampleTable: SampleTable? = null,
        @JsonProperty("splitBySample") val splitBySample: Boolean = true,
        @JsonProperty("readIdAsCellTag") val readIdAsCellTag: Boolean = false,
        @JsonProperty("limit") val limit: Long? = null,
        @JsonProperty("parameters") @JsonMerge val parameters: VDJCAlignerParameters,
    ) : MiXCRParams {
        override val command get() = MiXCRCommandDescriptor.align
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
                    f0.matches(InputFileType.FASTA) -> Fasta
                    f0.matches(InputFileType.BAM) -> BAM
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
        @set:Option(
            description = ["Pipe not aligned I1 reads into separate file."],
            names = ["--not-aligned-I1"],
            paramLabel = "<path.fastq[.gz]>",
            order = OptionsOrder.notAligned + 101
        )
        var notAlignedReadsI1: Path? = null
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
        var notAlignedReadsI2: Path? = null
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
        var notAlignedReadsR1: Path? = null
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
        var notAlignedReadsR2: Path? = null
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
        var notParsedReadsI1: Path? = null
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
        var notParsedReadsI2: Path? = null
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
        var notParsedReadsR1: Path? = null
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
        var notParsedReadsR2: Path? = null
            set(value) {
                ValidationException.requireFileType(value, InputFileType.FASTQ)
                field = value
            }

        private val allowedStates = listOf(
            "",
            "R1",
            "R1,R2",
            "I1,R1,R2",
            "I1,I2,R1,R2",
        )

        fun validate(inputType: Cmd.InputType) {
            fun Any?.tl(value: String) = if (this == null) emptyList() else listOf(value)

            fun checkFailedReadsOptions(
                optionPrefix: String,
                i1: Path?, i2: Path?,
                r1: Path?, r2: Path?
            ) {
                val states =
                    (i1.tl("I1") + i2.tl("I2") + r1.tl("R1") + r2.tl("R2"))
                        .joinToString(",")

                if (!allowedStates.contains(states))
                    throw ValidationException(
                        "Unsupported combination of reads in ${optionPrefix}-*: found $states expected one of " +
                                allowedStates.joinToString(" / ")
                    )

                if (r1 != null) {
                    when {
                        r2 == null && inputType == PairedEndFastq -> throw ValidationException(
                            "Option ${optionPrefix}-R2 is not specified but paired-end input data provided."
                        )

                        r2 != null && inputType == SingleEndFastq -> throw ValidationException(
                            "Option ${optionPrefix}-R2 is specified but single-end input data provided."
                        )

                        !inputType.isFastq -> throw ValidationException(
                            "Option ${optionPrefix}-* options are supported for fastq data input only."
                        )
                    }
                }
            }
            checkFailedReadsOptions(
                "--not-aligned",
                notAlignedReadsI1,
                notAlignedReadsI2,
                notAlignedReadsR1,
                notAlignedReadsR2
            )
            checkFailedReadsOptions(
                "--not-parsed",
                notParsedReadsI1,
                notParsedReadsI2,
                notParsedReadsR1,
                notParsedReadsR2
            )
        }
    }

    fun checkInputTemplates(paths: List<Path>) {
        when (paths.size) {
            1 -> ValidationException.requireFileType(
                paths[0],
                InputFileType.FASTQ,
                InputFileType.FASTA,
                InputFileType.BAM
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
        "([file_I1.fastq[.gz] [file_I2.fastq[.gz]]] file_R1.fastq[.gz] [file_R2.fastq[.gz]]|file.(fasta|bam|sam))"

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

    abstract class CmdBase : MiXCRCommandWithOutputs(), MiXCRPresetAwareCommand<Params> {
        @Option(
            names = ["-O"],
            description = ["Overrides aligner parameters from the selected preset"],
            paramLabel = Labels.OVERRIDES,
            order = OptionsOrder.overrides
        )
        private var overrides: Map<String, String> = mutableMapOf()

        @Option(
            description = [
                "Read pre-processing: trimming quality threshold.",
                "Zero value can be used to skip trimming.",
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
                "  Default tag pattern determined by the preset."
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
                        "only match reads to the corresponding pattern sections (i.e. first file to first section, etc...).",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["--tag-parse-unstranded"],
            order = OptionsOrder.main + 10_400
        )
        private var tagUnstranded = false

        @Option(
            description = [
                "Maximal bit budget, higher values allows more substitutions in small letters.",
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
                logger.warn("--limit and -n options are deprecated; use ${LimitInput.CMD_OPTION} instead.")
                field = value
            }

        override val paramsResolver = object : MiXCRParamsResolver<Params>(MiXCRParamsBundle::align) {
            override fun POverridesBuilderOps<Params>.paramsOverrides() {
                if (overrides.isNotEmpty()) {
                    // Printing warning message for some common mistakes in parameter overrides
                    for ((key) in overrides) if ("Parameters.parameters.relativeMinScore" == key.substring(1)) logger.warn(
                        "most probably you want to change \"${key[0]}Parameters.relativeMinScore\" " +
                                "instead of \"${key[0]}Parameters.parameters.relativeMinScore\". " +
                                "The latter should be touched only in a very specific cases."
                    )
                    Params::parameters jsonOverrideWith overrides
                }

                Params::trimmingQualityThreshold setIfNotNull trimmingQualityThreshold
                Params::trimmingWindowSize setIfNotNull trimmingWindowSize
                Params::bamDropNonVDJ setIfTrue dropNonVDJ
                Params::writeFailedAlignments setIfTrue writeAllResults
                Params::tagPattern setIfNotNull tagPatternFile?.readText()
                Params::tagUnstranded setIfTrue tagUnstranded
                Params::tagMaxBudget setIfNotNull tagMaxBudget
                Params::readIdAsCellTag setIfTrue readIdAsCellTag

                if (saveReads)
                    Params::parameters.updateBy {
                        it.setSaveOriginalReads(true)
                    }

                Params::limit setIfNotNull limit
            }

            override fun validateParams(params: Params) {
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
            description = ["Analysis preset. Sets all significant parameters of this and all downstream analysis steps. " +
                    "This is a required parameter. It is very important to carefully select the most appropriate preset " +
                    "for the data you analyse."],
            names = ["-p", "--preset"],
            paramLabel = "<name>",
            required = true,
            order = OptionsOrder.main + 1000
        )
        lateinit var presetName: String

        @Mixin
        var pipelineMixins: PipelineMiXCRMixinsHidden? = null

        @Mixin
        var alignMixins: AlignMiXCRMixins? = null

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

        @Mixin
        var genericMixins: GenericMiXCRMixins? = null

        private val mixins: MiXCRMixinCollection
            get() = MiXCRMixinCollection.empty + pipelineMixins + alignMixins + assembleMixins +
                    assembleContigsMixins + exportMixins + genericMixins

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

        /** I.e. list of mate-pair files */
        private val inputFileGroups: InputFileGroups by lazy {
            try {
                InputFileGroups(inputTemplates.parseAndRunAndCorrelateFSPattern())
            } catch (e: PathPatternExpandException) {
                throw ValidationException(e.message!!)
            }
        }

        override val inputFiles get() = inputFileGroups.allFiles

        override val outputFiles get() = listOf(outputFile)

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

        @Option(
            description = ["Use higher compression for output file, 10~25%% slower, minus 30~50%% of file size."],
            names = ["--high-compression"],
            order = OptionsOrder.main + 10_800
        )
        var highCompression = false

        @Mixin
        lateinit var pathsForNotAligned: PathsForNotAligned

        @Option(description = ["Show runtime buffer load."], names = ["--buffers"], hidden = true)
        var reportBuffers = false

        private val paramsSpec by lazy { MiXCRParamsSpec(presetName, mixins.mixins) }

        private val bpPair by lazy { paramsResolver.resolve(paramsSpec, printParameters = logger.verbose) }

        private val cmdParams get() = bpPair.second

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
            for (gene in vdjcLibrary.getGenes(Chains.parse(cmdParams.chains))) {
                if (gene.geneType == GeneType.Variable) {
                    totalV++
                    if (!parameters.containsRequiredFeature(gene)) {
                        totalVErrors++
                        if (gene.partitioning.isAvailable(correctingFeature)) hasVRegion++
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
        enum class InputType(val numberOfReads: Int, val isFastq: Boolean) {
            SingleEndFastq(1, true),
            PairedEndFastq(2, true),
            TripleEndFastq(3, true),
            QuadEndFastq(4, true),
            Fasta(1, false),
            BAM(2, false);

            val pairedRecords: Boolean?
                get() = when (numberOfReads) {
                    1 -> false
                    2 -> true
                    else -> null
                }
        }

        abstract class FastqGroupReader(fileGroups: List<FileGroup>) : OutputPortWithProgress<ProcessingBundle> {
            private val fileGroupIt = fileGroups.iterator()
            private val readerCount = fileGroups.size
            private var currentReaderIdx = -1

            // -1 used to indicate closed stream
            private var id = 0L
            private var currentReader: SequenceReader<SequenceRead>? = null
            private var currentFileTags: List<Pair<String, String>>? = null

            override fun take(): ProcessingBundle? {
                synchronized(fileGroupIt) {
                    if (id == -1L)
                        return null
                    while (true) {
                        currentReader
                            ?.take()
                            ?.let {
                                return ProcessingBundle(
                                    SequenceReadUtil.setReadId(id++, it),
                                    currentFileTags!!,
                                    it.id
                                )
                            }
                        // Terminating the stream if there are no more reads and readers
                        if (!fileGroupIt.hasNext()) {
                            id = -1L
                            return null
                        }
                        // Closing existing reader, if any
                        currentReader?.close()
                        val nextGroup = fileGroupIt.next()
                        // Opening new reader and saving nexct files tags
                        currentReader = nextReader(nextGroup)
                        currentFileTags = nextGroup.tags
                        currentReaderIdx++
                    }
                }
            }

            abstract fun nextReader(fileGroup: FileGroup): SequenceReader<SequenceRead>

            override fun close() {
                synchronized(fileGroupIt) {
                    // Closing existing reader, if any
                    currentReader?.close()
                    // Prevents any values to be returned by the stream
                    id = -1L
                }
            }

            override fun getProgress(): Double {
                if (currentReaderIdx == -1)
                    return 0.0
                return (currentReaderIdx + (currentReader as CanReportProgress).progress) / readerCount
            }

            override fun isFinished() = id == -1L
        }

        @Suppress("UNCHECKED_CAST")
        private fun createReader(): OutputPortWithProgress<ProcessingBundle> {
            // Common single fastq reader constructor
            val fastqReaderFactory: (Path) -> SingleFastqReader = { path: Path ->
                SingleFastqReader(
                    FileInputStream(path.toFile()),
                    SingleFastqReader.DEFAULT_QUALITY_FORMAT,
                    CompressionType.detectCompressionType(path.name),
                    false, readBufferSize,
                    cmdParams.replaceWildcards, true
                )
            }

            val sampleDescriptions = cmdParams.sampleTable?.samples
                ?.bySampleName()
                ?.values?.map { matchers ->
                    matchers.joinToString(" | ") { it.matchingInfoString() }
                }
                ?: emptyList()

            val keyParamter = cmdParams.tagPattern.toString()

            return when (inputFileGroups.inputType) {
                BAM -> {
                    if (inputFileGroups.fileGroups.size != 1)
                        throw ValidationException("File concatenation supported only for fastq files.")
                    val files = inputFileGroups.fileGroups.first().files
                    MiXCRMain.lm.reportApplicationInputs(files, keyParamter, sampleDescriptions)
                    BAMReader(files.toTypedArray(), cmdParams.bamDropNonVDJ, cmdParams.replaceWildcards)
                        .map { ProcessingBundle(it) }
                }

                Fasta -> {
                    if (inputFileGroups.fileGroups.size != 1 || inputFileGroups.fileGroups.first().files.size != 1)
                        throw ValidationException("File concatenation supported only for fastq files.")
                    val inputFile = inputFileGroups.fileGroups.first().files.first()
                    MiXCRMain.lm.reportApplicationInputs(listOf(inputFile), keyParamter, sampleDescriptions)
                    FastaSequenceReaderWrapper(
                        FastaReader(inputFile.toFile(), NucleotideSequence.ALPHABET),
                        cmdParams.replaceWildcards
                    )
                        .map { ProcessingBundle(it) }
                }

                SingleEndFastq -> {
                    MiXCRMain.lm.reportApplicationInputs(inputFileGroups.allFiles, keyParamter, sampleDescriptions)
                    object : FastqGroupReader(inputFileGroups.fileGroups) {
                        override fun nextReader(fileGroup: FileGroup) =
                            fastqReaderFactory(fileGroup.files[0]) as SequenceReader<SequenceRead>
                    }
                }

                PairedEndFastq -> {
                    MiXCRMain.lm.reportApplicationInputs(inputFileGroups.allFiles, keyParamter, sampleDescriptions)
                    object : FastqGroupReader(inputFileGroups.fileGroups) {
                        override fun nextReader(fileGroup: FileGroup) =
                            PairedFastqReader(
                                fastqReaderFactory(fileGroup.files[0]),
                                fastqReaderFactory(fileGroup.files[1])
                            ) as SequenceReader<SequenceRead>
                    }
                }

                else -> { // More than 2 reads
                    MiXCRMain.lm.reportApplicationInputs(inputFileGroups.allFiles, keyParamter, sampleDescriptions)
                    assert(inputFileGroups.fileGroups[0].files.size == inputFileGroups.inputType.numberOfReads)
                    object : FastqGroupReader(inputFileGroups.fileGroups) {
                        override fun nextReader(fileGroup: FileGroup) =
                            MultiReader(
                                *fileGroup.files.map(fastqReaderFactory).toTypedArray()
                            ) as SequenceReader<SequenceRead>
                    }
                }
            }
        }

        override fun validate() {
            checkInputTemplates(inputTemplates)
            ValidationException.requireFileType(outputFile, InputFileType.VDJCA)
            pathsForNotAligned.validate(inputFileGroups.inputType)

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
            val tagsExtractor = getTagsExtractor(cmdParams, inputFileGroups.tags)

            // true if final NSQTuple will have two reads, false otherwise
            val pairedPayload = tagsExtractor.pairedPatternPayload
                ?: inputFileGroups.inputType.pairedRecords
                ?: throw ValidationException("Triple and quad fastq inputs require tag pattern for parsing.")

            // Creating aligner
            val aligner = VDJCAligner.createAligner(
                alignerParameters,
                pairedPayload,
                cmdParams.overlapPairedReads
            )
            var numberOfExcludedNFGenes = 0
            var numberOfExcludedFGenes = 0
            for (gene in vdjcLibrary.getGenes(Chains.parse(cmdParams.chains))) {
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
            if (logger.verbose && numberOfExcludedNFGenes > 0) logger.warn("$numberOfExcludedNFGenes non-functional genes excluded.")
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
                createReader(),
                alignedWriter(outputFile),
                failedReadsWriter(
                    pathsForNotAligned.notAlignedReadsI1,
                    pathsForNotAligned.notAlignedReadsI2,
                    pathsForNotAligned.notAlignedReadsR1,
                    pathsForNotAligned.notAlignedReadsR2
                ),
                failedReadsWriter(
                    pathsForNotAligned.notParsedReadsI1,
                    pathsForNotAligned.notParsedReadsI2,
                    pathsForNotAligned.notParsedReadsR1,
                    pathsForNotAligned.notParsedReadsR2
                )
            ) { reader, writers, notAlignedWriter, notParsedWriter ->
                // Pre-create all writers
                val samples = tagsExtractor.samples
                if (samples == null || !cmdParams.splitBySample)
                    writers?.get("")
                else
                    samples.forEach { sample -> writers?.get(sample) }

                writers?.writeHeader(
                    MiXCRHeader(
                        inputHash,
                        paramsSpec,
                        MiXCRStepParams().add(MiXCRCommandDescriptor.align, cmdParams),
                        tagsExtractor.tagsInfo,
                        aligner.parameters,
                        aligner.parameters.featuresToAlignMap,
                        null,
                        null,
                        null
                    ),
                    aligner.usedGenes
                )
                val sReads = when {
                    cmdParams.limit != null -> reader.limit(cmdParams.limit!!)
                    else -> reader
                }

                // Shifting indels in homopolymers is effective only for alignments build with linear gap scoring,
                // consolidating some gaps, on the contrary, for alignments obtained with affine scoring such procedure
                // may break the alignment (gaps there are already consolidated as much as possible)
                val gtRequiringIndelShifts = alignerParameters.geneTypesWithLinearScoring
                val emptyHits = EnumMap<GeneType, Array<VDJCHit?>>(GeneType::class.java)
                for (gt in GeneType.values()) if (alignerParameters.getGeneAlignerParameters(gt) != null) emptyHits[gt] =
                    arrayOfNulls(0)
                val readsLayout = alignerParameters.readsLayout
                SmartProgressReporter.startProgressReport("Alignment", sReads)
                val mainInputReads = sReads
                    .chunked(64)
                    .buffered(max(16, threads.value))

                val step0 =
                    mainInputReads.mapUnchunked {
                        val parsed = tagsExtractor.parse(it)
                        if (parsed.status == NotParsed)
                            reportBuilder.onFailedAlignment(VDJCAlignmentFailCause.NoBarcode)
                        if (parsed.status == SampleNotMatched)
                            reportBuilder.onFailedAlignment(VDJCAlignmentFailCause.SampleNotMatched)
                        parsed
                    }

                val step1 = if (cmdParams.trimmingQualityThreshold > 0) {
                    val rep = ReadTrimmerReportBuilder()
                    val trimmerProcessor = ReadTrimmerProcessor(qualityTrimmerParameters, rep)
                    reportBuilder.setTrimmingReportBuilder(rep)
                    step0.mapUnchunked {
                        it.mapSequence(trimmerProcessor::process)
                    }
                } else
                    step0

                val step2 = step1.mapChunksInParallel(
                    bufferSize = max(16, threads.value),
                    threads = threads.value
                ) {
                    if (it.ok) {
                        var alignment =
                            aligner.process(it.sequence, it.read)?.setTagCount(TagCount(it.tags))
                        if (cmdParams.parameters.isSaveOriginalReads)
                            alignment = alignment?.setOriginalReads(it.read)
                        alignment = alignment?.shiftIndelsAtHomopolymers(gtRequiringIndelShifts)
                        it.copy(
                            alignment = alignment,
                            status = if (alignment == null) NotAligned else Good
                        )
                    } else
                        it
                }

                //if (reportBuffers) {
                //    checkNotNull(writer)
                //    println("Analysis threads: $threads")
                //    val reporter = StatusReporter()
                //    reporter.addBuffer(
                //        "Input (chunked; chunk size = 64)",
                //        mainInputReads.bufferStatusProvider
                //    )
                //    reporter.addBuffer(
                //        "Alignment result (chunked; chunk size = 64)",
                //        step2.outputBufferStatusProvider
                //    )
                //    reporter.addCustomProvider(object : StatusProvider {
                //        @Suppress("ObjectPropertyName")
                //        @Volatile
                //        var _status: String = ""
                //
                //        @Volatile
                //        var isClosed = false
                //        override fun updateStatus() {
                //            _status = "Busy encoders: " + writer.busyEncoders + " / " + writer.encodersCount
                //            isClosed = writer.isClosed
                //        }
                //
                //        override fun isFinished(): Boolean = isClosed
                //
                //        override fun getStatus(): String = _status
                //    })
                //    reporter.start()
                //}

                step2
                    .unchunked()
                    .ordered { it.read.id }
                    .forEach { bundle ->
                        if (bundle.status == NotParsed || bundle.status == SampleNotMatched)
                            notParsedWriter?.write(bundle.read)
                        if (bundle.status == NotAligned)
                            notAlignedWriter?.write(bundle.read)

                        val alignment = when {
                            bundle.alignment != null -> bundle.alignment

                            cmdParams.writeFailedAlignments -> {
                                // Creating empty alignment object if alignment for current read failed
                                val target = readsLayout.createTargets(bundle.sequence)[0]
                                var a = VDJCAlignments(
                                    emptyHits,
                                    if (bundle.tags == TagTuple.NO_TAGS)
                                        TagCount.NO_TAGS_1 else TagCount(bundle.tags),
                                    target.targets,
                                    SequenceHistory.RawSequence.of(bundle.read.id, target),
                                    if (alignerParameters.isSaveOriginalSequence) arrayOf(bundle.sequence) else null
                                )
                                if (alignerParameters.isSaveOriginalReads)
                                    a = a.setOriginalReads(bundle.read)
                                a
                            }

                            else -> return@forEach
                        }

                        if (alignment.isChimera)
                            reportBuilder.onChimera()

                        writers?.get(if (cmdParams.splitBySample) bundle.sample else "")?.write(alignment)
                    }

                writers?.setNumberOfProcessedReads(tagsExtractor.inputReads.get())
                reportBuilder.setFinishMillis(System.currentTimeMillis())
                if (tagsExtractor.reportAgg != null) reportBuilder.setTagReport(tagsExtractor.reportAgg.report)
                reportBuilder.setSampleStat(tagsExtractor.sampleStat)
                val report = reportBuilder.buildReport()
                writers?.setFooter(MiXCRFooter().addStepReport(MiXCRCommandDescriptor.align, report))

                // Writing report to stout
                ReportUtil.writeReportToStdout(report)
                reportOptions.appendToFiles(report)
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun failedReadsWriter(i1: Path?, i2: Path?, r1: Path?, r2: Path?): SequenceWriter<SequenceRead>? =
            when (r1) {
                null -> null
                else -> when (inputFileGroups.inputType) {
                    PairedEndFastq -> PairedFastqWriter(r1.toFile(), r2!!.toFile()) as SequenceWriter<SequenceRead>
                    SingleEndFastq -> SingleFastqWriter(r1.toFile()) as SequenceWriter<SequenceRead>
                    TripleEndFastq -> MultiFastqWriter(i1!!, r1, r2!!) as SequenceWriter<SequenceRead>
                    QuadEndFastq -> MultiFastqWriter(i1!!, i2!!, r1, r2!!) as SequenceWriter<SequenceRead>
                    else -> throw ApplicationException(
                        "Export of reads for which alignment / parsing failed allowed only for fastq inputs."
                    ) // must never happen because of parameters validation
                }
            }

        private fun alignedWriter(outputFile: Path): Writers? =
            when (outputFile.toString()) {
                "." -> null
                else -> {
                    object : Writers() {
                        override fun writerFactory(sampleName: String) = run {
                            VDJCAlignmentsWriter(
                                outputFile.resolveSibling(
                                    addSampleToFileName(outputFile.fileName.toString(), sampleName)
                                ),
                                concurrencyLimiter, VDJCAlignmentsWriter.DEFAULT_ALIGNMENTS_IN_BLOCK, highCompression
                            )
                        }
                    }
                }
            }

        abstract inner class Writers : AutoCloseable {
            private var header: MiXCRHeader? = null
            private var genes: List<VDJCGene>? = null
            private val writers = mutableMapOf<String, VDJCAlignmentsWriter>()
            protected val concurrencyLimiter: Semaphore = Semaphore(max(1, threads.value / 8))

            abstract fun writerFactory(sampleName: String): VDJCAlignmentsWriter

            fun writeHeader(header: MiXCRHeader, genes: List<VDJCGene>) {
                if (this.header != null)
                    throw IllegalStateException()

                for (writer in writers.values)
                    writer.writeHeader(header, genes)
                this.header = header
                this.genes = genes
            }

            operator fun get(sample: String) =
                writers.computeIfAbsent(sample) {
                    val writer = writerFactory(it)
                    if (header != null)
                        writer.writeHeader(header!!, genes!!)
                    writer
                }

            fun setFooter(footer: MiXCRFooter) {
                for (writer in writers.values)
                    writer.setFooter(footer)
            }

            fun setNumberOfProcessedReads(value: Long) {
                for (writer in writers.values)
                    writer.setNumberOfProcessedReads(value)
            }

            override fun close() {
                var re: Exception? = null
                for (w in writers.values)
                    try {
                        w.close()
                    } catch (e: Exception) {
                        if (re == null)
                            re = e
                        else
                            re.addSuppressed(e)
                    }
                if (re != null)
                    throw RuntimeException(re)
            }
        }

        companion object {
            private val libraryNameEnding: Pattern = Pattern.compile("\\.json(?:\\.gz|)$")
        }
    }

    fun addSampleToFileName(fileName: String, sampleName: String): String {
        val dotIndex = fileName.lastIndexOf('.')
        val prefix = fileName.substring(0, dotIndex)
        val extension = fileName.substring(dotIndex)
        val insert = if (sampleName == "") "" else ".$sampleName"
        return prefix + insert + extension
    }
}
