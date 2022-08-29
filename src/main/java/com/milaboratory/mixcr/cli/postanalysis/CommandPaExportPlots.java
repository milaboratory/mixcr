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
import com.milaboratory.mixcr.cli.CommonDescriptions;
import com.milaboratory.mixcr.postanalysis.plots.Filter;
import com.milaboratory.mixcr.postanalysis.plots.MetadataKt;
import com.milaboratory.util.StringUtil;
import jetbrains.letsPlot.intern.Plot;
import org.jetbrains.kotlinx.dataframe.DataFrame;
import org.jetbrains.kotlinx.dataframe.api.ToDataFrameKt;
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
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

@Command(name = "exportPlots",
        separator = " ",
        description = "Export postanalysis plots.")
public abstract class CommandPaExportPlots extends CommandPaExport {
    @Option(description = CommonDescriptions.METADATA,
            names = {"--metadata"})
    public String metadata;

    @Option(description = "Plot width",
            names = {"--width"})
    public int width = 0;

    @Option(description = "Plot height",
            names = {"--height"})
    public int height = 0;

    @Option(description = "Filter by metadata. Possible filters column=value, column>=value etc.",
            names = {"--filter"},
            split = ",")
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

    private DataFrame<?> metadataDf;

    /** Get metadata from file */
    protected DataFrame<?> metadata() {
        if (metadataDf != null)
            return metadataDf;
        if (metadata != null)
            return metadataDf = MetadataKt.readMetadata(metadata);
        if (getPaResult().metadata != null)
            return metadataDf = ToDataFrameKt.toDataFrame(getPaResult().metadata);
        return null;
    }

    @Override
    public void validate() {
        super.validate();
        try {
            ExportType.determine(Paths.get(out));
        } catch (Exception e) {
            throwValidationException("Unsupported file extension (possible: pdf, eps, svg, png): " + out);
        }
        if (metadata != null && !metadata.endsWith(".csv") && !metadata.endsWith(".tsv"))
            throwValidationException("Metadata should be .csv or .tsv");
        if (metadata != null) {
            if (!metadata().containsColumn("sample"))
                throwValidationException("Metadata must contain 'sample' column");
            List<String> samples = getInputFiles();
            @SuppressWarnings("unchecked")
            Map<String, String> mapping = StringUtil.matchLists(
                    samples,
                    ((List<Object>) metadata().get("sample").toList())
                            .stream().map(Object::toString).collect(toList())
            );
            if (mapping.size() < samples.size() || mapping.values().stream().anyMatch(Objects::isNull))
                throwValidationException("Metadata samples does not match input file names.");
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
