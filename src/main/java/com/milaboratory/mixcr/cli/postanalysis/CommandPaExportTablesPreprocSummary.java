package com.milaboratory.mixcr.cli.postanalysis;

import com.milaboratory.mixcr.postanalysis.SetPreprocessorSummary;
import picocli.CommandLine.Command;

@Command(name = "preprocSummary",
        sortOptions = false,
        separator = " ",
        description = "Export preprocessing summary tables.")
public final class CommandPaExportTablesPreprocSummary extends CommandPaExportTablesBase {
    public CommandPaExportTablesPreprocSummary() {}

    public CommandPaExportTablesPreprocSummary(PaResult paResult, String out) {
        super(paResult, out);
    }

    @Override
    void run1(PaResultByGroup result) {
        SetPreprocessorSummary.byCharToCSV(
                outDir().resolve(outPrefix() + outExtension(result.group)),
                result.schema,
                result.result,
                separator());
    }
}
