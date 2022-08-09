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

import com.milaboratory.miplots.ExportKt;
import com.milaboratory.miplots.stat.xcontinious.CorrelationMethod;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.cli.CommonDescriptions;
import com.milaboratory.mixcr.cli.MiXCRCommand;
import com.milaboratory.mixcr.postanalysis.Dataset;
import com.milaboratory.mixcr.postanalysis.SetPreprocessor;
import com.milaboratory.mixcr.postanalysis.overlap.OverlapGroup;
import com.milaboratory.mixcr.postanalysis.overlap.OverlapUtil;
import com.milaboratory.mixcr.postanalysis.plots.OverlapScatter;
import com.milaboratory.mixcr.postanalysis.plots.OverlapScatterRow;
import com.milaboratory.mixcr.postanalysis.preproc.ElementPredicate;
import com.milaboratory.mixcr.postanalysis.preproc.OverlapPreprocessorAdapter;
import com.milaboratory.mixcr.postanalysis.ui.DownsamplingParameters;
import com.milaboratory.mixcr.util.OutputPortWithProgress;
import com.milaboratory.util.SmartProgressReporter;
import io.repseq.core.Chains;
import jetbrains.letsPlot.intern.Plot;
import org.jetbrains.kotlinx.dataframe.DataFrame;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static io.repseq.core.Chains.*;

@Command(name = "overlapScatterPlot",
        separator = " ",
        description = "Plot overlap scatter-plot.")
public class CommandOverlapScatter extends MiXCRCommand {
    @Parameters(description = "cloneset_1.{clns|clna}...", index = "0")
    public String in1;
    @Parameters(description = "cloneset_2.{clns|clna}...", index = "1")
    public String in2;
    @Parameters(description = "output.[pdf|eps|png|jpeg]", index = "2")
    public String out;

    @Option(description = "Chains to export",
            names = "--chains")
    public List<String> chains = null;

    @Option(description = CommonDescriptions.ONLY_PRODUCTIVE,
            names = {"--only-productive"})
    public boolean onlyProductive = false;

    @Option(description = CommonDescriptions.DOWNSAMPLING,
            names = {"--downsampling"},
            required = true)
    public String downsampling;

    @Option(description = CommonDescriptions.OVERLAP_CRITERIA,
            names = {"--criteria"})
    public String overlapCriteria = "CDR3|AA|V|J";

    @Option(description = "Correlation method to use. Possible value: pearson, kendal, spearman",
            names = {"--method"})
    public String method = "pearson";

    @Option(description = "Do not apply log10 to clonotype frequences",
            names = {"--no-log"})
    public boolean noLog;

    @Override
    protected List<String> getInputFiles() {
        return Arrays.asList(in1, in2);
    }

    @Override
    protected List<String> getOutputFiles() {
        return Collections.singletonList(out);
    }

    private static String fName(String file) {
        return Paths.get(file).toAbsolutePath().getFileName().toString();
    }

    private Path outputPath(NamedChains chains) {
        if (chains == Chains.ALL_NAMED)
            return Paths.get(out);
        String fName = fName(out);
        return Paths.get(out).toAbsolutePath().getParent().resolve(fName.substring(0, fName.length() - 3) + chains.name + ".pdf");
    }

    @Override
    public void run0() throws Exception {
        DownsamplingParameters preproc = DownsamplingParameters.
                parse(downsampling, CommandPa.extractTagsInfo(getInputFiles()), false, onlyProductive);

        for (NamedChains curChains : this.chains == null
                ? Arrays.asList(TRAD_NAMED, TRB_NAMED, TRG_NAMED, IGH_NAMED, IGK_NAMED, IGL_NAMED)
                : this.chains.stream().map(Chains::getNamedChains).collect(Collectors.toList())) {

            OverlapPreprocessorAdapter.Factory<Clone> downsampling = new OverlapPreprocessorAdapter.Factory<>(preproc.getPreproc(curChains.chains));

            Dataset<OverlapGroup<Clone>> dataset = SetPreprocessor.processDatasets(downsampling.newInstance(),
                    OverlapUtil.overlap(
                            Arrays.asList(in1, in2),
                            new ElementPredicate.IncludeChains(curChains.chains),
                            OverlapUtil.parseCriteria(overlapCriteria).ordering()))[0];

            try (OutputPortWithProgress<OverlapGroup<Clone>> port = dataset.mkElementsPort()) {
                SmartProgressReporter.startProgressReport("Processing " + curChains.name, port);
                DataFrame<OverlapScatterRow> df = OverlapScatter.INSTANCE.dataFrame(port);
                if (df.rowsCount() == 0) {
                    continue;
                }
                Plot plot = OverlapScatter.INSTANCE.plot(df,
                        new OverlapScatter.PlotParameters(
                                fName(in1),
                                fName(in2),
                                CorrelationMethod.Companion.parse(method),
                                !noLog)
                );
                ExportKt.writeFile(outputPath(curChains), plot);
            }
        }
    }
}
