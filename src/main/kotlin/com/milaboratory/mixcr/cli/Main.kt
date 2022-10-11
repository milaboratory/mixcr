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

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.milaboratory.cli.ValidationException
import com.milaboratory.milm.MiXCRMain
import com.milaboratory.mixcr.cli.postanalysis.CommandDownsample
import com.milaboratory.mixcr.cli.postanalysis.CommandOverlapScatter
import com.milaboratory.mixcr.cli.postanalysis.CommandPa.CommandPostanalysisMain
import com.milaboratory.mixcr.cli.postanalysis.CommandPaExportPlots.CommandExportPlotsMain
import com.milaboratory.mixcr.cli.postanalysis.CommandPaExportPlotsBasicStatistics.ExportCDR3Metrics
import com.milaboratory.mixcr.cli.postanalysis.CommandPaExportPlotsBasicStatistics.ExportDiversity
import com.milaboratory.mixcr.cli.postanalysis.CommandPaExportPlotsGeneUsage
import com.milaboratory.mixcr.cli.postanalysis.CommandPaExportPlotsGeneUsage.JUsage
import com.milaboratory.mixcr.cli.postanalysis.CommandPaExportPlotsGeneUsage.VUsage
import com.milaboratory.mixcr.cli.postanalysis.CommandPaExportPlotsOverlap
import com.milaboratory.mixcr.cli.postanalysis.CommandPaExportPlotsVJUsage
import com.milaboratory.mixcr.cli.postanalysis.CommandPaExportTables
import com.milaboratory.mixcr.cli.postanalysis.CommandPaExportTablesPreprocSummary
import com.milaboratory.mixcr.cli.postanalysis.CommandPaIndividual
import com.milaboratory.mixcr.cli.postanalysis.CommandPaListMetrics
import com.milaboratory.mixcr.cli.postanalysis.CommandPaOverlap
import com.milaboratory.mixcr.cli.qc.CommandExportQc.CommandExportQcMain
import com.milaboratory.mixcr.cli.qc.CommandExportQcAlign
import com.milaboratory.mixcr.cli.qc.CommandExportQcChainUsage
import com.milaboratory.mixcr.cli.qc.CommandExportQcCoverage
import com.milaboratory.util.GlobalObjectMappers
import com.milaboratory.util.TempFileManager
import com.milaboratory.util.VersionInfo
import io.repseq.core.VDJCLibraryRegistry
import io.repseq.seqbase.SequenceResolvers
import picocli.CommandLine
import java.nio.file.Files
import java.nio.file.Paths

object Main {
    private var initialized = false

