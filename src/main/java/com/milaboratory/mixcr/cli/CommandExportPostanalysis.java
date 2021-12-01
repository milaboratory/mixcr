package com.milaboratory.mixcr.cli;

import com.milaboratory.mixcr.cli.CommandPostanalysis.PaResult;
import com.milaboratory.mixcr.cli.CommandPostanalysis.PaResultByChain;
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroup;
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroupOutputExtractor;
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroupResult;
import com.milaboratory.mixcr.postanalysis.ui.OutputTable;
import com.milaboratory.util.GlobalObjectMappers;
import io.repseq.core.Chains;
import picocli.CommandLine;

import java.io.File;
import java.nio.file.Paths;
import java.util.Map;

/**
 *
 */
public class CommandExportPostanalysis extends ACommandWithOutputMiXCR {
    @CommandLine.Parameters(index = "0", description = "pa_result.json")
    public String in;
    @CommandLine.Parameters(index = "1", description = "plt_out")
    public String out;

    <K> void writeTables(Chains.NamedChains chain, CharacteristicGroupResult<K> tableResult) {
        for (CharacteristicGroupOutputExtractor<K> view : tableResult.group.views)
            for (OutputTable t : view.getTables(tableResult).values())
                t.writeTSV(Paths.get("").toAbsolutePath(), prefix + chain.name + "_");
    }


//
//    private static String baseName(String fName) {
//        fName = Paths.get(fName).toAbsolutePath().getFileName().toString();
//        int i = fName.lastIndexOf(".");
//        if (i > 0)
//            return fName.substring(0, i);
//        else
//            return fName;
//    }

    @Override
    public void run0() throws Exception {
        PaResult data = GlobalObjectMappers.PRETTY.readValue(new File(in), PaResult.class);

        for (Map.Entry<String, PaResultByChain> e : data.results.entrySet()) {
            Chains chain = Chains.WELL_KNOWN_CHAINS_MAP.get(e.getKey());
            if (chain == null) {
                throw new IllegalArgumentException();
            }

            PaResultByChain result = e.getValue();

            for (CharacteristicGroup<?, ?> table : result.schema.tables)
                writeTables(chain, result.result.getTable(table));
        }
//        GeneUsage.INSTANCE.plotPDF(Path.of("vUsage.pdf"),
//                data.schema, data.result, "vUsage");
//
//        SingleSpectratype.INSTANCE.plotPDF(Path.of(out + "spectra.pdf"),
//                data.schema, data.result, "VSpectratype",
//                SingleSpectratype.SpectratypePlotSettings.Companion.getDefault());
//


    }
}
