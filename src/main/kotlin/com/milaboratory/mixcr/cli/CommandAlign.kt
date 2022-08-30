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
import com.milaboratory.core.io.CompressionType
import com.milaboratory.core.io.sequence.ConcatenatingSingleReader
import com.milaboratory.core.io.sequence.SequenceRead
import com.milaboratory.core.io.sequence.SequenceReadUtil
import com.milaboratory.core.io.sequence.SequenceReaderCloseable
import com.milaboratory.core.io.sequence.SequenceWriter
import com.milaboratory.core.io.sequence.SingleRead
import com.milaboratory.core.io.sequence.SingleReadImpl
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
import com.milaboratory.mitool.pattern.LibraryStructurePresetCollection.LibraryStructurePreset
import com.milaboratory.mitool.pattern.LibraryStructurePresetCollection.getPresetByName
import com.milaboratory.mitool.pattern.search.MatcherSettings.Companion.Default
import com.milaboratory.mitool.pattern.search.ReadSearchMode
import com.milaboratory.mitool.pattern.search.ReadSearchPlan
import com.milaboratory.mitool.pattern.search.ReadSearchPlan.Companion.create
import com.milaboratory.mitool.pattern.search.ReadSearchSettings
import com.milaboratory.mitool.pattern.search.ReadTagShortcut
import com.milaboratory.mitool.pattern.search.SearchSettings
import com.milaboratory.mitool.report.ParseReport
import com.milaboratory.mixcr.basictypes.MiXCRMetaInfo
import com.milaboratory.mixcr.basictypes.SequenceHistory
import com.milaboratory.mixcr.basictypes.VDJCAlignments
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter
import com.milaboratory.mixcr.basictypes.VDJCHit
import com.milaboratory.mixcr.basictypes.tag.SequenceAndQualityTagValue
import com.milaboratory.mixcr.basictypes.tag.TagCount
import com.milaboratory.mixcr.basictypes.tag.TagInfo
import com.milaboratory.mixcr.basictypes.tag.TagTuple
import com.milaboratory.mixcr.basictypes.tag.TagType
import com.milaboratory.mixcr.basictypes.tag.TagValueType
import com.milaboratory.mixcr.basictypes.tag.TagsInfo
import com.milaboratory.mixcr.util.Defaults.default3
import com.milaboratory.mixcr.vdjaligners.VDJCAligner
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters
import com.milaboratory.mixcr.vdjaligners.VDJCAlignmentFailCause
import com.milaboratory.mixcr.vdjaligners.VDJCAlignmentResult
import com.milaboratory.mixcr.vdjaligners.VDJCParametersPresets
import com.milaboratory.primitivio.buffered
import com.milaboratory.primitivio.chunked
import com.milaboratory.primitivio.forEach
import com.milaboratory.primitivio.invoke
import com.milaboratory.primitivio.map
import com.milaboratory.primitivio.mapInParallel
import com.milaboratory.primitivio.ordered
import com.milaboratory.primitivio.unchunked
import com.milaboratory.util.CanReportProgress
import com.milaboratory.util.GlobalObjectMappers
import com.milaboratory.util.JsonOverrider
import com.milaboratory.util.ReportHelper
import com.milaboratory.util.ReportUtil
import com.milaboratory.util.SmartProgressReporter
import io.repseq.core.Chains
import io.repseq.core.GeneFeature.VRegion
import io.repseq.core.GeneFeature.VRegionWithP
import io.repseq.core.GeneFeature.encode
import io.repseq.core.GeneType
import io.repseq.core.VDJCLibrary
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.regex.Pattern
import java.util.stream.Collectors
import kotlin.math.max

@CommandLine.Command(
    name = CommandAlign.ALIGN_COMMAND_NAME,
    sortOptions = false,
    separator = " ",
    description = ["Builds alignments with V,D,J and C genes for input sequencing reads."]
)
class CommandAlign : MiXCRCommand() {
    @CommandLine.Parameters(
        arity = "2..3",
        paramLabel = "files",
        hideParamSyntax = true,
        description = ["""file_R1.(fastq[.gz]|fasta) [file_R2.fastq[.gz]] alignments.vdjca
Use "{{n}}" if you want to concatenate files from multiple lanes, like:
my_file_L{{n}}_R1.fastq.gz my_file_L{{n}}_R2.fastq.gz"""]
    )
    private val inOut: List<String> = mutableListOf()

