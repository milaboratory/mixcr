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
import cc.redberry.pipe.util.CountLimitingOutputPort;
import cc.redberry.primitives.Filter;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.util.CanReportProgress;
import com.milaboratory.util.SmartProgressReporter;
import gnu.trove.set.hash.TLongHashSet;
import io.repseq.core.Chains;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.milaboratory.mixcr.cli.CommandFilterAlignments.FILTER_ALIGNMENTS_COMMAND_NAME;

@Command(name = FILTER_ALIGNMENTS_COMMAND_NAME,
        sortOptions = true,
        separator = " ",
        description = "Filter alignments.")
public class CommandFilterAlignments extends MiXCRCommand {
    static final String FILTER_ALIGNMENTS_COMMAND_NAME = "filterAlignments";

    @Parameters(description = "alignments.vdjca", index = "0")
    public String in;

    @Parameters(description = "alignments.filtered.vdjca", index = "1")
    public String out;

    @Option(description = "Specifies immunological protein chain gene for an alignment. If many, " +
            "separated by ','. Available genes: IGH, IGL, IGK, TRA, TRB, TRG, TRD.",
            names = {"-c", "--chains"})
    public String chains = "ALL";

    @Option(description = "Include only those alignments that contain specified feature.",
            names = {"-g", "--contains-feature"})
    public String containsFeature = null;

    @Option(description = "Include only those alignments which CDR3 equals to a specified sequence.",
            names = {"-e", "--cdr3-equals"})
    public String cdr3Equals = null;

    @Option(description = "Output only chimeric alignments.",
            names = {"-x", "--chimeras-only"})
    public boolean chimerasOnly = false;

    public long limit = 0;

    @Option(description = "Maximal number of reads to process",
            names = {"-n", "--limit"})
    public void setLimit(long limit) {
        if (limit <= 0)
            throwValidationException("-n / --limit must be positive.");
        this.limit = limit;
    }

    @Option(description = "List of read ids to export",
            names = {"-i", "--read-ids"})
    public List<Long> ids = new ArrayList<>();

    @Override
    protected List<String> getInputFiles() {
        return Collections.singletonList(in);
    }

    @Override
    protected List<String> getOutputFiles() {
        return Collections.singletonList(out);
    }

    TLongHashSet getReadIds() {
        if (ids.isEmpty())
            return null;
        return new TLongHashSet(ids);
    }

    public Chains getChains() {
        return Chains.parse(chains);
    }

    public VDJCAlignmentsReader getInputReader() throws IOException {
        return new VDJCAlignmentsReader(in);
    }

    public VDJCAlignmentsWriter getOutputWriter() throws IOException {
        return new VDJCAlignmentsWriter(out);
    }

    public GeneFeature getContainFeature() {
        if (containsFeature == null)
            return null;
        return GeneFeature.parse(containsFeature);
    }

    public NucleotideSequence getCdr3Equals() {
        if (cdr3Equals == null)
            return null;
        return new NucleotideSequence(cdr3Equals);
    }

    public AlignmentsFilter getFilter() {
        return new AlignmentsFilter(getContainFeature(), getCdr3Equals(), getChains(),
                getReadIds(), chimerasOnly);
    }

    @Override
    public void run0() throws Exception {
        try (VDJCAlignmentsReader reader = getInputReader();
             VDJCAlignmentsWriter writer = getOutputWriter()) {
            CanReportProgress progress = reader;
            OutputPort<VDJCAlignments> sReads = reader;
            if (limit != 0) {
                sReads = new CountLimitingOutputPort<>(sReads, limit);
                progress = SmartProgressReporter.extractProgress((CountLimitingOutputPort<?>) sReads);
            }
            writer.header(reader.getParameters(), reader.getUsedGenes(), reader.getTagsInfo());
            SmartProgressReporter.startProgressReport("Filtering", progress);
            int total = 0, passed = 0;
            final AlignmentsFilter filter = getFilter();
            for (VDJCAlignments al : CUtils.it(CUtils.buffered(sReads, 2048))) {
                ++total;
                if (filter.accept(al)) {
                    writer.write(al);
                    ++passed;
                }
            }
            writer.setNumberOfProcessedReads(reader.getNumberOfReads());
            System.out.printf("%s alignments analysed\n", total);
            System.out.printf("%s alignments written (%.1f%%)\n", passed, 100.0 * passed / total);
        }
    }

    public static final class AlignmentsFilter implements Filter<VDJCAlignments> {
        final GeneFeature containsFeature;
        final NucleotideSequence cdr3Equals;
        final Chains chains;
        final TLongHashSet readsIds;
        final boolean chimerasOnly;

        public AlignmentsFilter(GeneFeature containsFeature, NucleotideSequence cdr3Equals, Chains chains, TLongHashSet readsIds, boolean chimerasOnly) {
            this.containsFeature = containsFeature;
            this.cdr3Equals = cdr3Equals;
            this.chains = chains;
            this.readsIds = readsIds;
            this.chimerasOnly = chimerasOnly;
        }

        static boolean containsAny(TLongHashSet set, long[] r) {
            for (long l : r)
                if (set.contains(l))
                    return true;
            return false;
        }

        @Override
        public boolean accept(VDJCAlignments object) {
            if (object == null)
                return true;

            if (readsIds != null && !containsAny(readsIds, object.getReadIds()))
                return false;

            boolean lMatch = false;
            for (GeneType gt : GeneType.VDJC_REFERENCE) {
                final VDJCHit hit = object.getBestHit(gt);
                if (hit != null && chains.intersects(hit.getGene().getChains())) {
                    lMatch = true;
                    break;
                }
            }
            if (!lMatch)
                return false;

            if (containsFeature != null && object.getFeature(containsFeature) == null)
                return false;

            if (cdr3Equals != null) {
                final NSequenceWithQuality cdr3 = object.getFeature(GeneFeature.CDR3);
                if (cdr3 == null)
                    return false;
                if (!cdr3.getSequence().equals(cdr3Equals))
                    return false;
            }

            if (chimerasOnly)
                return object.isChimera();

            return true;
        }
    }
}
