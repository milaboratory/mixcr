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
@file:Suppress("ClassName", "EnumEntryName")

package com.milaboratory.mixcr.cli

import com.milaboratory.util.JsonOverrider
import io.repseq.core.Chains
import io.repseq.core.GeneFeature
import picocli.CommandLine
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

abstract class CommandAnalyze : MiXCRCommand() {
    interface WithNameWithDescription {
        val key: String
        val description: String
    }

    private enum class _StartingMaterial(
        override val description: String
    ) : WithNameWithDescription {
        rna("RNA"),
        dna("Genomic DNA");

        override val key: String = this.toString()
    }

    internal enum class _Chains(override val description: String, val chains: Chains) : WithNameWithDescription {
        tcr("All T-cell receptor types (TRA/TRB/TRG/TRD)", Chains.TCR),
        bcr("All B-cell receptor types (IGH/IGK/IGL/TRD)", Chains.IG),
        xcr("All T- and B-cell receptor types", Chains.ALL),
        tra("TRA chain", Chains.TRA),
        trb("TRB chain", Chains.TRB),
        trd("TRD chain", Chains.TRD),
        trg("TRG chain", Chains.TRG),
        igh("IGH chain", Chains.IGH),
        igk("IGK chain", Chains.IGK),
        igl("IGL chain", Chains.IGL);

        override val key: String = toString()
    }

    internal enum class _5EndPrimers(
        override val key: String,
        override val description: String
    ) : WithNameWithDescription {
        noVPrimers("no-v-primers", "No V gene primers (e.g. 5’RACE with template switch oligo or a like)"),
        vPrimers("v-primers", "V gene single primer / multiplex");
    }

    internal enum class _3EndPrimers(
        override val key: String,
        override val description: String
    ) : WithNameWithDescription {
        jPrimers("j-primers", "J gene single primer / multiplex"),
        jcPrimers("j-c-intron-primers", "J-C intron single primer / multiplex"),
        cPrimers(
            "c-primers",
            "C gene single primer / multiplex (e.g. IGHC primers specific to different immunoglobulin isotypes)"
        );
    }

    internal enum class _Adapters(
        override val key: String,
        override val description: String
    ) : WithNameWithDescription {
        adaptersPresent("adapters-present", "May be present"),
        noAdapters("no-adapters", "Absent / nearly absent / trimmed");
    }

    abstract class EnumCandidates private constructor(
        private val names: List<String>
    ) : List<String> by names {
        protected constructor(enum: Class<out WithNameWithDescription>) : this(enum.enumConstants.map { it.key })
    }

    internal class _StartingMaterialCandidates : EnumCandidates(_StartingMaterial::class.java)
    internal class _ChainsCandidates : EnumCandidates(_Chains::class.java)
    internal class _5EndCandidates : EnumCandidates(_5EndPrimers::class.java)
    internal class _3EndCandidates : EnumCandidates(_3EndPrimers::class.java)
    internal class _AdaptersCandidates : EnumCandidates(_Adapters::class.java)

    ///////////////////////////////////////////// Common options /////////////////////////////////////////////
    @CommandLine.Parameters(description = ["input_file1 [input_file2] analysisOutputName"])
    var inOut: List<String> = mutableListOf()

    @CommandLine.Option(description = [CommonDescriptions.SPECIES], names = ["-s", "--species"], required = true)
    var species = "hs"

    @CommandLine.Option(description = ["Aligner parameters preset"], names = ["--align-preset"])
    var alignPreset: String? = null
    protected var chains: Chains = Chains.ALL

    @CommandLine.Option(
        names = ["--receptor-type"],
        completionCandidates = _ChainsCandidates::class,
        description = ["Receptor type. Possible values: \${COMPLETION-CANDIDATES}"],
        required = false
    )
    fun setChains(chains: String) {
        val c: _Chains = parseDescriptor(chains)
            ?: throwValidationExceptionKotlin("Illegal value $chains for --receptor-type option.")
        this.chains = c.chains
    }

    private lateinit var startingMaterial: _StartingMaterial

    @CommandLine.Option(
        names = ["--starting-material"],
        completionCandidates = _StartingMaterialCandidates::class,
        description = ["Starting material. @|bold Possible values: \${COMPLETION-CANDIDATES}|@"],
        required = true
    )
    fun setStartingMaterial(value: String) {
        startingMaterial = parseDescriptor(value)
            ?: throwValidationExceptionKotlin("Illegal value for --starting-material parameter: $value")
    }

