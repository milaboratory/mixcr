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
package com.milaboratory.mixcr.cli;

import com.milaboratory.cli.ValidationException;
import com.milaboratory.milm.MiXCRMain;
import com.milaboratory.mixcr.cli.analyze.CommandAnalyzeAmplicon;
import com.milaboratory.mixcr.cli.analyze.CommandAnalyzeShotgun;
import com.milaboratory.mixcr.cli.postanalysis.*;
import com.milaboratory.mixcr.cli.qc.CommandExportQc;
import com.milaboratory.mixcr.cli.qc.CommandExportQcAlign;
import com.milaboratory.mixcr.cli.qc.CommandExportQcChainUsage;
import com.milaboratory.mixcr.cli.qc.CommandExportQcCoverage;
import com.milaboratory.util.GlobalObjectMappers;
import com.milaboratory.util.TempFileManager;
import com.milaboratory.util.VersionInfo;
import io.repseq.core.VDJCLibraryRegistry;
import io.repseq.seqbase.SequenceResolvers;
import kotlin.Unit;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.RunLast;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static com.fasterxml.jackson.module.kotlin.ExtensionsKt.kotlinModule;
import static com.milaboratory.mixcr.cli.CommandCorrectAndSortTags.CORRECT_AND_SORT_TAGS_COMMAND_NAME;

public final class Main {

    private static boolean initialized = false;

    public static void main(String... args) {
        VersionInfo versionInfo = VersionInfo.getVersionInfoForArtifact("mixcr");
        MiXCRMain.mixcrArtefactName = "mixcr." +
                versionInfo.getVersion() + "." +
                versionInfo.getBranch() + "." +
                versionInfo.getRevision() + "." +
                versionInfo.getTimestamp().toInstant().getEpochSecond();
        MiXCRMain.clazz = Main.class;
        MiXCRMain.main(args);
        MiXCRMain.lm.reportFeature("app", "mixcr");
        MiXCRMain.lm.reportFeature("mixcr.version", versionInfo.getVersion());
        if (args.length >= 1)
            MiXCRMain.lm.reportFeature("mixcr.subcommand1", args[0]);
        if (args.length >= 2)
            MiXCRMain.lm.reportFeature("mixcr.subcommand2", args[1]);

        GlobalObjectMappers.addModifier(om -> om.registerModule(kotlinModule(builder -> Unit.INSTANCE)));

        handleParseResult(parseArgs(args).getParseResult(), args);
    }

    public static void handleParseResult(ParseResult parseResult, String[] args) {
        ExceptionHandler<Object> exHandler = new ExceptionHandler<>();
        exHandler.andExit(1);
        RunLast runLast = new RunLast() {
            @Override
            protected List<Object> handle(ParseResult parseResult) throws CommandLine.ExecutionException {
                List<CommandLine> parsedCommands = parseResult.asCommandLineList();
                CommandLine commandLine = parsedCommands.get(parsedCommands.size() - 1);
                Object command = commandLine.getCommand();
                try {
                    if (command instanceof CommandSpec && ((CommandSpec) command).userObject() instanceof Runnable) {
                        ((Runnable) ((CommandSpec) command).userObject()).run();
                        return new ArrayList<>();
                    }
                    return super.handle(parseResult);
                } catch (ParameterException | CommandLine.ExecutionException ex) {
                    throw ex;
                } catch (Exception ex) {
                    throw new CommandLine.ExecutionException(commandLine,
                            "Error while running command (" + command + "): " + ex, ex);
                }
            }
        };

        try {
            runLast.handleParseResult(parseResult);
        } catch (ParameterException ex) {
            exHandler.handleParseException(ex, args);
        } catch (CommandLine.ExecutionException ex) {
            exHandler.handleExecutionException(ex, parseResult);
        }
    }

    private static boolean assertionsDisabled() {
        return System.getProperty("noAssertions") != null;
    }