    override fun getInputFiles(): List<String> = inOut.subList(0, inOut.size - 1)

    override fun getOutputFiles(): List<String> = inOut.subList(inOut.size - 1, inOut.size)

    @CommandLine.Option(description = ["Size of buffer for FASTQ readers"], names = ["--read-buffer"])
    var readBufferSize = 1 shl 22

    @CommandLine.Option(description = [CommonDescriptions.SPECIES], names = ["-s", "--species"], required = true)
    lateinit var species: String

    @CommandLine.Option(description = [CommonDescriptions.REPORT], names = ["-r", "--report"])
    var reportFile: String? = null

    @CommandLine.Option(description = [CommonDescriptions.JSON_REPORT], names = ["-j", "--json-report"])
    var jsonReport: String? = null

    @CommandLine.Option(
        description = ["V/D/J/C gene library"],
        names = ["-b", "--library"],
        paramLabel = "library"
    )
    var libraryName = "default"

    @CommandLine.Option(description = ["Processing threads"], names = ["-t", "--threads"])
    var threads = Runtime.getRuntime().availableProcessors()
        set(value) {
            if (value <= 0) throwValidationExceptionKotlin("-t / --threads must be positive")
            field = value
        }

    @CommandLine.Option(
        description = ["Use higher compression for output file, 10~25%% slower, minus 30~50%% of file size."],
        names = ["--high-compression"]
    )
    var highCompression = false
    var limit: Long = 0

    @CommandLine.Option(description = ["Maximal number of reads to process"], names = ["-n", "--limit"])
    fun setLimit(limit: Int) {
        if (limit <= 0) throwValidationExceptionKotlin("ERROR: -n / --limit must be positive", false)
        this.limit = limit.toLong()
    }

    @CommandLine.Option(
        description = ["Read pre-processing: trimming quality threshold"],
        names = ["--trimming-quality-threshold"]
    )
    var trimmingQualityThreshold: Byte = 0 // 17

    @CommandLine.Option(description = ["Read pre-processing: trimming window size"], names = ["--trimming-window-size"])
    var trimmingWindowSize: Byte = 6 // 3

    @CommandLine.Option(description = ["Parameters preset."], names = ["-p", "--preset"])
    var alignerParametersName = "default"

    @CommandLine.Option(names = ["-O"], description = ["Overrides default aligner parameter values"])
    var overrides: Map<String, String> = mutableMapOf()

    @CommandLine.Option(
        description = ["Specifies immunological chain / gene(s) for alignment. If many, separate by comma ','. " +
                "%nAvailable chains: IGH, IGL, IGK, TRA, TRB, TRG, TRD, etc..."],
        names = ["-c", "--chains"],
        hidden = true
    )
    var chains = "ALL"
        set(value) {
            warn(
                "Don't use --chains option on the alignment step. See --chains parameter in exportAlignments and " +
                        "exportClones actions to limit output to a subset of receptor chains."
            )
            field = value
        }

    @CommandLine.Option(description = ["Do not merge paired reads."], names = ["-d", "--no-merge"], hidden = true)
    var noMerge = false

    @Deprecated("")
    @CommandLine.Option(
        description = ["Copy read(s) description line from .fastq or .fasta to .vdjca file (can then be " +
                "exported with -descrR1 and -descrR2 options in exportAlignments action)."],
        names = ["-a", "--save-description"],
        hidden = true
    )
    fun setSaveReadDescription(b: Boolean) {
        throwValidationExceptionKotlin("--save-description was removed in 3.0: use -OsaveOriginalReads=true instead")
    }

    @CommandLine.Option(
        description = ["Write alignment results for all input reads (even if alignment failed)."],
        names = ["--write-all"]
    )
    var writeAllResults = false