    @CommandLine.Option(names = ["--impute-germline-on-export"], description = ["Export germline segments"])
    var exportGermline = false

    @CommandLine.Option(
        names = ["--only-productive"],
        description = ["Filter out-of-frame sequences and clonotypes with stop-codons in " +
                "clonal sequence export"]
    )
    var onlyProductive = false

    @CommandLine.Option(
        names = ["--contig-assembly"], description = ["Assemble longest possible sequences from input data. " +
                "Useful for shotgun-like data." +
                "%nNOTE: this will substantially increase analysis time."]
    )
    var contigAssembly = false

    @CommandLine.Option(names = ["--no-export"], description = ["Do not export clonotypes to tab-delimited file."])
    var noExport = false

    @CommandLine.Option(names = ["-r", "--report"], description = ["Report file path"])
    var report: String? = null

    @CommandLine.Option(
        names = ["-j", "--json-report"], description = ["Output json reports for each of the analysis steps. " +
                "Individual file will be created for each type of analysis step, value specified for this option will be used as a prefix."]
    )
    var jsonReport: String? = null

    @CommandLine.Option(names = ["-b", "--library"], description = ["V/D/J/C gene library"])
    var library = "default"

    @CommandLine.Option(description = ["Processing threads"], names = ["-t", "--threads"])
    var threads = Runtime.getRuntime().availableProcessors()
        set(value) {
            if (value <= 0) throwValidationExceptionKotlin("-t / --threads must be positive")
            field = value
        }

    private val reportFile: String
        get() = report ?: fNameForReport

    private fun <T : MiXCRCommand> inheritOptionsAndValidate(parameters: T): T {
        if (forceOverwrite) parameters.forceOverwrite = true
        parameters.quiet = true
        parameters.validate()
        parameters.quiet = false
        return parameters
    }

    @CommandLine.Option(
        names = ["--align"],
        description = ["Additional parameters for align step specified with double quotes (e.g --align \"--limit 1000\" --align \"-OminSumScore=100\" etc."],
        arity = "1"
    )
    var alignParameters: List<String> = mutableListOf()

    private val cmdAlign: CommandAlign.Cmd by lazy {
        inheritOptionsAndValidate(mkAlign())
    }

    open fun include5UTRInRNA(): Boolean = true

    open fun pipelineSpecificAlignParameters(): Collection<String> = emptyList()

    open fun pipelineSpecificAssembleParameters(): Collection<String> = emptyList()

    private fun inheritThreads(args: MutableList<String>, specificArgs: List<String>) {
        if (specificArgs.none { it.contains("--threads ") || it.contains("-t ") }) {
            args += "--threads"
            args += threads.toString()
        }
    }

    private fun addReportOptions(step: String, options: MutableList<String>) {
        // add report file
        options += "--report"
        options += reportFile

        // add json report file
        if (jsonReport != null) {
            options += "--json-report"
            val pref = when {
                Files.isDirectory(Paths.get(jsonReport!!)) ->
                    jsonReport + (if (jsonReport!!.endsWith(File.separator)) "" else File.separator)

                else -> "$jsonReport."
            }
            options += "$pref$step.jsonl"
        }
    }

    protected abstract fun needCorrectAndSortTags(): Boolean

    open fun mkAlign(): CommandAlign.Cmd {
        // align parameters
        val alignParameters = mutableListOf<String>()

        // add pre-defined parameters first (may be overriden)
        if (nAssemblePartialRounds > 0) alignParameters += "-OallowPartialAlignments=true"

        // add required parameters (for JCommander)
        alignParameters += "--species"
        alignParameters += species
        alignParameters += "--library"
        alignParameters += library
        inheritThreads(alignParameters, this.alignParameters)

        // adding report options
        addReportOptions("align", alignParameters)
        alignParameters += when {
            alignPreset != null -> "-p ${alignPreset}_4.0"
            chains.intersects(Chains.TCR) -> "-p rna-seq_4.0"
            else -> "-p kAligner2_4.0"
        }
        alignParameters += when (startingMaterial) {
            _StartingMaterial.rna -> "-OvParameters.geneFeatureToAlign=" +
                    if (include5UTRInRNA()) "VTranscriptWithP" else "VTranscriptWithout5UTRWithP"

            _StartingMaterial.dna -> "-OvParameters.geneFeatureToAlign=VGeneWithP"
        }

        // pipeline specific parameters
        alignParameters += pipelineSpecificAlignParameters()

        // add all override parameters
        alignParameters += this.alignParameters
            .flatMap { s: String ->
                s.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
            }

        // put input fastq files & output vdjca
        alignParameters += inputFiles
        alignParameters += fNameForAlignments

        // parse parameters
        val ap = CommandAlign.Cmd()
        ap.spec = spec
        CommandLine(ap).parseArgs(*alignParameters.toTypedArray())
        return ap
    }

