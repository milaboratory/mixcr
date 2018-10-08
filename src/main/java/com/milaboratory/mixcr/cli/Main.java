package com.milaboratory.mixcr.cli;

import com.milaboratory.util.TempFileManager;
import com.milaboratory.util.VersionInfo;
import io.repseq.core.VDJCLibraryRegistry;
import io.repseq.seqbase.SequenceResolvers;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.RunLast;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;


public final class Main {
    public static void main(String[] args) {
        parse(args);
    }

    private static boolean initialized = false;

    public static CommandLine parse(String... args) {
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

        CommandLine cmd = new CommandLine(new CommandMain());
        cmd.setCommandName(command);

        cmd.getSubcommands().get("analyze").addSubcommand("amplicon", CommandAnalyze.mkAmplicon());
        cmd.getSubcommands().get("analyze").addSubcommand("shotgun", CommandAnalyze.mkShotgun());

        cmd.addSubcommand("exportAlignments", CommandExport.mkAlignmentsSpec());
        cmd.addSubcommand("exportClones", CommandExport.mkClonesSpec());
        cmd.parseWithHandlers(
                new RunLast() {
                    @Override
                    protected List<Object> handle(CommandLine.ParseResult parseResult) throws CommandLine.ExecutionException {
                        List<CommandLine> parsedCommands = parseResult.asCommandLineList();
                        CommandLine commandLine = parsedCommands.get(parsedCommands.size() - 1);
                        Object command = commandLine.getCommand();
                        if (command instanceof CommandSpec && ((CommandSpec) command).userObject() instanceof Runnable) {
                            try {
                                ((Runnable) ((CommandSpec) command).userObject()).run();
                                return new ArrayList<>();
                            } catch (CommandLine.ParameterException ex) {
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
                },
                new ExceptionHandler<>(),
                args);
        return cmd;
    }

    public static class ExceptionHandler<R> extends CommandLine.DefaultExceptionHandler<R> {
        @Override
        public R handleParseException(CommandLine.ParameterException ex, String[] args) {
            if (ex instanceof ValidationException && !((ValidationException) ex).printHelp) {
                System.err.println(ex.getMessage());
                return returnResultOrExit(null);
            }
            return super.handleParseException(ex, args);
        }
    }
}
