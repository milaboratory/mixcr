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

import com.milaboratory.app.InputFileType
import com.milaboratory.app.ValidationException
import com.milaboratory.app.logger
import com.milaboratory.app.matches
import com.milaboratory.cli.MultiSampleRun.SAVE_OUTPUT_FILE_NAMES_OPTION
import com.milaboratory.cli.MultiSampleRun.listToSampleName
import com.milaboratory.cli.POverridesBuilderOps
import com.milaboratory.mitool.cli.Parse.readSearchPlan
import com.milaboratory.mitool.pattern.search.toTagsInfo
import com.milaboratory.mixcr.bam.BAMReader
import com.milaboratory.mixcr.cli.CommandAlign.STRICT_SAMPLE_NAME_MATCHING_OPTION
import com.milaboratory.mixcr.cli.CommandAlign.inputFileGroups
import com.milaboratory.mixcr.cli.CommonDescriptions.Labels
import com.milaboratory.mixcr.presets.AlignMixins
import com.milaboratory.mixcr.presets.AllowedMultipleRounds
import com.milaboratory.mixcr.presets.AnalyzeCommandDescriptor
import com.milaboratory.mixcr.presets.AnalyzeCommandDescriptor.Companion.dotAfterIfNotBlank
import com.milaboratory.mixcr.presets.AnalyzeCommandDescriptor.MiToolCommandDelegationDescriptor
import com.milaboratory.mixcr.presets.AnalyzeCommandDescriptor.MiToolCommandDelegationDescriptor.parse
import com.milaboratory.mixcr.presets.AnalyzeCommandDescriptor.align
import com.milaboratory.mixcr.presets.FullSampleSheetParsed
import com.milaboratory.mixcr.presets.MiXCRParamsBundle
import com.milaboratory.mixcr.presets.MiXCRParamsSpec
import com.milaboratory.mixcr.presets.MiXCRPipeline
import com.milaboratory.util.K_YAML_OM
import com.milaboratory.util.TempFileManager
import com.milaboratory.util.PathPatternExpandException
import com.milaboratory.util.parseAndRunAndCorrelateFSPattern
import picocli.CommandLine.ArgGroup
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Model.OptionSpec
import picocli.CommandLine.Model.PositionalParamSpec
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.system.exitProcess

object CommandAnalyze {
    const val COMMAND_NAME = "analyze"

    private const val inputsLabel = CommandAlign.inputsLabel

    private const val outputLabel = "output_prefix"

    fun mkCommandSpec(): CommandSpec = CommandSpec.forAnnotatedObject(Cmd::class.java)
        .addPositional(
            PositionalParamSpec.builder()
                .index("1")
                .required(false)
                .arity("0..*")
                .type(Path::class.java)
                .paramLabel(inputsLabel)
                .hideParamSyntax(true)
                .description(*CommandAlign.inputsDescription)
                .build()
        )
        .addPositional(
            PositionalParamSpec.builder()
                .index("2")
                .required(false)
                .arity("0..*")
                .type(String::class.java)
                .paramLabel(outputLabel)
                .hideParamSyntax(true)
                .description("Path prefix telling mixcr where to put all output files. If arguments ends with file separator, then outputs will be written in specified directory.")
                .build()
        ).apply {
            val optionNamesToHide = CommandAlign.PathsForNotAligned.optionNames
            val forDelete = options()
                .filter { it.longestName() in optionNamesToHide }
            val forReplace = forDelete.map { OptionSpec.builder(it).hidden(true).build() }
            forDelete.forEach { remove(it) }
            forReplace.forEach { add(it) }
        }

    @Command(
        description = ["Run full MiXCR pipeline for specific input."]
    )
    class Cmd : MiXCRCommand() {
        @Option(
            names = ["-f", "--force-overwrite"],
            description = ["Force overwrite of output file(s)."],
            order = OptionsOrder.forceOverride
        )
        var forceOverwrite = false

        @Option(
            names = [BAMReader.lenientBAMValidationOption],
            description = ["Make BAM validation very forgiving, use for malformed BAM files."],
            order = OptionsOrder.main + 10_890
        )
        var lenientBAMValidation = false

        @Option(
            names = [BAMReader.referenceForCramOption],
            description = ["Reference to the genome that was used for build a cram file"],
            order = OptionsOrder.main + 100,
            paramLabel = "genome.fasta[.gz]"
        )
        var referenceForCram: Path? = null

        @Parameters(
            index = "0",
            arity = "1",
            paramLabel = Labels.PRESET,
            description = ["Name of the analysis preset."],
            completionCandidates = PresetsCandidates::class
        )
        private lateinit var presetName: String