    @CommandLine.Option(
        names = ["--correctAndSortTagParameters"],
        description = ["Additional parameters for correctAndSortTagParameters step specified with double quotes."],
        arity = "1"
    )
    var correctAndSortTagsParameters: List<String> = mutableListOf()

    /** Build parameters for assemble partial  */
    private fun mkCorrectAndSortTags(input: String, output: String): CommandRefineTagsAndSort.Cmd {
        val correctAndSortTagsParameters = mutableListOf<String>()

        // adding report options
        addReportOptions("correctAndSortTags", correctAndSortTagsParameters)

        // add all override parameters
        correctAndSortTagsParameters += this.correctAndSortTagsParameters
            .flatMap { s: String ->
                s.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
            }
        correctAndSortTagsParameters += input
        correctAndSortTagsParameters += output

        // parse parameters
        val ap = CommandRefineTagsAndSort.Cmd()
        CommandLine(ap).parseArgs(*correctAndSortTagsParameters.toTypedArray())
        return inheritOptionsAndValidate(ap)
    }

    @CommandLine.Option(
        names = ["--assemblePartial"],
        description = ["Additional parameters for assemblePartial step specified with double quotes (e.g --assemblePartial \"--overlappedOnly\" --assemblePartial \"-OkOffset=0\" etc."],
        arity = "1"
    )
    var assemblePartialParameters: List<String> = mutableListOf()

    /** Build parameters for assemble partial  */
    private fun mkAssemblePartial(input: String, output: String): CommandAssemblePartial.Cmd {
        val assemblePartialParameters = mutableListOf<String>()

        // adding report options
        addReportOptions("assemblePartial", assemblePartialParameters)

        // add all override parameters
        assemblePartialParameters += this.assemblePartialParameters
            .flatMap { s: String ->
                s.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
            }
        assemblePartialParameters += input
        assemblePartialParameters += output

        // parse parameters
        val ap = CommandAssemblePartial.Cmd()
        CommandLine(ap).parseArgs(*assemblePartialParameters.toTypedArray())
        return inheritOptionsAndValidate(ap)
    }

    @CommandLine.Option(
        names = ["--extend"],
        description = ["Additional parameters for extend step specified with double quotes (e.g --extend \"--chains TRB\" --extend \"--quality 0\" etc."],
        arity = "1"
    )
    var extendAlignmentsParameters: List<String> = mutableListOf()

    /** Build parameters for extender  */
    private fun mkExtend(input: String, output: String): CommandExtend.Cmd {
        val extendParameters = mutableListOf<String>()

        // adding report options
        addReportOptions("extend", extendParameters)
        inheritThreads(extendParameters, extendAlignmentsParameters)

        // add all override parameters
        extendParameters += extendAlignmentsParameters
            .flatMap { s: String ->
                s.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
            }
        extendParameters += input
        extendParameters += output

        // parse parameters
        val ap = CommandExtend.Cmd()
        CommandLine(ap).parseArgs(*extendParameters.toTypedArray())
        return inheritOptionsAndValidate(ap)
    }

    @CommandLine.Option(
        names = ["--assemble"],
        description = ["Additional parameters for assemble step specified with double quotes (e.g --assemble \"-OassemblingFeatures=[V5UTR+L1+L2+FR1,FR3+CDR3]\" --assemble \"-ObadQualityThreshold=0\" etc."],
        arity = "1"
    )
    var assembleParameters: List<String> = mutableListOf()

    /** Build parameters for assemble  */
    private fun getAssemble(input: String, output: String): CommandAssemble.Cmd {
        return inheritOptionsAndValidate(mkAssemble(input, output))
    }

