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

import com.milaboratory.cli.POverridesBuilderOps
import com.milaboratory.mitool.helpers.PathPatternExpandException
import com.milaboratory.mitool.helpers.parseAndRunAndCorrelateFSPattern
import com.milaboratory.mixcr.AnyMiXCRCommand
import com.milaboratory.mixcr.MiXCRCommandDescriptor
import com.milaboratory.mixcr.MiXCRParamsBundle
import com.milaboratory.mixcr.MiXCRParamsSpec
import com.milaboratory.mixcr.MiXCRPipeline
import picocli.CommandLine.ArgGroup
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Model.CommandSpec
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
import kotlin.io.path.isDirectory
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
                .description(
                    "Two fastq files for paired reads or one file for single read data.",
                    "Use {{n}} if you want to concatenate files from multiple lanes, like:",
                    "my_file_L{{n}}_R1.fastq.gz my_file_L{{n}}_R2.fastq.gz"
                )
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
                .description("Path prefix telling mixcr where to put all output files")
                .build()
        )

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
            paramLabel = "<preset_name>",
            description = ["Name of the analysis preset."]
        )
        private lateinit var presetName: String

        @Parameters(
            index = "1",
            arity = "2..3",
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
        var exportMixins: List<ExportMiXCRMixins> = mutableListOf()

        @Mixin
        var genericMixins: GenericMiXCRMixins? = null

        @Mixin
        lateinit var pathsForNotAligned: CommandAlign.PathsForNotAligned

        @Mixin
        lateinit var threadsOption: ThreadsOption

        @Mixin
        lateinit var useLocalTemp: UseLocalTempOption

        private val mixins: MiXCRMixinCollection
            get() = MiXCRMixinCollection.empty + pipelineMixins + alignMixins + assembleMixins +
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

        // parsing inOut

        private val inputTemplates get() = inOut.dropLast(1).map { Paths.get(it) }

        private val outSuffix get() = inOut.last()

        /** Provides access to presets, mixins application, etc.. */
        private val paramsResolver = object : MiXCRParamsResolver<MiXCRPipeline>(MiXCRParamsBundle::pipeline) {
            override fun POverridesBuilderOps<MiXCRPipeline>.paramsOverrides() {}
        }

        override fun validate() {
            CommandAlign.checkInputTemplates(inputTemplates)
            val inputFileGroups = try {
                CommandAlign.InputFileGroups(inputTemplates.parseAndRunAndCorrelateFSPattern())
            } catch (e: PathPatternExpandException) {
                throw ValidationException(e.message!!)
            }
            pathsForNotAligned.validate(inputFileGroups.inputType)
            inputFileGroups.allFiles.forEach { input ->
                ValidationException.requireFileExists(input)
            }
            ValidationException.requireNoExtension(outSuffix)
        }

        override fun run0() {
            // Calculating output folder and output file suffix
            var outputIsFolder = outSuffix.endsWith(File.separator)
            val outputPath = Path(outSuffix)
            if (!outputIsFolder && outputPath.exists() && outputPath.isDirectory())
                outputIsFolder = true
            val outputNamePrefix = if (!outputIsFolder) outputPath.fileName.toString() else ""
            val outputFolder = if (!outputIsFolder) outputPath.parent ?: Path(".") else outputPath

            // Creating output folder if not yet exists
            if (!outputFolder.exists())
                outputFolder.createDirectories()

            // Creating params spec
            val mixins = mixins.mixins
            val paramsSpec = MiXCRParamsSpec(presetName, mixins)

            // Resolving parameters and sorting the pipeline according to the natural command order
            // (it must already be sorted, but just in case)
            val (bundle, pipeline) = paramsResolver.resolve(paramsSpec, printParameters = false)
                .let {
                    it.first to it.second.steps.sorted()
                }

            // Creating execution plan
            if (pipeline[0] != MiXCRCommandDescriptor.align)
                throw ValidationException("Pipeline must stat from the align action.")
            val planBuilder = PlanBuilder(
                bundle, outputFolder, outputNamePrefix,
                !noReports, !noJsonReports,
                inputTemplates, threadsOption, useLocalTemp
            )
            // Adding "align" step
            planBuilder.addStep(
                MiXCRCommandDescriptor.align,
                listOf("--preset", presetName) + mixins.flatMap { it.cmdArgs } + listOf(
                    "--not-aligned-R1" to pathsForNotAligned.notAlignedReadsR1,
                    "--not-aligned-R2" to pathsForNotAligned.notAlignedReadsR2,
                    "--not-parsed-R1" to pathsForNotAligned.notParsedReadsR1,
                    "--not-parsed-R2" to pathsForNotAligned.notParsedReadsR2,
                )
                    .filter { it.second != null }
                    .flatMap { listOf(it.first, it.second!!.toString()) }
            )
            // Adding all other steps
            pipeline.drop(1).forEach { cmd ->
                planBuilder.addStep(cmd)
            }

            // Using created plan
            val plan = planBuilder.executionPlan

            if (dryRun) {
                // Printing commands that would have been executed
                plan.forEach { pe -> println(pe) }
            } else {
                // Cleanup output files before executing the plan, if requested by the user
                if (forceOverwrite) {
                    var removedOne = false
                    plan.flatMap { it.output }.forEach {
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
                for (executionStep in plan) {
                    println("====================")
                    println("Running:")
                    println(executionStep)
                    val actualArgs = arrayOf(executionStep.command) + executionStep.args.toTypedArray()
                    val exitCode = Main.mkCmd().execute(*actualArgs)
                    if (exitCode != 0)
                        // Terminating execution if one of the steps resulted in error
                        exitProcess(exitCode)
                }
            }
        }

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
            val executionPlan = mutableListOf<ExecutionStep>()
            private val rounds = mutableMapOf<AnyMiXCRCommand, Int>()
            private var nextInputs: List<String> = initialInputs.map { it.toString() }

            fun addStep(cmd: AnyMiXCRCommand, extraArgs: List<String> = emptyList()) {
                val round = rounds.compute(cmd) { c, p ->
                    if (p == null)
                        0
                    else {
                        if (!c.allowMultipleRounds)
                            throw IllegalArgumentException("${c.command} don't allow multiple rounds of execution")
                        p + 1
                    }
                }!!
                val output =
                    listOf(outputFolder.resolve(cmd.outputName(outputNamePrefix, paramsBundle, round)).toString())

                val arguments = mutableListOf<String>()

                if (outputReports)
                    cmd.reportName(outputNamePrefix, paramsBundle, round)
                        ?.let {
                            arguments += listOf("--report", outputFolder.resolve(it).toString())
                        }

                if (outputJsonReports)
                    cmd.jsonReportName(outputNamePrefix, paramsBundle, round)
                        ?.let {
                            arguments += listOf("--json-report", outputFolder.resolve(it).toString())
                        }

                if (cmd.hasThreadsOption && threadsOption.isSet) {
                    arguments += listOf("--threads", threadsOption.value.toString())
                }

                if (cmd.hasUseLocalTempOption && useLocalTemp.value) {
                    arguments += "--use-local-temp"
                }

                executionPlan += ExecutionStep(
                    cmd.command,
                    round,
                    arguments,
                    extraArgs,
                    nextInputs,
                    output,
                )

                nextInputs = output
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
