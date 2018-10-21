package com.milaboratory.mixcr.cli;

import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.util.stream.Collectors;

/**
 *
 */
public class ABaseCommand {
    @Spec
    public CommandLine.Model.CommandSpec spec; // injected by picocli

    @Option(names = {"-h", "--help"},
            hidden = true)
    public void requestHelp(boolean b) {
        throwValidationException("ERROR: -h / --help is not supported: use `mixcr help [command]` for command usage.");
    }

    /** Throws validation exception */
    public void throwValidationException(String message, boolean printHelp) {
        throw new ValidationException(spec.commandLine(), message, printHelp);
    }

    /** Throws validation exception */
    public void throwValidationException(String message) {
        throwValidationException(message, true);
    }

    /** Throws execution exception */
    public void throwExecutionException(String message) {
        throw new CommandLine.ExecutionException(spec.commandLine(), message);
    }

    public String getCommandLineArguments() {
        return spec.commandLine().getParseResult().originalArgs().stream().collect(Collectors.joining(" "));
    }
}
