package com.milaboratory.mixcr.cli.newcli;

import org.junit.Test;
import picocli.CommandLine;

/**
 *
 */
public class MainTest {
    @Test
    public void test1() {
        String[] args = {"align", "-s", "hs", "-t", "2", "in", "asvar"};
        Main.main(args);

//        cmd.getParseResult().hasMatchedOption()

    }

    public static class ExceptionHandler<R> extends CommandLine.DefaultExceptionHandler<R> {
        @Override
        public R handleParseException(CommandLine.ParameterException ex, String[] args) {
            return super.handleParseException(ex, args);
        }

        @Override
        public R handleExecutionException(CommandLine.ExecutionException ex, CommandLine.ParseResult parseResult) {
            if (ex.getCause() instanceof ValidationException) {
                System.err.println(ex.getCause().getMessage());
                return returnResultOrExit(null);
            }
            return super.handleExecutionException(ex, parseResult);
        }
    }
}