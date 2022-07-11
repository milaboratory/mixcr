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

import com.milaboratory.mixcr.cli.CommonDescriptions;
import com.milaboratory.mixcr.cli.MiXCRCommand;
import com.milaboratory.mixcr.postanalysis.plots.MetadataKt;
import com.milaboratory.util.StringUtil;
import io.repseq.core.Chains;
import io.repseq.core.Chains.NamedChains;
import org.jetbrains.kotlinx.dataframe.DataFrame;
import org.jetbrains.kotlinx.dataframe.api.ToDataFrameKt;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

/**
 *
 */
public abstract class CommandPaExport extends MiXCRCommand {
    @Parameters(description = "Input file with postanalysis results.", index = "0", defaultValue = "pa.json.gz")
    public String in;
    @Option(description = CommonDescriptions.METADATA,
            names = {"--metadata"})
    public String metadata;
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