    /** Build parameters for assemble  */
    open fun mkAssemble(input: String, output: String): CommandAssemble.Cmd {
        val assembleParameters = mutableListOf<String>()

        // adding report options
        addReportOptions("assemble", assembleParameters)
        if (contigAssembly) assembleParameters += "--write-alignments"
        // inheritThreads(assembleParameters, this.assembleParameters)

        // pipeline specific parameters
        assembleParameters += pipelineSpecificAssembleParameters()

        // add all override parameters
        assembleParameters += this.assembleParameters
            .flatMap { s: String ->
                s.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
            }
        assembleParameters += input
        assembleParameters += output

        // parse parameters
        val ap = CommandAssemble.Cmd()
        CommandLine(ap).parseArgs(*assembleParameters.toTypedArray())
        return ap
    }

    @CommandLine.Option(
        names = ["--assembleContigs"],
        description = ["Additional parameters for assemble contigs step specified with double quotes"],
        arity = "1"
    )
    var assembleContigParameters: List<String> = mutableListOf()

    /** Build parameters for assemble  */
    private fun mkAssembleContigs(input: String, output: String): CommandAssembleContigs.Cmd {
        val assembleContigParameters = mutableListOf<String>()

        // adding report options
        addReportOptions("assembleContigs", assembleContigParameters)
        inheritThreads(assembleContigParameters, this.assembleContigParameters)

        // add all override parameters
        assembleContigParameters += this.assembleContigParameters
            .flatMap { s: String ->
                s.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
            }
        assembleContigParameters += input
        assembleContigParameters += output

        // parse parameters
        val ap = CommandAssembleContigs.Cmd()
        CommandLine(ap).parseArgs(*assembleContigParameters.toTypedArray())
        return inheritOptionsAndValidate(ap)
    }

    @CommandLine.Option(
        names = ["--export"],
        description = ["Additional parameters for exportClones step specified with double quotes (e.g --export \"-p full\" --export \"-cloneId\" etc."],
        arity = "1"
    )
    var exportParameters: List<String> = mutableListOf()

    /** Build parameters for export  */
    private fun mkExport(input: String, output: String, chains: String): CommandExport.CommandExportClones {
        val exportParameters = mutableListOf<String>()
        exportParameters += "--force-overwrite"
        exportParameters += "--chains"
        exportParameters += chains
        if (onlyProductive) {
            exportParameters += "--filter-out-of-frames"
            exportParameters += "--filter-stops"
        }
        if (exportGermline) exportParameters += "-p fullImputed"
        // TODO ? else exportParameters.add("-p full"); // for the consistent additional parameter behaviour

        // add all override parameters
        exportParameters += this.exportParameters
        exportParameters += input
        exportParameters += output
        val array = exportParameters
            .flatMap { s: String ->
                s.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
            }
            .toTypedArray()

        // parse parameters
        val cmd = CommandLine(CommandExport.mkClonesSpec())
        cmd.parseArgs(*array)
        return inheritOptionsAndValidate(cmd.commandSpec.userObject() as CommandExport.CommandExportClones)
    }

    /** number of rounds for assemblePartial  */
    @CommandLine.Option(names = ["--assemble-partial-rounds"], description = ["Number of rounds of assemblePartial"])
    var nAssemblePartialRounds: Int = 0

    /** whether to perform TCR alignments extension  */
    @CommandLine.Option(names = ["--do-not-extend-alignments"], description = ["Skip TCR alignments extension"])
    var doNotExtendAlignments: Boolean = false

    /** input raw sequencing data files  */
    public override fun getInputFiles(): List<String> = inOut.subList(0, inOut.size - 1)

    public override fun getOutputFiles(): List<String> = emptyList()

    /** the pattern of output file name ("myOutput" will produce "myOutput.vdjca", "myOutput.clns" etc files)  */
    private val outputNamePattern: String
        get() = inOut.last()

    private val fNameForReport: String
        get() = "$outputNamePattern.report"

    private val fNameForAlignments: String
        get() = "$outputNamePattern.vdjca"

    private val fNameForCorrectedAlignments: String
        get() = "$outputNamePattern.corrected.vdjca"

    private fun fNameForParAlignments(round: Int): String = "$outputNamePattern.rescued_$round.vdjca"

