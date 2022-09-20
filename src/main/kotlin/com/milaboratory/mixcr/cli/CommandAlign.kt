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
import cc.redberry.pipe.Processor
import cc.redberry.pipe.blocks.Merger
import cc.redberry.pipe.util.Chunk
import cc.redberry.pipe.util.CountLimitingOutputPort
import cc.redberry.pipe.util.StatusReporter
import cc.redberry.pipe.util.StatusReporter.StatusProvider
import com.fasterxml.jackson.annotation.JsonMerge
import com.fasterxml.jackson.annotation.JsonProperty
import com.milaboratory.cli.POverridesBuilderOps
import com.milaboratory.core.io.CompressionType
import com.milaboratory.core.io.sequence.*
import com.milaboratory.core.io.sequence.fasta.FastaReader
import com.milaboratory.core.io.sequence.fasta.FastaSequenceReaderWrapper
import com.milaboratory.core.io.sequence.fastq.PairedFastqReader
import com.milaboratory.core.io.sequence.fastq.PairedFastqWriter
import com.milaboratory.core.io.sequence.fastq.SingleFastqReader
import com.milaboratory.core.io.sequence.fastq.SingleFastqWriter
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.core.sequence.quality.QualityTrimmerParameters
import com.milaboratory.core.sequence.quality.ReadTrimmerProcessor
import com.milaboratory.milm.MiXCRMain
import com.milaboratory.mitool.helpers.expandPathNPattern
import com.milaboratory.mitool.pattern.search.*
import com.milaboratory.mitool.pattern.search.ReadSearchPlan.Companion.create
import com.milaboratory.mitool.report.ParseReport
import com.milaboratory.mixcr.MiXCRCommand
import com.milaboratory.mixcr.MiXCRParams
import com.milaboratory.mixcr.MiXCRParamsBundle
import com.milaboratory.mixcr.MiXCRParamsSpec
import com.milaboratory.mixcr.bam.BAMReader
import com.milaboratory.mixcr.basictypes.*
import com.milaboratory.mixcr.basictypes.tag.*
import com.milaboratory.mixcr.vdjaligners.VDJCAligner
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters
import com.milaboratory.mixcr.vdjaligners.VDJCAlignmentFailCause
import com.milaboratory.mixcr.vdjaligners.VDJCAlignmentResult
import com.milaboratory.primitivio.*
import com.milaboratory.util.CanReportProgress
import com.milaboratory.util.ReportHelper
import com.milaboratory.util.ReportUtil
import com.milaboratory.util.SmartProgressReporter
import io.repseq.core.Chains
import io.repseq.core.GeneFeature.*
import io.repseq.core.GeneType
import io.repseq.core.VDJCLibrary
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine.*
import java.io.FileInputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.regex.Pattern
import java.util.stream.Collectors
import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.math.max

object CommandAlign {
    const val COMMAND_NAME = "align"

    data class Params(
        @JsonProperty("species") val species: String = "",
        @JsonProperty("libraryName") val library: String = "default",
        @JsonProperty("trimmingQualityThreshold") val trimmingQualityThreshold: Byte,
        @JsonProperty("trimmingWindowSize") val trimmingWindowSize: Byte,
        @JsonProperty("chains") val chains: String = "ALL",
        @JsonProperty("overlapPairedReads") val overlapPairedReads: Boolean = true,
        @JsonProperty("bamDropNonVDJ") val bamDropNonVDJ: Boolean = false,
        @JsonProperty("writeFailedAlignments") val writeFailedAlignments: Boolean = false,
        @JsonProperty("tagPattern") val tagPattern: String? = null,
        @JsonProperty("tagUnstranded") val tagUnstranded: Boolean = false,
        @JsonProperty("tagMaxBudget") val tagMaxBudget: Double,
        @JsonProperty("limit") val limit: Long? = null,
        @JsonProperty("parameters") @JsonMerge val parameters: VDJCAlignerParameters,
    ) : MiXCRParams {
        override val command get() = MiXCRCommand.align
    }

    abstract class CmdBase : MiXCRPresetAwareCommand<Params>() {
        @Option(description = [CommonDescriptions.SPECIES], names = ["-s", "--species"])
        private var species: String? = null

