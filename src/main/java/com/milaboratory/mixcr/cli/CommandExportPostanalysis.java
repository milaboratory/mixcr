package com.milaboratory.mixcr.cli;

import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.cli.CommandPostanalysis.PaResult;
import com.milaboratory.mixcr.cli.CommandPostanalysis.PaResultByChain;
import com.milaboratory.mixcr.postanalysis.dataframe.SimpleStatistics;
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroup;
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroupOutputExtractor;
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroupResult;
import com.milaboratory.mixcr.postanalysis.ui.OutputTable;
import com.milaboratory.util.GlobalObjectMappers;
import io.repseq.core.Chains;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 *
 */
public abstract class CommandExportPostanalysis extends ACommandWithOutputMiXCR {
    @Option(names = {"-m", "--metadata"}, description = "Metadata file")
    public String metadata;
    @Parameters(index = "0", description = "pa_result.json")
    public String in;
    @Parameters(index = "1", description = "output")
    public String out;

    /** Check that directory */
    private void ensureChainsDirExists(Chains.NamedChains chains) {
        try {
            Files.createDirectory(Paths.get(chains.name));
        } catch (IOException e) {
            throwExecutionException(e.getMessage());
        }
    }


    Path outPath(Chains.NamedChains chains, String suffix) {
        return Paths.get(chains.name).resolve(suffix);
    }

    /** Cached PA result */
    private PaResult paResult = null;

    /** Get full PA result */
    PaResult getPaResult() {
        try {
            if (paResult != null)
                return paResult;
            return paResult = GlobalObjectMappers.PRETTY.readValue(new File(in), PaResult.class);
        } catch (IOException e) {
            throwValidationException("Broken input file: " + in);
            return null;
        }
    }

    @Override
    public void run0() throws Exception {
        for (Map.Entry<Chains.NamedChains, PaResultByChain> r : getPaResult().results.entrySet()) {
            ensureChainsDirExists(r.getKey());
            run(r.getKey(), r.getValue());
        }
    }

    abstract void run(Chains.NamedChains chains, PaResultByChain result);

    static final class ExportTables extends CommandExportPostanalysis {
        @Override
        void run(Chains.NamedChains chains, PaResultByChain result) {
            for (CharacteristicGroup<?, ?> table : result.schema.tables) {
                writeTables(chains, result.result.getTable(table));
            }
        }

        <K> void writeTables(Chains.NamedChains chain, CharacteristicGroupResult<K> tableResult) {
            for (CharacteristicGroupOutputExtractor<K> view : tableResult.group.views)
                for (OutputTable t : view.getTables(tableResult).values())
                    t.writeTSV(Paths.get(chain.name).toAbsolutePath(), "");
        }
    }

    static final class ExportBoxPlots extends CommandExportPostanalysis {
        @Option(names = {"-p", "--primary-group"}, description = "Primary group")
        public String primaryGroup;
        @Option(names = {"-s", "--secondary-group"}, description = "Secondary group")
        public String secondaryGroup;

        @Override
        void run(Chains.NamedChains chains, PaResultByChain result) {
            CharacteristicGroup<Clone, ?> biophysics = result.schema.getGroup(CommandPostanalysis.Biophysics);

            SimpleStatistics.INSTANCE.plotPDF(outPath(chains, "biophysics.pdf"),
                    result.result.forGroup(biophysics),
                    null,
                    SimpleStatistics.BoxPlotSettings.Companion.getDefault()
            );

            CharacteristicGroup<Clone, ?> diversity = result.schema.getGroup(CommandPostanalysis.Diversity);
            SimpleStatistics.INSTANCE.plotPDF(outPath(chains, "diversity.pdf"),
                    result.result.forGroup(diversity),
                    null,
                    SimpleStatistics.BoxPlotSettings.Companion.getDefault()
            );
        }
    }


    @CommandLine.Command(name = "exportPostanalysis",
            separator = " ",
            description = "Export postanalysis results.",
            subcommands = {
                    CommandLine.HelpCommand.class
            })
    public static class CommandExportPostanalysisMain {
    }
}
