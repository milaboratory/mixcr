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

import cc.redberry.pipe.CUtils;
import com.milaboratory.mixcr.basictypes.ClnsWriter;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.cli.CommonDescriptions;
import com.milaboratory.mixcr.cli.MiXCRCommand;
import com.milaboratory.mixcr.postanalysis.Dataset;
import com.milaboratory.mixcr.postanalysis.SetPreprocessor;
import com.milaboratory.mixcr.postanalysis.SetPreprocessorStat;
import com.milaboratory.mixcr.postanalysis.SetPreprocessorSummary;
import com.milaboratory.mixcr.postanalysis.ui.ClonotypeDataset;
import com.milaboratory.mixcr.postanalysis.ui.DownsamplingParameters;
import gnu.trove.map.hash.TIntObjectHashMap;
import io.repseq.core.Chains;
import io.repseq.core.VDJCLibraryRegistry;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Command(name = "downsample",
        separator = " ",
        description = "Downsample clonesets.")
public class CommandDownsample extends MiXCRCommand {
    @Parameters(description = "cloneset.{clns|clna}...")
    public List<String> in;

    @Option(description = "Filter specific chains",
            names = {"-c", "--chains"},
            required = true)
    public String chains = "ALL";

    @Option(description = CommonDescriptions.ONLY_PRODUCTIVE,
            names = {"--only-productive"})
    public boolean onlyProductive = false;

    @Option(description = CommonDescriptions.DOWNSAMPLING,
            names = {"--downsampling"},
            required = true)
    public String downsampling;

    @Option(description = "Write downsampling summary tsv/csv table.",
            names = {"--summary"},
            required = false)
    public String summary;
    @Option(description = "Suffix to add to output clns file.",
            names = {"--suffix"})
    public String suffix = "downsampled";

    @Option(description = "Output path prefix.",
            names = {"--out"})
    public String out;

    @Override
    protected List<String> getInputFiles() {
        return new ArrayList<>(in);
    }

    @Override
    protected List<String> getOutputFiles() {
        return getInputFiles().stream().map(this::output).map(Path::toString).collect(Collectors.toList());
    }

    @Override
    public void validate() {
        super.validate();
        if (summary != null && (!summary.endsWith(".tsv") && !summary.endsWith(".csv")))
            throwValidationException("summary table should ends with .csv/.tsv");
    }

    private Path output(String input) {
        String outName = Paths.get(input).getFileName().toString()
                .replace(".clna", "")
                .replace(".clns", "")
                + "." + chains + "." + suffix + ".clns";
        return out == null
                ? Paths.get(outName).toAbsolutePath()
                : Paths.get(out).resolve(outName).toAbsolutePath();
    }

    private void ensureOutputPathExists() {
        if (out != null) {
            try {
                Files.createDirectories(Paths.get(out).toAbsolutePath());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        if (summary != null) {
            try {
                Files.createDirectories(Paths.get(suffix).toAbsolutePath().getParent());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void run0() throws Exception {
        List<ClonotypeDataset> datasets = in.stream()
                .map(file ->
                        new ClonotypeDataset(file, file, VDJCLibraryRegistry.getDefault())
                ).collect(Collectors.toList());

        SetPreprocessor<Clone> preproc = DownsamplingParameters
                .parse(this.downsampling, CommandPa.extractTagsInfo(getInputFiles()), false, onlyProductive)
                .getPreproc(Chains.getByName(chains))
                .newInstance();

        Dataset<Clone>[] result = SetPreprocessor.processDatasets(preproc, datasets);
        ensureOutputPathExists();

        for (int i = 0; i < result.length; i++) {
            String input = in.get(i);
            try (ClnsWriter clnsWriter = new ClnsWriter(output(input).toFile())) {
                List<Clone> downsampled = new ArrayList<>();
                for (Clone c : CUtils.it(result[i].mkElementsPort()))
                    downsampled.add(c);

                ClonotypeDataset r = datasets.get(i);
                clnsWriter.writeHeader(
                        r.getAlignerParameters(), r.getAssemblerParameters(),
                        r.getTagsInfo(), r.ordering(), r.getUsedGenes(), r.getAlignerParameters(),
                        Collections.emptyList(), downsampled.size()
                );

                CUtils.drain(CUtils.asOutputPort(downsampled), clnsWriter.cloneWriter());
                clnsWriter.writeFooter(Collections.emptyList(), null);
            }
        }

        TIntObjectHashMap<List<SetPreprocessorStat>> summaryStat = preproc.getStat();
        for (int i = 0; i < result.length; i++) {
            String input = in.get(i);
            SetPreprocessorStat stat = SetPreprocessorStat.cumulative(summaryStat.get(i));
            System.out.println(input + ":" +
                    " isDropped=" + stat.dropped +
                    " nClonesBefore=" + stat.nElementsBefore +
                    " nClonesAfter=" + stat.nElementsAfter +
                    " sumWeightBefore=" + stat.sumWeightBefore +
                    " sumWeightAfter=" + stat.sumWeightAfter
            );
        }

        if (summary != null) {
            Map<String, List<SetPreprocessorStat>> summaryTable = new HashMap<>();
            for (int i = 0; i < in.size(); i++)
                summaryTable.put(in.get(i), summaryStat.get(i));

            SetPreprocessorSummary.toCSV(Paths.get(summary).toAbsolutePath(),
                    new SetPreprocessorSummary(summaryTable),
                    summary.endsWith("csv") ? "," : "\t");
        }
    }
}