    private val fNameForExtendedAlignments: String
        get() = "$outputNamePattern.extended.vdjca"

    private val fNameForClones: String
        get() = outputNamePattern + if (contigAssembly) ".clna" else ".clns"

    private val fNameForContigs: String
        get() = "$outputNamePattern.contigs.clns"

    private fun fNameForExportClones(chains: String): String = "$outputNamePattern.clonotypes.$chains.txt"

    override fun handleExistenceOfOutputFile(outFileName: String) {
        // Do nothing
    }

    override fun validate() {
        // don't invoke parent validation of input/output existelnce
        if (report == null) warn("NOTE: report file is not specified, using $reportFile to write report.")
        if (File(outputNamePattern).exists()) throwValidationExceptionKotlin(
            "Output file name prefix, matches the existing file name. Most probably you " +
                    "specified paired-end file names but forgot to specify output file name prefix.", false
        )
    }

    override fun run0() {
        JsonOverrider.suppressSameValueOverride = true

        // --- Running alignments
        cmdAlign.run()
        var fileWithAlignments = fNameForAlignments

        // --- Running correctAndSortTags
        if (needCorrectAndSortTags()) {
            val correctedVDJCA = fNameForCorrectedAlignments
            mkCorrectAndSortTags(fileWithAlignments, correctedVDJCA).run()
            fileWithAlignments = correctedVDJCA
        }

        // --- Running partial alignments
        for (round in 0 until nAssemblePartialRounds) {
            val fileWithParAlignments = fNameForParAlignments(round)
            mkAssemblePartial(fileWithAlignments, fileWithParAlignments).run()
            fileWithAlignments = fileWithParAlignments
        }

        // --- Running alignments extender
        if (!doNotExtendAlignments) {
            val fileWithExtAlignments = fNameForExtendedAlignments
            mkExtend(fileWithAlignments, fileWithExtAlignments).run()
            fileWithAlignments = fileWithExtAlignments
        }

        // --- Running assembler
        var fileWithClones = fNameForClones
        getAssemble(fileWithAlignments, fileWithClones).run()
        if (contigAssembly) {
            val fileWithContigs = fNameForContigs
            mkAssembleContigs(fileWithClones, fileWithContigs).run()
            fileWithClones = fileWithContigs
        }
        if (!noExport) // --- Running export
            if (chains != Chains.ALL) {
                for (chain in chains) {
                    mkExport(fileWithClones, fNameForExportClones(chain), chain).run()
                }
            } else {
                for (chain in arrayOf("ALL", "TRA", "TRB", "TRG", "TRD", "IGH", "IGK", "IGL")) {
                    mkExport(fileWithClones, fNameForExportClones(chain), chain).run()
                }
            }
    }

    ///////////////////////////////////////////// Amplicon /////////////////////////////////////////////
    @CommandLine.Command(
        name = "amplicon",
        sortOptions = false,
        separator = " ",
        description = ["Analyze targeted TCR/IG library amplification (5'RACE, Amplicon, Multiplex, etc)."]
    )
    class CommandAmplicon : CommandAnalyze() {
        init {
            doNotExtendAlignments = true
            nAssemblePartialRounds = 0
        }

        private lateinit var vPrimers: _5EndPrimers

        @CommandLine.Option(names = ["--extend-alignments"], description = ["Extend alignments"], required = false)
        fun setDoExtendAlignments(ignore: Boolean) {
            doNotExtendAlignments = false
        }

        @CommandLine.Option(
            names = ["--5-end"],
            completionCandidates = _5EndCandidates::class,
            description = ["5'-end of the library. @|bold Possible values: \${COMPLETION-CANDIDATES}|@"],
            required = true
        )
        fun set5End(value: String) {
            vPrimers = parseDescriptor(value)
                ?: throwValidationExceptionKotlin("Illegal value for --5-end parameter: $value")
        }

        private lateinit var jcPrimers: _3EndPrimers

        @CommandLine.Option(
            names = ["--3-end"],
            completionCandidates = _3EndCandidates::class,
            description = ["3'-end of the library. @|bold Possible values: \${COMPLETION-CANDIDATES}|@"],
            required = true
        )
        fun set3End(value: String) {
            jcPrimers = parseDescriptor(value)
                ?: throwValidationExceptionKotlin("Illegal value for --3-end parameter: $value")
        }

