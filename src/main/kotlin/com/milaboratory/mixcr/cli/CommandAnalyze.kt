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
import com.milaboratory.app.matches
import com.milaboratory.cli.POverridesBuilderOps
import com.milaboratory.mixcr.bam.BAMReader
import com.milaboratory.mixcr.cli.CommandAlign.SAVE_OUTPUT_FILE_NAMES_OPTION
import com.milaboratory.mixcr.cli.CommandAlign.STRICT_SAMPLE_NAME_MATCHING_OPTION
import com.milaboratory.mixcr.cli.CommandAlign.inputFileGroups
import com.milaboratory.mixcr.cli.CommandAlign.listSamplesForSeedFileName
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
import com.milaboratory.util.PathPatternExpandException
import com.milaboratory.util.parseAndRunAndCorrelateFSPattern
import com.milaboratory.util.requireSingleton
import picocli.CommandLine.ArgGroup
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Model.OptionSpec
import picocli.CommandLine.Model.PositionalParamSpec
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
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

            val planBuilder = PlanBuilder(
                bundle, outputFolder, outputNamePrefix,
                !noReports, !noJsonReports,
                inputTemplates, threadsOption, useLocalTemp, forceOverwrite
            )

            if (pipeline.first() == parse) {
                val mitoolPreset = bundle.mitool ?: throw ValidationException("No mitool params")
                val mitoolPresetPath = Paths.get("MiTool.preset.yaml").toFile()
                mitoolPresetPath.deleteOnExit()
                K_YAML_OM.writeValue(mitoolPresetPath, mitoolPreset)

                planBuilder.addStep(parse) { _, _, _ ->
                    buildList {
                        this += listOf("--preset", "local:${mitoolPresetPath.name.removeSuffix(".yaml")}")
                    }
                }

                pipeline
                    .drop(1) // without parse
                    .filterIsInstance<MiToolCommandDelegationDescriptor<*, *>>()
                    .forEach { step -> planBuilder.addStep(step) }
            }

            // Adding an option to save output files by align
            val sampleFileList = outputFolder.resolve("${outputNamePrefix.dotAfterIfNotBlank()}align.list")
                .takeIf { bundle.align!!.splitBySample && !dryRun }
            val extraAlignArgs: List<String> = buildList {
                sampleFileList?.let { sampleFileList ->
                    this += listOf(SAVE_OUTPUT_FILE_NAMES_OPTION, sampleFileList.toString())
                }
                if (strictMatching) {
                    this += STRICT_SAMPLE_NAME_MATCHING_OPTION
                }
                referenceForCram?.let { referenceForCram ->
                    this += listOf(BAMReader.referenceForCramOption, referenceForCram.toString())
                }
            }

            // Adding "align" step
            if (outputNoUsedReads)
                pathsForNotAligned.fillWithDefaults(inputFileGroups.inputType, outputFolder, outputNamePrefix)
            planBuilder.addStep(align) { _, _, _ ->
                buildList {
                    this += listOf("--preset", presetName)
                    this += extraAlignArgs
                    this += mixins.flatMap { it.cmdArgs }
                    this += pathsForNotAligned.argsForAlign()
                    if (outputNamePrefix.isBlank())
                        this += listOf("--output-name-suffix", "alignments")
                }
            }

            planBuilder.executeSteps(dryRun)

            // Taking into account that there are multiple outputs from the align command
            if (sampleFileList != null) {
                planBuilder.setActualAlignOutputs(sampleFileList.readLines())
                sampleFileList.deleteExisting()
            }

            // Adding all steps with calculations
            pipeline
                .filterNot { it is MiToolCommandDelegationDescriptor<*, *> }
                .drop(1)
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
            private val forceOverride: Boolean
        ) {
            private val executionPlan = mutableListOf<ExecutionStep>()
            private var nextInputs: List<InputFileSet> = listOf(InputFileSet("", initialInputs.map { it.toString() }))
            private val outputsForCommands = mutableListOf<Pair<AnalyzeCommandDescriptor<*, *>, List<InputFileSet>>>()

            fun setActualAlignOutputs(fileNames: List<String>) {
                val outputSeed = Path(nextInputs.requireSingleton().fileNames.requireSingleton()).name
                val samples = listSamplesForSeedFileName(
                    if (outputNamePrefix.isBlank()) "alignments" else "",
                    outputSeed,
                    fileNames
                )
                nextInputs = samples.map {
                    InputFileSet(it.sample, listOf(outputFolder.resolve(it.fileName).toString()))
                }
            }

            fun executeSteps(dryRun: Boolean) {
                if (dryRun) {
                    // Printing commands that would have been executed
                    executionPlan.forEach { pe -> println(pe) }
                } else {
                    // Executing the plan
                    for (executionStep in executionPlan) {
                        println("\n" + Util.surround("mixcr ${executionStep.command}", ">", "<"))
                        println("Running:")
                        println(executionStep)
                        val actualArgs = executionStep.command.split(" ") + executionStep.args
                        val exitCode = Main.execute(*actualArgs.toTypedArray())
                        if (exitCode != 0)
                        // Terminating execution if one of the steps resulted in error
                            exitProcess(exitCode)
                    }
                }

                // Clearing the list of planned steps as they already executed
                executionPlan.clear()
            }

            private fun String.removeExtension() = substring(0, lastIndexOf('.'))

            fun addQC() {
                val inputsForQc = outputsForCommands.findLast { (command) -> command.outputSupportsQc }!!.second
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
                        listOf(
                            outputFolder.resolve(outputName.removeExtension() + ".txt").toString(),
                            outputFolder.resolve(outputName.removeExtension() + ".json").toString()
                        )
                    )
                }
            }

            fun addExportStep(cmd: AnalyzeCommandDescriptor.ExportCommandDescriptor<*>) {
                val runAfter = cmd.runAfterLastOf()
                // if there is nothing to run on (production command is removed), don't run it
                val (_, inputsForExport) = outputsForCommands.findLast { (cmd) -> cmd in runAfter } ?: return
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
                        listOf(outputFolder.resolve(outputName).toString())
                    )
                }
            }

            fun addStep(
                cmd: AnalyzeCommandDescriptor<*, *>,
                extraArgs: (outputFolder: Path, prefix: String, sampleName: String) -> List<String> = { _, _, _ -> emptyList() }
            ) {
                val roundsCount = (cmd as? AllowedMultipleRounds)?.roundsCount(paramsBundle) ?: 1

                repeat(roundsCount) { round ->

                    val nextInputsBuilder = mutableListOf<InputFileSet>()

                    nextInputs.forEach { inputs ->
                        val arguments = mutableListOf<String>()

                        if (forceOverride && cmd !is MiToolCommandDelegationDescriptor<*, *>)
                            arguments += "-f"

                        if (outputReports)
                            cmd.textReportName(outputNamePrefix, inputs.sampleName, paramsBundle, round)?.let {
                                arguments += listOf("--report", outputFolder.resolve(it).toString())
                            }

                        if (outputJsonReports)
                            cmd.jsonReportName(outputNamePrefix, inputs.sampleName, paramsBundle, round)?.let {
                                arguments += listOf("--json-report", outputFolder.resolve(it).toString())
                            }

                        if (cmd.hasThreadsOption && threadsOption.isSet) {
                            arguments += listOf("--threads", threadsOption.value.toString())
                        }

                        if (cmd.hasUseLocalTempOption && useLocalTemp.value) {
                            arguments += "--use-local-temp"
                        }

                        val outputName = cmd.outputName(outputNamePrefix, inputs.sampleName, paramsBundle, round)
                        val output = listOf(outputFolder.resolve(outputName).toString())

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
