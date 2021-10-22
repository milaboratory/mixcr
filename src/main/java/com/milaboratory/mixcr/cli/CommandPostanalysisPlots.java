package com.milaboratory.mixcr.cli;

import com.milaboratory.mixcr.postanalysis.dataframe.GeneUsage;
import com.milaboratory.mixcr.postanalysis.dataframe.SingleSpectratype;
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

        GeneUsage.INSTANCE.plotPDF(Path.of("vUsage.pdf"),
                data.schema, data.result, "vUsage");

        SingleSpectratype.INSTANCE.plotPDF(Path.of(out + "spectra.pdf"),
                data.schema, data.result, "VSpectratype",
                SingleSpectratype.SpectratypePlotSettings.Companion.getDefault());




    }
}
