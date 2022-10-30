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
import com.milaboratory.core.sequence.AminoAcidSequence
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.milm.MiXCRMain
import com.milaboratory.miplots.StandardPlots
import com.milaboratory.mixcr.basictypes.GeneFeatures
import com.milaboratory.mixcr.cli.postanalysis.CommandDownsample
import com.milaboratory.mixcr.cli.postanalysis.CommandOverlapScatter
import com.milaboratory.mixcr.cli.postanalysis.CommandPa.CommandPostanalysisMain
import com.milaboratory.mixcr.cli.postanalysis.CommandPaExportPlots.CommandExportPlotsMain
import com.milaboratory.mixcr.cli.postanalysis.CommandPaExportPlotsBasicStatistics
import com.milaboratory.mixcr.cli.postanalysis.CommandPaExportPlotsGeneUsage
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
import com.milaboratory.mixcr.cli.qc.CommandExportQcTags
import com.milaboratory.util.GlobalObjectMappers
import com.milaboratory.util.TempFileManager
import com.milaboratory.util.VersionInfo
import io.repseq.core.Chains
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import io.repseq.core.ReferencePoint
import io.repseq.core.VDJCLibraryRegistry
import io.repseq.seqbase.SequenceResolvers
import picocli.CommandLine
import picocli.CommandLine.IHelpSectionRenderer
import picocli.CommandLine.Model.OptionSpec
import picocli.CommandLine.Model.UsageMessageSpec.SECTION_KEY_COMMAND_LIST
import picocli.CommandLine.Model.UsageMessageSpec.SECTION_KEY_COMMAND_LIST_HEADING
import picocli.CommandLine.Model.UsageMessageSpec.SECTION_KEY_SYNOPSIS
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess

object Main {
    private var initialized = false

