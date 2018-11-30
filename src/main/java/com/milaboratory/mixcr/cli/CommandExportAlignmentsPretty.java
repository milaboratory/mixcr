package com.milaboratory.mixcr.cli;

import cc.redberry.pipe.CUtils;
import cc.redberry.primitives.Filter;
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
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static cc.redberry.primitives.FilterUtil.ACCEPT_ALL;
import static cc.redberry.primitives.FilterUtil.and;

@Command(name = "exportAlignmentsPretty",
        sortOptions = true,
        separator = " ",
        description = "Export verbose information about alignments.")
public class CommandExportAlignmentsPretty extends ACommandSimpleExportMiXCR {
    @Option(description = "Output only top hits",
            names = {"-t", "--top"})
    public boolean onlyTop = false;

    @Option(description = "Output full gene sequence",
            names = {"-a", "--gene"})
    public boolean geneSequence = false;

    @Option(description = "Limit number of alignments before filtering",
            names = {"-b", "--limit-before"})
    public Integer limitBefore = null;

    @Option(description = "Limit number of filtered alignments; no more " +
            "than N alignments will be outputted",
            names = {"-n", "--limit"})
    public Integer limitAfter = null;

    @Option(description = "Filter export to a specific protein chain gene (e.g. TRA or IGH).",
            names = {"-c", "--chains"})
    public String chain = "ALL";

    @Option(description = "Number of output alignments to skip",
            names = {"-s", "--skip"})
    public Integer skipAfter = null;

    @Option(description = "Output only alignments where CDR3 exactly equals to given sequence",
            names = {"-e", "--cdr3-equals"})
    public String cdr3Equals = null;

    @Option(description = "Output only alignments which contain a corresponding gene feature",
            names = {"-g", "--feature"})
    public String feature = null;

    @Option(description = "Output only alignments where target read contains a given substring",
            names = {"-r", "--read-contains"})
    public String readContains = null;

    @Option(description = "Custom filter",
            names = {"--filter"})
    public String filter = null;

    @Option(description = "Print descriptions",
            names = {"-d", "--descriptions"})
    public boolean printDescriptions = false;

    @Option(description = "List of read ids to export",
            names = {"-i", "--read-ids"})
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
    public Filter<VDJCAlignments> mkFilter() {
        final Chains chains = getChain();

        List<Filter<VDJCAlignments>> filters = new ArrayList<>();
        if (filter != null)
            filters.add(AFilter.build(filter));

        filters.add(object -> {
            for (GeneType gt : GeneType.VJC_REFERENCE) {
                VDJCHit bestHit = object.getBestHit(gt);
                if (bestHit != null && chains.intersects(bestHit.getGene().getChains()))
                    return true;
            }
            return false;
        });

        final TLongHashSet readIds = getReadIds();
        if (readIds != null)
            filters.add(object -> {
                for (long id : object.getReadIds())
                    if (readIds.contains(id))
                        return true;
                return false;
            });

        if (feature != null) {
            final GeneFeature feature = GeneFeature.parse(this.feature);
            filters.add(object -> {
                NSequenceWithQuality f = object.getFeature(feature);
                return f != null && f.size() > 0;
            });
        }

        if (readContains != null)
            filters.add(object -> {
                for (int i = 0; i < object.numberOfTargets(); i++)
                    if (object.getTarget(i).getSequence().toString().contains(readContains))
                        return true;
                return false;
            });

        if (cdr3Equals != null) {
            final NucleotideSequence seq = new NucleotideSequence(cdr3Equals);
            filters.add(object -> {
                NSequenceWithQuality feature = object.getFeature(GeneFeature.CDR3);
                return feature != null && feature.getSequence().equals(seq);
            });
        }

        if (filters.isEmpty())
            return ACCEPT_ALL;

        return and(filters.toArray(new Filter[filters.size()]));
    }


    public static final int LINE_LENGTH = 80;
    public static final int LINE_OFFSET = 7;
    public static final int MAX_LENGTH = 2 * LINE_OFFSET + LINE_LENGTH;

    @Override
    public void run0() throws Exception {
        Filter<VDJCAlignments> filter = mkFilter();
        long total = 0, filtered = 0;
        try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(in);
             PrintStream output = out == null ? System.out :
                     new PrintStream(new BufferedOutputStream(new FileOutputStream(out), 32768))
        ) {
            long countBefore = limitBefore == null ? Long.MAX_VALUE : limitBefore;
            long countAfter = limitAfter == null ? Long.MAX_VALUE : limitAfter;
            long skipAfter = this.skipAfter == null ? 0 : this.skipAfter;
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

                    if (verbose)
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
            if (printDescriptions) {
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
                hits = onlyTop ? new VDJCHit[]{hits[0]} : hits;
                for (VDJCHit hit : hits) {
                    exists = true;
                    output.println(hit.getGene().getName() + " (total score = " + hit.getScore() + ")");
                    for (int i = 0; i < alignments.numberOfTargets(); i++) {
                        Alignment<NucleotideSequence> alignment = hit.getAlignment(i);

                        if (alignment == null)
                            continue;

                        output.println("Alignment of Sequence" + i + " (score = " + alignment.getScore() + "):");
                        for (AlignmentHelper subHelper : alignment.getAlignmentHelper().split(LINE_LENGTH, LINE_OFFSET)) {
                            output.println(subHelper.toStringWithSeq2Quality(alignments.getTarget(i).getQuality()));
                            output.println();
                        }
                        if (geneSequence) {
                            output.println("Gene sequence:");
                            output.println(alignment.getSequence1());
                            output.println();
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
}