        @Parameters(
            index = "1",
            arity = "2..5",
            paramLabel = "$inputsLabel $outputLabel",
            // help is covered by mkCommandSpec
            hidden = true
        )
        private var inOut: List<String> = mutableListOf()

        @ArgGroup(
            validate = false,
            heading = PipelineMiXCRMixins.DESCRIPTION,
            multiplicity = "0..*",
            order = OptionsOrder.mixins.pipeline
        )
        var pipelineMixins: List<PipelineMiXCRMixins> = mutableListOf()

        @ArgGroup(
            validate = false,
            heading = AlignMiXCRMixins.DESCRIPTION,
            multiplicity = "0..*",
            order = OptionsOrder.mixins.align
        )
        var alignMixins: List<AlignMiXCRMixins> = mutableListOf()

        @ArgGroup(
            validate = false,
            heading = RefineTagsAndSortMiXCRMixins.DESCRIPTION,
            multiplicity = "0..*",
            order = OptionsOrder.mixins.refineTagsAndSort
        )
        var refineAndSortMixins: List<RefineTagsAndSortMiXCRMixins> = mutableListOf()

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

        @Mixin
        lateinit var pathsForNotAligned: CommandAlign.PathsForNotAligned

        @Mixin
        lateinit var threadsOption: ThreadsOption

        @Mixin
        lateinit var useLocalTemp: UseLocalTempOption

        private val mixins: MiXCRMixinCollection
            get() = MiXCRMixinCollection.empty + pipelineMixins + alignMixins + refineAndSortMixins + assembleMixins +
                    assembleContigsMixins + exportMixins + genericMixins + qcMixins

        // @Option(
        //     description = ["Delete all output files of the command if they already exist."],
        //     names = ["-f", "--force-overwrite"]
        // )
        // private var deleteOutputs: Boolean = false

        @Option(
            description = ["Dry run. Print commands that would have been executed and exit."],
            names = ["--dry-run"],
            hidden = true
        )
        private var dryRun: Boolean = false

        @Option(
            description = ["Write intermediate files to the specified folder instead of next to the " +
                    "output files, creating it if needed. Intermediates are the step outputs that " +
                    "later steps read: the .mic and .vdjca files, and any .clns/.clna produced " +
                    "before the last one. Reports, QC and exports are unaffected. Scratch data of " +
                    "the relocated steps follows them here; the last step's stays in the system " +
                    "temp folder. Mutually exclusive with --intermediates-in-temp."],
            names = ["--intermediates-dir"],
            paramLabel = "<path>",
            order = OptionsOrder.intermediates
        )
        private var intermediatesDir: Path? = null

        @Option(
            description = ["Write intermediate files to a folder MiXCR creates inside the system " +
                    "temp folder and removes when the run exits, including after a failed step but " +
                    "not if the process is killed outright. The system temp folder is \$TMPDIR when " +
                    "that is set, otherwise the JVM default, which is normally /tmp; inside a " +
                    "container that is the container's own /tmp unless \$TMPDIR is passed in. It has " +
                    "to have room for the intermediates. Use --intermediates-dir to choose the " +
                    "location yourself. Mutually exclusive with --intermediates-dir."],
            names = ["--intermediates-in-temp"],
            order = OptionsOrder.intermediates + 1
        )
        private var intermediatesInTemp: Boolean = false

        @Option(
            description = ["Delete each intermediate file as soon as the last step that reads it " +
                    "has finished, so the run holds only what it still needs rather than every file " +
                    "it has produced. A file given an explicit --output-path is a deliverable and is " +
                    "never deleted. Recovering from a failed step means re-running from the input files."],
            names = ["--remove-intermediates"],
            order = OptionsOrder.intermediates + 2
        )
        private var removeIntermediates: Boolean = false

        @Option(
            description = ["Write one produced file to an explicit path, overriding every other " +
                    "placement option and making that file a deliverable. The name is the file name " +
                    "the step produces, as printed with the step's command while the run executes, " +
                    "e.g. --output-path result.vdjca=/data/result.vdjca. A name this pipeline does " +
                    "not produce is rejected, listing the ones it does. Repeat to pin several files. " +
                    "For multi-sample input use the name produced before the input is split by " +
                    "sample; with several samples the folder of the path is used and each sample " +
                    "keeps its own file name."],
            names = ["--output-path"],
            paramLabel = "<name=path>",
            order = OptionsOrder.intermediates + 3
        )
        private var outputPaths: Map<String, Path> = mutableMapOf()

