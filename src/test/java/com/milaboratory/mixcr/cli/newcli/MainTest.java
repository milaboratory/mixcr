package com.milaboratory.mixcr.cli.newcli;

import org.junit.Test;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class MainTest {
    @Test
    public void test1() {
        String[] args = {"align", "-s", "hs", "-t", "2", "/var", "asvar"};
        Main.main(args);
    }

    @Test
    public void test2() {
        String[] args = {"help", "exportClones"};
        Main.main(args);
    }

    @Test
    public void asd() {
        System.out.println(Main.parse("help", "exportClones"));
    }

    @Test
    public void asdas() {
        System.out.println(Main.parse("exportClones",
                "-jHit", "-vHit", "-count", "-c", "IGH", "-t", "-o", "-m", "10", "-p", "min",
                "/Users/poslavskysv/Projects/milab/temp/alsCont.clns", "."));
    }

    @Test
    public void asdasdas() {
        System.out.println(Main.parse("exportAlignments",
                "-jHit", "-vHit",  "-c", "IGH", "-p", "min",
                "/Users/poslavskysv/Projects/milab/temp/als.vdjca", "."));
    }

    @Test
    public void et() {
        H cmd = CommandLine.populateCommand(new H(), "-l");
        System.out.println(cmd.p);
    }

    @CommandLine.Command(name = "align",
            // sortOptions = false,
            separator = " ",
            description = "Builds alignments with V,D,J and C genes for input sequencing reads.")
    class H {
        @CommandLine.Option(arity = "0", names = "-l")
        List<String> p = new ArrayList<>();
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