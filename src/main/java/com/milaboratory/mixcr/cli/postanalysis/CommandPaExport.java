package com.milaboratory.mixcr.cli.postanalysis;

import com.milaboratory.mixcr.cli.ACommandWithOutputMiXCR;
import io.repseq.core.Chains;
import io.repseq.core.Chains.NamedChains;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *
 */
public abstract class CommandPaExport extends ACommandWithOutputMiXCR {
    @Parameters(description = "Input file with PA results", index = "0", defaultValue = "pa.json.gz")
    public String in;
    @Option(description = "Metadata file (csv/tsv).",
            names = {"-m", "--meta", "--metadata"})
    public String metadata;
    @Option(description = "Export for specific chains only",
            names = {"--chains"})
    public List<String> chains;

    @Override
    protected List<String> getInputFiles() {
        return Collections.singletonList(in);
    }

    @Override
    public void validateInfo(String inputFile) {
        super.validateInfo(inputFile);
        if (metadata != null && !metadata.endsWith(".csv") && !metadata.endsWith(".tsv"))
            throwValidationException("Metadata should be .csv or .tsv");
    }

    /**
     * Cached PA result
     */
    private PaResult paResult = null;

    /**
     * Get full PA result
     */
    protected PaResult getPaResult() {
        if (paResult != null)
            return paResult;
        return paResult = PaResult.readJson(Path.of(in).toAbsolutePath());
    }

    @Override
    public void run0() throws Exception {
        Set<NamedChains> set = chains == null
                ? null
                : chains.stream().map(Chains::getNamedChains).collect(Collectors.toSet());

        for (PaResultByGroup r : getPaResult().results) {
            if (set == null || set.stream().anyMatch(c -> c.chains.intersects(r.group.chains.chains)))
                run(r);
        }
    }

    abstract void run(PaResultByGroup result);

    @CommandLine.Command(name = "exportPa",
            separator = " ",
            description = "Export postanalysis results.",
            subcommands = {
                    CommandLine.HelpCommand.class
            })
    public static class CommandExportPaMain {}
}