        @Option(
            description = ["V/D/J/C gene library"],
            names = ["-b", "--library"],
            paramLabel = "library"
        )
        private var library: String? = null

        @Option(names = ["-O"], description = ["Overrides aligner parameters from the selected preset"])
        private var overrides: Map<String, String> = mutableMapOf()

        @Option(
            description = ["Read pre-processing: trimming quality threshold"],
            names = ["--trimming-quality-threshold"]
        )
        private var trimmingQualityThreshold: Byte? = null

        @Option(description = ["Read pre-processing: trimming window size"], names = ["--trimming-window-size"])
        private var trimmingWindowSize: Byte? = null

        @Option(
            description = ["Specifies immunological chain / gene(s) for alignment. If many, separate by comma ','. " +
                    "%nAvailable chains: IGH, IGL, IGK, TRA, TRB, TRG, TRD, etc..."],
            names = ["-c", "--chains"],
            hidden = true
        )
        private var chains: String? = null
            set(value) {
                warn(
                    "Don't use --chains option on the alignment step. See --chains parameter in exportAlignments and " +
                            "exportClones actions to limit output to a subset of receptor chains."
                )
                field = value
            }

        @Option(
            description = ["Do not merge paired reads."],
            names = ["-d", "--no-merge"],
            hidden = true
        )
        private var noMerge = false

        @Option(
            description = ["Drop reads from bam file mapped on human chromosomes except with VDJ region (2, 7, 14, 22)"],
            names = ["--drop-non-vdj"],
            hidden = true
        )
        private var dropNonVDJ = false

        @Option(
            description = ["Write alignment results for all input reads (even if alignment failed)."],
            names = ["--write-all"]
        )
        private var writeAllResults = false

        private class TagPatternOptions {
            @Option(
                description = ["Tag pattern to extract from the read."],
                names = ["--tag-pattern"]
            )
            var tagPatternString: String? = null

            @Option(
                description = ["Read tag pattern from a file."],
                names = ["--tag-pattern-file"]
            )
            var tagPatternFile: String? = null
        }

        @ArgGroup(exclusive = true, multiplicity = "0..1")
        private val tagPattern: TagPatternOptions? = null

        @Option(
            description = ["If paired-end input is used, determines whether to try all combinations of mate-pairs or " +
                    "only match reads to the corresponding pattern sections (i.e. first file to first section, etc...)"],
            names = ["--tag-parse-unstranded"]
        )
        private var tagUnstranded = false

        @Option(
            description = ["Maximal bit budget, higher values allows more substitutions in small letters."],
            names = ["--tag-max-budget"]
        )
        private var tagMaxBudget: Double? = null

        @Option(
            description = ["Copy original reads (sequences + qualities + descriptions) to .vdjca file."],
            names = ["-g", "--save-reads"],
            hidden = true
        )
        private var saveReads = false

        @Option(description = ["Maximal number of reads to process"], names = ["-n", "--limit"])
        private var limit: Long? = null

        override val paramsResolver = object : MiXCRParamsResolver<Params>(this, MiXCRParamsBundle::align) {
            override fun POverridesBuilderOps<Params>.paramsOverrides() {
                Params::species setIfNotNull species

                if (overrides.isNotEmpty()) {
                    // Printing warning message for some common mistakes in parameter overrides
                    for ((key) in overrides) if ("Parameters.parameters.relativeMinScore" == key.substring(1)) warn(
                        "WARNING: most probably you want to change \"${key[0]}Parameters.relativeMinScore\" " +
                                "instead of \"${key[0]}Parameters.parameters.relativeMinScore\". " +
                                "The latter should be touched only in a very specific cases."
                    )
                    Params::parameters jsonOverrideWith overrides
                }

                Params::library setIfNotNull library
                Params::trimmingQualityThreshold setIfNotNull trimmingQualityThreshold
                Params::trimmingWindowSize setIfNotNull trimmingWindowSize
                Params::overlapPairedReads resetIfTrue noMerge
                Params::bamDropNonVDJ setIfTrue dropNonVDJ
                Params::writeFailedAlignments setIfTrue writeAllResults
                tagPattern?.apply {
                    Params::tagPattern setIfNotNull tagPatternString
                    Params::tagPattern setIfNotNull tagPatternFile?.let { Path(it) }?.readText()
                }
                Params::tagUnstranded setIfTrue tagUnstranded
                Params::tagMaxBudget setIfNotNull tagMaxBudget

                if (saveReads)
                    Params::parameters.updateBy {
                        it.setSaveOriginalReads(true)
                    }

                Params::limit setIfNotNull limit
            }

            override fun validateParams(params: Params) {
                if (params.species.isEmpty())
                    throwValidationExceptionKotlin("Species not set, please use -s / --species option to specified it.")
            }
        }
    }

