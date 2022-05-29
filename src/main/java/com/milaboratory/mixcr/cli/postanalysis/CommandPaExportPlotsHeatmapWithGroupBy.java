package com.milaboratory.mixcr.cli.postanalysis;

import picocli.CommandLine.Option;

import java.util.List;

public abstract class CommandPaExportPlotsHeatmapWithGroupBy extends CommandPaExportPlotsHeatmap {
    @Option(description = "Group heatmaps by specific metadata properties.",
            names = {"--group-by"})
    public List<String> groupBy;
}