        private lateinit var adapters: _Adapters

        @CommandLine.Option(
            names = ["--adapters"],
            completionCandidates = _AdaptersCandidates::class,
            description = ["Presence of PCR primers and/or adapter sequences. If sequences of primers used for PCR or adapters are present in sequencing data, it may influence the accuracy of V, J and C gene segments identification and CDR3 mapping. @|bold Possible values: \${COMPLETION-CANDIDATES}|@"],
            required = true
        )
        fun setAdapters(value: String) {
            adapters = parseDescriptor(value)
                ?: throwValidationExceptionKotlin("Illegal value for --adapters parameter: $value")
        }

        private var assemblingFeature = GeneFeature.CDR3

        @CommandLine.Option(
            names = ["--region-of-interest"],
            description = ["MiXCR will use only reads covering the whole target region; reads which partially cover selected region will be dropped during clonotype assembly. All non-CDR3 options require long high-quality paired-end data. See https://mixcr.readthedocs.io/en/master/geneFeatures.html for details."],
            required = false
        )
        private fun setRegionOfInterest(v: String) {
            try {
                assemblingFeature = GeneFeature.parse(v)
            } catch (e: Exception) {
                throwValidationExceptionKotlin("Illegal gene feature: $v")
            }
            if (!assemblingFeature.contains(GeneFeature.ShortCDR3)) {
                throwValidationExceptionKotlin("--region-of-interest must cover CDR3")
            }
        }

        @CommandLine.Option(description = ["UMI pattern to extract from the read."], names = ["--umi-pattern"])
        var umiPattern: String? = null

        @CommandLine.Option(description = ["UMI pattern name from the built-in list."], names = ["--tag-pattern-name"])
        var umiPatternName: String? = null

        @CommandLine.Option(description = ["Read UMI pattern from a file."], names = ["--umi-pattern-file"])
        var umiPatternFile: String? = null

        override fun include5UTRInRNA(): Boolean {
            // (1) [ adapters == _Adapters.noAdapters ]
            // If user specified that no adapter sequences are present in the data
            // we can safely extend reference V region to cover 5'UTR, as there is
            // no chance of false alignment extension over non-mRNA derived sequence
            //
            // (2) If [ vPrimers == _5EndPrimers.vPrimers && adapters == _Adapters.adaptersPresent ]
            // VAlignerParameters.floatingLeftBound will be true, so it is also safe to add 5'UTR to the
            // reference as the alignment will not be extended if sequences don't match.
            //
            // In all other cases addition of 5'UTR to the reference may lead to false extension of V alignment
            // over adapter sequence.
            // return adapters == _Adapters.noAdapters || vPrimers == _5EndPrimers.vPrimers; // read as adapters == _Adapters.noAdapters || floatingV()
            return true
        }

        private val floatingV: Boolean
            get() = vPrimers == _5EndPrimers.vPrimers || adapters == _Adapters.adaptersPresent

        private val floatingJ: Boolean
            get() = jcPrimers == _3EndPrimers.jPrimers && adapters == _Adapters.adaptersPresent

        private val floatingC: Boolean
            get() = jcPrimers == _3EndPrimers.cPrimers && adapters == _Adapters.adaptersPresent

        override fun needCorrectAndSortTags(): Boolean =
            umiPattern != null || umiPatternName != null || umiPatternFile != null

        override fun pipelineSpecificAlignParameters(): Collection<String> {
            val list = mutableListOf(
                "-OvParameters.parameters.floatingLeftBound=$floatingV",
                "-OjParameters.parameters.floatingRightBound=$floatingJ",
                "-OcParameters.parameters.floatingRightBound=$floatingC"
            )
            umiPattern?.let { umiPattern ->
                require(!umiPattern.lowercase(Locale.getDefault()).contains("cell")) {
                    "UMI pattern can't contain cell barcodes."
                }
                list += "--tag-pattern"
                list += umiPattern
            }
            umiPatternName?.let { umiPatternName ->
                list += "--tag-pattern-name"
                list += umiPatternName
            }
            umiPatternFile?.let { umiPatternFile ->
                list += "--tag-pattern-file"
                list += umiPatternFile
            }
            return list
        }

