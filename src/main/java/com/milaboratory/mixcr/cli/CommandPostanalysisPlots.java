package com.milaboratory.mixcr.cli;

import com.milaboratory.mixcr.postanalysis.dataframe.SingleSpectratype;
import com.milaboratory.mixcr.postanalysis.plots.BoxPlots;
import com.milaboratory.mixcr.postanalysis.plots.SpectraPlots;
import com.milaboratory.util.GlobalObjectMappers;
import picocli.CommandLine;

import java.io.File;
import java.nio.file.Path;

/**
 *
 */
public class CommandPostanalysisPlots extends ACommandWithOutputMiXCR {
    @CommandLine.Parameters(index = "0", description = "pa_result.json")
    public String in;
    @CommandLine.Parameters(index = "1", description = "plt_out")
    public String out;

    @Override
    public void run0() throws Exception {
        CommandPostanalysis.PostanalysisData data = GlobalObjectMappers.PRETTY.readValue(new File(in), CommandPostanalysis.PostanalysisData.class);


        SingleSpectratype.INSTANCE.plotPDF(Path.of(out + "spectra.pdf"),
                data.schema, data.result, "VSpectratype",
                SingleSpectratype.SpectratypePlotSettings.Companion.getDefault());

        SpectraPlots.INSTANCE.singleSpectra(Path.of(out + "spectraApp.pdf"),
                data.schema, data.result, "VSpectratype", 20, null, true);

        BoxPlots.INSTANCE.individualBoxPlots(Path.of(out),
                data.schema, data.result, "cdr3Properties", null);

    }
}
