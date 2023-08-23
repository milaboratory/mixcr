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

import com.milaboratory.app.ApplicationException
import com.milaboratory.app.ValidationException
import com.milaboratory.app.logger
import com.milaboratory.core.sequence.AminoAcidSequence
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.milm.MiXCRMain
import com.milaboratory.miplots.StandardPlots
import com.milaboratory.mitool.pattern.SequenceSetCollection
import com.milaboratory.mixcr.cli.MiXCRCommand.OptionsOrder
import com.milaboratory.mixcr.cli.postanalysis.CommandDownsample
import com.milaboratory.mixcr.cli.postanalysis.CommandOverlapScatter
import com.milaboratory.mixcr.cli.postanalysis.CommandPa.CommandPostanalysisMain
import com.milaboratory.mixcr.cli.postanalysis.CommandPaExportPlots.CommandExportPlotsMain
import com.milaboratory.mixcr.cli.postanalysis.CommandPaExportPlotsBasicStatistics
import com.milaboratory.mixcr.cli.postanalysis.CommandPaExportPlotsGeneUsage
import com.milaboratory.mixcr.cli.postanalysis.CommandPaExportPlotsOverlap
import com.milaboratory.mixcr.cli.postanalysis.CommandPaExportPlotsVJUsage
import com.milaboratory.mixcr.cli.postanalysis.CommandPaExportTablesBase
import com.milaboratory.mixcr.cli.postanalysis.CommandPaIndividual
import com.milaboratory.mixcr.cli.postanalysis.CommandPaListMetrics
import com.milaboratory.mixcr.cli.postanalysis.CommandPaOverlap
import com.milaboratory.mixcr.cli.qc.CommandExportQc.CommandExportQcMain
import com.milaboratory.mixcr.cli.qc.CommandExportQcAlign
import com.milaboratory.mixcr.cli.qc.CommandExportQcChainUsage
import com.milaboratory.mixcr.cli.qc.CommandExportQcCoverage
import com.milaboratory.mixcr.cli.qc.CommandExportQcTags
import com.milaboratory.mixcr.presets.Presets
import com.milaboratory.mixcr.util.MiXCRVersionInfo
import com.milaboratory.util.TempFileManager
import com.milaboratory.util.VersionInfo
import com.sun.management.OperatingSystemMXBean
import io.repseq.core.Chains
import io.repseq.core.GeneFamilyName
import io.repseq.core.GeneFeature
import io.repseq.core.GeneFeatures
import io.repseq.core.GeneName
import io.repseq.core.GeneType
import io.repseq.core.GeneVariantName
import io.repseq.core.ReferencePoint
import io.repseq.core.VDJCLibraryRegistry
import io.repseq.seqbase.SequenceResolvers
import org.apache.commons.io.FileUtils
import picocli.AutoComplete.GenerateCompletion
import picocli.CommandLine
import picocli.CommandLine.IHelpSectionRenderer
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Model.OptionSpec
import picocli.CommandLine.Model.UsageMessageSpec.SECTION_KEY_COMMAND_LIST
import picocli.CommandLine.Model.UsageMessageSpec.SECTION_KEY_COMMAND_LIST_HEADING
import picocli.CommandLine.Model.UsageMessageSpec.SECTION_KEY_SYNOPSIS
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.Path
import kotlin.system.exitProcess

object Main {
    private var initialized = false

    @JvmStatic
    fun main(vararg args: String) {
        initializeSystem()
        if ("-v" in args) {
            CommandMain.VersionProvider().version.forEach { println(it) }
            exitProcess(0)
        }
        val versionInfo = VersionInfo.getVersionInfoForArtifact("mixcr")
        System.setProperty("application", "mixcr.${versionInfo.version}.${versionInfo.revision}")
        MiXCRMain.mixcrArtefactName = "mixcr." +
                versionInfo.version + "." +
                versionInfo.branch + "." +
                versionInfo.revision + "." + versionInfo.timestamp.toInstant().epochSecond
        MiXCRMain.clazz = Main::class.java
        MiXCRMain.main(*args)
        MiXCRMain.lm.reportFeature("app", "mixcr")
        MiXCRMain.lm.reportFeature("mixcr.version", versionInfo.version)
        val actualArgs = args.toList() - "-h"
        if (actualArgs.isNotEmpty()) MiXCRMain.lm.reportFeature("mixcr.subcommand1", actualArgs[0])
        if (actualArgs.size >= 2) MiXCRMain.lm.reportFeature("mixcr.subcommand2", actualArgs[1])
        // GlobalObjectMappers.addModifier { om: ObjectMapper -> om.registerModule(kotlinModule {}) }
        // GlobalObjectMappers.addModifier { om: ObjectMapper -> om.enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE) }
        SequenceSetCollection.addSearchPath(Path(System.getProperty("user.home"), ".mixcr", "presets"))
        val commandLine = mkCmd(args)
        try {
            exitProcess(commandLine.execute(*args))
        } catch (e: OutOfMemoryError) {
            if (logger.verbose) {
                e.printStackTrace()
            }
            val memoryInOSMessage = memoryInOS()?.let { "${it / FileUtils.ONE_MB} Mb" }
            System.err.println("Not enough memory for run command, try to increase -Xmx. Available memory: ${memoryInOSMessage ?: "unknown"}")
            System.err.println("Example: `mixcr -Xmx40g ${args.joinToString(" ")}`")
            val mb = Runtime.getRuntime().maxMemory() / FileUtils.ONE_MB
            System.err.println("This run used approximately ${mb}m of memory")
            exitProcess(2)
        }
    }

