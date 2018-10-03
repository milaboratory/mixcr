package com.milaboratory.mixcr.cli.newcli;

import picocli.CommandLine;
import picocli.CommandLine.RunLast;


public final class Main {
    public static void main(String[] args) {
        parse(args);
    }


    public static CommandLine parse(String[] args) {
        CommandLine cmd = new CommandLine(new CommandMain());
        cmd.parseWithHandlers(
                new RunLast(),
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
