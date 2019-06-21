/*
 * Copyright (c) 2014-2019, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
 * (here and after addressed as Inventors)
 * All Rights Reserved
 *
 * Permission to use, copy, modify and distribute any part of this program for
 * educational, research and non-profit purposes, by non-profit institutions
 * only, without fee, and without a written agreement is hereby granted,
 * provided that the above copyright notice, this paragraph and the following
 * three paragraphs appear in all copies.
 *
 * Those desiring to incorporate this work into commercial products or use for
 * commercial purposes should contact MiLaboratory LLC, which owns exclusive
 * rights for distribution of this program for commercial purposes, using the
 * following email address: licensing@milaboratory.com.
 *
 * IN NO EVENT SHALL THE INVENTORS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
 * SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
 * ARISING OUT OF THE USE OF THIS SOFTWARE, EVEN IF THE INVENTORS HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * THE SOFTWARE PROVIDED HEREIN IS ON AN "AS IS" BASIS, AND THE INVENTORS HAS
 * NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR
 * MODIFICATIONS. THE INVENTORS MAKES NO REPRESENTATIONS AND EXTENDS NO
 * WARRANTIES OF ANY KIND, EITHER IMPLIED OR EXPRESS, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY OR FITNESS FOR A
 * PARTICULAR PURPOSE, OR THAT THE USE OF THE SOFTWARE WILL NOT INFRINGE ANY
 * PATENT, TRADEMARK OR OTHER RIGHTS.
 */
package com.milaboratory.mixcr.cli;

import com.milaboratory.cli.ValidationException;
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
                } catch (ParameterException ex) {
                    throw ex;
                } catch (CommandLine.ExecutionException ex) {
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

    public static CommandLine mkCmd() {
        System.setProperty("picocli.usage.width", "100");

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

                .addSubcommand("assemblePartial", CommandAssemblePartialAlignments.class)
                .addSubcommand("extend", CommandExtend.class)

                .addSubcommand("exportAlignments", CommandExport.mkAlignmentsSpec())
                .addSubcommand("exportAlignmentsPretty", CommandExportAlignmentsPretty.class)
                .addSubcommand("exportClones", CommandExport.mkClonesSpec())
                .addSubcommand("exportClonesPretty", CommandExportClonesPretty.class)

                .addSubcommand("exportReadsForClones", CommandExportReadsForClones.class)
                .addSubcommand("exportAlignmentsForClones", CommandExportAlignmentsForClones.class)
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

        cmd.setSeparator(" ");
        return cmd;
    }

    public static CommandLine parseArgs(String... args) {
        if (args.length == 0)
            args = new String[]{"help"};
        ExceptionHandler exHandler = new ExceptionHandler();
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
