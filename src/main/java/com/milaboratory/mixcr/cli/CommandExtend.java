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
import cc.redberry.pipe.blocks.ParallelProcessor;
import cc.redberry.pipe.util.OrderedOutputPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.milaboratory.core.alignment.AlignmentScoring;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.mixcr.util.VDJCObjectExtender;
import com.milaboratory.util.ReportUtil;
import com.milaboratory.util.SmartProgressReporter;
import io.repseq.core.Chains;
import io.repseq.core.GeneType;
import io.repseq.core.ReferencePoint;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

import static com.milaboratory.mixcr.basictypes.IOUtil.extractFileType;
import static com.milaboratory.mixcr.cli.CommandExtend.EXTEND_COMMAND_NAME;

@Command(name = EXTEND_COMMAND_NAME,
        sortOptions = true,
        separator = " ",
        description = "Impute alignments or clones with germline sequences.")
public class CommandExtend extends MiXCRCommand {
    static final String EXTEND_COMMAND_NAME = "extend";

    @Parameters(description = "data.[vdjca|clns|clna]", index = "0")
    public String in;

    @Parameters(description = "extendeed.[vdjca|clns|clna]", index = "1")
    public String out;

    @Option(description = "Apply procedure only to alignments with specific immunological-receptor chains.",
            names = {"-c", "--chains"})
    public String chains = "TCR";

    @Option(description = CommonDescriptions.REPORT,
            names = {"-r", "--report"})
    public String reportFile;

    @Option(description = CommonDescriptions.JSON_REPORT,
            names = {"-j", "--json-report"})
    public String jsonReport = null;

    @Option(description = "Quality score value to assign imputed sequences",
            names = {"-q", "--quality"})
    public byte extensionQuality = 30;

    public int threads = Runtime.getRuntime().availableProcessors();
    ;

    @Option(description = "Processing threads",
            names = {"-t", "--threads"})
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

    @Override
    protected List<String> getInputFiles() {
        return Collections.singletonList(in);
    }

    @Override
    protected List<String> getOutputFiles() {
        return Collections.singletonList(out);
    }

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
    public void run0() throws Exception {
        switch (extractFileType(Paths.get(in))) {
            case VDJCA:
                processVDJCA();
                break;
            case CLNS:
                processClns();
                break;
            case CLNA:
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

        CloneSet newCloneSet = new CloneSet(clones, cloneSet.getUsedGenes(), cloneSet.getAlignmentParameters(),
                cloneSet.getAssemblerParameters(), cloneSet.getTagsInfo(), cloneSet.getOrdering());

        try (ClnsWriter writer = new ClnsWriter(out)) {
            writer.writeCloneSet(newCloneSet);
        }
    }

    @SuppressWarnings("unchecked")
    void processVDJCA() throws IOException {
        try (final VDJCAlignmentsReader reader = new VDJCAlignmentsReader(in);
             final VDJCAlignmentsWriter writer = new VDJCAlignmentsWriter(out)) {
            SmartProgressReporter.startProgressReport("Extending alignments", reader);

            writer.header(reader.getParameters(), reader.getUsedGenes(), reader.getTagsInfo());

            ProcessWrapper<VDJCAlignments> process = new ProcessWrapper<>(reader,
                    reader.getParameters().getVAlignerParameters().getParameters().getScoring(),
                    reader.getParameters().getJAlignerParameters().getParameters().getScoring());

            // Shifting indels in homopolymers is effective only for alignments build with linear gap scoring,
            // consolidating some gaps, on the contrary, for alignments obtained with affine scoring such procedure
            // may break the alignment (gaps there are already consolidated as much as possible)
            Set<GeneType> gtRequiringIndelShifts = reader.getParameters().getGeneTypesWithLinearScoring();

            for (VDJCAlignments alignments : CUtils.it(new OrderedOutputPort<>(process.getOutput(), VDJCAlignments::getAlignmentsIndex)))
                writer.write(alignments.shiftIndelsAtHomopolymers(gtRequiringIndelShifts));

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
            ReportUtil.writeReportToStdout(report);

            if (reportFile != null)
                ReportUtil.appendReport(reportFile, report);

            if (jsonReport != null)
                ReportUtil.appendJsonReport(jsonReport, report);
        }
    }
}
