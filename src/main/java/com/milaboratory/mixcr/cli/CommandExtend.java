package com.milaboratory.mixcr.cli;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.blocks.ParallelProcessor;
import cc.redberry.pipe.util.OrderedOutputPort;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.milaboratory.core.alignment.AlignmentScoring;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.mixcr.util.VDJCObjectExtender;
import com.milaboratory.util.SmartProgressReporter;
import io.repseq.core.Chains;
import io.repseq.core.ReferencePoint;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static com.milaboratory.mixcr.cli.CommandExtend.EXTEND_COMMAND_NAME;

@Command(name = EXTEND_COMMAND_NAME,
        sortOptions = true,
        separator = " ",
        description = "Extend corresponding entity (clone or alignment) using germline sequence.")
public class CommandExtend extends ACommandWithResumeWithSingleInput {
    static final String EXTEND_COMMAND_NAME = "extend";

    @Option(description = "Apply procedure only to alignments with specific immunological-receptor chains.",
            names = {"-c", "--chains"})
    public String chains = "TCR";

    @Option(description = "Report file.",
            names = {"-r", "--report"})
    public String reportFile;

    @Option(description = "JSON report file.",
            names = {"--json-report"})
    public String jsonReport = null;

    @Option(description = "Quality score of extended sequence.",
            names = {"-q", "--quality"})
    public byte extensionQuality = 30;

    public int threads = 2;

    @Option(description = "Processing threads", names = {"-t", "--threads"})
    public void setThreads(int threads) {
        if (threads < 0)
            throwValidationException("-t / --threads must be positive");
        this.threads = threads;
    }

    @Option(description = "V extension anchor point.",
            names = {"--v-anchor"})
    public String vAnchorPoint = "CDR3Begin";

    @Option(description = "J extension anchor point.",
            names = {"--j-anchor"})
    public String jAnchorPoint = "CDR3End";

    @Option(description = "Minimal V hit score to perform left extension.",
            names = {"--min-v-score"})
    public int minimalVScore = 100;

    @Option(description = "Minimal J hit score alignment to perform right extension.",
            names = {"--min-j-score"})
    public int minimalJScore = 70;

    public Chains getChains() {
        return Chains.parse(chains);
    }

    public ReferencePoint getVAnchorPoint() {
        return ReferencePoint.parse(vAnchorPoint);
    }

    public ReferencePoint getJAnchorPoint() {
        return ReferencePoint.parse(jAnchorPoint);
    }

    @Override
    public ActionConfiguration getConfiguration() {
        return new ExtendConfiguration(getChains(), extensionQuality, getVAnchorPoint(), getJAnchorPoint(), minimalVScore, minimalJScore);
    }


    @Override
    public void run1() throws Exception {
        IOUtil.MiXCRFileType fileType = IOUtil.detectFilType(in);

        switch (fileType) {
            case VDJCA:
                processVDJCA();
                break;
            case Clns:
                processClns();
                break;
            case ClnA:
                throwValidationException("Operation is not supported for ClnA files.");
                break;
            default:
                throwValidationException("Not supported file type.");
        }
    }

    void processClns() throws IOException {
        CloneSet cloneSet = CloneSetIO.read(in);

        OutputPort<Clone> outputPort = CUtils.asOutputPort(cloneSet);
        ProcessWrapper<Clone> process = new ProcessWrapper<>(outputPort,
                cloneSet.getAlignmentParameters().getVAlignerParameters().getParameters().getScoring(),
                cloneSet.getAlignmentParameters().getJAlignerParameters().getParameters().getScoring());

        List<Clone> clones = new ArrayList<>(cloneSet.getClones().size());
        for (Clone clone : CUtils.it(process.getOutput()))
            clones.add(clone.resetParentCloneSet());

        clones.sort(Comparator.comparing(Clone::getId));

        CloneSet newCloneSet = new CloneSet(clones, cloneSet.getUsedGenes(), cloneSet.getAlignedFeatures(),
                cloneSet.getAlignmentParameters(), cloneSet.getAssemblerParameters());

        try (ClnsWriter writer = new ClnsWriter(getFullPipelineConfiguration(), newCloneSet, out)) {
            writer.write();
        }
    }

