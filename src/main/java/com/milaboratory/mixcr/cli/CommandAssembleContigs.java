/*
 * Copyright (c) 2014-2019, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
 * (here and after addressed as Inventors)
 * All Rights Reserved
 *
 * Permission to use, copy, modify and distribute any part of this program for
 * educational, research and non-profit purposes, by non-profit institutions
 * only, without fee, and without a written agreement is hereby granted,
 * provided that the above copyright notice, this paragraph and the following
 * three paragraphs appear in all copies.
 *
 * Those desiring to incorporate this work into commercial products or use for
 * commercial purposes should contact MiLaboratory LLC, which owns exclusive
 * rights for distribution of this program for commercial purposes, using the
 * following email address: licensing@milaboratory.com.
 *
 * IN NO EVENT SHALL THE INVENTORS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
 * SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
 * ARISING OUT OF THE USE OF THIS SOFTWARE, EVEN IF THE INVENTORS HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * THE SOFTWARE PROVIDED HEREIN IS ON AN "AS IS" BASIS, AND THE INVENTORS HAS
 * NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR
 * MODIFICATIONS. THE INVENTORS MAKES NO REPRESENTATIONS AND EXTENDS NO
 * WARRANTIES OF ANY KIND, EITHER IMPLIED OR EXPRESS, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY OR FITNESS FOR A
 * PARTICULAR PURPOSE, OR THAT THE USE OF THE SOFTWARE WILL NOT INFRINGE ANY
 * PATENT, TRADEMARK OR OTHER RIGHTS.
 */
package com.milaboratory.mixcr.cli;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.blocks.ParallelProcessor;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.milaboratory.cli.ActionConfiguration;
import com.milaboratory.mixcr.assembler.CloneAssemblerParameters;
import com.milaboratory.mixcr.assembler.CloneFactory;
import com.milaboratory.mixcr.assembler.fullseq.CoverageAccumulator;
import com.milaboratory.mixcr.assembler.fullseq.FullSeqAssembler;
import com.milaboratory.mixcr.assembler.fullseq.FullSeqAssemblerParameters;
import com.milaboratory.mixcr.assembler.fullseq.FullSeqAssemblerReport;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.primitivio.PipeDataInputReader;
import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.PrimitivO;
import com.milaboratory.util.SmartProgressReporter;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGene;
import io.repseq.core.VDJCGeneId;
import io.repseq.core.VDJCLibraryRegistry;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import static com.milaboratory.mixcr.cli.CommandAssembleContigs.ASSEMBLE_CONTIGS_COMMAND_NAME;
import static com.milaboratory.util.StreamUtil.noMerge;

@Command(name = ASSEMBLE_CONTIGS_COMMAND_NAME,
        sortOptions = true,
        separator = " ",
        description = "Assemble full sequences.")
public class CommandAssembleContigs extends ACommandWithSmartOverwriteWithSingleInputMiXCR {
    static final String ASSEMBLE_CONTIGS_COMMAND_NAME = "assembleContigs";

    public int threads = Runtime.getRuntime().availableProcessors();

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

    @Option(description = "Report file.",
            names = {"--debug-report"}, hidden = true)
    public String debugReportFile;

    @Option(description = CommonDescriptions.JSON_REPORT,
            names = {"-j", "--json-report"})
    public String jsonReport = null;

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

    @Override
    public ActionConfiguration getConfiguration() {
        return new AssembleContigsConfiguration(getFullSeqAssemblerParameters());
    }

