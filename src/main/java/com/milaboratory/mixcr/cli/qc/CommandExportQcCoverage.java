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

import cc.redberry.pipe.CUtils;
import com.milaboratory.miplots.ExportKt;
import com.milaboratory.miplots.ExportType;
import com.milaboratory.mixcr.cli.MiXCRCommand;
import com.milaboratory.mixcr.qc.Coverage;
import jetbrains.letsPlot.intern.Plot;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;


@Command(name = "coverage",
        separator = " ",
        description = "Reads coverage plots.")
public class CommandExportQcCoverage extends MiXCRCommand {
    @Parameters(description = "sample1.vdjca ... coverage.[pdf|eps|png|jpeg]")
    public List<String> in;
    @Option(names = {"--show-boundaries"},
            description = "Show V alignment begin and J alignment end")
    public boolean showAlignmentBoundaries = false;

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
        List<Path> in = getInputFiles().stream()
                .map(Paths::get)
                .collect(Collectors.toList());

        List<Plot> plts = Collections.synchronizedList(new ArrayList<>());
        CUtils.processAllInParallel(
                CUtils.asOutputPort(in),
                input -> plts.addAll(Coverage.INSTANCE.coveragePlot(input, showAlignmentBoundaries)),
                Runtime.getRuntime().availableProcessors()
        );
        Path out = Paths.get(getOutputFiles().get(0));
        ExportType exportType = ExportType.determine(out);
        if (exportType == ExportType.PDF)
            ExportKt.writePDFFigure(out, plts);
        else {
            int i = 1;
            for (Plot plt : plts) {
                String outStr = getOutputFiles().get(0);
                int l = outStr.lastIndexOf(".");
                String suff;
                if (i < 3)
                    suff = "R" + i;
                else
                    suff = "Overlap";
                outStr = outStr.substring(0, l) + "_" + suff + outStr.substring(l);
                ExportKt.writeFile(Paths.get(outStr), plt);
                ++i;
            }
        }
    }
}
