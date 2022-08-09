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
package com.milaboratory.mixcr.cli.qc;

import com.milaboratory.miplots.ExportKt;
import com.milaboratory.mixcr.basictypes.IOUtil;
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType;
import com.milaboratory.mixcr.cli.MiXCRCommand;
import com.milaboratory.mixcr.qc.ChainUsage;
import io.repseq.core.Chains;
import io.repseq.core.Chains.NamedChains;
import jetbrains.letsPlot.intern.Plot;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@CommandLine.Command(name = "chainUsage",
        separator = " ",
        description = "Chain usage plot.")
public class CommandExportQcChainUsage extends MiXCRCommand {
    @Parameters(description = "sample1.[vdjca|clnx] ... usage.[pdf|eps|png|jpeg]")
    public List<String> in;
    @Option(
            names = "--absolute-values",
            description = "Plot in absolute values instead of percent"
    )
    public boolean absoluteValues = false;
    @Option(
            names = "--align-chain-usage",
            description = "When specifying .clnx files on input force to plot chain usage for alignments"
    )
    public boolean alignChainUsage = false;

    @Option(
            names = "--chains",
            description = "Specify which chains to export. Possible values: TRAD, TRB, TRG, IGH, IGK, IGL.",
            split = ","
    )
    public List<String> chains;

    @Override
    protected List<String> getInputFiles() {
        return in.subList(0, in.size() - 1);
    }

    @Override
    protected List<String> getOutputFiles() {
        return in.subList(in.size() - 1, in.size());
    }

    @Override
    public void run0() throws Exception {
        MiXCRFileType fileType = IOUtil.extractFileType(Paths.get(in.get(0)));
        List<Path> files = getInputFiles().stream().map(Paths::get)
                .collect(Collectors.toList());
        List<NamedChains> chains = this.chains == null
                ? ChainUsage.getDefaultChainsList()
                : this.chains.stream().map(Chains::getNamedChains).collect(Collectors.toList());

        Plot plt;
        switch (fileType) {
            case CLNA:
            case CLNS:
                if (alignChainUsage)
                    plt = ChainUsage.INSTANCE.chainUsageAlign(
                            files,
                            !absoluteValues,
                            chains
                    );
                else plt = ChainUsage.INSTANCE.chainUsageAssemble(
                        files,
                        !absoluteValues,
                        chains
                );
                break;
            case VDJCA:
                plt = ChainUsage.INSTANCE.chainUsageAlign(
                        files,
                        !absoluteValues,
                        chains
                );
                break;
            default:
                throw new RuntimeException();
        }
        ExportKt.writeFile(Paths.get(getOutputFiles().get(0)), plt);
    }
}