        @Option(
            description = ["Don't output report files for each of the steps"],
            names = ["--no-reports"],
            order = OptionsOrder.report + 100
        )
        private var noReports: Boolean = false

        @Option(
            description = ["Don't output json report files for each of the steps"],
            names = ["--no-json-reports"],
            order = OptionsOrder.report + 101
        )
        private var noJsonReports: Boolean = false

        @Option(
            description = ["If specified, not aligned reads will be written in `{output_prefix}.not_aligned.{(I1|I2|R1|R2)}.fastq.gz`, " +
                    "not parsed reads will be written in `{output_prefix}.not_parsed.{(I1|I2|R1|R2)}.fastq.gz`"],
            names = ["--output-not-used-reads"],
            order = OptionsOrder.report + 200
        )
        private var outputNoUsedReads: Boolean = false

        @Option(
            description = ["Write consensus alignments. Beware, output can be very big."],
            names = ["--output-consensus-alignments"],
            order = OptionsOrder.report + 220,
            hidden = true,
        )
        private var consensusAlignments: Boolean = false

        @Option(
            description = ["Write consensus state statistics. Beware, output can be extremely big."],
            names = ["--output-consensus-state-stat"],
            order = OptionsOrder.report + 221,
            hidden = true,
        )
        private var consensusStateStats: Boolean = false

        @Option(
            description = ["Specify downsampling coefficient to apply while collecting consensus state statistics."],
            names = ["--downsample-consensus-state-stat"],
            order = OptionsOrder.report + 222,
            hidden = true,
        )
        private var consensusStateStatsDownsampling: Double? = null

        @Option(
            description = [
                "Perform strict matching against input sample sheet (one substitution will be allowed by default).",
                "This option only valid if input file is *.tsv sample sheet."
            ],
            names = [STRICT_SAMPLE_NAME_MATCHING_OPTION],
            order = OptionsOrder.report + 300
        )
        private var strictMatching = false

        // parsing inOut

        private val inputTemplates get() = inOut.dropLast(1).map { Paths.get(it) }

        private val outSuffix get() = inOut.last()

        /** Provides access to presets, mixins application, etc.. */
        private val paramsResolver = object : MiXCRParamsResolver<MiXCRPipeline>(MiXCRParamsBundle::pipeline) {
            override fun POverridesBuilderOps<MiXCRPipeline>.paramsOverrides() {}
        }

        private val inputSampleSheet: FullSampleSheetParsed? by lazy {
            if (inputTemplates.size == 1 && inputTemplates[0].name.endsWith(".tsv"))
                FullSampleSheetParsed.parse(inputTemplates[0])
            else
                null
        }

        /** I.e. list of mate-pair files */
        private val inputFileGroups: CommandAlign.InputFileGroups by lazy {
            try {
                inputSampleSheet
                    ?.inputFileGroups
                    ?: CommandAlign.InputFileGroups(inputTemplates.parseAndRunAndCorrelateFSPattern())
            } catch (e: PathPatternExpandException) {
                throw ValidationException(e.message!!)
            }
        }

        override fun validate() {
            CommandAlign.checkInputTemplates(inputTemplates)
            ValidationException.requireFileType(referenceForCram, InputFileType.FASTA, InputFileType.FASTA_GZ)
            if (referenceForCram != null) {
                ValidationException.require(inputTemplates.first().matches(InputFileType.CRAM)) {
                    "--reference-for-cram could be specified only with CRAM input"
                }
            }
            pathsForNotAligned.validate(inputFileGroups.inputType)
            inputFileGroups.allFiles.forEach { input ->
                ValidationException.requireFileExists(input)
            }
            ValidationException.requireNoExtension(Paths.get(outSuffix))

            if (strictMatching && inputSampleSheet == null)
                throw ValidationException("$STRICT_SAMPLE_NAME_MATCHING_OPTION is valid only with sample sheet input, i.e. a *.tsv file.")

            if (intermediatesDir != null && intermediatesInTemp)
                throw ValidationException("--intermediates-dir and --intermediates-in-temp are mutually exclusive.")

            intermediatesDir?.let { dir ->
                if (dir.exists() && !dir.isDirectory())
                    throw ValidationException("--intermediates-dir is not a folder: $dir")
            }
        }

