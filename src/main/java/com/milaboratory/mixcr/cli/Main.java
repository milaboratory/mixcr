package com.milaboratory.mixcr.cli;

import com.milaboratory.util.TempFileManager;
import com.milaboratory.util.VersionInfo;
import io.repseq.core.VDJCLibraryRegistry;
import io.repseq.seqbase.SequenceResolvers;
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


public final class Main {

    private static boolean initialized = false;

    public static void main(String... args) {
        handleParseResult(parseArgs(args).getParseResult(), args);
    }

    public static void handleParseResult(ParseResult parseResult, String[] args) {
        ExceptionHandler<Object> exHandler = new ExceptionHandler<>();
        RunLast runLast = new RunLast() {
            @Override
            protected List<Object> handle(ParseResult parseResult) throws CommandLine.ExecutionException {
                List<CommandLine> parsedCommands = parseResult.asCommandLineList();
                CommandLine commandLine = parsedCommands.get(parsedCommands.size() - 1);
                Object command = commandLine.getCommand();
                if (command instanceof CommandSpec && ((CommandSpec) command).userObject() instanceof Runnable) {
                    try {
                        ((Runnable) ((CommandSpec) command).userObject()).run();
                        return new ArrayList<>();
                    } catch (ParameterException ex) {
                        throw ex;
                    } catch (CommandLine.ExecutionException ex) {
                        throw ex;
                    } catch (Exception ex) {
                        throw new CommandLine.ExecutionException(commandLine,
                                "Error while running command (" + command + "): " + ex, ex);
                    }
                }
                return super.handle(parseResult);
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

    public static CommandLine mkCmd() {
        // Getting command string if executed from script
        String command = System.getProperty("mixcr.command", "java -jar mixcr.jar");

        if (!initialized) {
            // Checking whether we are running a snapshot version
            if (VersionInfo.getVersionInfoForArtifact("mixcr").getVersion().contains("SNAPSHOT"))
                // If so, enable asserts
                ClassLoader.getSystemClassLoader().setDefaultAssertionStatus(true);

            TempFileManager.setPrefix("mixcr_");

            Path cachePath = Paths.get(System.getProperty("user.home"), ".mixcr", "cache");
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

                .addSubcommand("align", CommandAlign.class)
                .addSubcommand("assemble", CommandAssemble.class)
                .addSubcommand("assembleContigs", CommandAssembleContigs.class)
                .addSubcommand("exportClones", CommandExport.mkClonesSpec())

                .addSubcommand("assemblePartial", CommandAssemblePartialAlignments.class)
                .addSubcommand("extend", CommandExtend.class)

                .addSubcommand("exportAlignments", CommandExport.mkAlignmentsSpec())
                .addSubcommand("exportAlignmentsPretty", CommandExportAlignmentsPretty.class)
                .addSubcommand("exportClonesPretty", CommandExportClonesPretty.class)

                .addSubcommand("exportReadsForClones", CommandExportClonesReads.class)
                .addSubcommand("exportReads", CommandExportReads.class)

                .addSubcommand("mergeAlignments", CommandMergeAlignments.class)
                .addSubcommand("filterAlignments", CommandFilterAlignments.class)
                .addSubcommand("sortAlignments", CommandSortAlignments.class)

                .addSubcommand("alignmentsDiff", CommandAlignmentsDiff.class)
                .addSubcommand("clonesDiff", CommandClonesDiff.class)

                .addSubcommand("alignmentsStat", CommandAlignmentsStats.class)
                .addSubcommand("listLibraries", CommandListLibraries.class)
                .addSubcommand("versionInfo", CommandVersionInfo.class)
                .addSubcommand("pipelineInfo", CommandPipelineInfo.class)
                .addSubcommand("slice", CommandSlice.class)
                .addSubcommand("info", CommandInfo.class);

        cmd.getSubcommands()
                .get("analyze")
                .addSubcommand("amplicon", CommandAnalyze.mkAmplicon())
                .addSubcommand("shotgun", CommandAnalyze.mkShotgun());
        return cmd;
    }

    public static CommandLine parseArgs(String... args) {
        if (args.length == 0)
            args = new String[]{"help"};
        CommandLine cmd = mkCmd();
        cmd.parseArgs(args);
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
