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

import com.milaboratory.mixcr.cli.MiXCRCommand;
import io.repseq.core.Chains;
import io.repseq.core.Chains.NamedChains;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *
 */
public abstract class CommandPaExport extends MiXCRCommand {
    @Parameters(description = "Input file with postanalysis results.", index = "0", defaultValue = "pa.json.gz")
    public String in;
    @Option(description = "Export for specific chains only",
            names = {"--chains"})
    public List<String> chains;
    /** Cached PA result */
    private PaResult paResult = null;

    public CommandPaExport() {}

    /** Constructor used to export tables from code */
    CommandPaExport(PaResult paResult) {
        this.paResult = paResult;
    }

    @Override
    protected List<String> getInputFiles() {
        return Collections.singletonList(in);
    }

    /**
     * Get full PA result
     */
    protected PaResult getPaResult() {
        if (paResult != null)
            return paResult;
        return paResult = PaResult.readJson(Paths.get(in).toAbsolutePath());
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
}
