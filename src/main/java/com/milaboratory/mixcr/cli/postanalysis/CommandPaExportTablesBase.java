package com.milaboratory.mixcr.cli.postanalysis;

import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

abstract class CommandPaExportTablesBase extends CommandPaExport {
    @Parameters(description = "Path for output files", index = "1", defaultValue = "path/table.tsv")
    public String out;

    @Override
    public void validate() {
        super.validate();
        if (!out.endsWith(".tsv") && !out.endsWith(".csv"))
            throwValidationException("Output file must have .tsv or .csv extension");
    }

    protected Path outDir() {
        return Path.of(out).toAbsolutePath().getParent();
    }

    protected String outPrefix() {
        String fName = Path.of(out).getFileName().toString();
        return fName.substring(0, fName.length() - 4);
    }

    protected String outExtension(IsolationGroup group) {
        return group.extension() + "." + out.substring(out.length() - 3);
    }

    protected String separator() {
        return out.endsWith("tsv") ? "\t" : ",";
    }

    @Override
    void run(PaResultByGroup result) {
        try {
            Files.createDirectories(outDir());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        run1(result);
    }

    abstract void run1(PaResultByGroup result);
}
