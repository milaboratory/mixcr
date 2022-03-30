package com.milaboratory.mixcr.cli.postanalysis;

import com.milaboratory.mixcr.postanalysis.SetPreprocessorSummary;
import picocli.CommandLine;

@CommandLine.Command(name = "preprocSummary",
        sortOptions = false,
        separator = " ",
        description = "Export preprocessing summary tables.")
public final class CommandPaExportTablesPreprocSummary extends CommandPaExportTablesBase {
    @Override
    void run1(PaResultByGroup result) {
        SetPreprocessorSummary.writeToCSV(
                outDir().resolve(outPrefix() + outExtension(result.group)),
                result.result.preprocSummary, null, separator());
    }
}
