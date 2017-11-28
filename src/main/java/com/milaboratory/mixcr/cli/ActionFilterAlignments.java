package com.milaboratory.mixcr.cli;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.util.CountLimitingOutputPort;
import cc.redberry.primitives.Filter;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.converters.LongConverter;
import com.beust.jcommander.validators.PositiveInteger;
import com.milaboratory.cli.Action;
import com.milaboratory.cli.ActionHelper;
import com.milaboratory.cli.ActionParameters;
import com.milaboratory.cli.ActionParametersWithOutput;
import com.milaboratory.core.io.sequence.SequenceRead;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Bolotin
 * @author Stanislav Poslavsky
 */
public final class ActionFilterAlignments implements Action {
    public final FilterAlignmentsFilterParameters parameters = new FilterAlignmentsFilterParameters();

    @Override
    public void go(ActionHelper helper) throws Exception {
        try (VDJCAlignmentsReader reader = parameters.getInput();
             VDJCAlignmentsWriter writer = parameters.getOutput()) {
            CanReportProgress progress = reader;
            OutputPort<VDJCAlignments> sReads = reader;
            if (parameters.limit != 0) {
                sReads = new CountLimitingOutputPort<>(sReads, parameters.limit);
                progress = SmartProgressReporter.extractProgress((CountLimitingOutputPort<?>) sReads);
            }
            writer.header(reader.getParameters(), reader.getUsedGenes());
            SmartProgressReporter.startProgressReport("Filtering", progress);
            int total = 0, passed = 0;
            final AlignmentsFilter filter = parameters.getFilter();
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

    @Override
    public String command() {
        return "filterAlignments";
    }

    @Override
    public ActionParameters params() {
        return parameters;
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

    @Parameters(commandDescription = "Filter alignments.")
    public static final class FilterAlignmentsFilterParameters extends ActionParametersWithOutput {
        @Parameter(description = "input_file.vdjca output_file.vdjca", variableArity = true)
        public List<String> parameters = new ArrayList<>();

        @Parameter(description = "Specifies immunological protein chain gene for an alignment. If many, " +
                "separated by ','. Available genes: IGH, IGL, IGK, TRA, TRB, TRG, TRD.",
                names = {"-c", "--chains"})
        public String chains = "ALL";

        @Parameter(description = "Include only those alignments that contain specified feature.",
                names = {"-g", "--contains-feature"})
        public String containsFeature = null;

        @Parameter(description = "Include only those alignments which CDR3 equals to a specified sequence.",
                names = {"-e", "--cdr3-equals"})
        public String cdr3Equals = null;

        @Parameter(description = "Output only chimeric alignments.",
                names = {"-x", "--chimeras-only"})
        public Boolean chimerasOnly = null;

        @Parameter(description = "Maximal number of reads to process",
                names = {"-n", "--limit"}, validateWith = PositiveInteger.class)
        public long limit = 0;

        @Parameter(description = "List of read ids to export",
                names = {"-i", "--read-ids"}, converter = LongConverter.class)
        public List<Long> ids = new ArrayList<>();

        TLongHashSet getReadIds() {
            if (ids.isEmpty())
                return null;
            return new TLongHashSet(ids);
        }

        @Override
        protected List<String> getOutputFiles() {
            return Collections.singletonList(parameters.get(1));
        }

        public Chains getChains() {
            return Chains.parse(chains);
        }

        public VDJCAlignmentsReader getInput() throws IOException {
            return new VDJCAlignmentsReader(parameters.get(0));
        }

        public VDJCAlignmentsWriter getOutput() throws IOException {
            return new VDJCAlignmentsWriter(parameters.get(1));
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
                    getReadIds(), chimerasOnly == null ? false : chimerasOnly);
        }
    }
}
