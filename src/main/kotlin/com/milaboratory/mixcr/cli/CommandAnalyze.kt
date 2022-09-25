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
import com.milaboratory.mixcr.MiXCRCommand
import com.milaboratory.mixcr.MiXCRParamsBundle
import com.milaboratory.mixcr.MiXCRParamsSpec
import com.milaboratory.mixcr.MiXCRPipeline
import picocli.CommandLine
import picocli.CommandLine.*
import java.io.File
import java.nio.file.Path
import kotlin.io.path.*

object CommandAnalyze {
    const val COMMAND_NAME = "analyze"

    @Command(
        name = "analyze",
        separator = " ",
        description = ["Run full MiXCR pipeline for specific input."],
    )
    class Cmd : AbstractMiXCRCommand() {
        @Parameters(
            index = "0",
            arity = "1",
            paramLabel = "preset_name",
            description = ["Name of the analysis preset."]
        )
        private lateinit var presetName: String

        @Parameters(
            index = "1",
            arity = "2..3",
            paramLabel = "input_R1.fastq[.gz] [input_R2.fastq[.gz]] output_prefix",
            description = ["Paths of input files with sequencing data and path prefix ",
                "telling mixcr where to put all output files"]
        )
        private var inOut: List<String> = mutableListOf()

        @ArgGroup(validate = false, heading = "Analysis mix-ins")
        private var mixins: AllMiXCRMixins? = null

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
            names = ["--no-reports"]
        )
        private var noReports: Boolean = false

        @Option(
            description = ["Don't output json report files for each of the steps"],
            names = ["--no-json-reports"]
        )
        private var noJsonReports: Boolean = false

        // parsing inOut

        private val inFiles get() = inOut.dropLast(1)

        private val outSuffix get() = inOut[inOut.size - 1]

        // the following two lines are to implement the AbstractMiXCRCommand interfaces,
        // analyze is an exception, and it not fully use the functionality of the AbstractMiXCRCommand
        // TODO maybe it is a good idea to restructure the CLI classes to make "analyze" fit more naturally in the hierarchy
        override fun getInputFiles() = emptyList<String>() // inFiles
        override fun getOutputFiles() = emptyList<String>()

        /** Provides access to presets, mixins application, etc.. */
        private val paramsResolver = object : MiXCRParamsResolver<MiXCRPipeline>(
            this, MiXCRParamsBundle::pipeline
        ) {
            override fun POverridesBuilderOps<MiXCRPipeline>.paramsOverrides() {}
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
            val mixins = mixins?.mixins ?: emptyList()
            val paramsSpec = MiXCRParamsSpec(presetName, mixins)

            // Resolving parameters and sorting the pipeline according to the natural command order
            // (it must already be sorted, but just in case)
            val (bundle, pipeline) = paramsResolver.resolve(paramsSpec, printParameters = false)
                .let {
                    it.first to it.second.sorted()
                }

            // Creating execution plan
            if (pipeline[0] != MiXCRCommand.align)
                throwExecutionExceptionKotlin("Pipeline must stat from the align action.")
            val planBuilder = PlanBuilder(
                bundle, outputFolder, outputNamePrefix,
                !noReports, !noJsonReports,
                inFiles
            )
            // Adding "align" step
            planBuilder.addStep(MiXCRCommand.align,
                listOf("--preset", presetName) + mixins.flatMap { it.cmdArgs })
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
                    val cmd = executionStep.command.createCommand()
                    CommandLine(cmd).parseArgs(*executionStep.args.toTypedArray())
                    cmd.run()
                }
            }
        }

        class PlanBuilder(
            private val paramsBundle: MiXCRParamsBundle,
            private val outputFolder: Path,
            private val outputNamePrefix: String,
            private val outputReports: Boolean,
            private val outputJsonReports: Boolean,
            initialInputs: List<String>,
        ) {
            val executionPlan = mutableListOf<ExecutionStep>()
            private val rounds = mutableMapOf<MiXCRCommand<*>, Int>()
            private var nextInputs: List<String> = initialInputs

            fun addStep(cmd: MiXCRCommand<*>, extraArgs: List<String> = emptyList()) {
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

                executionPlan += ExecutionStep(
                    cmd, round,
                    arguments,
                    extraArgs,
                    nextInputs,
                    output,
                )

                nextInputs = output
            }
        }

        data class ExecutionStep(
            val command: MiXCRCommand<*>,
            val round: Int,
            val arguments: List<String>,
            val extraArgs: List<String>,
            val inputs: List<String>,
            val output: List<String>
        ) {
            val args get() = arguments + extraArgs + inputs + output
            override fun toString() = (listOf("mixcr", command.command) + args).joinToString(" ")
        }
    }
}
