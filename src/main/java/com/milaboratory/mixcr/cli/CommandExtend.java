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
import cc.redberry.pipe.util.OrderedOutputPort;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.milaboratory.cli.ActionConfiguration;
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

import static com.milaboratory.mixcr.basictypes.IOUtil.*;
import static com.milaboratory.mixcr.cli.CommandExtend.EXTEND_COMMAND_NAME;

@Command(name = EXTEND_COMMAND_NAME,
        sortOptions = true,
        separator = " ",
        description = "Impute alignments or clones with germline sequences.")
public class CommandExtend extends ACommandWithSmartOverwriteWithSingleInputMiXCR {
    static final String EXTEND_COMMAND_NAME = "extend";

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

    public int threads = Runtime.getRuntime().availableProcessors();;

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
        switch (getInputFileInfo().fileType) {
            case MAGIC_VDJC:
                processVDJCA();
                break;
            case MAGIC_CLNS:
                processClns();
                break;
            case MAGIC_CLNA:
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
                cloneSet.getAssemblerParameters());

        try (ClnsWriter writer = new ClnsWriter(out)) {
            writer.writeCloneSet(getFullPipelineConfiguration(), newCloneSet);
        }
    }

    @SuppressWarnings("unchecked")
    void processVDJCA() throws IOException {
        try (final VDJCAlignmentsReader reader = new VDJCAlignmentsReader(in);
             final VDJCAlignmentsWriter writer = new VDJCAlignmentsWriter(out)) {
            SmartProgressReporter.startProgressReport("Extending alignments", reader);

            writer.header(reader.getParameters(), reader.getUsedGenes(), getFullPipelineConfiguration());

            ProcessWrapper<VDJCAlignments> process = new ProcessWrapper<>(reader,
                    reader.getParameters().getVAlignerParameters().getParameters().getScoring(),
                    reader.getParameters().getJAlignerParameters().getParameters().getScoring());

            for (VDJCAlignments alignments : CUtils.it(new OrderedOutputPort<>(process.getOutput(), VDJCAlignments::getAlignmentsIndex)))
                writer.write(alignments.shiftIndelsAtHomopolymers());
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

    @JsonAutoDetect(
            fieldVisibility = JsonAutoDetect.Visibility.ANY,
            isGetterVisibility = JsonAutoDetect.Visibility.NONE,
            getterVisibility = JsonAutoDetect.Visibility.NONE)
    @JsonTypeInfo(
            use = JsonTypeInfo.Id.CLASS,
            include = JsonTypeInfo.As.PROPERTY,
            property = "type")
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