        override fun pipelineSpecificAssembleParameters(): Collection<String> = listOf(
            "-OassemblingFeatures=\"[${GeneFeature.encode(assemblingFeature).replace(" ".toRegex(), "")}]\"",
            "-OseparateByV=${!floatingV}",
            "-OseparateByJ=${!floatingJ}",
            "-OseparateByC=${!(floatingC || floatingJ)}"
        )
    }

    ///////////////////////////////////////////// Shotgun /////////////////////////////////////////////
    @CommandLine.Command(
        name = "shotgun",
        sortOptions = false,
        separator = " ",
        description = ["Analyze random-fragmented data (like RNA-Seq, Exome-Seq, etc). " +
                "This pipeline assumes the data contain no adapter / primer sequences. " +
                "Adapter trimming must be performed for the data containing any artificial sequence parts " +
                "(e.g. single-cell / molecular-barcoded data)."]
    )
    class CommandShotgun : CommandAnalyze() {
        init {
            nAssemblePartialRounds = 2
            doNotExtendAlignments = false
        }

        override fun needCorrectAndSortTags(): Boolean = false

        override fun mkAlign(): CommandAlign.Cmd {
            val align = super.mkAlign()
            val alignmentParameters = align.alignerParameters
            if (alignmentParameters.vAlignerParameters.parameters.isFloatingLeftBound)
                throwValidationExceptionKotlin("'shotgun' pipeline requires '-OvParameters.parameters.floatingLeftBound=false'.")
            if (alignmentParameters.jAlignerParameters.parameters.isFloatingRightBound)
                throwValidationExceptionKotlin("'shotgun' pipeline requires '-OjParameters.parameters.floatingRightBound=false'.")
            if (alignmentParameters.cAlignerParameters.parameters.isFloatingRightBound)
                throwValidationExceptionKotlin("'shotgun' pipeline requires '-OcParameters.parameters.floatingRightBound=false'.")
            return align
        }

        override fun pipelineSpecificAssembleParameters(): Collection<String> = listOf(
            "-OseparateByV=true",
            "-OseparateByJ=true"
        )

        override fun mkAssemble(input: String, output: String): CommandAssemble.Cmd {
            val assemble = super.mkAssemble(input, output)

            // FIXME poslavsky ?
            // val cloneAssemblyParameters = assemble.cloneAssemblerParameters
            // if (!Arrays.equals(cloneAssemblyParameters.assemblingFeatures, arrayOf(GeneFeature.CDR3)))
            //     throwValidationExceptionKotlin(
            //         "'shotgun' pipeline can only use CDR3 as assembling feature. " +
            //                 "See --contig-assembly and --impute-germline-on-export options if you want to " +
            //                 "cover wider part of the receptor sequence."
            //     )

            return assemble
        }
    }

    @CommandLine.Command(
        name = "analyze",
        separator = " ",
        description = ["Run full MiXCR pipeline for specific input."],
        subcommands = [CommandLine.HelpCommand::class]
    )
    class CommandAnalyzeMain

    companion object {
        @JvmStatic
        fun mkAmplicon(): CommandLine.Model.CommandSpec {
            val spec = CommandLine.Model.CommandSpec.forAnnotatedObject(CommandAmplicon::class.java)
            for (option in spec.options()) {
                val name = option.names()[0]
                if (name in arrayOf(
                        "--assemblePartial",
                        "--extend",
                        "--assemble-partial-rounds",
                        "--do-extend-alignments"
                    )
                ) {
                    val hidden = CommandLine.Model.OptionSpec::class.java.superclass.getDeclaredField("hidden")
                    hidden.isAccessible = true
                    hidden.setBoolean(option, true)
                }
                if (name == "--chains") {
                    val hidden = CommandLine.Model.OptionSpec::class.java.superclass.getDeclaredField("required")
                    hidden.isAccessible = true
                    hidden.setBoolean(option, true)
                }
            }
            return spec
        }

        @JvmStatic
        fun mkShotgun(): CommandLine.Model.CommandSpec =
            CommandLine.Model.CommandSpec.forAnnotatedObject(CommandShotgun::class.java)
    }
}

private inline fun <reified T> parseDescriptor(value: String): T? where T : CommandAnalyze.WithNameWithDescription, T : Enum<T> =
    enumValues<T>().firstOrNull { enum ->
        enum.key.equals(value, ignoreCase = true)
    }