    @Command(
        name = COMMAND_NAME,
        sortOptions = false,
        description = ["Builds alignments with V,D,J and C genes for input sequencing reads."]
    )
    class Cmd : CmdBase() {
        @Option(
            description = ["Analysis preset. Sets all significant parameters of this and all downstream analysis steps. " +
                    "This is a required parameter. It is very important to carefully select the most appropriate preset " +
                    "for the data you analyse."],
            names = ["-p", "--preset"],
            required = true,
        )
        lateinit var presetName: String

        @ArgGroup(validate = false, heading = "Analysis mix-ins")
        var mixins: AllMiXCRMixins? = null

        @Option(
            description = ["Don't embed preset into the output file"],
            names = ["--dont-embed-preset"]
        )
        var dontEmbedPreset = false

        @Parameters(
            arity = "2..3",
            paramLabel = "files",
            hideParamSyntax = true,
            description = [
                "file_R1.(fastq[.gz]|fasta|bam|sam) [file_R2.(fastq[.gz]|bam|sam)] [file_RN.(bam|sam)] alignments.vdjca",
                "Use {{n}} if you want to concatenate files from multiple lanes, like:",
                "my_file_L{{n}}_R1.fastq.gz my_file_L{{n}}_R2.fastq.gz"
            ]
        )
        private val inOut: List<String> = mutableListOf()

        override fun getInputFiles(): List<String> = inOut.subList(0, inOut.size - 1)

        override fun getOutputFiles(): List<String> = inOut.subList(inOut.size - 1, inOut.size)

        @Option(description = ["Size of buffer for FASTQ readers"], names = ["--read-buffer"])
        var readBufferSize = 1 shl 22 // 4 Mb

        @Option(description = [CommonDescriptions.REPORT], names = ["-r", "--report"])
        var reportFile: String? = null

        @Option(description = [CommonDescriptions.JSON_REPORT], names = ["-j", "--json-report"])
        var jsonReport: String? = null

        @Option(description = ["Processing threads"], names = ["-t", "--threads"])
        var threads = Runtime.getRuntime().availableProcessors()
            set(value) {
                if (value <= 0) throwValidationExceptionKotlin("-t / --threads must be positive")
                field = value
            }

        @Option(
            description = ["Use higher compression for output file, 10~25%% slower, minus 30~50%% of file size."],
            names = ["--high-compression"]
        )
        var highCompression = false


        @Option(description = ["Pipe not aligned R1 reads into separate file."], names = ["--not-aligned-R1"])
        var failedReadsR1: String? = null

        @Option(description = ["Pipe not aligned R2 reads into separate file."], names = ["--not-aligned-R2"])
        var failedReadsR2: String? = null

        @Option(description = ["Show runtime buffer load."], names = ["--buffers"], hidden = true)
        var reportBuffers = false

        private val paramsSpec by lazy { MiXCRParamsSpec(presetName, mixins?.mixins ?: emptyList()) }

        private val bpPair by lazy { paramsResolver.resolve(paramsSpec) }

        private val bundle by lazy { bpPair.first }