    @SuppressWarnings("unchecked")
    void processVDJCA() throws IOException {
        try (final VDJCAlignmentsReader reader = new VDJCAlignmentsReader(in);
             final VDJCAlignmentsWriter writer = new VDJCAlignmentsWriter(out)) {
            SmartProgressReporter.startProgressReport("Processing", reader);

            writer.header(reader.getParameters(), reader.getUsedGenes(), getFullPipelineConfiguration());

            ProcessWrapper<VDJCAlignments> process = new ProcessWrapper<>(reader,
                    reader.getParameters().getVAlignerParameters().getParameters().getScoring(),
                    reader.getParameters().getJAlignerParameters().getParameters().getScoring());

            for (VDJCAlignments alignments : CUtils.it(new OrderedOutputPort<>(process.getOutput(), VDJCAlignments::getAlignmentsIndex)))
                writer.write(alignments);
            writer.setNumberOfProcessedReads(reader.getNumberOfReads());

            process.finish();
        }
    }

    final class ProcessWrapper<T extends VDJCObject> {
        final ReportWrapper report;
        final ParallelProcessor<T, T> output;

        public ProcessWrapper(OutputPort<T> input,
                              AlignmentScoring<NucleotideSequence> vScoring, AlignmentScoring<NucleotideSequence> jScoring) {
            VDJCObjectExtender<T> extender = new VDJCObjectExtender<>(getChains(), extensionQuality,
                    vScoring, jScoring,
                    minimalVScore, minimalJScore,
                    getVAnchorPoint(),
                    getJAnchorPoint());
            this.output = new ParallelProcessor<>(input, extender, threads);
            this.report = new ReportWrapper(EXTEND_COMMAND_NAME, extender);
            report.setStartMillis(System.currentTimeMillis());
            report.setInputFiles(in);
            report.setOutputFiles(out);
            report.setCommandLine(getCommandLineArguments());
        }

        public OutputPort<T> getOutput() {
            return output;
        }

        public void finish() throws JsonProcessingException {
            report.setFinishMillis(System.currentTimeMillis());

            // Writing report to stout
            System.out.println("============= Report ==============");
            Util.writeReportToStdout(report);

            if (reportFile != null)
                Util.writeReport(reportFile, report);

            if (jsonReport != null)
                Util.writeJsonReport(jsonReport, report);
        }
    }

    public static class ExtendConfiguration implements ActionConfiguration {
        final Chains chains;
        final byte extensionQuality;
        final ReferencePoint vAnchorPoint, jAnchorPoint;
        final int minimalVScore;
        final int minimalJScore;

        @JsonCreator
        public ExtendConfiguration(@JsonProperty("chains") Chains chains,
                                   @JsonProperty("extensionQuality") byte extensionQuality,
                                   @JsonProperty("vAnchorPoint") ReferencePoint vAnchorPoint,
                                   @JsonProperty("jAnchorPoint") ReferencePoint jAnchorPoint,
                                   @JsonProperty("minimalVScore") int minimalVScore,
                                   @JsonProperty("minimalJScore") int minimalJScore) {
            this.chains = chains;
            this.extensionQuality = extensionQuality;
            this.vAnchorPoint = vAnchorPoint;
            this.jAnchorPoint = jAnchorPoint;
            this.minimalVScore = minimalVScore;
            this.minimalJScore = minimalJScore;
        }

        @Override
        public String actionName() {
            return EXTEND_COMMAND_NAME;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ExtendConfiguration that = (ExtendConfiguration) o;
            return extensionQuality == that.extensionQuality &&
                    minimalVScore == that.minimalVScore &&
                    minimalJScore == that.minimalJScore &&
                    Objects.equals(chains, that.chains) &&
                    Objects.equals(vAnchorPoint, that.vAnchorPoint) &&
                    Objects.equals(jAnchorPoint, that.jAnchorPoint);
        }

        @Override
        public int hashCode() {
            return Objects.hash(chains, extensionQuality, vAnchorPoint, jAnchorPoint, minimalVScore, minimalJScore);
        }
    }
}
