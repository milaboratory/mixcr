package com.milaboratory.mixcr.cli.newcli.mixins;

import picocli.CommandLine;

import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public class OverrideOptions {
    @CommandLine.Option(names = {"-O"}, description = "Overrides default parameter values")
    public Map<String, String> overrides = new HashMap<>();
}