        override fun run0() {
            // Calculating output folder and output file suffix
            val outputIsFolder = outSuffix.endsWith(File.separator)
            val outputPath = Path(outSuffix)
            val outputNamePrefix = if (!outputIsFolder) outputPath.fileName.toString() else ""
            val outputFolder = if (!outputIsFolder) outputPath.parent ?: Path("") else outputPath

            // Creating output folder if not yet exists
            if (!outputFolder.exists())
                outputFolder.createDirectories()

            // Creating params spec, the same way it is done in align
            val mixins = mixins.mixins +
                    listOfNotNull(inputSampleSheet?.tagPattern?.let { AlignMixins.SetTagPattern(it) })
            val paramsSpec = MiXCRParamsSpec(presetName, mixins)

            // Resolving parameters and sorting the pipeline according to the natural command order
            // (it must already be sorted, but just in case)
            val (bundle, pipeline) = paramsResolver.resolve(paramsSpec, printParameters = false)
                .let { (first, second) -> first to second.steps.sortedBy { it.order } }
            ValidationException.requireDistinct(pipeline) {
                "There should not be repeatable steps"
            }

            // Creating execution plan
            if (pipeline.first() !in arrayOf(align, parse))
                throw ValidationException("Pipeline must stat from the `align` or `parse` action.")

            // Folder for the files later steps consume; null keeps them next to the output files
            val intermediatesFolder: Path? = when {
                intermediatesInTemp -> TempFileManager.newTempDir().toPath()
                else -> intermediatesDir?.also { if (!it.exists()) it.createDirectories() }
            }

            // Exports and qc read a production step's output, they don't extend the chain, so the
            // last production step is the one whose output nothing else consumes
            val terminalStep = pipeline
                .filterNot { it is AnalyzeCommandDescriptor.ExportCommandDescriptor<*> }
                .filterNot { it == AnalyzeCommandDescriptor.qc }
                .last()

            // Every name --output-path can address, taken before the input is split by sample. A
            // name that matches nothing would otherwise be a silent no-op, and under
            // --remove-intermediates the file the caller meant to keep would be deleted instead.
            val producibleNames = if (outputPaths.isEmpty()) emptySet() else buildSet {
                val qcStep = AnalyzeCommandDescriptor.qc
                    .takeIf { bundle.qc?.checks?.isNotEmpty() == true && it !in pipeline }
                (pipeline + listOfNotNull(qcStep)).forEach { cmd ->
                    val rounds = (cmd as? AllowedMultipleRounds)?.roundsCount(bundle) ?: 1
                    repeat(rounds) { round ->
                        // A step the bundle carries no parameters for produces nothing to address,
                        // and asking it for a name throws. The pipeline tolerates such a step
                        // elsewhere, so listing the names must not be what breaks the run.
                        val outputName = try {
                            cmd.outputName(outputNamePrefix, "", bundle, round)
                        } catch (e: RuntimeException) {
                            return@repeat
                        }
                        if (cmd == AnalyzeCommandDescriptor.qc) {
                            val base = outputName.substring(0, outputName.lastIndexOf('.'))
                            add("$base.txt")
                            add("$base.json")
                        } else
                            add(outputName)
                        if (!noReports)
                            cmd.textReportName(outputNamePrefix, "", bundle, round)?.let { add(it) }
                        if (!noJsonReports)
                            cmd.jsonReportName(outputNamePrefix, "", bundle, round)?.let { add(it) }
                    }
                }
            }
            outputPaths.entries.groupBy({ it.value }, { it.key })
                .filterValues { it.size > 1 }
                .forEach { (path, names) ->
                    throw ValidationException(
                        "--output-path sends ${names.sorted().joinToString(" and ")} to the same " +
                                "path $path"
                    )
                }

            val unknownPins = outputPaths.keys - producibleNames
            if (unknownPins.isNotEmpty())
                throw ValidationException(
                    "--output-path does not match any file this run produces: " +
                            "${unknownPins.sorted().joinToString(", ")}. " +
                            "Available: ${producibleNames.sorted().joinToString(", ")}"
                )

            val planBuilder = PlanBuilder(
                bundle, outputFolder, outputNamePrefix,
                !noReports, !noJsonReports,
                inputTemplates, threadsOption, useLocalTemp, forceOverwrite,
                intermediatesFolder, outputPaths, removeIntermediates, terminalStep
            )

            if (pipeline.first() == parse) {
                val mitoolPreset = bundle.mitool ?: throw ValidationException("No mitool params")
                val parseParams = mitoolPreset.parse ?: throw ValidationException("No mitool parse params")
                val plan = parseParams.readSearchPlan
                if (outputNoUsedReads) {
                    // fill up args of not parsed reads in symmetry of input files
                    pathsForNotAligned.fillWithDefaults(
                        inputFileGroups.inputType,
                        outputFolder,
                        outputNamePrefix,
                        addNotAligned = false,
                        addNotParsed = true
                    )
                    // fill up args for not aligned reads according to payload tags count that will be in mitool results
                    pathsForNotAligned.fillWithDefaults(
                        CommandAlign.Cmd.InputType.MIC(plan.toTagsInfo()),
                        outputFolder,
                        outputNamePrefix,
                        addNotAligned = true,
                        addNotParsed = false
                    )
                }
                // mitool resolves a local: name against its own search path, which includes the
                // working directory, and Path.resolve returns an absolute argument unchanged, so
                // this path works either relative or absolute. It is passed on exactly as given
                // rather than resolved, because it is recorded in the command line of every
                // downstream file header: keeping it relative is what lets two runs of the same
                // analysis in different folders produce identical files. Relocating the
                // intermediates puts that location in the header instead, which is the caller's
                // choice to make.
                val mitoolPresetPath = (intermediatesFolder ?: outputFolder)
                    .resolve("${outputNamePrefix.dotAfterIfNotBlank()}MiTool.preset.yaml")
                mitoolPresetPath.toFile().deleteOnExit()
                K_YAML_OM.writeValue(mitoolPresetPath.toFile(), mitoolPreset)

                // Adding an option to save output files by parse
                val sampleFileList = (intermediatesFolder ?: outputFolder)
                    .resolve("${outputNamePrefix.dotAfterIfNotBlank()}parse.list.tsv")
                    .also { it.deleteIfExists() }
                    .toFile().also { it.deleteOnExit() }

                planBuilder.addStep(parse) { _, _, _ ->
                    buildList {
                        this += listOf("--preset", "local:${mitoolPresetPath.toString().removeSuffix(".yaml")}")
                        this += listOf(SAVE_OUTPUT_FILE_NAMES_OPTION, sampleFileList.toString())
                        this += pathsForNotAligned.argsOfNotParsedForMiToolParse()
                    }
                }

                planBuilder.executeSteps(dryRun)
                // Taking into account that there are multiple outputs from the mitool parse command.
                planBuilder.setActualOutputs(parse, sampleFileList.toPath())

                pipeline
                    .drop(1) // without parse
                    .filterIsInstance<MiToolCommandDelegationDescriptor<*, *>>()
                    .forEach { step -> planBuilder.addStep(step) }
            } else {
                // fill up args of not aligned and not parsed reads in symmetry of input files
                if (outputNoUsedReads) {
                    pathsForNotAligned.fillWithDefaults(inputFileGroups.inputType, outputFolder, outputNamePrefix)
                }
            }

            // TODO when MiTool will support sample tags from reads, recombine all mitool inputs in one align command

            val sampleFileListFiles = mutableMapOf<String, Path>()

            planBuilder.addStep(align) { _, _, sampleName ->
                buildList {
                    this += listOf("--preset", presetName)
                    if (bundle.align!!.splitBySample && !dryRun) {
                        // Adding an option to save output files by align
                        val sampleFileList = (intermediatesFolder ?: outputFolder)
                            .resolve("${outputNamePrefix.dotAfterIfNotBlank()}${sampleName.dotAfterIfNotBlank()}align.list.tsv")
                            .also { it.deleteIfExists() }
                            .toFile().also { it.deleteOnExit() }
                        sampleFileListFiles[sampleName] = sampleFileList.toPath()
                        this += listOf(SAVE_OUTPUT_FILE_NAMES_OPTION, sampleFileList.toString())
                    }

                    if (strictMatching) {
                        this += STRICT_SAMPLE_NAME_MATCHING_OPTION
                    }
                    referenceForCram?.let { referenceForCram ->
                        this += listOf(BAMReader.referenceForCramOption, referenceForCram.toString())
                    }
                    if (lenientBAMValidation)
                        this += listOf(BAMReader.lenientBAMValidationOption)

                    this += mixins.flatMap { it.cmdArgs }
                    this += pathsForNotAligned.argsOfNotAlignedForAlign()
                    if (pipeline.first() != parse)
                        this += pathsForNotAligned.argsOfNotParsedForAlign()
                }
            }

            planBuilder.executeSteps(dryRun)

            // Taking into account that there are multiple outputs from the align command.
            // Even so, mitool could split into several files and then align could split each too
            if (sampleFileListFiles.isNotEmpty()) {
                planBuilder.setActualOutputs(align, sampleFileListFiles)
            }

            // Adding all steps with calculations
            pipeline
                // already added
                .filterNot { it is MiToolCommandDelegationDescriptor<*, *> }
                // it's align, already added
                .drop(1)
                // exports will be added separately
                .filterNot { cmd -> cmd is AnalyzeCommandDescriptor.ExportCommandDescriptor }
                .forEach { cmd ->
                    planBuilder.addStep(cmd) { outputFolder, prefix, sampleName ->
                        when (cmd) {
                            AnalyzeCommandDescriptor.assemble -> {
                                val additionalArgs = mutableListOf<String>()
                                if (consensusAlignments)
                                    additionalArgs += listOf(
                                        "--consensus-alignments",
                                        outputFolder.resolve(
                                            AnalyzeCommandDescriptor.assemble.consensusAlignments(prefix, sampleName)
                                        ).toString()
                                    )
                                if (consensusStateStats)
                                    additionalArgs += listOf(
                                        "--consensus-state-stat",
                                        outputFolder.resolve(
                                            AnalyzeCommandDescriptor.assemble.consensusStateStats(prefix, sampleName)
                                        ).toString()
                                    )
                                consensusStateStatsDownsampling?.let {
                                    additionalArgs += listOf("--downsample-consensus-state-stat", it.toString())
                                }
                                additionalArgs
                            }

                            else -> emptyList()
                        }
                    }
                }

            if (bundle.qc?.checks?.isNotEmpty() == true) {
                planBuilder.addQC()
            }

            pipeline
                .filterIsInstance<AnalyzeCommandDescriptor.ExportCommandDescriptor<*>>()
                .forEach { cmd ->
                    planBuilder.addExportStep(cmd)
                }

            // Executing all actions after align
            planBuilder.executeSteps(dryRun)

            println("Analysis finished successfully.")

            println("===============================")
            println("Visualize results and simplify your immune data analysis with a convenient UI.")
            println("Explore and install Platforma.bio here: https://platforma.bio")
        }

