package com.milaboratory.mixcr.cli.postanalysis;

import picocli.CommandLine.Option;

public abstract class CommandPaExportPlotsHeatmap extends CommandPaExportPlots {
    @Option(description = "Width of horizontal labels. One unit corresponds to the width of one tile.",
            names = {"--h-labels-size"})
    public double hLabelsSize = -1.0;
    @Option(description = "Height of vertical labels. One unit corresponds to the height of one tile.",
            names = {"--v-labels-size"})
    public double vLabelsSize = -1.0;
}
