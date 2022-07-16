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
package com.milaboratory.mixcr.cli;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import com.milaboratory.mixcr.assembler.CloneAssemblerParameters;
import com.milaboratory.mixcr.assembler.CloneFactory;
import com.milaboratory.mixcr.assembler.fullseq.*;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.mixcr.basictypes.tag.TagCount;
import com.milaboratory.mixcr.basictypes.tag.TagTuple;
import com.milaboratory.mixcr.basictypes.tag.TagsInfo;
import com.milaboratory.mixcr.util.Concurrency;
import com.milaboratory.primitivio.PipeDataInputReader;
import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.PrimitivO;
import com.milaboratory.util.JsonOverrider;
import com.milaboratory.util.ReportUtil;
import com.milaboratory.util.SmartProgressReporter;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGene;
import io.repseq.core.VDJCGeneId;
import io.repseq.core.VDJCLibraryRegistry;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import static com.milaboratory.mixcr.cli.CommandAssembleContigs.ASSEMBLE_CONTIGS_COMMAND_NAME;
import static com.milaboratory.util.StreamUtil.noMerge;

@Command(name = ASSEMBLE_CONTIGS_COMMAND_NAME,
        sortOptions = true,
        separator = " ",
        description = "Assemble full sequences.")
public class CommandAssembleContigs extends MiXCRCommand {
    public static final String ASSEMBLE_CONTIGS_COMMAND_NAME = "assembleContigs";

    public int threads = Runtime.getRuntime().availableProcessors();

    @Parameters(description = "clones.clna", index = "0")
    public String in;

    @Parameters(description = "clones.clns", index = "1")
    public String out;

    @Option(description = "Processing threads",
            names = {"-t", "--threads"})
    public void setThreads(int threads) {
        if (threads <= 0)
            throwValidationException("-t / --threads must be positive");
        this.threads = threads;
    }

    @Option(names = "-O", description = "Overrides default parameter values.")
    public Map<String, String> overrides = new HashMap<>();

    @Option(description = CommonDescriptions.REPORT,
            names = {"-r", "--report"})
    public String reportFile;

    @Option(description = "Ignore tags",
            names = {"--ignore-tags"})
    public boolean ignoreTags;

    @Option(description = "Report file.",
            names = {"--debug-report"}, hidden = true)
    public String debugReportFile;

    @Option(description = CommonDescriptions.JSON_REPORT,
            names = {"-j", "--json-report"})
    public String jsonReport = null;

    @Override
    protected List<String> getInputFiles() {
        return Collections.singletonList(in);
    }

    @Override
    protected List<String> getOutputFiles() {
        return Collections.singletonList(out);
    }

    public FullSeqAssemblerParameters getFullSeqAssemblerParameters() {
        FullSeqAssemblerParameters p = FullSeqAssemblerParameters.getByName("default");
        if (!overrides.isEmpty()) {
            // Perform parameters overriding
            p = JsonOverrider.override(p, FullSeqAssemblerParameters.class, overrides);
            if (p == null)
                throwValidationException("failed to override some parameter: " + overrides);
        }
        return p;
    }

    final FullSeqAssemblerReportBuilder reportBuilder = new FullSeqAssemblerReportBuilder();