    public static CommandLine mkCmd() {
        System.setProperty("picocli.usage.width", "100");

        // Getting command string if executed from script
        String command = System.getProperty("mixcr.command", "java -jar mixcr.jar");

        if (!initialized) {
            // Checking whether we are running a test version
            if (!assertionsDisabled() && !VersionInfo.getVersionInfoForArtifact("mixcr").isProductionBuild())
                // If so, enable asserts
                ClassLoader.getSystemClassLoader().setDefaultAssertionStatus(true);

            TempFileManager.setPrefix("mixcr_");

            Path cachePath = Paths.get(System.getProperty("user.home"), ".mixcr", "cache");
            String repseqioCacheEnv = System.getenv("REPSEQIO_CACHE");
            if (repseqioCacheEnv != null) {
                cachePath = Paths.get(repseqioCacheEnv);
            }
            //if (System.getProperty("allow.http") != null || System.getenv("MIXCR_ALLOW_HTTP") != null)
            //TODO add mechanism to deny http requests
            SequenceResolvers.initDefaultResolver(cachePath);

            Path libraries = Paths.get(System.getProperty("user.home"), ".mixcr", "libraries");

            VDJCLibraryRegistry.getDefault().addPathResolverWithPartialSearch(".");

            if (System.getProperty("mixcr.path") != null) {
                Path bin = Paths.get(System.getProperty("mixcr.path"));
                Path searchPath = bin.resolve("libraries");
                if (Files.exists(searchPath))
                    VDJCLibraryRegistry.getDefault().addPathResolverWithPartialSearch(searchPath);
            }

            if (System.getProperty("library.path") != null)
                VDJCLibraryRegistry.getDefault().addPathResolverWithPartialSearch(System.getProperty("library.path"));

            if (System.getenv("MIXCR_LIBRARY_PATH") != null)
                VDJCLibraryRegistry.getDefault().addPathResolverWithPartialSearch(System.getenv("MIXCR_LIBRARY_PATH"));

            if (Files.exists(libraries))
                VDJCLibraryRegistry.getDefault().addPathResolverWithPartialSearch(libraries);

            initialized = true;
        }

        CommandLine cmd = new CommandLine(new CommandMain())
                .setCommandName(command)
                .addSubcommand("help", CommandLine.HelpCommand.class)
                .addSubcommand("analyze", CommandAnalyze.CommandAnalyzeMain.class)

                .addSubcommand("postanalysis", CommandPa.CommandPostanalysisMain.class)
                .addSubcommand("downsample", CommandDownsample.class)
                .addSubcommand("exportPlots", CommandPaExportPlots.CommandExportPlotsMain.class)
                .addSubcommand("exportTables", CommandPaExportTables.class)
                .addSubcommand("exportPreprocTables", CommandPaExportTablesPreprocSummary.class)
                .addSubcommand("overlapScatterPlot", CommandOverlapScatter.class)

                .addSubcommand("align", CommandAlign.class)
                .addSubcommand("assemble", CommandAssemble.class)
                // .addSubcommand("groupCells", CommandGroupCells.class)
                .addSubcommand("assembleContigs", CommandAssembleContigs.class)

                .addSubcommand(CORRECT_AND_SORT_TAGS_COMMAND_NAME, CommandCorrectAndSortTags.class)

                .addSubcommand("assemblePartial", CommandAssemblePartialAlignments.class)
                .addSubcommand("extend", CommandExtend.class)

                .addSubcommand("exportAlignments", CommandExport.mkAlignmentsSpec())
                .addSubcommand("exportAlignmentsPretty", CommandExportAlignmentsPretty.class)
                .addSubcommand("exportClones", CommandExport.mkClonesSpec())
                .addSubcommand("exportClonesPretty", CommandExportClonesPretty.class)

                .addSubcommand("exportReports", CommandExportReports.class)
                .addSubcommand("exportQc", CommandExportQc.class)

                .addSubcommand("exportClonesOverlap", CommandExportOverlap.mkSpec())

                .addSubcommand("exportAirr", CommandExportAirr.class)

                .addSubcommand("exportReadsForClones", CommandExportReadsForClones.class)
                .addSubcommand("exportAlignmentsForClones", CommandExportAlignmentsForClones.class)
                .addSubcommand("exportReads", CommandExportReads.class)

                .addSubcommand("mergeAlignments", CommandMergeAlignments.class)
                .addSubcommand("filterAlignments", CommandFilterAlignments.class)
                .addSubcommand("sortAlignments", CommandSortAlignments.class)
                .addSubcommand("sortClones", CommandSortClones.class)

                .addSubcommand("alignmentsDiff", CommandAlignmentsDiff.class)
                .addSubcommand("clonesDiff", CommandClonesDiff.class)

                .addSubcommand("itestAssemblePreClones", ITestCommandAssemblePreClones.class)

                .addSubcommand("alignmentsStat", CommandAlignmentsStats.class)
                .addSubcommand("listLibraries", CommandListLibraries.class)
                .addSubcommand("versionInfo", CommandVersionInfo.class)
                .addSubcommand("slice", CommandSlice.class);

        cmd.getSubcommands()
                .get("analyze")
                .addSubcommand("amplicon", CommandAnalyze.mkAmplicon())
                .addSubcommand("shotgun", CommandAnalyze.mkShotgun())
                .addSubcommand("amplicon2", CommandAnalyzeAmplicon.class)
                .addSubcommand("shotgun2", CommandAnalyzeShotgun.class)
                .addSubcommand("10x", CommandAnalyze.mk10x());

        cmd.getSubcommands()
                .get("postanalysis")
                .addSubcommand("individual", CommandSpec.forAnnotatedObject(CommandPaIndividual.class))
                .addSubcommand("overlap", CommandSpec.forAnnotatedObject(CommandPaOverlap.class));

        cmd.getSubcommands()
                .get("exportPlots")
                .addSubcommand("listMetrics", CommandSpec.forAnnotatedObject(CommandPaListMetrics.class))
                .addSubcommand("cdr3metrics", CommandSpec.forAnnotatedObject(CommandPaExportPlotsBasicStatistics.ExportCDR3Metrics.class))
                .addSubcommand("diversity", CommandSpec.forAnnotatedObject(CommandPaExportPlotsBasicStatistics.ExportDiversity.class))
                .addSubcommand("vUsage", CommandSpec.forAnnotatedObject(CommandPaExportPlotsGeneUsage.ExportVUsage.class))
                .addSubcommand("jUsage", CommandSpec.forAnnotatedObject(CommandPaExportPlotsGeneUsage.ExportJUsage.class))
                .addSubcommand("isotypeUsage", CommandSpec.forAnnotatedObject(CommandPaExportPlotsGeneUsage.ExportIsotypeUsage.class))
                .addSubcommand("vjUsage", CommandSpec.forAnnotatedObject(CommandPaExportPlotsVJUsage.class))
                .addSubcommand("overlap", CommandSpec.forAnnotatedObject(CommandPaExportPlotsOverlap.class));

        cmd.getSubcommands()
                .get("exportQc")
                .addSubcommand("align", CommandSpec.forAnnotatedObject(CommandExportQcAlign.class))
                .addSubcommand("chainUsage", CommandSpec.forAnnotatedObject(CommandExportQcChainUsage.class))
                .addSubcommand("coverage", CommandSpec.forAnnotatedObject(CommandExportQcCoverage.class));

        cmd.setSeparator(" ");
        return cmd;
    }

    public static CommandLine parseArgs(String... args) {
        if (args.length == 0)
            args = new String[]{"help"};
        ExceptionHandler<?> exHandler = new ExceptionHandler<>();
        exHandler.andExit(1);
        CommandLine cmd = mkCmd();
        try {
            cmd.parseArgs(args);
        } catch (ParameterException ex) {
            exHandler.handleParseException(ex, args);
        }
        return cmd;
    }

    public static class ExceptionHandler<R> extends CommandLine.DefaultExceptionHandler<R> {
        @Override
        public R handleParseException(ParameterException ex, String[] args) {
            if (ex instanceof ValidationException && !((ValidationException) ex).printHelp) {
                System.err.println(ex.getMessage());
                return returnResultOrExit(null);
            }
            return super.handleParseException(ex, args);
        }
    }
}
