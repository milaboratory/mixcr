/*
 * Copyright (c) 2014-2015, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
 * commercial purposes should contact the Inventors using one of the following
 * email addresses: chudakovdm@mail.ru, chudakovdm@gmail.com
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
import cc.redberry.pipe.VoidProcessor;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.milaboratory.cli.Action;
import com.milaboratory.cli.ActionHelper;
import com.milaboratory.cli.ActionParameters;
import com.milaboratory.cli.HiddenAction;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.info.AlignmentInfoCollector;
import com.milaboratory.mixcr.info.GeneFeatureCoverageCollector;
import com.milaboratory.mixcr.info.ReferencePointCoverageCollector;
import com.milaboratory.util.SmartProgressReporter;
import io.repseq.core.GeneFeature;
import io.repseq.core.ReferencePoint;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import static io.repseq.core.GeneFeature.*;
import static io.repseq.core.ReferencePoint.*;

@HiddenAction
public class ActionAlignmentsStat implements Action {
    final AParameters actionParameters = new AParameters();

    @Override
    public void go(ActionHelper helper) throws Exception {

        long[] geneFeatureCounters = new long[targetFeatures.length];
        AlignmentInfoCollector[] collectors = new AlignmentInfoCollector[targetFeatures.length + targetReferencePoints.length];

        int i = 0;

        for (GeneFeature targetFeature : targetFeatures)
            collectors[i++] = new GeneFeatureCoverageCollector(targetFeature);

        for (ReferencePoint targetReferencePoint : targetReferencePoints)
            collectors[i++] = new ReferencePointCoverageCollector(targetReferencePoint, 40, 40);

        final Collector collector = new Collector(collectors);

        try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(actionParameters.getInputFileName());
             PrintStream output = actionParameters.getOutputFileName().equals("-") ? System.out :
                     new PrintStream(new BufferedOutputStream(new FileOutputStream(actionParameters.getOutputFileName()), 32768))
        ) {
            SmartProgressReporter.startProgressReport("Analysis", reader);
            CUtils.processAllInParallel(reader, collector, Math.min(4, Runtime.getRuntime().availableProcessors()));
            collector.end();

            if (output == System.out)
                output.println();

            collector.write(output);
        }
    }

    private static final GeneFeature[] targetFeatures = {
            V5UTR,
            new GeneFeature(L1Begin, -20, 0),
            L1, VIntron, L2, FR1, CDR1, FR2, CDR2, FR3, CDR3, FR4,
            new GeneFeature(FR4, 0, -3)
    };

    private static final ReferencePoint[] targetReferencePoints = {
            L1Begin, L1End, L2Begin, FR1Begin, CDR1Begin, FR2Begin, CDR2Begin, FR3Begin, CDR3Begin,
            FR4Begin, FR4End
    };

    @Override
    public String command() {
        return "alignmentsStat";
    }

    @Override
    public AParameters params() {
        return actionParameters;
    }

    @Parameters(commandDescription = "Alignments statistics.")
    public static final class AParameters extends ActionParameters {
        @Parameter(description = "input_file.vdjca [output.txt]", variableArity = true)
        public List<String> parameters = new ArrayList<>();

        public String getInputFileName() {
            return parameters.get(0);
        }

        public String getOutputFileName() {
            return parameters.size() == 1 ? "-" : parameters.get(1);
        }

        @Override
        public void validate() {
            if (parameters.size() == 0 || parameters.size() > 2)
                throw new ParameterException("Wrong number of parameters.");
            super.validate();
        }
    }

    private static class Collector implements VoidProcessor<VDJCAlignments> {
        final AlignmentInfoCollector[] collectors;

        public Collector(AlignmentInfoCollector... collectors) {
            this.collectors = collectors;
        }

        @Override
        public void process(VDJCAlignments input) {
            for (AlignmentInfoCollector collector : collectors)
                collector.put(input);
        }

        public void end() {
            for (AlignmentInfoCollector collector : collectors)
                collector.end();
        }

        public void write(PrintStream writer) {
            for (AlignmentInfoCollector collector : collectors)
                collector.writeResult(writer);
        }
    }
}