    @Override
    public void run0() throws Exception {
        long beginTimestamp = System.currentTimeMillis();

        FullSeqAssemblerParameters assemblerParameters = getFullSeqAssemblerParameters();
        int totalClonesCount = 0;
        List<VDJCGene> genes;
        MiXCRMetaInfo info;
        CloneAssemblerParameters cloneAssemblerParameters;
        TagsInfo tagsInfo;
        VDJCSProperties.CloneOrdering ordering;
        List<MiXCRCommandReport> reports;
        try (ClnAReader reader = new ClnAReader(in, VDJCLibraryRegistry.getDefault(), Concurrency.noMoreThan(4));
             PrimitivO tmpOut = new PrimitivO(new BufferedOutputStream(new FileOutputStream(out))); // TODO ????
             BufferedWriter debugReport = debugReportFile == null ? null : new BufferedWriter(new OutputStreamWriter(new FileOutputStream(debugReportFile)))) {

            reports = reader.reports();
            ordering = reader.ordering();

            final CloneFactory cloneFactory = new CloneFactory(reader.getAssemblerParameters().getCloneFactoryParameters(),
                    reader.getAssemblingFeatures(), reader.getUsedGenes(), reader.getAlignerParameters().getFeaturesToAlignMap());

            info = reader.getInfo();
            cloneAssemblerParameters = reader.getAssemblerParameters();
            tagsInfo = reader.getTagsInfo();
            genes = reader.getUsedGenes();
            IOUtil.registerGeneReferences(tmpOut, genes, info.getAlignerParameters());

            ClnAReader.CloneAlignmentsPort cloneAlignmentsPort = reader.clonesAndAlignments();
            SmartProgressReporter.startProgressReport("Assembling contigs", cloneAlignmentsPort);

            OutputPort<Clone[]> parallelProcessor = CUtils.orderedParallelProcessor(cloneAlignmentsPort, cloneAlignments -> {
                Clone clone = cloneAlignments.clone;

                if (ignoreTags)
                    clone = clone.setTagCount(new TagCount(TagTuple.NO_TAGS, clone.getTagCount().sum()));

                try {
                    // Collecting statistics

                    EnumMap<GeneType, Map<VDJCGeneId, CoverageAccumulator>> coverages = clone.getHitsMap()
                            .entrySet().stream()
                            .filter(e -> e.getValue() != null && e.getValue().length > 0)
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    e ->
                                            Arrays.stream(e.getValue())
                                                    .filter(h ->
                                                            (h.getGeneType() != GeneType.Variable && h.getGeneType() != GeneType.Joining) ||
                                                                    FullSeqAssembler.checkGeneCompatibility(h, cloneAssemblerParameters.getAssemblingFeatures()[0]))
                                                    .collect(
                                                            Collectors.toMap(
                                                                    h -> h.getGene().getId(),
                                                                    CoverageAccumulator::new
                                                            )
                                                    ),
                                    noMerge(),
                                    () -> new EnumMap<>(GeneType.class)));

                    // Filtering empty maps
                    coverages = coverages.entrySet().stream()
                            .filter(e -> !e.getValue().isEmpty())
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    Map.Entry::getValue,
                                    noMerge(),
                                    () -> new EnumMap<>(GeneType.class)));

                    if (!coverages.containsKey(GeneType.Variable) || !coverages.containsKey(GeneType.Joining)) {
                        // Something went really wrong
                        reportBuilder.onAssemblyCanceled(clone);
                        return new Clone[]{clone};
                    }

                    for (VDJCAlignments alignments : CUtils.it(cloneAlignments.alignments()))
                        for (Map.Entry<GeneType, VDJCHit[]> e : alignments.getHitsMap().entrySet())
                            for (VDJCHit hit : e.getValue())
                                Optional.ofNullable(coverages.get(e.getKey()))
                                        .flatMap(m -> Optional.ofNullable(m.get(hit.getGene().getId())))
                                        .ifPresent(acc -> acc.accumulate(hit));

                    // Selecting best hits for clonal sequence assembly based in the coverage information
                    final EnumMap<GeneType, VDJCHit> bestGenes = coverages.entrySet().stream()
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    accs -> accs.getValue().entrySet().stream()
                                            .max(Comparator.comparing(e -> e.getValue().getNumberOfCoveredPoints(1)))
                                            .map(e -> e.getValue().hit).get(),
                                    noMerge(),
                                    () -> new EnumMap<>(GeneType.class)));

                    // Performing contig assembly

                    FullSeqAssembler fullSeqAssembler = new FullSeqAssembler(
                            cloneFactory, assemblerParameters,
                            clone, info.getAlignerParameters(),
                            bestGenes.get(GeneType.Variable), bestGenes.get(GeneType.Joining)
                    );

                    fullSeqAssembler.setReport(reportBuilder);

                    FullSeqAssembler.RawVariantsData rawVariantsData = fullSeqAssembler.calculateRawData(cloneAlignments::alignments);

                    if (debugReport != null) {
                        synchronized (debugReport) {
                            try (FileOutputStream fos = new FileOutputStream(debugReportFile + "." + clone.getId())) {
                                final String content = rawVariantsData.toCsv((byte) 10);
                                fos.write(content.getBytes());
                            }

                            try {
                                debugReport.write("Clone: " + clone.getId());
                                debugReport.newLine();
                                debugReport.write(rawVariantsData.toString());
                                debugReport.newLine();
                                debugReport.newLine();
                                debugReport.write("==========================================");
                                debugReport.newLine();
                                debugReport.newLine();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }

                    return fullSeqAssembler.callVariants(rawVariantsData);
                } catch (Throwable re) {
                    throw new RuntimeException("While processing clone #" + clone.getId(), re);
                }
            }, 1024, threads);

            for (Clone[] clones : CUtils.it(parallelProcessor)) {
                totalClonesCount += clones.length;
                for (Clone cl : clones)
                    tmpOut.writeObject(cl);
            }

            assert reportBuilder.getInitialCloneCount() == reader.numberOfClones();
        }

        assert reportBuilder.getFinalCloneCount() == totalClonesCount;
        assert reportBuilder.getFinalCloneCount() >= reportBuilder.getInitialCloneCount();

        int cloneId = 0;
        Clone[] clones = new Clone[totalClonesCount];
        try (PrimitivI tmpIn = new PrimitivI(new BufferedInputStream(new FileInputStream(out)))) {
            IOUtil.registerGeneReferences(tmpIn, genes, info.getAlignerParameters());
            int i = 0;
            for (Clone clone : CUtils.it(new PipeDataInputReader<>(Clone.class, tmpIn, totalClonesCount)))
                clones[i++] = clone.setId(cloneId++);
        }

        CloneSet cloneSet = new CloneSet(Arrays.asList(clones), genes, info, ordering);

        try (ClnsWriter writer = new ClnsWriter(out)) {
            writer.writeCloneSet(cloneSet);


            reportBuilder.setStartMillis(beginTimestamp);
            reportBuilder.setInputFiles(in);
            reportBuilder.setOutputFiles(out);
            reportBuilder.setCommandLine(getCommandLineArguments());
            reportBuilder.setFinishMillis(System.currentTimeMillis());

            FullSeqAssemblerReport report = reportBuilder.buildReport();
            // Writing report to stout
            ReportUtil.writeReportToStdout(report);

            if (reportFile != null)
                ReportUtil.appendReport(reportFile, report);

            if (jsonReport != null)
                ReportUtil.appendJsonReport(jsonReport, report);

            writer.writeFooter(reports, report);
        }
    }
}
