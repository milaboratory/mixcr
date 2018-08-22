/*
 * Copyright (c) 2014-2018, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
import cc.redberry.pipe.blocks.ParallelProcessor;
import cc.redberry.pipe.util.Indexer;
import cc.redberry.pipe.util.OrderedOutputPort;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.milaboratory.cli.Action;
import com.milaboratory.cli.ActionHelper;
import com.milaboratory.cli.ActionParameters;
import com.milaboratory.cli.ActionParametersWithOutput;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter;
import com.milaboratory.mixcr.util.AlignmentExtender;
import com.milaboratory.util.SmartProgressReporter;
import io.repseq.core.Chains;
import io.repseq.core.ReferencePoint;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Stanislav Poslavsky
 */
public class ActionExtendAlignments implements Action {
    private final ExtendCD3Parameters parameters;

    public ActionExtendAlignments(ExtendCD3Parameters parameters) {
        this.parameters = parameters;
    }

    public ActionExtendAlignments() {
        this(new ExtendCD3Parameters());
    }

    @Override
    public void go(ActionHelper helper) throws Exception {
        long beginTimestamp = System.currentTimeMillis();
        try (final VDJCAlignmentsReader reader = new VDJCAlignmentsReader(parameters.getInput());
             final VDJCAlignmentsWriter writer = new VDJCAlignmentsWriter(parameters.getOutput())) {
            SmartProgressReporter.startProgressReport("Processing", reader);

            writer.header(reader.getParameters(), reader.getUsedGenes());
            AlignmentExtender extender = new AlignmentExtender(parameters.getChains(), parameters.extensionQuality,
                    reader.getParameters().getVAlignerParameters().getParameters().getScoring(),
                    reader.getParameters().getJAlignerParameters().getParameters().getScoring(),
                    parameters.minimalVScore, parameters.minimalJScore,
                    ReferencePoint.parse(parameters.vAnchorPoint),
                    ReferencePoint.parse(parameters.jAnchorPoint));
            ReportWrapper report = new ReportWrapper(command(), extender);
            report.setStartMillis(beginTimestamp);
            report.setInputFiles(new String[]{parameters.getInput()});
            report.setOutputFiles(new String[]{parameters.getOutput()});
            report.setCommandLine(helper.getCommandLineArguments());

            ParallelProcessor<VDJCAlignments, VDJCAlignments> pp = new ParallelProcessor<>(reader, extender, 2);
            for (VDJCAlignments alignments : CUtils.it(new OrderedOutputPort<>(pp,
                    new Indexer<VDJCAlignments>() {
                        @Override
                        public long getIndex(VDJCAlignments o) {
                            return o.getAlignmentsIndex();
                        }
                    })))
                writer.write(alignments);
            writer.setNumberOfProcessedReads(reader.getNumberOfReads());

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
        return "extendAlignments";
    }

    @Override
    public ActionParameters params() {
        return parameters;
    }

    @Parameters(commandDescription = "Extend alignment using germline sequence.")
    public static class ExtendCD3Parameters extends ActionParametersWithOutput {
        @Parameter(description = "input.vdjca[.gz] output.vdjca[.gz]")
        public List<String> parameters = new ArrayList<>();

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

        @Parameter(description = "V extension anchor point.",
                names = {"--v-anchor"})
        public String vAnchorPoint = "CDR3Begin";

        @Parameter(description = "J extension anchor point.",
                names = {"--j-anchor"})
        public String jAnchorPoint = "CDR3End";

        @Parameter(description = "Minimal score of V alignment to perform left extension.",
                names = {"--min-v-score"})
        public int minimalVScore = 100;

        @Parameter(description = "Minimal score of J alignment to perform right extension.",
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
