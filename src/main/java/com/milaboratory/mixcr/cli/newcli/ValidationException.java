package com.milaboratory.mixcr.cli.newcli;

import picocli.CommandLine;
import picocli.CommandLine.ParameterException;

/**
 *
 */
public class ValidationException extends ParameterException {
    public final boolean printHelp;

    public ValidationException(CommandLine commandLine, String msg, boolean printHelp) {
        super(commandLine, msg);
        this.printHelp = printHelp;
    }
}
