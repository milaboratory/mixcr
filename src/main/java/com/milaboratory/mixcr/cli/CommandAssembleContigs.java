package com.milaboratory.mixcr.cli;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.blocks.ParallelProcessor;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.assembler.CloneAssemblerParameters;
import com.milaboratory.mixcr.assembler.CloneFactory;
import com.milaboratory.mixcr.assembler.fullseq.FullSeqAssembler;
import com.milaboratory.mixcr.assembler.fullseq.FullSeqAssemblerParameters;
import com.milaboratory.mixcr.assembler.fullseq.FullSeqAssemblerReport;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.primitivio.PipeDataInputReader;
import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.PrimitivO;
import com.milaboratory.util.SmartProgressReporter;
import io.repseq.core.VDJCGene;
import io.repseq.core.VDJCLibraryRegistry;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.*;
import java.util.*;

import static com.milaboratory.mixcr.cli.CommandAssembleContigs.ASSEMBLE_CONTIGS_COMMAND_NAME;

@Command(name = ASSEMBLE_CONTIGS_COMMAND_NAME,
        sortOptions = true,
        separator = " ",
        description = "Assembles full sequence.")
public class CommandAssembleContigs extends ACommandWithResumeWithSingleInput {
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

    @Option(description = "Report file.",
            names = {"-r", "--report"})
    public String reportFile;

    @Option(description = "Report file.",
            names = {"--debug-report"}, hidden = true)
    public String debugReportFile;

    @Option(description = "JSON report file.",
            names = {"--json-report"})
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
                    FullSeqAssembler fullSeqAssembler = new FullSeqAssembler(cloneFactory, assemblerParameters, cloneAlignments.clone, alignerParameters);
                    fullSeqAssembler.setReport(report);

                    FullSeqAssembler.RawVariantsData rawVariantsData = fullSeqAssembler.calculateRawData(cloneAlignments::alignments);

                    if (debugReport != null) {
                        synchronized (debugReport) {
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