        private val cmdParams by lazy { bpPair.second }

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
                warn(
                    "WARNING: forcing -OvParameters.geneFeatureToAlign=${encode(correctingFeature)} " +
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

        private val isInputPaired: Boolean
            get() = inputFiles.size == 2 || isInputBAM

        private val isInputBAM: Boolean
            get() = inputFiles[0].lowercase(Locale.getDefault()).endsWith(".bam")
                    || inputFiles[0].lowercase(Locale.getDefault()).endsWith(".sam")


        private fun createReader(): SequenceReaderCloseable<out SequenceRead> {
            // Common single fastq reader constructor
            val readerFactory: (Path) -> SingleFastqReader = { path: Path ->
                SingleFastqReader(
                    FileInputStream(path.toFile()),
                    SingleFastqReader.DEFAULT_QUALITY_FORMAT,
                    CompressionType.detectCompressionType(inputFiles[0]),
                    false, readBufferSize,
                    true, true
                )
            }
            return if (isInputBAM) {
                val bamNames = inputFiles
                val readers = Array<Path>(bamNames.size) { i ->
                    Paths.get(bamNames[i])
                }
                BAMReader(readers, cmdParams.bamDropNonVDJ, true)
            } else if (isInputPaired) {
                val resolved = inputFiles.map { rf: String -> expandPathNPattern(Paths.get(rf)) }
                MiXCRMain.lm.reportApplicationInputs(resolved.flatten())
                val readers = resolved.map { paths ->
                    ConcatenatingSingleReader(paths.map(readerFactory))
                }
                PairedFastqReader(readers[0], readers[1])
            } else {
                val `in` = inputFiles[0]
                val s = `in`.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                when {
                    s[s.size - 1] == "fasta" || s[s.size - 1] == "fa" -> FastaSequenceReaderWrapper(
                        FastaReader(`in`, NucleotideSequence.ALPHABET),
                        true
                    )

                    else -> {
                        val resolved = expandPathNPattern(Paths.get(`in`))
                        MiXCRMain.lm.reportApplicationInputs(resolved)
                        ConcatenatingSingleReader(resolved.map(readerFactory))
                    }
                }
            }
        }

        private fun getTagPattern(): TagSearchPlan? {
            if (cmdParams.tagPattern == null)
                return null
            val searchSettings = ReadSearchSettings(
                SearchSettings.Default.copy(bitBudget = cmdParams.tagMaxBudget),
                if (isInputPaired) if (cmdParams.tagUnstranded) ReadSearchMode.PairedUnknown else ReadSearchMode.PairedDirect else ReadSearchMode.Single
            )
            val readSearchPlan = create(cmdParams.tagPattern!!, searchSettings)
            val parseInfo = parseTagsFromSet(readSearchPlan.allTags)
            println("The following tags and their roles were recognised:")
            println("  Payload tags: " + java.lang.String.join(", ", parseInfo.readTags))
            parseInfo.tags
                .groupBy { it.type }
                .forEach { (tagType: TagType, tagInfos: List<TagInfo>) ->
                    println("  $tagType tags: " + tagInfos.stream().map { obj: TagInfo -> obj.name }
                        .collect(Collectors.joining()))
                }
            val tagShortcuts = parseInfo.tags
                .map { tagInfo -> readSearchPlan.tagShortcut(tagInfo.name) }
            val readShortcuts = parseInfo.readTags
                .map { name -> readSearchPlan.tagShortcut(name) }
            if (readShortcuts.isEmpty())
                throwValidationExceptionKotlin("Tag pattern has no read (payload) groups, nothing to align.", false)
            if (readShortcuts.size > 2) throwValidationExceptionKotlin(
                "Tag pattern contains too many read groups, only R1 or R1+R2 combinations are supported.",
                false
            )
            if (failedReadsR1 != null) {
                if (failedReadsR2 == null && readShortcuts.size == 2) throwValidationExceptionKotlin(
                    "Option --not-aligned-R2 is not specified but tag pattern defines two payload reads.",
                    false
                )
                if (failedReadsR2 != null && readShortcuts.size == 1) throwValidationExceptionKotlin(
                    "Option --not-aligned-R2 is specified but tag pattern defines only one payload read.",
                    false
                )
            }
            return TagSearchPlan(readSearchPlan, tagShortcuts, readShortcuts, parseInfo.tags)
        }

        override fun inputsMustExist(): Boolean {
            return false
        }

        override fun validate() {
            super.validate()
            if (inOut.size > 3) throwValidationExceptionKotlin("Too many input files.")
            if (inOut.size < 2) throwValidationExceptionKotlin("No output file.")
            if (failedReadsR2 != null && failedReadsR1 == null) throwValidationExceptionKotlin("Wrong input for --not-aligned-R1,2")
            if (failedReadsR1 != null && cmdParams.tagPattern == null && failedReadsR2 != null != isInputPaired) throwValidationExceptionKotlin(
                "Option --not-aligned-R2 is not set.",
                false
            )
            if (cmdParams.library.contains("/") || cmdParams.library.contains("\\")) {
                val libraryLocations = Paths.get(
                    System.getProperty("user.home"),
                    ".mixcr",
                    "libraries",
                    "mylibrary.json"
                ).toString()
                throwValidationExceptionKotlin(
                    "Library name can't be a path. Place your library to one of the library search locations " +
                            "(e.g. '$libraryLocations', and put just a library name as -b / --library option value (e.g. '--library mylibrary').",
                    false
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

        override fun run0() {
            // Saving initial timestamp
            val beginTimestamp = System.currentTimeMillis()

            // Printing library level warnings, if specified for the library
            if (!vdjcLibrary.warnings.isEmpty()) {
                warn("Library warnings:")
                for (l in vdjcLibrary.warnings) warn(l)
            }

            // Printing citation notice, if specified for the library
            if (!vdjcLibrary.citations.isEmpty()) {
                warn("Please cite:")
                for (l in vdjcLibrary.citations) warn(l)
            }

            // Tags
            val tagSearchPlan = getTagPattern()
            val pairedPayload = if (tagSearchPlan != null) tagSearchPlan.readShortcuts.size == 2 else isInputPaired

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
                        if (verbose) warn("WARNING: Functional gene " + gene.name + " excluded due to " + exclusionReason)
                    } else ++numberOfExcludedNFGenes
                }
            }
            if (numberOfExcludedFGenes > 0) warn(
                "WARNING: $numberOfExcludedFGenes functional genes were excluded, " +
                        "re-run with --verbose option to see the list of excluded genes and exclusion reason."
            )
            if (verbose && numberOfExcludedNFGenes > 0) warn("WARNING: $numberOfExcludedNFGenes non-functional genes excluded.")
            if (aligner.vGenesToAlign.isEmpty()) throwExecutionExceptionKotlin(
                "No V genes to align. Aborting execution. See warnings for more info " +
                        "(turn on verbose warnings by adding --verbose option)."
            )
            if (aligner.jGenesToAlign.isEmpty()) throwExecutionExceptionKotlin(
                "No J genes to align. Aborting execution. See warnings for more info " +
                        "(turn on verbose warnings by adding --verbose option)."
            )
            reportBuilder.setStartMillis(beginTimestamp)
            reportBuilder.setInputFiles(inputFiles)
            reportBuilder.setOutputFiles(outputFiles)
            reportBuilder.commandLine = commandLineArguments
            if (tagSearchPlan != null) reportBuilder.tagReportBuilder = tagSearchPlan.report

            // Attaching report to aligner
            aligner.setEventsListener(reportBuilder)
            val outputFile = outputFiles[0]
            createReader().use { reader ->
                alignedWriter(outputFile).use { writer ->
                    notAlignedWriter(pairedPayload).use { notAlignedWriter: SequenceWriter<SequenceRead>? ->
                        writer?.writeHeader(
                            MiXCRHeader(
                                paramsSpec,
                                if (tagSearchPlan != null) TagsInfo(
                                    0,
                                    *tagSearchPlan.tagInfos.toTypedArray()
                                ) else TagsInfo.NO_TAGS,
                                aligner.parameters,
                                null,
                                null,
                                null
                            ),
                            aligner.usedGenes
                        )
                        val sReads: OutputPort<out SequenceRead> = when {
                            cmdParams.limit != null -> CountLimitingOutputPort(reader, cmdParams.limit!!)
                            else -> reader
                        }

                        val progress: CanReportProgress = when (sReads) {
                            is CountLimitingOutputPort -> SmartProgressReporter.extractProgress(sReads)
                            is CanReportProgress -> sReads
                            else -> throw IllegalArgumentException()
                        }


                        // Shifting indels in homopolymers is effective only for alignments build with linear gap scoring,
                        // consolidating some gaps, on the contrary, for alignments obtained with affine scoring such procedure
                        // may break the alignment (gaps there are already consolidated as much as possible)
                        val gtRequiringIndelShifts = alignerParameters.geneTypesWithLinearScoring
                        val emptyHits = EnumMap<GeneType, Array<VDJCHit?>>(GeneType::class.java)
                        for (gt in GeneType.values()) if (alignerParameters.getGeneAlignerParameters(gt) != null) emptyHits[gt] =
                            arrayOfNulls(0)
                        val readsLayout = alignerParameters.readsLayout
                        SmartProgressReporter.startProgressReport("Alignment", progress)
                        @Suppress("UNCHECKED_CAST")
                        val mainInputReads: Merger<Chunk<out SequenceRead>> = (sReads
                            .chunked(64) as OutputPort<Chunk<out SequenceRead>>)
                            .buffered(max(16, threads))
                        val readTrimmerProcessor: ReadTrimmerProcessor<SequenceRead>?
                        if (cmdParams.trimmingQualityThreshold > 0) {
                            val rep = ReadTrimmerReportBuilder()
                            readTrimmerProcessor = ReadTrimmerProcessor(qualityTrimmerParameters, rep)
                            reportBuilder.setTrimmingReportBuilder(rep)
                        } else {
                            readTrimmerProcessor = null
                        }

                        // Creating processor from aligner
                        val processor: Processor<SequenceRead, VDJCAlignmentResult<SequenceRead>> = when {
                            tagSearchPlan != null -> Processor { input: SequenceRead ->
                                val parsed = tagSearchPlan.parse(input)
                                if (parsed == null) {
                                    reportBuilder.onFailedAlignment(input, VDJCAlignmentFailCause.NoBarcode)
                                    return@Processor VDJCAlignmentResult(input)
                                }
                                val read = when (readTrimmerProcessor) {
                                    null -> parsed.payloadRead
                                    else -> readTrimmerProcessor.process(parsed.payloadRead)
                                }
                                aligner(read).withTagTuple(parsed.tags)
                            }

                            else -> aligner
                        }

                        val mainInputReadsPreprocessed: OutputPort<Chunk<out SequenceRead>> = when {
                            tagSearchPlan != null || readTrimmerProcessor == null -> mainInputReads
                            else -> mainInputReads.map { readTrimmerProcessor(it) }
                        }

                        val alignedChunks = mainInputReadsPreprocessed.mapInParallel(
                            bufferSize = max(16, threads),
                            threads = threads
                        ) { chunk -> processor(chunk) }
                        if (reportBuffers) {
                            checkNotNull(writer)
                            println("Analysis threads: $threads")
                            val reporter = StatusReporter()
                            reporter.addBuffer("Input (chunked; chunk size = 64)", mainInputReads.bufferStatusProvider)
                            reporter.addBuffer(
                                "Alignment result (chunked; chunk size = 64)",
                                alignedChunks.outputBufferStatusProvider
                            )
                            reporter.addCustomProvider(object : StatusProvider {
                                @Suppress("ObjectPropertyName")
                                @Volatile
                                var _status: String = ""

                                @Volatile
                                var isClosed = false
                                override fun updateStatus() {
                                    _status = "Busy encoders: " + writer.busyEncoders + " / " + writer.encodersCount
                                    isClosed = writer.isClosed
                                }

                                override fun isFinished(): Boolean = isClosed

                                override fun getStatus(): String = _status
                            })
                            reporter.start()
                        }
                        val alignments = alignedChunks
                            .unchunked()
                            .map { it.shiftIndelsAtHomopolymers(gtRequiringIndelShifts) }

                        alignments
                            .ordered { it.read.id }
                            .forEach { result ->
                                val read = result.read
                                var alignment = when {
                                    result.alignment != null -> result.alignment
                                    cmdParams.writeFailedAlignments -> {
                                        // Creating empty alignment object if alignment for current read failed
                                        val target = readsLayout.createTargets(read)[0]
                                        VDJCAlignments(
                                            emptyHits,
                                            if (result.tagTuple == null) TagCount.NO_TAGS_1 else TagCount(result.tagTuple),
                                            target.targets,
                                            SequenceHistory.RawSequence.of(read.id, target),
                                            if (alignerParameters.isSaveOriginalReads) arrayOf(read) else null
                                        )
                                    }

                                    else -> null
                                }
                                if (alignment == null) {
                                    notAlignedWriter?.write(result.read)
                                    return@forEach
                                }
                                alignment = alignment.setTagCount(
                                    if (result.tagTuple == null) TagCount.NO_TAGS_1 else TagCount(result.tagTuple)
                                )
                                if (alignment.isChimera) reportBuilder.onChimera()
                                writer?.write(alignment)
                            }
                        writer?.setNumberOfProcessedReads(reader.numberOfReads)
                        reportBuilder.setFinishMillis(System.currentTimeMillis())
                        val report = reportBuilder.buildReport()
                        writer?.setFooter(MiXCRFooter(listOf(report)))

                        // Writing report to stout
                        ReportUtil.writeReportToStdout(report)
                        if (reportFile != null) ReportUtil.appendReport(reportFile, report)
                        if (jsonReport != null) ReportUtil.appendJsonReport(jsonReport, report)
                    }
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun notAlignedWriter(pairedPayload: Boolean): SequenceWriter<SequenceRead>? = when (failedReadsR1) {
            null -> null
            else -> when {
                pairedPayload -> PairedFastqWriter(failedReadsR1, failedReadsR2) as SequenceWriter<SequenceRead>
                else -> SingleFastqWriter(failedReadsR1) as SequenceWriter<SequenceRead>
            }
        }

        private fun alignedWriter(outputFile: String) = when (outputFile) {
            "." -> null
            else -> VDJCAlignmentsWriter(
                outputFile, max(1, threads / 8),
                VDJCAlignmentsWriter.DEFAULT_ALIGNMENTS_IN_BLOCK, highCompression
            )
        }

        private class TaggedSequence(val tags: TagTuple, val payloadRead: SequenceRead)

        private class TagSearchPlan(
            private val plan: ReadSearchPlan,
            private val tagShortcuts: List<ReadTagShortcut>, val readShortcuts: List<ReadTagShortcut>,
            val tagInfos: List<TagInfo>
        ) {
            val report = ParseReport(plan)

            fun parse(read: SequenceRead): TaggedSequence? {
                val result = plan.search(read)
                report.consume(result)
                if (result.hit == null) return null
                val tags = tagShortcuts
                    .map { readTagShortcut: ReadTagShortcut ->
                        SequenceAndQualityTagValue(result.getTagValue(readTagShortcut).value)
                    }
                    .toTypedArray()
                val reads = Array<SingleRead>(readShortcuts.size) { i ->
                    SingleReadImpl(
                        read.id,
                        result.getTagValue(readShortcuts[i]).value,
                        if (read.numberOfReads() <= i) read.getRead(0).description else read.getRead(i).description
                    )
                }
                return TaggedSequence(TagTuple(*tags), SequenceReadUtil.construct(*reads))
            }
        }

        data class ParseInfo(
            val tags: List<TagInfo>,
            val readTags: List<String>
        )

        private fun parseTagsFromSet(names: Set<String>): ParseInfo {
            val tags: MutableList<TagInfo> = ArrayList()
            val readTags: MutableList<String> = ArrayList()
            for (name in names) {
                when {
                    name.startsWith("S") -> tags += TagInfo(
                        TagType.Sample,
                        TagValueType.SequenceAndQuality,
                        name,
                        0
                    )

                    name.startsWith("CELL") -> tags += TagInfo(
                        TagType.Cell,
                        TagValueType.SequenceAndQuality,
                        name,
                        0
                    )

                    name.startsWith("UMI") || name.startsWith("MI") -> tags += TagInfo(
                        TagType.Molecule,
                        TagValueType.SequenceAndQuality,
                        name,
                        0
                    )

                    name.matches(Regex("R\\d+")) -> readTags += name
                    else -> warn("Can't recognize tag type for name \"$name\", this tag will be ignored during analysis.")
                }
            }
            tags
                .map { it.type }
                .distinct()
                .forEach { tagType -> MiXCRMain.lm.reportFeature("mixcr.tag-type", tagType.toString()) }
            return ParseInfo(
                tags
                    .sorted()
                    .mapIndexed { i, tag -> tag.withIndex(i) },
                readTags.sorted()
            )
        }

        companion object {
            private val libraryNameEnding: Pattern = Pattern.compile("\\.json(?:\\.gz|)$")
        }
    }
}