    @JvmStatic
    fun main(args: Array<String>) {
        val versionInfo = VersionInfo.getVersionInfoForArtifact("mixcr")
        MiXCRMain.mixcrArtefactName = "mixcr." +
                versionInfo.version + "." +
                versionInfo.branch + "." +
                versionInfo.revision + "." + versionInfo.timestamp.toInstant().epochSecond
        MiXCRMain.clazz = Main::class.java
        MiXCRMain.main(*args)
        MiXCRMain.lm.reportFeature("app", "mixcr")
        MiXCRMain.lm.reportFeature("mixcr.version", versionInfo.version)
        if (args.size >= 1) MiXCRMain.lm.reportFeature("mixcr.subcommand1", args[0])
        if (args.size >= 2) MiXCRMain.lm.reportFeature("mixcr.subcommand2", args[1])
        GlobalObjectMappers.addModifier { om: ObjectMapper -> om.registerModule(kotlinModule { }) }
        GlobalObjectMappers.addModifier { om: ObjectMapper -> om.enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE) }
        handleParseResult(parseArgs(*args).parseResult, args)
    }

    fun handleParseResult(parseResult: CommandLine.ParseResult?, args: Array<String>?) {
        val exHandler = ExceptionHandler<Any?>()
        exHandler.andExit(1)
        val runLast: CommandLine.RunLast = object : CommandLine.RunLast() {
            @Throws(CommandLine.ExecutionException::class)
            override fun handle(parseResult: CommandLine.ParseResult): List<Any> {
                val parsedCommands = parseResult.asCommandLineList()
                val commandLine = parsedCommands[parsedCommands.size - 1]
                val command = commandLine.getCommand<Any>()
                return try {
                    if (command is CommandLine.Model.CommandSpec && command.userObject() is Runnable) {
                        (command.userObject() as Runnable).run()
                        return ArrayList()
                    }
                    super.handle(parseResult)
                } catch (ex: CommandLine.ParameterException) {
                    throw ex
                } catch (ex: CommandLine.ExecutionException) {
                    throw ex
                } catch (ex: Exception) {
                    throw CommandLine.ExecutionException(
                        commandLine,
                        "Error while running command ($command): $ex", ex
                    )
                }
            }
        }
        try {
            runLast.handleParseResult(parseResult)
        } catch (ex: CommandLine.ParameterException) {
            exHandler.handleParseException(ex, args!!)
        } catch (ex: CommandLine.ExecutionException) {
            exHandler.handleExecutionException(ex, parseResult)
        }
    }

    private fun assertionsDisabled(): Boolean {
        return System.getProperty("noAssertions") != null
    }

    @JvmStatic
    fun mkCmd(): CommandLine {
        System.setProperty("picocli.usage.width", "100")

        // Getting command string if executed from script
        val command = System.getProperty("mixcr.command", "java -jar mixcr.jar")
        if (!initialized) {
            // Checking whether we are running a test version
            if (!assertionsDisabled() && !VersionInfo.getVersionInfoForArtifact("mixcr").isProductionBuild) // If so, enable asserts
                ClassLoader.getSystemClassLoader().setDefaultAssertionStatus(true)
            TempFileManager.setPrefix("mixcr_")
            var cachePath = Paths.get(System.getProperty("user.home"), ".mixcr", "cache")
            val repseqioCacheEnv = System.getenv("REPSEQIO_CACHE")
            if (repseqioCacheEnv != null) {
                cachePath = Paths.get(repseqioCacheEnv)
            }
            //if (System.getProperty("allow.http") != null || System.getenv("MIXCR_ALLOW_HTTP") != null)
            //TODO add mechanism to deny http requests
            SequenceResolvers.initDefaultResolver(cachePath)
            val libraries = Paths.get(System.getProperty("user.home"), ".mixcr", "libraries")
            VDJCLibraryRegistry.getDefault().addPathResolverWithPartialSearch(".")
            if (System.getProperty("mixcr.path") != null) {
                val bin = Paths.get(System.getProperty("mixcr.path"))
                val searchPath = bin.resolve("libraries")
                if (Files.exists(searchPath)) VDJCLibraryRegistry.getDefault()
                    .addPathResolverWithPartialSearch(searchPath)
            }
            if (System.getProperty("library.path") != null) VDJCLibraryRegistry.getDefault()
                .addPathResolverWithPartialSearch(
                    System.getProperty("library.path")
                )
            if (System.getenv("MIXCR_LIBRARY_PATH") != null) VDJCLibraryRegistry.getDefault()
                .addPathResolverWithPartialSearch(
                    System.getenv("MIXCR_LIBRARY_PATH")
                )
            if (Files.exists(libraries)) VDJCLibraryRegistry.getDefault().addPathResolverWithPartialSearch(libraries)
            initialized = true
        }
        val cmd = CommandLine(CommandMain())
            .setCommandName(command)
            .addSubcommand("help", CommandLine.HelpCommand::class.java)
            .addSubcommand(CommandAnalyze.COMMAND_NAME, CommandAnalyze.Cmd::class.java) // Core command sequence
            .addSubcommand(CommandAlign.COMMAND_NAME, CommandAlign.Cmd::class.java)
            .addSubcommand(CommandRefineTagsAndSort.COMMAND_NAME, CommandRefineTagsAndSort.Cmd::class.java)
            .addSubcommand(CommandAssemblePartial.COMMAND_NAME, CommandAssemblePartial.Cmd::class.java)
            .addSubcommand(CommandExtend.COMMAND_NAME, CommandExtend.Cmd::class.java)
            .addSubcommand(
                CommandAssemble.COMMAND_NAME,
                CommandAssemble.Cmd::class.java
            ) // .addSubcommand("groupCells", CommandGroupCells.class)
            .addSubcommand(CommandAssembleContigs.COMMAND_NAME, CommandAssembleContigs.Cmd::class.java)
            .addSubcommand("postanalysis", CommandPostanalysisMain::class.java)
            .addSubcommand("downsample", CommandDownsample::class.java)
            .addSubcommand("exportPlots", CommandExportPlotsMain::class.java)
            .addSubcommand("exportTables", CommandPaExportTables::class.java)
            .addSubcommand("exportPreprocTables", CommandPaExportTablesPreprocSummary::class.java)
            .addSubcommand("overlapScatterPlot", CommandOverlapScatter::class.java)
            .addSubcommand("bam2fastq", CommandBAM2fastq::class.java)
            .addSubcommand(CommandExportAlignments.COMMAND_NAME, CommandExportAlignments.mkSpec())
            .addSubcommand("exportAlignmentsPretty", CommandExportAlignmentsPretty::class.java)
            .addSubcommand(CommandExportClones.COMMAND_NAME, CommandExportClones.mkSpec())
            .addSubcommand("exportClonesPretty", CommandExportClonesPretty::class.java)
            .addSubcommand("exportReports", CommandExportReports::class.java)
            .addSubcommand("exportQc", CommandExportQcMain::class.java)
            .addSubcommand("exportClonesOverlap", CommandExportOverlap.mkSpec())
            .addSubcommand("exportAirr", CommandExportAirr::class.java)
            .addSubcommand("exportReadsForClones", CommandExportReadsForClones::class.java)
            .addSubcommand(CommandExportAlignmentsForClones.COMMAND_NAME, CommandExportAlignmentsForClones::class.java)
            .addSubcommand("exportReads", CommandExportReads::class.java)
            .addSubcommand("mergeAlignments", CommandMergeAlignments::class.java)
            .addSubcommand(CommandFilterAlignments.COMMAND_NAME, CommandFilterAlignments::class.java)
            .addSubcommand("sortAlignments", CommandSortAlignments::class.java)
            .addSubcommand("sortClones", CommandSortClones::class.java)
            .addSubcommand("alignmentsDiff", CommandAlignmentsDiff::class.java)
            .addSubcommand("clonesDiff", CommandClonesDiff::class.java)
            .addSubcommand("itestAssemblePreClones", ITestCommandAssemblePreClones::class.java)
            .addSubcommand("alignmentsStat", CommandAlignmentsStats::class.java)
            .addSubcommand("listLibraries", CommandListLibraries::class.java)
            .addSubcommand("versionInfo", CommandVersionInfo::class.java)
            .addSubcommand("slice", CommandSlice::class.java)
            .addSubcommand(CommandFindShmTrees.COMMAND_NAME, CommandFindShmTrees::class.java)
            .addSubcommand(
                CommandExportShmTreesTableWithNodes.COMMAND_NAME,
                CommandExportShmTreesTableWithNodes.mkCommandSpec()
            )
            .addSubcommand(CommandExportShmTreesTable.COMMAND_NAME, CommandExportShmTreesTable.mkCommandSpec())
            .addSubcommand(CommandExportShmTreesNewick.COMMAND_NAME, CommandExportShmTreesNewick::class.java)
            .addSubcommand(CommandFindAlleles.COMMAND_NAME, CommandFindAlleles::class.java) // Util
            .addSubcommand(CommandExportPreset.COMMAND_NAME, CommandExportPreset.Cmd::class.java)


        // cmd.getSubcommands()
        //         .get("analyze")
        //         .addSubcommand("amplicon", CommandAnalyze.mkAmplicon())
        //         .addSubcommand("shotgun", CommandAnalyze.mkShotgun());
        cmd.subcommands["postanalysis"]!!
            .addSubcommand(
                "individual",
                CommandLine.Model.CommandSpec.forAnnotatedObject(CommandPaIndividual::class.java)
            )
            .addSubcommand("overlap", CommandLine.Model.CommandSpec.forAnnotatedObject(CommandPaOverlap::class.java))
        cmd.subcommands["exportPlots"]!!
            .addSubcommand(
                "listMetrics",
                CommandLine.Model.CommandSpec.forAnnotatedObject(CommandPaListMetrics::class.java)
            )
            .addSubcommand(
                "cdr3metrics",
                CommandLine.Model.CommandSpec.forAnnotatedObject(ExportCDR3Metrics::class.java)
            )
            .addSubcommand("diversity", CommandLine.Model.CommandSpec.forAnnotatedObject(ExportDiversity::class.java))
            .addSubcommand("vUsage", CommandLine.Model.CommandSpec.forAnnotatedObject(VUsage::class.java))
            .addSubcommand("jUsage", CommandLine.Model.CommandSpec.forAnnotatedObject(JUsage::class.java))
            .addSubcommand(
                "isotypeUsage",
                CommandLine.Model.CommandSpec.forAnnotatedObject(CommandPaExportPlotsGeneUsage.IsotypeUsage::class.java)
            )
            .addSubcommand(
                "vjUsage",
                CommandLine.Model.CommandSpec.forAnnotatedObject(CommandPaExportPlotsVJUsage::class.java)
            )
            .addSubcommand(
                "overlap",
                CommandLine.Model.CommandSpec.forAnnotatedObject(CommandPaExportPlotsOverlap::class.java)
            )
            .addSubcommand(
                "shmTrees",
                CommandLine.Model.CommandSpec.forAnnotatedObject(CommandExportShmTreesPlots::class.java)
            )
        cmd.subcommands["exportQc"]!!
            .addSubcommand("align", CommandLine.Model.CommandSpec.forAnnotatedObject(CommandExportQcAlign::class.java))
            .addSubcommand(
                "chainUsage",
                CommandLine.Model.CommandSpec.forAnnotatedObject(CommandExportQcChainUsage::class.java)
            )
            .addSubcommand(
                "coverage",
                CommandLine.Model.CommandSpec.forAnnotatedObject(CommandExportQcCoverage::class.java)
            )
        cmd.separator = " "
        return cmd
    }

    @JvmStatic
    fun parseArgs(vararg args: String?): CommandLine {
        var args = args
        if (args.size == 0) args = arrayOf<String?>("help")
        val exHandler: ExceptionHandler<*> = ExceptionHandler<Any>()
        exHandler.andExit(1)
        val cmd = mkCmd()
        try {
            cmd.parseArgs(*args)
        } catch (ex: CommandLine.ParameterException) {
            exHandler.handleParseException(ex, args as Array<String>)
        }
        return cmd
    }

    class ExceptionHandler<R> : CommandLine.DefaultExceptionHandler<R?>() {
        override fun handleParseException(ex: CommandLine.ParameterException, args: Array<String>): R? {
            if (ex is ValidationException && !ex.printHelp) {
                System.err.println(ex.message)
                return returnResultOrExit(null)
            }
            return super.handleParseException(ex, args)
        }
    }
}
