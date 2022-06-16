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

import cc.redberry.primitives.Filter;
import com.milaboratory.core.alignment.MultiAlignmentHelper;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.*;
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
import java.util.List;

import static cc.redberry.primitives.FilterUtil.ACCEPT_ALL;
import static cc.redberry.primitives.FilterUtil.and;

@Command(name = "exportClonesPretty",
        sortOptions = true,
        separator = " ",
        description = "Export verbose information about clones.")
public class CommandExportClonesPretty extends ACommandSimpleExportMiXCR {
    public static final int LINE_LENGTH = 80;
    public static final int LINE_OFFSET = 7;
    public static final int MAX_LENGTH = 2 * LINE_OFFSET + LINE_LENGTH;

    @Option(description = "Limit number of alignments before filtering",
            names = {"-b", "--limitBefore"})
    public Integer limitBefore = null;

    @Option(description = "Limit number of filtered alignments; no more " +
            "than N alignments will be outputted",
            names = {"-n", "--limit"})
    public Integer limitAfter = null;

    @Option(description = "List of clone ids to export",
            names = {"-i", "--clone-ids"})
    public List<Long> ids = new ArrayList<>();

    @Option(description = "Number of output alignments to skip",
            names = {"-s", "--skip"})
    public Integer skipAfter = null;

    @Option(description = "Filter export to a specific protein chain gene (e.g. TRA or IGH).",
            names = {"-c", "--chains"})
    public String chain = "ALL";

    @Option(description = "Only output clones where CDR3 (not whole clonal sequence) exactly equals to given sequence",
            names = {"-e", "--cdr3-equals"})
    public String cdr3Equals = null;

    @Option(description = "Only output clones where target clonal sequence contains sub-sequence.",
            names = {"-r", "--clonal-sequence-contains"})
    public String csContain = null;

    public Chains getChain() {
        return Chains.parse(chain);
    }

    TLongHashSet getCloneIds() {
        if (ids.isEmpty())
            return null;
        return new TLongHashSet(ids);
    }

    @SuppressWarnings("unchecked")
    public Filter<Clone> mkFilter() {
        final Chains chains = getChain();

        List<Filter<Clone>> filters = new ArrayList<>();

        final TLongHashSet cloneIds = getCloneIds();
        if (cloneIds != null)
            filters.add(object -> cloneIds.contains(object.getId()));

        filters.add(object -> {
            for (GeneType gt : GeneType.VJC_REFERENCE) {
                VDJCHit bestHit = object.getBestHit(gt);
                if (bestHit != null && chains.intersects(bestHit.getGene().getChains()))
                    return true;
            }
            return false;
        });

        if (csContain != null) {
            csContain = csContain.toUpperCase();
            filters.add(object -> {
                for (int i = 0; i < object.numberOfTargets(); i++)
                    if (object.getTarget(i).getSequence().toString().contains(csContain))
                        return true;
                return false;
            });
        }

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

    @Override
    public void run0() throws Exception {
        Filter<Clone> filter = mkFilter();
        long total = 0, filtered = 0;
        CloneSet cloneSet = CloneSetIO.read(in);

        try (PrintStream output = out == null ? System.out :
                new PrintStream(new BufferedOutputStream(new FileOutputStream(out), 32768))) {
            long countBefore = limitBefore == null ? Long.MAX_VALUE : limitBefore;
            long countAfter = limitAfter == null ? Long.MAX_VALUE : limitAfter;
            long skipAfter = this.skipAfter == null ? 0 : this.skipAfter;
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

}