    @Deprecated("")
    @CommandLine.Option(
        description = ["Copy original reads (sequences + qualities + descriptions) to .vdjca file."],
        names = ["-g", "--save-reads"],
        hidden = true
    )
    fun setSaveOriginalReads(b: Boolean) {
        throwValidationExceptionKotlin("--save-reads was removed in 3.0: use -OsaveOriginalReads=true instead")
    }

    @CommandLine.Option(description = ["Pipe not aligned R1 reads into separate file."], names = ["--not-aligned-R1"])
    var failedReadsR1: String? = null

    @CommandLine.Option(description = ["Pipe not aligned R2 reads into separate file."], names = ["--not-aligned-R2"])
    var failedReadsR2: String? = null

    @CommandLine.Option(description = ["Show runtime buffer load."], names = ["--buffers"], hidden = true)
    var reportBuffers = false

    @CommandLine.Option(description = ["Tag pattern to extract from the read."], names = ["--tag-pattern"])
    var tagPattern: String? = null

    @CommandLine.Option(description = ["Read tag pattern from a file."], names = ["--tag-pattern-file"])
    var tagPatternFile: String? = null

    @CommandLine.Option(
        description = ["Tag architecture preset to load from the built-in list. " +
                "This also implies different default settings related to tag processing for other steps executed for the " +
                "output file."], names = ["--tag-preset"]
    )
    var tagPreset: String? = null

    @CommandLine.Option(
        description = ["If paired-end input is used, determines whether to try all combinations of mate-pairs or only match " +
                "reads to the corresponding pattern sections (i.e. first file to first section, etc...)"],
        names = ["--tag-parse-unstranded"]
    )
    var tagUnstranded = false

    @CommandLine.Option(
        description = ["Maximal bit budget, higher values allows more substitutions in small letters. (default: " +
                SearchSettings.DEFAULT_BIT_BUDGET + " or from tag preset)"], names = ["--tag-max-budget"]
    )
    var tagMaxBudget: Double? = null
    val alignerParameters: VDJCAlignerParameters by lazy {
        var alignerParameters: VDJCAlignerParameters
        if (alignerParametersName.endsWith(".json")) {
            alignerParameters = GlobalObjectMappers.getOneLine()
                .readValue(File(alignerParametersName), VDJCAlignerParameters::class.java)
        } else {
            alignerParameters = VDJCParametersPresets.getByName(alignerParametersName)
                ?: throwValidationExceptionKotlin("Unknown aligner parameters: $alignerParametersName")
            if (overrides.isNotEmpty()) {
                // Printing warning message for some common mistakes in parameter overrides
                for ((key) in overrides) if ("Parameters.parameters.relativeMinScore" == key.substring(1)) warn(
                    "WARNING: most probably you want to change \"${key[0]}Parameters.relativeMinScore\" " +
                            "instead of \"${key[0]}Parameters.parameters.relativeMinScore\". " +
                            "The latter should be touched only in a very specific cases."
                )

                // Perform parameters overriding
                alignerParameters =
                    JsonOverrider.override(alignerParameters, VDJCAlignerParameters::class.java, overrides)
                        ?: throwValidationExceptionKotlin("Failed to override some parameter: $overrides")
            }
        }

        // Detect if automatic featureToAlign correction is required
        var totalV = 0
        var totalVErrors = 0
        var hasVRegion = 0
        val correctingFeature = when {
            alignerParameters.vAlignerParameters.geneFeatureToAlign.hasReversedRegions() -> VRegionWithP
            else -> VRegion
        }
        for (gene in vdjcLibrary.getGenes(Chains.parse(chains))) {
            if (gene.geneType == GeneType.Variable) {
                totalV++
                if (!alignerParameters.containsRequiredFeature(gene)) {
                    totalVErrors++
                    if (gene.partitioning.isAvailable(correctingFeature)) hasVRegion++
                }
            }
        }

        // Performing V featureToAlign correction if needed
        if (totalVErrors > totalV * 0.9 && hasVRegion > totalVErrors * 0.8) {
            val currentGenFeature = encode(alignerParameters.vAlignerParameters.geneFeatureToAlign)
            warn(
                "WARNING: forcing -OvParameters.geneFeatureToAlign=${encode(correctingFeature)} " +
                        "since current gene feature ($currentGenFeature) is absent in " +
                        "${ReportHelper.PERCENT_FORMAT.format(100.0 * totalVErrors / totalV)}% of V genes."
            )
            alignerParameters.vAlignerParameters.geneFeatureToAlign = correctingFeature
        }
        alignerParameters
    }

