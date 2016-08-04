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
import cc.redberry.primitives.Filter;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.alignment.AlignmentHelper;
import com.milaboratory.core.alignment.MultiAlignmentHelper;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.cli.Action;
import com.milaboratory.cli.ActionHelper;
import com.milaboratory.cli.ActionParameters;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.mixcr.cli.afiltering.AFilter;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import com.milaboratory.mixcr.reference.LociLibraryManager;
import com.milaboratory.mixcr.reference.Locus;
import com.milaboratory.util.NSequenceWithQualityPrintHelper;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static cc.redberry.primitives.FilterUtil.ACCEPT_ALL;
import static cc.redberry.primitives.FilterUtil.and;

public class ActionPrettyAlignments implements Action {
    public static final int LINE_LENGTH = 80;
    public static final int LINE_OFFSET = 7;
    public static final int MAX_LENGTH = 2 * LINE_OFFSET + LINE_LENGTH;
    final AParameters actionParameters = new AParameters();

    @Override
    public void go(ActionHelper helper) throws Exception {
        Filter<VDJCAlignments> filter = actionParameters.getFilter();
        long total = 0, filtered = 0;
        try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(actionParameters.getInputFileName(),
                LociLibraryManager.getDefault());
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
                    ++filtered;

                    if (--skipAfter >= 0)
                        continue;

                    if (--countAfter < 0)
                        break;

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
        output.println(">>> Read id: " + alignments.getReadId());
        output.println();
        final String[] descriptions = alignments.getDescriptions();
        for (int i = 0; i < alignments.numberOfTargets(); i++) {
            if (actionParameters.printDescriptions() && descriptions != null)
                output.println(">>> Description: " + descriptions[i] + "\n");

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
        output.println(">>> Read id: " + alignments.getReadId());
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
                            if (actionParameters.isAlleleSequence()) {
                                output.println("Allele sequence:");
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

        @Parameter(description = "Output only top",
                names = {"-t", "--top"})
        public Boolean onlyTop = null;

        @Parameter(description = "Output full allele sequence",
                names = {"-a", "--allele"})
        public Boolean alleleSequence = null;

        @Parameter(description = "Limit number of alignments before filtering",
                names = {"-b", "--limitBefore"})
        public Integer limitBefore = null;

        @Parameter(description = "Limit number of filtered alignments; no more " +
                "than N alignments will be outputted",
                names = {"-n", "--limit"})
        public Integer limitAfter = null;

        @Parameter(description = "Filter export to specific loci (e.g. TRA or IGH).",
                names = {"-l", "--filter-locus"})
        public String loci = "ALL";

        @Parameter(description = "Number of output alignments to skip",
                names = {"-s", "--skip"})
        public Integer skipAfter = null;

        @Parameter(description = "Only output alignments where CDR3 contains given substring",
                names = {"-c", "--cdr3-contains"})
        public String cdr3Contains = null;

        @Parameter(description = "Only output alignments where CDR3 exactly equals to given sequence",
                names = {"-e", "--cdr3-equals"})
        public String cdr3Equals = null;

        @Parameter(description = "Only output alignments which contains corresponding gene feature",
                names = {"-g", "--feature"})
        public String feature = null;

        @Parameter(description = "Only output alignments where target read contains given substring",
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

        public Set<Locus> getLoci() {
            return Util.parseLoci(loci);
        }

        public Filter<VDJCAlignments> getFilter() {
            final Set<Locus> loci = getLoci();

            List<Filter<VDJCAlignments>> filters = new ArrayList<>();
            if (filter != null)
                filters.add(AFilter.build(filter));

            filters.add(new Filter<VDJCAlignments>() {
                @Override
                public boolean accept(VDJCAlignments object) {
                    for (GeneType gt : GeneType.VJC_REFERENCE) {
                        VDJCHit bestHit = object.getBestHit(gt);
                        if (bestHit != null && loci.contains(bestHit.getGene().getLocus()))
                            return true;
                    }
                    return false;
                }
            });

            if (cdr3Contains != null)
                filters.add(new Filter<VDJCAlignments>() {
                    @Override
                    public boolean accept(VDJCAlignments object) {
                        NSequenceWithQuality feature = object.getFeature(GeneFeature.CDR3);
                        return feature != null && feature.getSequence().toString().contains(cdr3Contains);
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

        public boolean isAlleleSequence() {
            return alleleSequence == null ? false : alleleSequence;
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
