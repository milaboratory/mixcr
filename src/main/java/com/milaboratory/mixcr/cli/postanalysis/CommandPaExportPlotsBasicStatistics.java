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

import com.milaboratory.miplots.StandardPlots.PlotType;
import com.milaboratory.miplots.stat.util.PValueCorrection;
import com.milaboratory.miplots.stat.util.RefGroup;
import com.milaboratory.miplots.stat.util.TestMethod;
import com.milaboratory.miplots.stat.xcontinious.CorrelationMethod;
import com.milaboratory.miplots.stat.xdiscrete.LabelFormat;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.postanalysis.PostanalysisResult;
import com.milaboratory.mixcr.postanalysis.diversity.DiversityMeasure;
import com.milaboratory.mixcr.postanalysis.plots.BasicStatRow;
import com.milaboratory.mixcr.postanalysis.plots.BasicStatistics;
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroup;
import com.milaboratory.mixcr.postanalysis.ui.PostanalysisParametersIndividual;
import jetbrains.letsPlot.intern.Plot;
import org.jetbrains.kotlinx.dataframe.DataFrame;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public abstract class CommandPaExportPlotsBasicStatistics extends CommandPaExportPlots {
    @Option(description = "Plot type. Possible values: boxplot, boxplot-bindot, boxplot-jitter, " +
            "lineplot, lineplot-bindot, lineplot-jitter, " +
            "violin, violin-bindot, barplot, barplot-stacked, " +
            "scatter",
            names = {"--plot-type"})
    public String plotType;

    @Option(description = "Primary group",
            names = {"-p", "--primary-group"})
    public String primaryGroup;

    @Option(description = "List of comma separated primary group values",
            names = {"-pv", "--primary-group-values"},
            split = ",")
    public List<String> primaryGroupValues;

    @Option(description = "Secondary group",
            names = {"-s", "--secondary-group"})
    public String secondaryGroup;

    @Option(description = "List of comma separated secondary group values",
            names = {"-sv", "--secondary-group-values"},
            split = ",")
    public List<String> secondaryGroupValues;

    @Option(description = "Facet by",
            names = {"--facet-by"})
    public String facetBy;

    @Option(description = "Select specific metrics to export.",
            names = {"--metric"})
    public List<String> metrics;

    @Option(description = "Hide overall p-value",
            names = {"--hide-overall-p-value"})
    public boolean hideOverallPValue;

    @Option(description = "Show pairwise p-value comparisons",
            names = {"--pairwise-comparisons"})
    public boolean pairwiseComparisons;

    @Option(description = "Reference group. Can be \"all\" or some specific value.",
            names = {"--ref-group"})
    public String refGroup;

    @Option(description = "Hide non-significant observations",
            names = {"--hide-ns"})
    public boolean hideNS;

    @Option(description = "Do paired analysis",
            names = {"--paired"})
    public boolean paired;

    @Option(description = "Test method. Default is Wilcoxon. Available methods: Wilcoxon, ANOVA, TTest, KruskalWallis, KolmogorovSmirnov",
            names = {"--method"})
    public String method = "Wilcoxon";

    @Option(description = "Test method for multiple groups comparison. Default is KruskalWallis. Available methods: ANOVA, KruskalWallis, KolmogorovSmirnov",
            names = {"--method-multiple-groups"})
    public String methodForMultipleGroups = "KruskalWallis";

    @Option(description = "Method used to adjust p-values. Default is Holm. Available methods: none, BenjaminiHochberg, BenjaminiYekutieli, Bonferroni, Hochberg, Holm, Hommel",
            names = {"--p-adjust-method"})
    public String pAdjustMethod = "Holm";

    @Option(description = "Show significance level instead of p-values",
            names = {"--show-significance"})
    public boolean showSignificance;

    abstract String group();

    abstract Predicate<String> metricsFilter();

    @Override
    void run(PaResultByGroup result) {
        CharacteristicGroup<Clone, ?> ch = result.schema.getGroup(group());
        PostanalysisResult paResult = result.result.forGroup(ch);
        DataFrame<?> metadata = metadata();
        Predicate<String> mf = metricsFilter();
        DataFrame<BasicStatRow> df = BasicStatistics.INSTANCE.dataFrame(
                paResult,
                mf::test,
                metadata
        );

        df = filter(df);
        if (df.rowsCount() == 0)
            return;

        RefGroup rg = null;
        if (Objects.equals(refGroup, "all"))
            rg = RefGroup.Companion.getAll();
        else if (refGroup != null)
            rg = RefGroup.Companion.of(refGroup);

        PlotType plotType = BasicStatistics.INSTANCE.parsePlotType(this.plotType);

        LabelFormat labelFormat = showSignificance
                ? LabelFormat.Companion.getSignificance()
                : new LabelFormat.Companion.Formatted();

        BasicStatistics.PlotParameters par = new BasicStatistics.PlotParameters(
                plotType,
                primaryGroup,
                secondaryGroup,
                primaryGroupValues,
                secondaryGroupValues,
                facetBy,
                !hideOverallPValue,
                pairwiseComparisons,
                rg,
                hideNS,
                null,
                labelFormat,
                labelFormat,
                paired,
                TestMethod.valueOf(method),
                TestMethod.valueOf(methodForMultipleGroups),
                pAdjustMethod.equals("none") ? null : PValueCorrection.Method.valueOf(pAdjustMethod),
                CorrelationMethod.Pearson
        );

        List<Plot> plots = BasicStatistics.INSTANCE.plots(df, par);
        writePlots(result.group, plots);
    }

    @Command(name = "cdr3metrics",
            sortOptions = false,
            separator = " ",
            description = "Export CDR3 metrics")
    public static class ExportCDR3Metrics extends CommandPaExportPlotsBasicStatistics {
        @Override
        String group() {
            return PostanalysisParametersIndividual.CDR3Metrics;
        }

        @Override
        Predicate<String> metricsFilter() {
            if (metrics == null || metrics.isEmpty())
                return t -> true;
            HashSet<String> set = new HashSet<>(Arrays.asList(PostanalysisParametersIndividual.SUPPORTED_CDR3_METRICS));
            for (String m : metrics) {
                if (!set.contains(m.toLowerCase()))
                    throw new IllegalArgumentException("Unknown metric: " + m);
            }
            return new HashSet<>(metrics)::contains;
        }
    }

    @Command(name = "diversity",
            sortOptions = false,
            separator = " ",
            description = "Export diversity metrics")
    public static class ExportDiversity extends CommandPaExportPlotsBasicStatistics {
        @Override
        String group() {
            return PostanalysisParametersIndividual.Diversity;
        }

        @Override
        Predicate<String> metricsFilter() {
            if (metrics == null || metrics.isEmpty())
                return t -> true;
            HashMap<String, String> map = new HashMap<String, String>() {{
                put("chao1".toLowerCase(), DiversityMeasure.Chao1.name);
                put("efronThisted".toLowerCase(), DiversityMeasure.EfronThisted.name);
                put("inverseSimpsonIndex".toLowerCase(), DiversityMeasure.InverseSimpsonIndex.name);
                put("giniIndex".toLowerCase(), DiversityMeasure.GiniIndex.name);
                put("observed".toLowerCase(), DiversityMeasure.Observed.name);
                put("shannonWiener".toLowerCase(), DiversityMeasure.ShannonWiener.name);
                put("normalizedShannonWienerIndex".toLowerCase(), DiversityMeasure.NormalizedShannonWienerIndex.name);
                put("d50", "d50");
            }};
            for (String m : metrics) {
                if (!map.containsKey(m.toLowerCase()))
                    throw new IllegalArgumentException("Unknown metric: " + m);
            }
            return metrics.stream().map(String::toLowerCase).map(map::get).collect(Collectors.toSet())::contains;
        }
    }
}