    @JvmStatic
    fun main(vararg args: String) {
        val versionInfo = VersionInfo.getVersionInfoForArtifact("mixcr")
        MiXCRMain.mixcrArtefactName = "mixcr." +
                versionInfo.version + "." +
                versionInfo.branch + "." +
                versionInfo.revision + "." + versionInfo.timestamp.toInstant().epochSecond
        MiXCRMain.clazz = Main::class.java
        MiXCRMain.main(*args)
        MiXCRMain.lm.reportFeature("app", "mixcr")
        MiXCRMain.lm.reportFeature("mixcr.version", versionInfo.version)
        if (args.isNotEmpty()) MiXCRMain.lm.reportFeature("mixcr.subcommand1", args[0])
        if (args.size >= 2) MiXCRMain.lm.reportFeature("mixcr.subcommand2", args[1])
        GlobalObjectMappers.addModifier { om: ObjectMapper -> om.registerModule(kotlinModule {}) }
        GlobalObjectMappers.addModifier { om: ObjectMapper -> om.enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE) }
        exitProcess(mkCmd().execute(*args))
    }

    private fun assertionsDisabled(): Boolean {
        return System.getProperty("noAssertions") != null
    }

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

        val groups: MutableList<Pair<String, Set<String>>> = mutableListOf()

        fun CommandLine.commandsGroup(group: CommandsGroup): CommandLine {
            group.commands.forEach { (name, command) ->
                addSubcommand(name, command)
            }
            groups += group.name to group.commands.map { it.first }.toSet()
            return this
        }

        val cmd = CommandLine(CommandMain::class.java)
            .setCommandName(command)
            .addSubcommand(CommandAnalyze.COMMAND_NAME, CommandAnalyze.mkCommandSpec())
            .addSubcommand(CommandAlign.COMMAND_NAME, CommandAlign.mkCommandSpec())
            .addSubcommand(CommandRefineTagsAndSort.COMMAND_NAME, CommandRefineTagsAndSort.Cmd::class.java)
            .addSubcommand(CommandAssemblePartial.COMMAND_NAME, CommandAssemblePartial.Cmd::class.java)
            .addSubcommand(CommandExtend.COMMAND_NAME, CommandExtend.Cmd::class.java)
            .addSubcommand(CommandAssemble.COMMAND_NAME, CommandAssemble.Cmd::class.java)
            // .addSubcommand("groupCells", CommandGroupCells.class)
            .addSubcommand(CommandAssembleContigs.COMMAND_NAME, CommandAssembleContigs.Cmd::class.java)
            .addSubcommand(CommandFindAlleles.COMMAND_NAME, CommandFindAlleles::class.java)
            .addSubcommand(CommandFindShmTrees.COMMAND_NAME, CommandFindShmTrees.mkCommandSpec())
            .addSubcommand("downsample", CommandDownsample::class.java)
            .commandsGroup(
                CommandsGroup("Postanalysis commands")
                    .addSubcommand(
                        "postanalysis",
                        CommandLine(CommandPostanalysisMain::class.java)
                            .addSubcommand("individual", CommandPaIndividual.mkCommandSpec())
                            .addSubcommand("overlap", CommandPaOverlap.mkCommandSpec())
                            .addSubcommand("help", DeprecatedHelp::class.java)
                    )
            )
            .commandsGroup(
                CommandsGroup("Export commands")
                    .addSubcommand("exportTables", CommandPaExportTables::class.java)
                    .addSubcommand("exportPreprocTables", CommandPaExportTablesPreprocSummary::class.java)
                    .addSubcommand(
                        "exportPlots",
                        CommandLine(CommandExportPlotsMain::class.java)
                            .addSubcommand("listMetrics", CommandPaListMetrics::class.java)
                            .addSubcommand(
                                "cdr3metrics",
                                CommandPaExportPlotsBasicStatistics.ExportCDR3Metrics::class.java
                            )
                            .addSubcommand("diversity", CommandPaExportPlotsBasicStatistics.ExportDiversity::class.java)
                            .addSubcommand("vUsage", CommandPaExportPlotsGeneUsage.VUsage::class.java)
                            .addSubcommand("jUsage", CommandPaExportPlotsGeneUsage.JUsage::class.java)
                            .addSubcommand("isotypeUsage", CommandPaExportPlotsGeneUsage.IsotypeUsage::class.java)
                            .addSubcommand("vjUsage", CommandPaExportPlotsVJUsage::class.java)
                            .addSubcommand("overlap", CommandPaExportPlotsOverlap::class.java)
                            .addSubcommand("shmTrees", CommandExportShmTreesPlots::class.java)
                            .addSubcommand("help", DeprecatedHelp::class.java)
                    )
                    .addSubcommand("overlapScatterPlot", CommandOverlapScatter::class.java)
                    .addSubcommand(CommandExportAlignments.COMMAND_NAME, CommandExportAlignments.mkSpec())
                    .addSubcommand("exportAlignmentsPretty", CommandExportAlignmentsPretty::class.java)
                    .addSubcommand(CommandExportClones.COMMAND_NAME, CommandExportClones.mkSpec())
                    .addSubcommand("exportClonesPretty", CommandExportClonesPretty::class.java)
                    .addSubcommand("exportShmTreesWithNodes", CommandExportShmTreesTableWithNodes.mkCommandSpec())
                    .addSubcommand("exportShmTrees", CommandExportShmTreesTable.mkCommandSpec())
                    .addSubcommand("exportShmTreesNewick", CommandExportShmTreesNewick::class.java)
                    .addSubcommand("exportReports", CommandExportReports::class.java)
                    .addSubcommand(
                        "exportQc",
                        CommandLine(CommandExportQcMain::class.java)
                            .addSubcommand("align", CommandExportQcAlign.mkCommandSpec())
                            .addSubcommand("chainUsage", CommandExportQcChainUsage.mkCommandSpec())
                            .addSubcommand("tags", CommandExportQcTags.mkCommandSpec())
                            .addSubcommand("coverage", CommandExportQcCoverage.mkCommandSpec())
                            .addSubcommand("help", DeprecatedHelp::class.java)
                    )
                    .addSubcommand("exportClonesOverlap", CommandExportOverlap.mkSpec())
                    .addSubcommand("exportAirr", CommandExportAirr::class.java)
            )
            .commandsGroup(
                CommandsGroup("Util commands")
                    .addSubcommand("exportReadsForClones", CommandExportReadsForClones::class.java)
                    .addSubcommand("exportAlignmentsForClones", CommandExportAlignmentsForClones::class.java)
                    .addSubcommand("exportReads", CommandExportReads::class.java)
                    .addSubcommand("mergeAlignments", CommandMergeAlignments.mkCommandSpec())
                    .addSubcommand("filterAlignments", CommandFilterAlignments::class.java)
                    .addSubcommand("sortAlignments", CommandSortAlignments::class.java)
                    .addSubcommand("sortClones", CommandSortClones::class.java)
                    .addSubcommand("alignmentsDiff", CommandAlignmentsDiff::class.java)
                    .addSubcommand("clonesDiff", CommandClonesDiff::class.java)
                    .addSubcommand("versionInfo", CommandVersionInfo::class.java)
                    .addSubcommand("slice", CommandSlice::class.java)
                    .addSubcommand("exportPreset", CommandExportPreset::class.java)
            )

            //hidden
            .addSubcommand("alignmentsStat", CommandAlignmentsStats::class.java)
            .addSubcommand("bam2fastq", CommandBAM2fastq::class.java)
            .addSubcommand("itestAssemblePreClones", ITestCommandAssemblePreClones::class.java)
            .addSubcommand("listLibraries", CommandListLibraries::class.java)
            .addSubcommand("help", DeprecatedHelp::class.java)
            .addSubcommand("exportHelp", CommandExportHelp::class.java)
            .addSubcommand("exportAllPresets", CommandExportAllPresets::class.java)
            .addSubcommand("exportSchemas", CommandExportSchemas::class.java)

        cmd.setHelpSectionRenderRecursively(SECTION_KEY_SYNOPSIS) { help ->
            val commandSpec = help.commandSpec()
            when {
                !commandSpec.usageMessage().customSynopsis().isNullOrEmpty() -> help.customSynopsis()
                commandSpec.usageMessage().abbreviateSynopsis() -> help.abbreviatedSynopsis()
                commandSpec.subcommands().isNotEmpty() -> help.detailedSynopsis(
                    help.synopsisHeadingLength(),
                    Comparator.comparing { option: OptionSpec -> option.required() }.reversed()
                        .thenComparing { option: OptionSpec -> option.order() },
                    false
                )
                else -> {
                    //try to use long names for options. It's too expensive to rewrite picocli code, so temporary remove short aliases from options
                    val optionsToConvert = commandSpec.options().toList()
                        .filter {
                            try {
                                commandSpec.remove(it)
                                true
                            } catch (e: java.lang.UnsupportedOperationException) {
                                check(e.message == "Cannot remove ArgSpec that is part of an ArgGroup")
                                //can't remove option from argGroup
                                false
                            }
                        }
                    //rebuild options with only longest names
                    val withLongNames = optionsToConvert
                        .map { optionSpec ->
                            OptionSpec.builder(optionSpec)
                                .names(optionSpec.longestName())
                                .build()
                        }
                    withLongNames.forEach { commandSpec.add(it) }
                    val result = help.detailedSynopsis(
                        help.synopsisHeadingLength(),
                        Comparator.comparing { option: OptionSpec -> option.required() }.reversed()
                            .thenComparing { option: OptionSpec -> option.order() },
                        false
                    )
                    //return original options
                    withLongNames.forEach { commandSpec.remove(it) }
                    optionsToConvert.forEach { commandSpec.add(it) }
                    result
                }
            }
        }

        cmd.helpSectionMap.remove(SECTION_KEY_COMMAND_LIST_HEADING)
        cmd.helpSectionMap[SECTION_KEY_COMMAND_LIST] = IHelpSectionRenderer { help ->
            var result = help.createHeading("Base commands:\n")
            val groupedCommands = groups.map { it.second }.flatten().toSet()
            result += help.commandList(help.subcommands().filterKeys { it !in groupedCommands })
            help.subcommands()
                .forEach { (_, helpForCommand) ->
                    if (helpForCommand.subcommands().isNotEmpty()) {
                        val editedDescription = helpForCommand.commandSpec().usageMessage().description()
                        editedDescription[editedDescription.size - 1] =
                            editedDescription[editedDescription.size - 1] + " This command has subcommands, use -h to see more"
                        helpForCommand.commandSpec().usageMessage().description(*editedDescription)
                    }
                }
            groups.forEach { (groupHeader, commands) ->
                result += help.createHeading("$groupHeader:\n")
                result += help.commandList(help.subcommands().filterKeys { it in commands })
            }
            result
        }

        cmd.separator = " "
        cmd.registerConverter { ReferencePoint.parse(it) }
        cmd.registerConverter { GeneFeatures.parse(it) }
        cmd.registerConverter { GeneFeature.parse(it) }
        cmd.registerConverter { GeneType.parse(it) }
        cmd.registerConverter { Chains.parse(it) }
        cmd.registerConverter { NucleotideSequence(it) }
        cmd.registerConverter { AminoAcidSequence(it) }
        cmd.registerConverter { arg ->
            StandardPlots.PlotType.values().find { it.cliName == arg.lowercase() }
                ?: throw ValidationException("unknown plot type: $arg")
        }
        cmd.isCaseInsensitiveEnumValuesAllowed = true
        val defaultParameterExceptionHandler = cmd.parameterExceptionHandler
        cmd.setParameterExceptionHandler { ex, args ->
            when (val cause = ex.cause) {
                is ValidationException -> ex.commandLine.handleValidationException(cause)
                else -> defaultParameterExceptionHandler.handleParseException(ex, args)
            }
        }
        cmd.setExecutionExceptionHandler { ex, commandLine, _ ->
            when (ex) {
                is ValidationException -> commandLine.handleValidationException(ex)
                is ApplicationException -> {
                    commandLine.printErrorMessage(ex.message)
                    if (ex.printHelp) {
                        commandLine.printHelp()
                    }
                    if (logger.verbose) {
                        ex.printStackTrace()
                    }
                    commandLine.commandSpec.exitCodeOnExecutionException()
                }
                else -> throw CommandLine.ExecutionException(
                    commandLine,
                    "Error while running command ${commandLine.commandName} $ex", ex
                )
            }
        }

        return cmd
    }

    private fun CommandLine.setHelpSectionRenderRecursively(name: String, renderer: IHelpSectionRenderer) {
        helpSectionMap[name] = renderer
        subcommands.values.forEach { it.setHelpSectionRenderRecursively(name, renderer) }
    }

    private inline fun <reified T : Any> CommandLine.registerConverter(noinline function: (String) -> T): CommandLine {
        registerConverter(T::class.java) { arg ->
            when {
                arg.isNullOrBlank() -> null
                else -> function(arg)
            }
        }
        return this
    }

    private fun CommandLine.handleValidationException(exception: ValidationException): Int {
        printErrorMessage(exception.message)
        if (exception.printHelp) {
            printHelp()
        }
        if (logger.verbose) {
            exception.printStackTrace()
        }
        return commandSpec.exitCodeOnInvalidInput()
    }

    private fun CommandLine.printHelp() {
        usage(err, colorScheme)
    }

    private fun CommandLine.printErrorMessage(message: String) {
        err.println(colorScheme.errorText(message))
    }

    @JvmStatic
    fun parseArgs(vararg args: String): CommandLine {
        @Suppress("UNCHECKED_CAST")
        val resultArgs = when {
            args.isEmpty() -> arrayOf("help")
            else -> args as Array<String>
        }
        val cmd = mkCmd()
        cmd.parseArgs(*resultArgs)
        return cmd
    }

    private class CommandsGroup(
        val name: String
    ) {
        val commands: MutableList<Pair<String, Any>> = mutableListOf()

        fun addSubcommand(name: String, command: Any): CommandsGroup {
            commands += name to command
            return this
        }
    }
}
