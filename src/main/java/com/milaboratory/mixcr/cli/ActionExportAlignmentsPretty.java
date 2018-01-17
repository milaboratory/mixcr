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
import cc.redberry.pipe.OutputPort;
import cc.redberry.primitives.Filter;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.converters.LongConverter;
import com.milaboratory.cli.Action;
import com.milaboratory.cli.ActionHelper;
import com.milaboratory.cli.ActionParameters;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.alignment.AlignmentHelper;
import com.milaboratory.core.alignment.MultiAlignmentHelper;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.mixcr.cli.afiltering.AFilter;
import com.milaboratory.util.NSequenceWithQualityPrintHelper;
import gnu.trove.set.hash.TLongHashSet;
import io.repseq.core.Chains;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static cc.redberry.primitives.FilterUtil.ACCEPT_ALL;
import static cc.redberry.primitives.FilterUtil.and;

public class ActionExportAlignmentsPretty implements Action {
    public static final int LINE_LENGTH = 80;
    public static final int LINE_OFFSET = 7;
    public static final int MAX_LENGTH = 2 * LINE_OFFSET + LINE_LENGTH;
    final AParameters actionParameters = new AParameters();

    @Override
    public void go(ActionHelper helper) throws Exception {
        Filter<VDJCAlignments> filter = actionParameters.getFilter();
        long total = 0, filtered = 0;
        try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(actionParameters.getInputFileName());
             PrintStream output = actionParameters.getOutputFileName().equals("-") ? System.out :
                     new PrintStream(new BufferedOutputStream(new FileOutputStream(actionParameters.getOutputFileName()), 32768))
        ) {
            long countBefore = actionParameters.limitBefore == null ? Long.MAX_VALUE : actionParameters.limitBefore;
            long countAfter = actionParameters.limitAfter == null ? Long.MAX_VALUE : actionParameters.limitAfter;
            long skipAfter = actionParameters.skipAfter == null ? 0 : actionParameters.skipAfter;
            for (final VDJCAlignments alignments : CUtils.it(reader)) {
                if (--countBefore < 0)
                    break;
                ++total;
                if (filter.accept(alignments)) {
                    if (--skipAfter >= 0)
                        continue;

                    if (--countAfter < 0)
                        break;

                    ++filtered;

                    if (actionParameters.isVerbose())
                        outputVerbose(output, alignments);
                    else
                        outputCompact(output, alignments);
                }
            }
            output.println("Filtered: " + filtered + " / " + total + " = " + (100.0 * filtered / total) + "%");
        }
    }

    public void outputCompact(PrintStream output, final VDJCAlignments alignments) {
        output.println(">>> Read ids: " + Arrays.toString(alignments.getReadIds())
                .replace("[", "")
                .replace("]", ""));
        output.println();
        for (int i = 0; i < alignments.numberOfTargets(); i++) {
            if (actionParameters.printDescriptions()) {
                output.println(">>> Assembly history: " + alignments.getHistory(i) + "\n");
            }

            MultiAlignmentHelper targetAsMultiAlignment = VDJCAlignmentsFormatter.getTargetAsMultiAlignment(alignments, i);
            if (targetAsMultiAlignment == null)
                continue;
            MultiAlignmentHelper[] split = targetAsMultiAlignment.split(80);
            for (MultiAlignmentHelper spl : split) {
                output.println(spl);
                output.println();
            }
        }
    }

    public void outputVerbose(PrintStream output, final VDJCAlignments alignments) {
        output.println(">>> Read ids: " + Arrays.toString(alignments.getReadIds())
                .replace("[", "")
                .replace("]", ""));
        output.println();
        output.println(">>> Target sequences (input sequences):");
        output.println();
        for (int i = 0; i < alignments.numberOfTargets(); i++) {
            output.println("Sequence" + i + ":");

            final VDJCPartitionedSequence partitionedTarget = alignments.getPartitionedTarget(i);

            printGeneFeatures(new Filter<GeneFeature>() {
                @Override
                public boolean accept(GeneFeature object) {
                    return partitionedTarget.getPartitioning().isAvailable(object);
                }
            }, output, "Contains features: ");

            output.println();

            output.print(new NSequenceWithQualityPrintHelper(alignments.getTarget(i), LINE_OFFSET, LINE_LENGTH));
        }

        if (alignments.numberOfTargets() > 1) {

            // Printing a set of available gene features for a full read

            output.println(">>> Gene features that can be extracted from this paired-read: ");

            printGeneFeatures(new Filter<GeneFeature>() {
                @Override
                public boolean accept(GeneFeature object) {
                    return alignments.getFeature(object) != null;
                }
            }, output, "");
        }

        output.println();
        for (GeneType geneType : GeneType.values()) {
            output.println(">>> Alignments with " + geneType.getLetter() + " gene:");
            output.println();
            boolean exists = false;
            VDJCHit[] hits = alignments.getHits(geneType);
            if (hits.length > 0) {
                hits = actionParameters.isOnlyTop() ? new VDJCHit[]{hits[0]} : hits;
                for (VDJCHit hit : hits) {
                    exists = true;
                    output.println(hit.getGene().getName() + " (total score = " + hit.getScore() + ")");
                    for (int i = 0; i < alignments.numberOfTargets(); i++) {
                        Alignment<NucleotideSequence> alignment = hit.getAlignment(i);

                        if (alignment == null)
                            continue;

                        output.println("Alignment of Sequence" + i + " (score = " + (alignment == null ? "NaN" : alignment.getScore()) + "):");
                        if (alignment != null) {
                            for (AlignmentHelper subHelper : alignment.getAlignmentHelper().split(LINE_LENGTH, LINE_OFFSET)) {
                                output.println(subHelper.toStringWithSeq2Quality(alignments.getTarget(i).getQuality()));
                                output.println();
                            }
                            if (actionParameters.printGeneSequence()) {
                                output.println("Gene sequence:");
                                output.println(alignment.getSequence1());
                                output.println();
                            }
                        }
                    }
                }
            }
            if (!exists) {
                output.println("No hits.");
                output.println();
            }
        }
        char[] ll = new char[94];
        Arrays.fill(ll, '=');
        output.println(ll);
        output.println();
    }

    public void printGeneFeatures(Filter<GeneFeature> containsFilter, PrintStream output, String prefix) {
        output.print(prefix);
        int totalLength = prefix.length();
        boolean first = true;
        for (GeneFeature geneFeature : GeneFeature.getNameByFeature().keySet()) {
            if (!containsFilter.accept(geneFeature))
                continue;
            if (first) first = false;
            else output.print(", ");
            String name = GeneFeature.getNameByFeature(geneFeature);
            if (totalLength + name.length() + 2 >= MAX_LENGTH) {
                output.println();
                totalLength = 0;
            }
            output.print(name);
            totalLength += name.length() + 2;
        }
        output.println();
    }

    @Override
    public String command() {
        return "exportAlignmentsPretty";
    }

    @Override
    public AParameters params() {
        return actionParameters;
    }

    @Parameters(commandDescription = "Export full alignments.")
    public static final class AParameters extends ActionParameters {
        @Parameter(description = "input_file.vdjca [output.txt]", variableArity = true)
        public List<String> parameters = new ArrayList<>();

        @Parameter(description = "Output only top number of clones",
                names = {"-t", "--top"})
        public Boolean onlyTop = null;

        @Parameter(description = "Output full gene sequence",
                names = {"-a", "--gene"})
        public Boolean geneSequence = null;

        @Parameter(description = "Limit number of alignments before filtering",
                names = {"-b", "--limit-before"})
        public Integer limitBefore = null;

        @Parameter(description = "Limit number of filtered alignments; no more " +
                "than N alignments will be outputted",
                names = {"-n", "--limit"})
        public Integer limitAfter = null;

        @Parameter(description = "Filter export to a specific protein chain gene (e.g. TRA or IGH).",
                names = {"-c", "--chains"})
        public String chain = "ALL";

        @Parameter(description = "Number of output alignments to skip",
                names = {"-s", "--skip"})
        public Integer skipAfter = null;

        @Parameter(description = "Only output alignments where CDR3 exactly equals to given sequence",
                names = {"-e", "--cdr3-equals"})
        public String cdr3Equals = null;

        @Parameter(description = "Only output alignments which contain a corresponding gene feature",
                names = {"-g", "--feature"})
        public String feature = null;

        @Parameter(description = "Only output alignments where target read contains a given substring",
                names = {"-r", "--read-contains"})
        public String readContains = null;

        @Parameter(description = "Custom filter",
                names = {"-f", "--filter"})
        public String filter = null;

        @Parameter(description = "Verbose output (old)",
                names = {"-v", "--verbose"})
        public Boolean verbose = null;

        @Parameter(description = "Print descriptions",
                names = {"-d", "--descriptions"})
        public Boolean descr = null;

        @Parameter(description = "List of read ids to export",
                names = {"-i", "--read-ids"},
                converter = LongConverter.class)
        public List<Long> ids = new ArrayList<>();

        TLongHashSet getReadIds() {
            if (ids.isEmpty())
                return null;
            return new TLongHashSet(ids);
        }

        public Chains getChain() {
            return Chains.parse(chain);
        }

        @SuppressWarnings("unchecked")
        public Filter<VDJCAlignments> getFilter() {
            final Chains chains = getChain();

            List<Filter<VDJCAlignments>> filters = new ArrayList<>();
            if (filter != null)
                filters.add(AFilter.build(filter));

            filters.add(new Filter<VDJCAlignments>() {
                @Override
                public boolean accept(VDJCAlignments object) {
                    for (GeneType gt : GeneType.VJC_REFERENCE) {
                        VDJCHit bestHit = object.getBestHit(gt);
                        if (bestHit != null && chains.intersects(bestHit.getGene().getChains()))
                            return true;
                    }
                    return false;
                }
            });

            final TLongHashSet readIds = getReadIds();
            if (readIds != null)
                filters.add(new Filter<VDJCAlignments>() {
                    @Override
                    public boolean accept(VDJCAlignments object) {
                        for (long id : object.getReadIds())
                            if (readIds.contains(id))
                                return true;
                        return false;
                    }
                });

            if (feature != null) {
                final GeneFeature feature = GeneFeature.parse(this.feature);
                filters.add(new Filter<VDJCAlignments>() {
                    @Override
                    public boolean accept(VDJCAlignments object) {
                        NSequenceWithQuality f = object.getFeature(feature);
                        return f != null && f.size() > 0;
                    }
                });
            }

            if (readContains != null)
                filters.add(new Filter<VDJCAlignments>() {
                    @Override
                    public boolean accept(VDJCAlignments object) {
                        for (int i = 0; i < object.numberOfTargets(); i++)
                            if (object.getTarget(i).getSequence().toString().contains(readContains))
                                return true;
                        return false;
                    }
                });

            if (cdr3Equals != null) {
                final NucleotideSequence seq = new NucleotideSequence(cdr3Equals);
                filters.add(new Filter<VDJCAlignments>() {
                    @Override
                    public boolean accept(VDJCAlignments object) {
                        NSequenceWithQuality feature = object.getFeature(GeneFeature.CDR3);
                        return feature != null && feature.getSequence().equals(seq);
                    }
                });
            }

            if (filters.isEmpty())
                return ACCEPT_ALL;

            return and(filters.toArray(new Filter[filters.size()]));
        }

        public boolean printDescriptions() {
            return descr != null && descr;
        }

        public boolean isVerbose() {
            return verbose != null && verbose;
        }

        public boolean isOnlyTop() {
            return onlyTop == null ? false : onlyTop;
        }

        public boolean printGeneSequence() {
            return geneSequence == null ? false : geneSequence;
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
