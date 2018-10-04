package com.milaboratory.mixcr.cli.newcli;

import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.RunLast;

import java.util.ArrayList;
import java.util.List;


public final class Main {
    public static void main(String[] args) {
        parse(args);
    }

    public static CommandLine parse(String... args) {
        CommandLine cmd = new CommandLine(new CommandMain());
        cmd.addSubcommand("exportAlignments", CommandExport.mkAlignmentsSpec());
        cmd.addSubcommand("exportClones", CommandExport.mkClonesSpec());
        cmd.parseWithHandlers(
                new RunLast() {
                    @Override
                    protected List<Object> handle(CommandLine.ParseResult parseResult) throws CommandLine.ExecutionException {
                        List<CommandLine> parsedCommands = parseResult.asCommandLineList();
                        CommandLine commandLine = parsedCommands.get(parsedCommands.size() - 1);
                        Object command = commandLine.getCommand();
                        if (command instanceof CommandSpec && ((CommandSpec) command).userObject() instanceof CommandExport) {
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
