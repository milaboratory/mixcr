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

import com.milaboratory.app.ValidationException
import com.milaboratory.cli.POverridesBuilderOps
import com.milaboratory.mixcr.cli.CommandAlign.SAVE_OUTPUT_FILE_NAMES_OPTION
import com.milaboratory.mixcr.cli.CommandAlign.STRICT_SAMPLE_NAME_MATCHING_OPTION
import com.milaboratory.mixcr.cli.CommandAlign.inputFileGroups
import com.milaboratory.mixcr.cli.CommandAlign.listSamplesForSeedFileName
import com.milaboratory.mixcr.cli.CommonDescriptions.Labels
import com.milaboratory.mixcr.presets.AlignMixins
import com.milaboratory.mixcr.presets.AnyMiXCRCommand
import com.milaboratory.mixcr.presets.FullSampleSheetParsed
import com.milaboratory.mixcr.presets.MiXCRCommandDescriptor
import com.milaboratory.mixcr.presets.MiXCRCommandDescriptor.Companion.dotAfterIfNotBlank
import com.milaboratory.mixcr.presets.MiXCRParamsBundle
import com.milaboratory.mixcr.presets.MiXCRParamsSpec
import com.milaboratory.mixcr.presets.MiXCRPipeline
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


        @Mixin
        lateinit var pathsForNotAligned: CommandAlign.PathsForNotAligned

        @Mixin
        lateinit var threadsOption: ThreadsOption

        @Mixin
        lateinit var useLocalTemp: UseLocalTempOption

        private val mixins: MiXCRMixinCollection
            get() = MiXCRMixinCollection.empty + pipelineMixins + alignMixins + refineAndSortMixins + assembleMixins +
                    assembleContigsMixins + exportMixins + genericMixins

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
            hidden = true,
            names = ["--run-qc-on-each-step"],
            order = OptionsOrder.qcOnEveryStep
        )
        private var qcAfterEachStep: Boolean = false

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
                .let {
                    it.first to it.second.steps.sorted()
                }

            // Pre-calculating set of actions requiring a QC to be executed after them
            val commandToRunQcFor = mutableSetOf<AnyMiXCRCommand>()
            if (bundle.qc?.checks?.isNotEmpty() == true) {
                commandToRunQcFor +=
                    pipeline.findLast { it.outputSupportsQc } as AnyMiXCRCommand
                if (qcAfterEachStep)
                    commandToRunQcFor += pipeline.filter { it.outputSupportsQc }
            }

            // Creating execution plan
            if (pipeline[0] != MiXCRCommandDescriptor.align)
                throw ValidationException("Pipeline must stat from the align action.")

            val planBuilder = PlanBuilder(
                bundle, outputFolder, outputNamePrefix,
                !noReports, !noJsonReports,
                inputTemplates, threadsOption, useLocalTemp
            )

            // Helper function, captures commandToRunQcFor
            fun PlanBuilder.addStepAndQc(
                cmd: AnyMiXCRCommand,
                extraArgs: List<String> = emptyList()
            ) {
                addStep(cmd, extraArgs)
                if (cmd in commandToRunQcFor)
                    addQC()
            }

            // Adding an option to save output files by align
            val sampleFileList = outputFolder.resolve("${outputNamePrefix.dotAfterIfNotBlank()}align.list")
                .takeIf { bundle.align!!.splitBySample }
            val extraAlignArgs =
                (sampleFileList?.let { listOf(SAVE_OUTPUT_FILE_NAMES_OPTION, it.toString()) } ?: emptyList()) +
                        (if (strictMatching) listOf(STRICT_SAMPLE_NAME_MATCHING_OPTION) else emptyList())

            // Adding "align" step
            if (outputNoUsedReads)
                pathsForNotAligned.fillWithDefaults(inputFileGroups.inputType, outputFolder, outputNamePrefix)
            planBuilder.addStepAndQc(
                MiXCRCommandDescriptor.align,
                listOf("--preset", presetName) + extraAlignArgs
                        + mixins.flatMap { it.cmdArgs } + pathsForNotAligned.argsForAlign()
            )

            planBuilder.executeSteps(forceOverwrite, dryRun)

            // Taking into account that there are multiple outputs from the align command
            if (sampleFileList != null) {
                planBuilder.setActualAlignOutputs(sampleFileList.readLines())
                sampleFileList.deleteExisting()
            }

            // Adding all other steps
            pipeline.drop(1).forEach { cmd ->
                planBuilder.addStepAndQc(cmd)
            }

            // Executing all actions after align
            planBuilder.executeSteps(forceOverwrite, dryRun)

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
        ) {
            private val executionPlan = mutableListOf<ExecutionStep>()
            private val rounds = mutableMapOf<AnyMiXCRCommand, Int>()
            private var nextInputs: List<InputFileSet> = listOf(InputFileSet("", initialInputs.map { it.toString() }))

            private var qcRounds = 0

            fun setActualAlignOutputs(fileNames: List<String>) {
                val outputSeed = Path(nextInputs.requireSingleton().fileNames.requireSingleton()).name
                val samples = listSamplesForSeedFileName(outputSeed, fileNames)
                nextInputs = samples.map {
                    InputFileSet(it.sample, listOf(outputFolder.resolve(it.fileName).toString()))
                }
            }

            fun executeSteps(forceOverwrite: Boolean, dryRun: Boolean) {
                if (dryRun) {
                    // Printing commands that would have been executed
                    executionPlan.forEach { pe -> println(pe) }
                } else {
                    // Cleanup output files before executing the plan, if requested by the user
                    if (forceOverwrite) {
                        var removedOne = false
                        executionPlan.flatMap { it.output }.forEach {
                            val op = Path(it)
                            if (op.exists()) {
                                if (!removedOne) {
                                    println("Cleanup:")
                                    removedOne = true
                                }
                                println("  - removing: $it")
                                op.deleteExisting()
                            }
                        }
                    }

                    // Executing the plan
                    for (executionStep in executionPlan) {
                        println("\n" + Util.surround("mixcr ${executionStep.command}", ">", "<"))
                        println("Running:")
                        println(executionStep)
                        val actualArgs = arrayOf(executionStep.command) + executionStep.args.toTypedArray()
                        val exitCode = Main.mkCmd().execute(*actualArgs)
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
                for (nextInput in nextInputs) {
                    check(nextInput.fileNames.size == 1)
                    executionPlan += ExecutionStep(
                        MiXCRCommandDescriptor.qc.command,
                        qcRounds++,
                        listOf("--print-to-stdout"),
                        emptyList(),
                        listOf(nextInput.fileNames.first()),
                        listOf(nextInput.fileNames.first().removeExtension() + ".qc.txt")
                    )
                }
            }

            fun addStep(
                cmd: AnyMiXCRCommand,
                extraArgs: List<String> = emptyList()
            ) {
                val round = rounds.compute(cmd) { c, p ->
                    if (p == null)
                        0
                    else {
                        if (!c.allowMultipleRounds)
                            throw IllegalArgumentException("${c.command} don't allow multiple rounds of execution")
                        p + 1
                    }
                }!!

                val nextInputsBuilder = mutableListOf<InputFileSet>()

                nextInputs.forEach { inputs ->
                    // val outputNamePrefixFull = if (inputs.sampleName != "")
                    //     outputNamePrefix.dotIfNotBlank() + inputs.sampleName else outputNamePrefix

                    val arguments = mutableListOf<String>()

                    if (outputReports)
                        cmd.textReportName(outputNamePrefix, inputs.sampleName, paramsBundle, round)
                            ?.let {
                                arguments += listOf("--report", outputFolder.resolve(it).toString())
                            }

                    if (outputJsonReports)
                        cmd.jsonReportName(outputNamePrefix, inputs.sampleName, paramsBundle, round)
                            ?.let {
                                arguments += listOf("--json-report", outputFolder.resolve(it).toString())
                            }

                    if (cmd.hasThreadsOption && threadsOption.isSet) {
                        arguments += listOf("--threads", threadsOption.value.toString())
                    }

                    if (cmd.hasUseLocalTempOption && useLocalTemp.value) {
                        arguments += "--use-local-temp"
                    }

                    val output = listOf(
                        outputFolder.resolve(cmd.outputName(outputNamePrefix, inputs.sampleName, paramsBundle, round))
                            .toString()
                    )

                    executionPlan += ExecutionStep(
                        cmd.command,
                        round,
                        arguments,
                        extraArgs,
                        inputs.fileNames,
                        output,
                    )

                    nextInputsBuilder +=
                        InputFileSet(
                            inputs.sampleName,
                            listOf(
                                outputFolder.resolve(
                                    cmd.outputName(
                                        outputNamePrefix,
                                        inputs.sampleName,
                                        paramsBundle,
                                        round
                                    )
                                )
                                    .toString()
                            )
                        )
                }

                nextInputs = nextInputsBuilder
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
            override fun toString() = (listOf("mixcr", command) + args).joinToString(" ")
        }
    }
}
