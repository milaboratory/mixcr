package com.milaboratory.mixcr.cli;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.blocks.ParallelProcessor;
import cc.redberry.pipe.util.Indexer;
import cc.redberry.pipe.util.OrderedOutputPort;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.validators.PositiveInteger;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.milaboratory.cli.Action;
import com.milaboratory.cli.ActionHelper;
import com.milaboratory.cli.ActionParameters;
import com.milaboratory.cli.ActionParametersWithOutput;
import com.milaboratory.core.alignment.AlignmentScoring;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.mixcr.util.VDJCObjectExtender;
import com.milaboratory.util.SmartProgressReporter;
import io.repseq.core.Chains;
import io.repseq.core.ReferencePoint;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author Stanislav Poslavsky
 */
public class ActionExtend implements Action {
    private final ExtendActionParameters parameters = new ExtendActionParameters();

    @Override
    public void go(ActionHelper helper) throws Exception {
        IOUtil.MiXCRFileType fileType = IOUtil.detectFilType(parameters.getInput());

        switch (fileType) {
            case VDJCA:
                processVDJCA(helper);
                break;
            case Clns:
                processClns(helper);
                break;
            case ClnA:
                System.out.println("Operation is not supported for ClnA files.");
                System.exit(1);
                break;
            default:
                System.out.println("Not supported file type.");
                System.exit(1);

        }
    }

    void processClns(ActionHelper helper) throws IOException {
        CloneSet cloneSet = CloneSetIO.read(parameters.getInput());

        OutputPort<Clone> outputPort = CUtils.asOutputPort(cloneSet);
        ProcessWrapper<Clone> process = new ProcessWrapper<>(outputPort,
                cloneSet.getAlignmentParameters().getVAlignerParameters().getParameters().getScoring(),
                cloneSet.getAlignmentParameters().getJAlignerParameters().getParameters().getScoring(),
                helper);

        List<Clone> clones = new ArrayList<>(cloneSet.getClones().size());
        for (Clone clone : CUtils.it(process.getOutput()))
            clones.add(clone.resetParentCloneSet());

        clones.sort(Comparator.comparing(Clone::getId));

        CloneSet newCloneSet = new CloneSet(clones, cloneSet.getUsedGenes(), cloneSet.getAlignedFeatures(),
                cloneSet.getAlignmentParameters(), cloneSet.getAssemblerParameters());

        CloneSetIO.write(newCloneSet, parameters.getOutput());
    }

    @SuppressWarnings("unchecked")
    void processVDJCA(ActionHelper helper) throws IOException {
        try (final VDJCAlignmentsReader reader = new VDJCAlignmentsReader(parameters.getInput());
             final VDJCAlignmentsWriter writer = new VDJCAlignmentsWriter(parameters.getOutput())) {
            SmartProgressReporter.startProgressReport("Processing", reader);

            writer.header(reader.getParameters(), reader.getUsedGenes());

            ProcessWrapper<VDJCAlignments> process = new ProcessWrapper<>(reader,
                    reader.getParameters().getVAlignerParameters().getParameters().getScoring(),
                    reader.getParameters().getJAlignerParameters().getParameters().getScoring(),
                    helper);

            for (VDJCAlignments alignments : CUtils.it(new OrderedOutputPort<>(process.getOutput(),
                    new Indexer<VDJCAlignments>() {
                        @Override
                        public long getIndex(VDJCAlignments o) {
                            return o.getAlignmentsIndex();
                        }
                    })))
                writer.write(alignments);
            writer.setNumberOfProcessedReads(reader.getNumberOfReads());

            process.finish();
        }
    }

    final class ProcessWrapper<T extends VDJCObject> {
        final ReportWrapper report;
        final ParallelProcessor<T, T> output;

        public ProcessWrapper(OutputPort<T> input,
                              AlignmentScoring<NucleotideSequence> vScoring, AlignmentScoring<NucleotideSequence> jScoring,
                              ActionHelper helper) {
            VDJCObjectExtender<T> extender = new VDJCObjectExtender<>(parameters.getChains(), parameters.extensionQuality,
                    vScoring, jScoring,
                    parameters.minimalVScore, parameters.minimalJScore,
                    ReferencePoint.parse(parameters.vAnchorPoint),
                    ReferencePoint.parse(parameters.jAnchorPoint));
            this.output = new ParallelProcessor<>(input, extender, parameters.threads);
            this.report = new ReportWrapper(command(), extender);
            report.setStartMillis(System.currentTimeMillis());
            report.setInputFiles(parameters.getInput());
            report.setOutputFiles(parameters.getOutput());
            report.setCommandLine(helper.getCommandLineArguments());
        }

        public OutputPort<T> getOutput() {
            return output;
        }

        public void finish() throws JsonProcessingException {
            report.setFinishMillis(System.currentTimeMillis());

            // Writing report to stout
            System.out.println("============= Report ==============");
            Util.writeReportToStdout(report);

            if (parameters.report != null)
                Util.writeReport(parameters.report, report);

            if (parameters.jsonReport != null)
                Util.writeJsonReport(parameters.jsonReport, report);
        }
    }

    @Override
    public String command() {
        return "extend";
    }

    @Override
    public ActionParameters params() {
        return parameters;
    }

    @Parameters(commandDescription = "Extend corresponding entity (clone or alignment) using germline sequence.")
    private static final class ExtendActionParameters extends ActionParametersWithOutput {
        @Parameter(description = "input.vdjca[.gz]|.clns output.vdjca[.gz]|.clns")
        public List<String> parameters;

        @Parameter(description = "Apply procedure only to alignments with specific immunological-receptor chains.",
                names = {"-c", "--chains"})
        public String chains = "TCR";

        @Parameter(description = "Report file.",
                names = {"-r", "--report"})
        public String report;

        @Parameter(description = "JSON report file.",
                names = {"--json-report"})
        public String jsonReport = null;

        @Parameter(description = "Quality score of extended sequence.",
                names = {"-q", "--quality"})
        public byte extensionQuality = 30;

        @Parameter(description = "Processing threads", names = {"-t", "--threads"},
                validateWith = PositiveInteger.class)
        public int threads = 2;

        @Parameter(description = "V extension anchor point.",
                names = {"--v-anchor"})
        public String vAnchorPoint = "CDR3Begin";

        @Parameter(description = "J extension anchor point.",
                names = {"--j-anchor"})
        public String jAnchorPoint = "CDR3End";

        @Parameter(description = "Minimal V hit score to perform left extension.",
                names = {"--min-v-score"})
        public int minimalVScore = 100;

        @Parameter(description = "Minimal J hit score alignment to perform right extension.",
                names = {"--min-j-score"})
        public int minimalJScore = 70;

        private String getInput() {
            return parameters.get(0);
        }

        private String getOutput() {
            return parameters.get(1);
        }

        public Chains getChains() {
            return Chains.parse(chains);
        }

        @Override
        protected List<String> getOutputFiles() {
            return parameters.subList(1, parameters.size());
        }
    }
}
