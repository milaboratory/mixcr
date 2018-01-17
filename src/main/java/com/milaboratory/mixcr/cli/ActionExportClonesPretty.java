/*
 * Copyright (c) 2014-2016, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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

import cc.redberry.primitives.Filter;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.milaboratory.cli.Action;
import com.milaboratory.cli.ActionHelper;
import com.milaboratory.cli.ActionParameters;
import com.milaboratory.core.alignment.MultiAlignmentHelper;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.*;
import io.repseq.core.Chains;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import static cc.redberry.primitives.FilterUtil.ACCEPT_ALL;
import static cc.redberry.primitives.FilterUtil.and;

public class ActionExportClonesPretty implements Action {
    public static final int LINE_LENGTH = 80;
    public static final int LINE_OFFSET = 7;
    public static final int MAX_LENGTH = 2 * LINE_OFFSET + LINE_LENGTH;
    final AParameters actionParameters = new AParameters();

    @Override
    public void go(ActionHelper helper) throws Exception {
        Filter<Clone> filter = actionParameters.getFilter();
        long total = 0, filtered = 0;
        CloneSet cloneSet = CloneSetIO.read(actionParameters.getInputFileName());

        try (PrintStream output = actionParameters.getOutputFileName().equals("-") ? System.out :
                new PrintStream(new BufferedOutputStream(new FileOutputStream(actionParameters.getOutputFileName()), 32768))
        ) {
            long countBefore = actionParameters.limitBefore == null ? Long.MAX_VALUE : actionParameters.limitBefore;
            long countAfter = actionParameters.limitAfter == null ? Long.MAX_VALUE : actionParameters.limitAfter;
            long skipAfter = actionParameters.skipAfter == null ? 0 : actionParameters.skipAfter;
            for (final Clone clone : cloneSet) {
                if (--countBefore < 0)
                    break;

                ++total;

                if (filter.accept(clone)) {
                    ++filtered;

                    if (--skipAfter >= 0)
                        continue;

                    if (--countAfter < 0)
                        break;

                    outputCompact(output, clone);
                }
            }
            output.println("Filtered: " + filtered + " / " + total + " = " + (100.0 * filtered / total) + "%");
        }
    }

    public static void outputCompact(PrintStream output, final Clone clone) {
        output.println(">>> Clone id: " + clone.getId());
        output.println(">>> Abundance, reads (fraction): " + clone.getCount() + " (" + clone.getFraction() + ")");
        output.println();
        for (int i = 0; i < clone.numberOfTargets(); i++) {
            MultiAlignmentHelper targetAsMultiAlignment = VDJCAlignmentsFormatter.getTargetAsMultiAlignment(clone, i, true, false);
            if (targetAsMultiAlignment == null)
                continue;
            MultiAlignmentHelper[] split = targetAsMultiAlignment.split(80);
            for (MultiAlignmentHelper spl : split) {
                output.println(spl);
                output.println();
            }
        }
        output.println();
    }

    @Override
    public String command() {
        return "exportClonesPretty";
    }

    @Override
    public AParameters params() {
        return actionParameters;
    }

    @Parameters(commandDescription = "Export verbose clone information.")
    public static final class AParameters extends ActionParameters {
        @Parameter(description = "input_file.clns [output.txt]", variableArity = true)
        public List<String> parameters = new ArrayList<>();

        @Parameter(description = "Limit number of alignments before filtering",
                names = {"-b", "--limitBefore"})
        public Integer limitBefore = null;

        @Parameter(description = "Limit number of filtered alignments; no more " +
                "than N alignments will be outputted",
                names = {"-n", "--limit"})
        public Integer limitAfter = null;

        @Parameter(description = "Number of output alignments to skip",
                names = {"-s", "--skip"})
        public Integer skipAfter = null;

        @Parameter(description = "Filter export to a specific protein chain gene (e.g. TRA or IGH).",
                names = {"-c", "--chains"})
        public String chain = "ALL";

        @Parameter(description = "Only output clones where CDR3 (not whole clonal sequence) exactly equals to given sequence",
                names = {"-e", "--cdr3-equals"})
        public String cdr3Equals = null;

        @Parameter(description = "Only output clones where target clonal sequence contains sub-sequence.",
                names = {"-r", "--clonal-sequence-contains"})
        public String csContain = null;

        public Chains getChain() {
            return Chains.parse(chain);
        }

        public Filter<Clone> getFilter() {
            final Chains chains = getChain();

            List<Filter<Clone>> filters = new ArrayList<>();

            filters.add(new Filter<Clone>() {
                @Override
                public boolean accept(Clone object) {
                    for (GeneType gt : GeneType.VJC_REFERENCE) {
                        VDJCHit bestHit = object.getBestHit(gt);
                        if (bestHit != null && chains.intersects(bestHit.getGene().getChains()))
                            return true;
                    }
                    return false;
                }
            });

            if (csContain != null) {
                csContain = csContain.toUpperCase();
                filters.add(new Filter<Clone>() {
                    @Override
                    public boolean accept(Clone object) {
                        for (int i = 0; i < object.numberOfTargets(); i++)
                            if (object.getTarget(i).getSequence().toString().contains(csContain))
                                return true;
                        return false;
                    }
                });
            }

            if (cdr3Equals != null) {
                final NucleotideSequence seq = new NucleotideSequence(cdr3Equals);
                filters.add(new Filter<Clone>() {
                    @Override
                    public boolean accept(Clone object) {
                        NSequenceWithQuality feature = object.getFeature(GeneFeature.CDR3);
                        return feature != null && feature.getSequence().equals(seq);
                    }
                });
            }

            if (filters.isEmpty())
                return ACCEPT_ALL;

            return and(filters.toArray(new Filter[filters.size()]));
        }

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
}
