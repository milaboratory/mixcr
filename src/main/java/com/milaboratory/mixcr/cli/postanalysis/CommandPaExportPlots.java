package com.milaboratory.mixcr.cli.postanalysis;

import com.milaboratory.miplots.ExportKt;
import com.milaboratory.mixcr.postanalysis.plots.Filter;
import com.milaboratory.mixcr.postanalysis.plots.MetadataKt;
import jetbrains.letsPlot.intern.Plot;
import org.jetbrains.kotlinx.dataframe.DataFrame;
import org.jetbrains.kotlinx.dataframe.api.DataFrameIterableKt;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

abstract class CommandPaExportPlots extends CommandPaExport {
    @Option(description = "Plot width", names = {"--width"})
    public int width = 0;
    @Option(description = "Plot height", names = {"--height"})
    public int height = 0;
    @Option(description = "Filter by metadata. Possible filters column=value, column>=value etc.",
            names = {"--filter"})
    public List<String> filterByMetadata;
    @Parameters(description = "Output PDF file name", index = "1", defaultValue = "plot.pdf")
    public String out;

    protected <T> DataFrame<T> filter(DataFrame<T> df) {
        if (filterByMetadata != null)
            for (Filter f : filterByMetadata.stream().map(f -> MetadataKt.parseFilter(metadata(), f)).collect(Collectors.toList()))
                df = f.apply(df);
        return df;
    }

    @Override
    public void validate() {
        super.validate();
        if (!out.endsWith(".pdf"))
            throwValidationException("Output file must ends with .pdf extension");
        if (filterByMetadata != null && metadata == null)
            throwValidationException("Filter is specified by metadata is not.");
    }

    private DataFrame<?> metadataDf;

    /** Get metadata from file */
    protected DataFrame<?> metadata() {
        if (metadataDf != null)
            return metadataDf;
        if (metadata != null)
            return metadataDf = MetadataKt.readMetadata(metadata);
        if (getPaResult().metadata != null)
            return metadataDf = DataFrameIterableKt.toDataFrame(getPaResult().metadata);
        return null;
    }

    String plotDestStr(IsolationGroup group) {
        return out.substring(0, out.length() - 4) + group.extension() + ".pdf";
    }

    Path plotDestPath(IsolationGroup group) {
        return Path.of(plotDestStr(group));
    }

    String tablesDestStr(IsolationGroup group) {
        return out.substring(0, out.length() - 3) + "preproc" + group.extension() + ".tsv";
    }

    Path tablesDestPath(IsolationGroup group) {
        return Path.of(tablesDestStr(group));
    }

    void writeTables(IsolationGroup group, List<byte[]> tables) {
        ExportKt.writePDF(tablesDestPath(group), tables);
    }

    void writePlots(IsolationGroup group, List<Plot> plots) {
        ExportKt.writePDFFigure(plotDestPath(group), plots);
    }

    void writePlots(IsolationGroup group, Plot plot) {
        ExportKt.writePDF(tablesDestPath(group), plot);
    }
}
