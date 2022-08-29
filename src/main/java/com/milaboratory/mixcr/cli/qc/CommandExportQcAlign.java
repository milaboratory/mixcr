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
import com.milaboratory.mixcr.cli.MiXCRCommand;
import com.milaboratory.mixcr.qc.AlignmentQC;
import com.milaboratory.mixcr.qc.SizeParameters;
import jetbrains.letsPlot.intern.Plot;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;


@Command(name = "align",
        separator = " ",
        description = "QC plot for alignments.")
public class CommandExportQcAlign extends CommandExportQc {
    @Parameters(description = "sample1.vdjca ... align.[pdf|eps|png|jpeg]")
    public List<String> in;
    @Option(
            names = "--absolute-values",
            description = "Plot in absolute values instead of percent"
    )
    public boolean absoluteValues = false;

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
        Plot plt = AlignmentQC.INSTANCE.alignQc(
                getInputFiles().stream().map(Paths::get)
                        .collect(Collectors.toList()),
                !absoluteValues,
                getSizeParameters()
        );
        ExportKt.writeFile(Paths.get(getOutputFiles().get(0)), plt);
    }
}