        class InputFileSet(val sampleName: String, val fileNames: List<String>)

        class PlanBuilder(
            private val paramsBundle: MiXCRParamsBundle,
            private val outputFolder: Path,
            private val outputNamePrefix: String,
            private val outputReports: Boolean,
            private val outputJsonReports: Boolean,
            initialInputs: List<Path>,
            private val threadsOption: ThreadsOption,
            private val useLocalTemp: UseLocalTempOption,
            private val forceOverride: Boolean,
            private val intermediatesFolder: Path?,
            private val outputPaths: Map<String, Path>,
            private val removeIntermediates: Boolean,
            private val terminalStep: AnalyzeCommandDescriptor<*, *>
        ) {
            private val executionPlan = mutableListOf<ExecutionStep>()
            private var nextInputs: List<InputFileSet> = listOf(InputFileSet("", initialInputs.map { it.toString() }))
            private val outputsForCommands = mutableListOf<Pair<AnalyzeCommandDescriptor<*, *>, List<InputFileSet>>>()

            /** Produced files that are safe to delete once no planned step reads them any more */
            private val removableIntermediates = mutableSetOf<String>()

            /** Where each command put its outputs, for resolving the file names it reported */
            private val placements = mutableMapOf<AnalyzeCommandDescriptor<*, *>, StepPlacement>()

            private class StepPlacement(val folder: Path, val removable: Boolean)

            /**
             * Output of every step but the last production one is read further down the pipeline. Rounds of
             * one command chain the same way, so only the last round of the last command is a deliverable.
             */
            private fun isIntermediate(cmd: AnalyzeCommandDescriptor<*, *>, round: Int, roundsCount: Int) =
                cmd != terminalStep || round != roundsCount - 1

            /**
             * Path pinned by --output-path, if any.
             *
             * Pins are keyed by [seedName], the name the step produces before the input is split by
             * sample, so one key addresses a step rather than one sample of it. A single path cannot
             * hold several samples, so with more than one it contributes its folder and each sample
             * keeps its own name.
             */
            private fun pinned(actualName: String, seedName: String, singleSample: Boolean): Path? =
                outputPaths[seedName]?.let { pinned ->
                    if (singleSample) pinned else (pinned.parent ?: Path("")).resolve(actualName)
                }

            /** Files that stay with the output files, unless pinned */
            private fun deliverable(actualName: String, seedName: String, singleSample: Boolean): Path =
                pinned(actualName, seedName, singleSample) ?: outputFolder.resolve(actualName)

            /**
             * The precedence chain: an explicit --output-path wins, then the intermediates folder for
             * anything read further down the pipeline, then the positional output prefix.
             */
            private fun resolveOutput(
                actualName: String,
                seedName: String,
                intermediate: Boolean,
                singleSample: Boolean
            ): Path {
                pinned(actualName, seedName, singleSample)?.let { return it }
                val folder = if (intermediate) intermediatesFolder ?: outputFolder else outputFolder
                return folder.resolve(actualName)
            }

            fun setActualOutputs(cmd: AnalyzeCommandDescriptor<*, *>, outputFilesList: Path) {
                setActualOutputs(cmd, mapOf("" to outputFilesList))
            }

            /**
             * Replaces the planned output of [cmd] with the files it actually wrote, one per sample.
             *
             * The list files hold bare file names, written as siblings of the output the step was given,
             * so they resolve against the folder that step wrote to rather than the output prefix.
             */
            fun setActualOutputs(cmd: AnalyzeCommandDescriptor<*, *>, outputFilesList: Map<String, Path>) {
                val placement = placements.getValue(cmd)
                nextInputs = outputFilesList.flatMap { (prefix, file) ->
                    val withoutHeader = file.readLines().drop(1)
                    withoutHeader.map { it.split("\t") }.map { line ->
                        val sampleName = listToSampleName(line.drop(2))
                        val output = placement.folder.resolve(line[0]).toString()
                        if (placement.removable)
                            removableIntermediates += output
                        InputFileSet(
                            "${prefix.dotAfterIfNotBlank()}$sampleName",
                            listOf(output)
                        )
                    }
                }
            }

            fun executeSteps(dryRun: Boolean) {
                if (dryRun) {
                    // Printing commands that would have been executed
                    executionPlan.forEach { pe -> println(pe) }
                } else {
                    // A file is freed after the last step of this batch that reads it, so one that an
                    // export or QC step still reads outlives the production step that consumed it.
                    // Readers of a given file are always planned in the same batch as each other.
                    val lastReaderOf = mutableMapOf<String, Int>()
                    if (removeIntermediates)
                        executionPlan.forEachIndexed { index, step ->
                            step.inputs
                                .filter { it in removableIntermediates }
                                .forEach { lastReaderOf[it] = index }
                        }
                    val freeAfter = lastReaderOf.entries.groupBy({ it.value }, { it.key })

                    // Executing the plan
                    executionPlan.forEachIndexed { index, executionStep ->
                        println("\n" + Util.surround("mixcr ${executionStep.command}", ">", "<"))
                        println("Running:")
                        println(executionStep)
                        val actualArgs = executionStep.command.split(" ") + executionStep.args
                        val exitCode = Main.execute(*actualArgs.toTypedArray())
                        if (exitCode != 0)
                        // Terminating execution if one of the steps resulted in error
                            exitProcess(exitCode)
                        freeAfter[index]?.forEach { file ->
                            try {
                                Path(file).deleteIfExists()
                            } catch (e: IOException) {
                                logger.warn("Can't remove intermediate file $file: ${e.message}")
                            }
                        }
                    }
                }

                // Clearing the list of planned steps as they already executed
                executionPlan.clear()
            }

            private fun String.removeExtension() = substring(0, lastIndexOf('.'))

            fun addQC() {
                val inputsForQc = outputsForCommands.findLast { (command) -> command.outputSupportsQc }!!.second
                val singleSample = inputsForQc.size == 1
                for (input in inputsForQc) {
                    check(input.fileNames.size == 1)
                    val round = 0
                    val cmd = AnalyzeCommandDescriptor.qc
                    val outputName = cmd.outputName(outputNamePrefix, input.sampleName, paramsBundle, round)
                    val arguments = mutableListOf("--print-to-stdout")
                    if (forceOverride)
                        arguments += "-f"

                    executionPlan += ExecutionStep(
                        cmd.command,
                        round,
                        arguments,
                        emptyList(),
                        listOf(input.fileNames.first()),
                        run {
                            val seed = cmd.outputName(outputNamePrefix, "", paramsBundle, round)
                            listOf(
                                deliverable(
                                    outputName.removeExtension() + ".txt",
                                    seed.removeExtension() + ".txt",
                                    singleSample
                                ).toString(),
                                deliverable(
                                    outputName.removeExtension() + ".json",
                                    seed.removeExtension() + ".json",
                                    singleSample
                                ).toString()
                            )
                        }
                    )
                }
            }

            fun addExportStep(cmd: AnalyzeCommandDescriptor.ExportCommandDescriptor<*>) {
                val runAfter = cmd.runAfterLastOf()
                // if there is nothing to run on (production command is removed), don't run it
                val (_, inputsForExport) = outputsForCommands.findLast { (cmd) -> cmd in runAfter } ?: return
                val singleSample = inputsForExport.size == 1
                for (input in inputsForExport) {
                    check(input.fileNames.size == 1)
                    val round = 0
                    val outputName = cmd.outputName(outputNamePrefix, input.sampleName, paramsBundle, round)

                    val arguments = mutableListOf<String>()
                    if (forceOverride)
                        arguments += "-f"

                    executionPlan += ExecutionStep(
                        cmd.command,
                        round,
                        arguments,
                        emptyList(),
                        listOf(input.fileNames.first()),
                        listOf(
                            deliverable(
                                outputName,
                                cmd.outputName(outputNamePrefix, "", paramsBundle, round),
                                singleSample
                            ).toString()
                        )
                    )
                }
            }

            fun addStep(
                cmd: AnalyzeCommandDescriptor<*, *>,
                extraArgs: (outputFolder: Path, prefix: String, sampleName: String) -> List<String> = { _, _, _ -> emptyList() }
            ) {
                val roundsCount = (cmd as? AllowedMultipleRounds)?.roundsCount(paramsBundle) ?: 1

                repeat(roundsCount) { round ->
                    val intermediate = isIntermediate(cmd, round, roundsCount)
                    val seedName = cmd.outputName(outputNamePrefix, "", paramsBundle, round)
                    val singleSample = nextInputs.size == 1

                    val nextInputsBuilder = mutableListOf<InputFileSet>()

                    nextInputs.forEach { inputs ->
                        val arguments = mutableListOf<String>()

                        if (forceOverride && cmd !is MiToolCommandDelegationDescriptor<*, *>)
                            arguments += "-f"

                        // Reports are deliverables even for a step whose data output is an intermediate,
                        // so they stay with the output files while the data output moves
                        if (outputReports)
                            cmd.textReportName(outputNamePrefix, inputs.sampleName, paramsBundle, round)?.let {
                                val seed = cmd.textReportName(outputNamePrefix, "", paramsBundle, round)!!
                                arguments += listOf("--report", deliverable(it, seed, singleSample).toString())
                            }

                        if (outputJsonReports)
                            cmd.jsonReportName(outputNamePrefix, inputs.sampleName, paramsBundle, round)?.let {
                                val seed = cmd.jsonReportName(outputNamePrefix, "", paramsBundle, round)!!
                                arguments += listOf("--json-report", deliverable(it, seed, singleSample).toString())
                            }

                        if (cmd.hasThreadsOption && threadsOption.isSet) {
                            arguments += listOf("--threads", threadsOption.value.toString())
                        }

                        // A step whose output was relocated puts its scratch data next to that
                        // output, so the scratch follows the intermediates. Steps take no scratch
                        // folder of their own, which makes this the only mechanism available, and
                        // it is the same for the steps delegated to mitool as for MiXCR's own.
                        // The last step is left alone: its output is a deliverable at the output
                        // prefix, and its scratch belongs in the system temp folder rather than
                        // next to the results.
                        if (cmd.hasUseLocalTempOption &&
                            (useLocalTemp.value || (intermediate && intermediatesFolder != null))
                        )
                            arguments += "--use-local-temp"

                        val outputName = cmd.outputName(outputNamePrefix, inputs.sampleName, paramsBundle, round)
                        val outputPath = resolveOutput(outputName, seedName, intermediate, singleSample)
                        val output = listOf(outputPath.toString())

                        // A pinned file is a deliverable by definition and is never freed
                        val removable = intermediate && seedName !in outputPaths
                        if (removable)
                            removableIntermediates += output.first()
                        placements[cmd] = StepPlacement(outputPath.parent ?: Path(""), removable)

                        executionPlan += ExecutionStep(
                            when (cmd) {
                                is MiToolCommandDelegationDescriptor<*, *> -> "mitool ${cmd.mitoolCommand.command}"
                                else -> cmd.command
                            },
                            round,
                            arguments,
                            extraArgs(outputFolder, outputNamePrefix, inputs.sampleName),
                            inputs.fileNames,
                            output,
                        )

                        nextInputsBuilder += InputFileSet(inputs.sampleName, output)
                    }

                    outputsForCommands += cmd to nextInputsBuilder
                    nextInputs = nextInputsBuilder
                }
            }

        }

        data class ExecutionStep(
            val command: String,
            val round: Int,
            val arguments: List<String>,
            val extraArgs: List<String>,
            val inputs: List<String>,
            val output: List<String>
        ) {
            val args get() = arguments + extraArgs + inputs + output
            override fun toString() = (listOf("mixcr") + command.split(" ") + args).joinToString(" ")
        }
    }
}