    @Override
    public void run1() throws Exception {
        long beginTimestamp = System.currentTimeMillis();

        final FullSeqAssemblerReport report = new FullSeqAssemblerReport();
        FullSeqAssemblerParameters assemblerParameters = getFullSeqAssemblerParameters();
        int totalClonesCount = 0;
        List<VDJCGene> genes;
        VDJCAlignerParameters alignerParameters;
        CloneAssemblerParameters cloneAssemblerParameters;
        try (ClnAReader reader = new ClnAReader(in, VDJCLibraryRegistry.getDefault());
             PrimitivO tmpOut = new PrimitivO(new BufferedOutputStream(new FileOutputStream(out)));
             BufferedWriter debugReport = debugReportFile == null ? null : new BufferedWriter(new OutputStreamWriter(new FileOutputStream(debugReportFile)))) {

            final CloneFactory cloneFactory = new CloneFactory(reader.getAssemblerParameters().getCloneFactoryParameters(),
                    reader.getAssemblingFeatures(), reader.getGenes(), reader.getAlignerParameters().getFeaturesToAlignMap());

            alignerParameters = reader.getAlignerParameters();
            cloneAssemblerParameters = reader.getAssemblerParameters();
            genes = reader.getGenes();
            IOUtil.registerGeneReferences(tmpOut, genes, alignerParameters);

            ClnAReader.CloneAlignmentsPort cloneAlignmentsPort = reader.clonesAndAlignments();
            SmartProgressReporter.startProgressReport("Assembling", cloneAlignmentsPort);

            OutputPort<Clone[]> parallelProcessor = new ParallelProcessor<>(cloneAlignmentsPort, cloneAlignments -> {
                try {
                    // Collecting statistics

                    EnumMap<GeneType, Map<VDJCGeneId, CoverageAccumulator>> coverages = cloneAlignments.clone.getHitsMap()
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
                        report.onAssemblyCanceled(cloneAlignments.clone);
                        return new Clone[]{cloneAlignments.clone};
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
                            cloneAlignments.clone, alignerParameters,
                            bestGenes.get(GeneType.Variable), bestGenes.get(GeneType.Joining)
                    );

                    fullSeqAssembler.setReport(report);

                    FullSeqAssembler.RawVariantsData rawVariantsData = fullSeqAssembler.calculateRawData(cloneAlignments::alignments);

                    if (debugReport != null) {
                        synchronized (debugReport) {
                            try (FileOutputStream fos = new FileOutputStream(debugReportFile + "." + cloneAlignments.clone.getId())) {
                                final String content = rawVariantsData.toCsv((byte) 10);
                                fos.write(content.getBytes());
                            }

                            try {
                                debugReport.write("Clone: " + cloneAlignments.clone.getId());
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
                    throw new RuntimeException("While processing clone #" + cloneAlignments.clone.getId(), re);
                }
            }, threads);

            for (Clone[] clones : CUtils.it(parallelProcessor)) {
                totalClonesCount += clones.length;
                for (Clone cl : clones)
                    tmpOut.writeObject(cl);
            }

            assert report.getInitialCloneCount() == reader.numberOfClones();
        }

        assert report.getFinalCloneCount() == totalClonesCount;
        assert report.getFinalCloneCount() >= report.getInitialCloneCount();

        Clone[] clones = new Clone[totalClonesCount];
        try (PrimitivI tmpIn = new PrimitivI(new BufferedInputStream(new FileInputStream(out)))) {
            IOUtil.registerGeneReferences(tmpIn, genes, alignerParameters);
            int i = 0;
            for (Clone clone : CUtils.it(new PipeDataInputReader<>(Clone.class, tmpIn, totalClonesCount)))
                clones[i++] = clone;
        }

        Arrays.sort(clones, Comparator.comparingDouble(c -> -c.getCount()));
        for (int i = 0; i < clones.length; i++)
            clones[i] = clones[i].setId(i);
        CloneSet cloneSet = new CloneSet(Arrays.asList(clones), genes, alignerParameters.getFeaturesToAlignMap(),
                alignerParameters, cloneAssemblerParameters);

        try (ClnsWriter writer = new ClnsWriter(getFullPipelineConfiguration(), cloneSet, out)) {
            SmartProgressReporter.startProgressReport(writer);
            writer.write();
        }

        ReportWrapper reportWrapper = new ReportWrapper(ASSEMBLE_CONTIGS_COMMAND_NAME, report);
        reportWrapper.setStartMillis(beginTimestamp);
        reportWrapper.setInputFiles(in);
        reportWrapper.setOutputFiles(out);
        reportWrapper.setCommandLine(getCommandLineArguments());
        reportWrapper.setFinishMillis(System.currentTimeMillis());

        // Writing report to stout
        System.out.println("============= Report ==============");
        Util.writeReportToStdout(report);

        if (reportFile != null)
            Util.writeReport(reportFile, reportWrapper);

        if (jsonReport != null)
            Util.writeJsonReport(jsonReport, reportWrapper);
    }

    @JsonAutoDetect(
            fieldVisibility = JsonAutoDetect.Visibility.ANY,
            isGetterVisibility = JsonAutoDetect.Visibility.NONE,
            getterVisibility = JsonAutoDetect.Visibility.NONE)
    @JsonTypeInfo(
            use = JsonTypeInfo.Id.CLASS,
            include = JsonTypeInfo.As.PROPERTY,
            property = "type")
    public static class AssembleContigsConfiguration implements ActionConfiguration {
        public final FullSeqAssemblerParameters assemblerParameters;

        @JsonCreator
        public AssembleContigsConfiguration(
                @JsonProperty("assemblerParameters") FullSeqAssemblerParameters assemblerParameters) {
            this.assemblerParameters = assemblerParameters;
        }

        @Override
        public String actionName() {
            return ASSEMBLE_CONTIGS_COMMAND_NAME;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AssembleContigsConfiguration that = (AssembleContigsConfiguration) o;
            return Objects.equals(assemblerParameters, that.assemblerParameters);
        }

        @Override
        public int hashCode() {
            return Objects.hash(assemblerParameters);
        }
    }
}