    private fun assertionsDisabled(): Boolean = System.getProperty("noAssertions") != null

    fun mkCmd(cmdArgs: Array<out String>? = null): CommandLine {
        System.setProperty("picocli.usage.width", "100")

        val groups: MutableList<Pair<String, Set<String>>> = mutableListOf()

        fun CommandLine.commandsGroup(group: CommandsGroup): CommandLine {
            group.commands.forEach { (name, command) ->
                addSubcommand(name, command)
            }
            groups += group.name to group.commands.map { it.first }.toSet()
            return this
        }

        // Getting command string if executed from script
        val command = System.getProperty("mixcr.command", "java -jar mixcr.jar")

        val cmd = CommandLine(CommandMain::class.java)
            .setCommandName(command)
            .addSubcommand(CommandSpec.forAnnotatedObject(GenerateCompletion()).also {
                it.usageMessage().hidden(true)
                it.remove(it.findOption("-h"))
            })
            .addSubcommand(CommandAnalyze.COMMAND_NAME, CommandAnalyze.mkCommandSpec())
            .addSubcommand(CommandAlign.COMMAND_NAME, CommandAlign.mkCommandSpec())
            .addSubcommand(CommandRefineTagsAndSort.COMMAND_NAME, CommandRefineTagsAndSort.Cmd::class.java)
            .addSubcommand(CommandAssemblePartial.COMMAND_NAME, CommandAssemblePartial.Cmd::class.java)
            .addSubcommand(CommandExtend.COMMAND_NAME, CommandExtend.Cmd::class.java)
            .addSubcommand(CommandAssemble.COMMAND_NAME, CommandAssemble.Cmd::class.java)
            .addSubcommand(CommandAssembleContigs.COMMAND_NAME, CommandAssembleContigs.Cmd::class.java)
            .addSubcommand(CommandGroupClones.COMMAND_NAME, CommandGroupClones.Cmd::class.java)
            .addSubcommand(CommandFindAlleles.COMMAND_NAME, CommandFindAlleles::class.java)
            .addSubcommand(CommandFindShmTrees.COMMAND_NAME, CommandFindShmTrees.mkCommandSpec())
            .addSubcommand("downsample", CommandDownsample::class.java)
            .addSubcommand(CommandQcChecks.COMMAND_NAME, CommandQcChecks.Cmd::class.java)
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
                    .addSubcommand("exportTables", CommandPaExportTablesBase.Tables::class.java)
                    .addSubcommand("exportPreprocTables", CommandPaExportTablesBase.PreprocSummary::class.java)
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
                        CommandExportReportsAsTable.COMMAND_NAME,
                        CommandExportReportsAsTable.mkCommandSpec()
                    )
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
                    .addSubcommand("buildLibrary", CommandBuildLibrary::class.java)
                    .addSubcommand("mergeLibrary", CommandMergeLibrary::class.java)
                    .addSubcommand("debugLibrary", CommandDebugLibrary::class.java)
                    .addSubcommand(Presets.LIST_PRESETS_COMMAND_NAME, CommandListPresets::class.java)
            )

            // hidden
            .addSubcommand("alignmentsStat", CommandAlignmentsStats::class.java)
            .addSubcommand("bam2fastq", CommandBAM2fastq::class.java)
            .addSubcommand("itestAssemblePreClones", ITestCommandAssemblePreClones::class.java)
            .addSubcommand("listLibraries", CommandListLibraries::class.java)
            .addSubcommand("help", DeprecatedHelp::class.java)
            .addSubcommand("exportHelp", CommandExportHelp::class.java)
            .addSubcommand("exportAllPresets", CommandExportAllPresets::class.java)
            .addSubcommand("exportSchemas", CommandExportSchemas::class.java)

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

        return cmd
            .registerLogger()
            .overrideSynopsysHelp()
            .registerConvertors()
            .registerExceptionHandlers(cmdArgs)
    }

    fun initializeSystem() {
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
            // if (System.getProperty("allow.http") != null || System.getenv("MIXCR_ALLOW_HTTP") != null)
            // TODO add mechanism to deny http requests
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
    }

    private fun CommandLine.registerExceptionHandlers(cmdArgs: Array<out String>?): CommandLine {
        val defaultParameterExceptionHandler = parameterExceptionHandler
        setParameterExceptionHandler { ex, args ->
            err.println(MiXCRVersionInfo.get().shortestVersionString)
            when (val cause = ex.cause) {
                is ValidationException -> ex.commandLine.handleValidationException(cause)
                else -> defaultParameterExceptionHandler.handleParseException(ex, args)
            }
        }
        setExecutionExceptionHandler { ex, commandLine, _ ->
            err.println("Please copy the following information along with the stacktrace:")
            err.println("   Version: " + MiXCRVersionInfo.get().shortestVersionString)
            err.println("        OS: " + System.getProperty("os.name"))
            err.println("      Java: " + System.getProperty("java.version"))
            if (cmdArgs != null) {
                err.println("  Cmd args: " + cmdArgs.joinToString(" "))
            }
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

                else -> {
                    ex.rethrowOOM()
                    throw CommandLine.ExecutionException(
                        commandLine,
                        "Error while running command ${commandLine.commandName} $ex", ex
                    )
                }
            }
        }
        return this
    }

    private fun Throwable.rethrowOOM() {
        if (this is OutOfMemoryError) throw this
        suppressed.forEach { it.rethrowOOM() }
        cause?.rethrowOOM()
    }

    private fun CommandLine.registerConvertors(): CommandLine {
        registerConverter { ReferencePoint.parse(it) }
        registerConverter { GeneFeatures.parse(it) }
        registerConverter { GeneFeature.parse(it) }
        registerConverter { GeneType.parse(it) }
        registerConverter { Chains.parse(it) }
        registerConverter { NucleotideSequence(it) }
        registerConverter { AminoAcidSequence(it) }
        registerConverter { GeneVariantName(it) }
        registerConverter { GeneName(it) }
        registerConverter { GeneFamilyName(it) }
        registerConverter { arg ->
            StandardPlots.PlotType.values().find { it.cliName == arg.lowercase() }
                ?: throw ValidationException("unknown plot type: $arg")
        }
        isCaseInsensitiveEnumValuesAllowed = true
        return this
    }

    private fun CommandLine.overrideSynopsysHelp(): CommandLine {
        setHelpSectionRenderRecursively(SECTION_KEY_SYNOPSIS) { help ->
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
                    // try to use long names for options. It's too expensive to rewrite picocli code, so temporary remove short aliases from options
                    val optionsToConvert = commandSpec.options().toList()
                        .filter {
                            try {
                                commandSpec.remove(it)
                                true
                            } catch (e: UnsupportedOperationException) {
                                check(e.message == "Cannot remove ArgSpec that is part of an ArgGroup")
                                // can't remove option from argGroup
                                false
                            }
                        }
                    // rebuild options with only longest names
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
                    // return original options
                    withLongNames.forEach { commandSpec.remove(it) }
                    optionsToConvert.forEach { commandSpec.add(it) }
                    result
                }
            }
        }
        return this
    }

    private fun CommandLine.registerLogger(): CommandLine {
        setExecutionStrategy { parseResult ->
            val setVerbose = parseResult.subcommands().any { it.matchedOption("--verbose")?.getValue() ?: false }
            logger.verbose = logger.verbose || setVerbose
            logger.noWarnings = logger.noWarnings ||
                    parseResult.subcommands().any { it.matchedOption("--no-warnings")?.getValue() ?: false }
            if (setVerbose) {
                logger.debug {
                    val memoryInJVMMessage = "${Runtime.getRuntime().maxMemory() / FileUtils.ONE_MB} Mb"
                    val memoryInOSMessage = memoryInOS()?.let { "${it / FileUtils.ONE_MB} Mb" }

                    val availableProcessors = Runtime.getRuntime().availableProcessors()
                    val usedCPU = parseResult.subcommands()
                        .firstNotNullOfOrNull { it.matchedOption("--treads")?.getValue<Int?>() }
                    "Available CPU: $availableProcessors, used CPU: ${usedCPU ?: "unspecified"}. " +
                            "Available memory: ${memoryInOSMessage ?: "unknown"}, used memory: $memoryInJVMMessage"
                }
            }
            CommandLine.RunLast().execute(parseResult)
        }

        registerLoggerOptions(subcommands.values)
        return this
    }

    private fun memoryInOS(): Long? =
        try {
            (ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean).totalPhysicalMemorySize
        } catch (e: Throwable) {
            null
        }

    private fun registerLoggerOptions(commandLines: Collection<CommandLine>) {
        for (commandLine in commandLines) {
            if (commandLine.subcommands.isEmpty()) {
                commandLine.commandSpec
                    .addOption(
                        OptionSpec
                            .builder("-nw", "--no-warnings")
                            .description("Suppress all warning messages.")
                            .order(OptionsOrder.logger)
                            .build()
                    )
                    .addOption(
                        OptionSpec
                            .builder("--verbose")
                            .description("Verbose messages.")
                            .order(OptionsOrder.logger + 1)
                            .build()
                    )
            } else {
                registerLoggerOptions(commandLine.subcommands.values)
            }
        }
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