    private val vdjcLibrary: VDJCLibrary by lazy {
        val libraryName = libraryNameEnding.matcher(libraryName).replaceAll("")
        VDJCLibraryRegistry.getDefault().getLibrary(libraryName, species)
    }

    private fun taggedAnalysis(): Boolean = tagPattern != null || tagPreset != null || tagPatternFile != null

    private val isInputPaired: Boolean
        get() = inputFiles.size == 2

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
        return if (isInputPaired) {
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
        val options = listOf(tagPattern, tagPreset, tagPatternFile)
        if (options.all { it == null }) return null
        if (options.count { it != null } != 1)
            throwValidationExceptionKotlin("--tag-pattern, --tag-pattern-name and --tag-pattern-file can't be used together")
        val preset: LibraryStructurePreset? = tagPreset?.let { getPresetByName(it) }
        val tagPattern: String? = when {
            this.tagPattern != null -> this.tagPattern
            preset != null -> preset.pattern
            tagPatternFile != null -> try {
                String(Files.readAllBytes(Paths.get(tagPatternFile!!)))
            } catch (e: IOException) {
                throwValidationExceptionKotlin(e.message!!)
            }
            else -> throw AssertionError()
        }
        println("Tags will be extracted using the following pattern:")
        println(tagPattern)
        val searchSettings = ReadSearchSettings(
            SearchSettings(
                default3(
                    tagMaxBudget,
                    preset,
                    LibraryStructurePreset::maxErrorBudget,
                    SearchSettings.DEFAULT_BIT_BUDGET
                ),
                SearchSettings.DEFAULT_LENGTH_REWARD, Default
            ),
            if (isInputPaired) if (tagUnstranded) ReadSearchMode.PairedUnknown else ReadSearchMode.PairedDirect else ReadSearchMode.Single
        )
        val readSearchPlan = create(tagPattern!!, searchSettings)
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
        if (failedReadsR1 != null && !taggedAnalysis() && failedReadsR2 != null != isInputPaired) throwValidationExceptionKotlin(
            "Option --not-aligned-R2 is not set.",
            false
        )
        if (libraryName.contains("/") || libraryName.contains("\\")) {
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
            trimmingQualityThreshold.toFloat(),
            trimmingWindowSize.toInt()
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
            !noMerge
        )
        var numberOfExcludedNFGenes = 0
        var numberOfExcludedFGenes = 0
        for (gene in vdjcLibrary.getGenes(Chains.parse(chains))) {
            val featureSequence = alignerParameters.extractFeatureToAlign(gene)

            // exclusionReason is null ==> gene is not excluded
            var exclusionReason: String? = null
            if (featureSequence == null) exclusionReason =
                "absent " + encode(alignerParameters.getFeatureToAlign(gene.geneType)) else if (featureSequence.containsWildcards()) exclusionReason =
                "wildcard symbols in " + encode(alignerParameters.getFeatureToAlign(gene.geneType))
            if (exclusionReason == null) aligner.addGene(gene) // If there are no reasons to exclude the gene, adding it to aligner
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
                    writer?.header(
                        MiXCRMetaInfo(
                            tagPreset,
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
                        limit != 0L -> CountLimitingOutputPort(reader, limit)
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
                    if (trimmingQualityThreshold > 0) {
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
                                writeAllResults -> {
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
                    writer?.writeFooter(listOf(report), null)

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
        const val ALIGN_COMMAND_NAME = "align"
        private val libraryNameEnding: Pattern = Pattern.compile("\\.json(?:\\.gz|)$")
    }
}
