/*
 * Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
package com.milaboratory.mixcr.cli.postanalysis;

import com.milaboratory.miplots.ExportKt;
import com.milaboratory.miplots.ExportType;
import com.milaboratory.mixcr.postanalysis.plots.Filter;
import com.milaboratory.mixcr.postanalysis.plots.MetadataKt;
import jetbrains.letsPlot.intern.Plot;
import org.jetbrains.kotlinx.dataframe.DataFrame;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Command(name = "exportPlots",
        separator = " ",
        description = "Export postanalysis plots.")
public abstract class CommandPaExportPlots extends CommandPaExport {
    @Option(description = "Plot width",
            names = {"--width"})
    public int width = 0;

    @Option(description = "Plot height",
            names = {"--height"})
    public int height = 0;

    @Option(description = "Filter by metadata. Possible filters column=value, column>=value etc.",
            names = {"--filter"})
    public List<String> filterByMetadata;

    @Parameters(description = "Output PDF/EPS/PNG/JPEG file name", index = "1", defaultValue = "plot.pdf")
    public String out;

    @Override
    protected List<String> getOutputFiles() {
        return Collections.emptyList(); // output will be always overriden
    }

    protected <T> DataFrame<T> filter(DataFrame<T> df) {
        if (filterByMetadata != null)
            for (Filter f : filterByMetadata.stream().map(f -> MetadataKt.parseFilter(metadata(), f)).collect(Collectors.toList()))
                df = f.apply(df);
        return df;
    }

    @Override
    public void validate() {
        super.validate();
        try {
            ExportType.determine(Paths.get(out));
        } catch (Exception e) {
            throwValidationException("Unsupported file extension (possible: pdf, eps, svg, png): " + out);
        }
        if (filterByMetadata != null && metadata() == null)
            throwValidationException("Filter is specified by metadata is not.");
    }

    String plotDestStr(IsolationGroup group) {
        String ext = out.substring(out.length() - 4);
        return out.substring(0, out.length() - 4) + group.extension() + ext;
    }

    Path plotDestPath(IsolationGroup group) {
        return Paths.get(plotDestStr(group));
    }

    protected void ensureOutputPathExists() {
        try {
            Files.createDirectories(Paths.get(out).toAbsolutePath().getParent());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

//    void writePlotsAndSummary(IsolationGroup group, List<byte[]> plots) {
//        ensureOutputPathExists();
//        ExportKt.writePDF(plotDestPath(group), plots);
//    }

    void writePlots(IsolationGroup group, List<Plot> plots) {
        ensureOutputPathExists();
        ExportKt.writeFile(plotDestPath(group), plots);
    }

    void writePlots(IsolationGroup group, Plot plot) {
        writePlots(group, Collections.singletonList(plot));
    }

    @Command(name = "exportPlots",
            separator = " ",
            description = "Export postanalysis plots.",
            subcommands = {
                    CommandLine.HelpCommand.class
            })
    public static class CommandExportPlotsMain {}
}
