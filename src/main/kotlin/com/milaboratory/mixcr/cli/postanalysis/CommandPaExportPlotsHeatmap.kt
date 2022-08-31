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

import com.milaboratory.miplots.color.Palettes;
import com.milaboratory.miplots.color.UniversalPalette;
import picocli.CommandLine.Option;

public abstract class CommandPaExportPlotsHeatmap extends CommandPaExportPlots {
    @Option(description = "Width of horizontal labels. One unit corresponds to the width of one tile.",
            names = {"--h-labels-size"})
    public double hLabelsSize = -1.0;

    @Option(description = "Height of vertical labels. One unit corresponds to the height of one tile.",
            names = {"--v-labels-size"})
    public double vLabelsSize = -1.0;

    @Option(description = "Color palette for heatmap. Available names: diverging, viridis2magma, lime2rose, " +
            "blue2red, teal2red, softSpectral, sequential, viridis, magma, sunset, rainbow, salinity, density. Default is density.",
            names = {"--palette"})
    public String palette = "density";

    UniversalPalette parsePallete(){
        return Palettes.parse(palette);
    }
}
