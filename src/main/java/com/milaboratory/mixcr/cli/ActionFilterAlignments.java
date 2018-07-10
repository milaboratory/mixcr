package com.milaboratory.mixcr.cli;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.util.CountLimitingOutputPort;
import cc.redberry.primitives.Filter;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.converters.LongConverter;
import com.beust.jcommander.validators.PositiveInteger;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.cli.ActionHelper;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.util.CanReportProgress;
import com.milaboratory.util.SmartProgressReporter;
import gnu.trove.set.hash.TLongHashSet;
import io.repseq.core.Chains;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;

import java.io.IOException;
import java.util.*;

/**
 * @author Dmitry Bolotin
 * @author Stanislav Poslavsky
 */
public final class ActionFilterAlignments extends AbstractActionWithResumeOption {
    public final FilterAlignmentsParameters parameters = new FilterAlignmentsParameters();

    @Override
    public void go0(ActionHelper helper) throws Exception {
        try (VDJCAlignmentsReader reader = parameters.getInput();
             VDJCAlignmentsWriter writer = parameters.getOutput()) {
            CanReportProgress progress = reader;
            OutputPort<VDJCAlignments> sReads = reader;
            if (parameters.limit != 0) {
                sReads = new CountLimitingOutputPort<>(sReads, parameters.limit);
                progress = SmartProgressReporter.extractProgress((CountLimitingOutputPort<?>) sReads);
            }
            writer.header(reader.getParameters(), reader.getUsedGenes(), null);
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
    public FilterAlignmentsParameters params() {
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

    public static class FilterConfiguration implements ActionConfiguration {
        public final Chains chains;
        public final boolean chimerasOnly;
        public final long limit;
        public final long[] ids;
        public final GeneFeature containFeature;
        public final NucleotideSequence cdr3Equals;

        @JsonCreator
        public FilterConfiguration(@JsonProperty("chains") Chains chains,
                                   @JsonProperty("chimerasOnly") boolean chimerasOnly,
                                   @JsonProperty("limit") long limit,
                                   @JsonProperty("ids") long[] ids,
                                   @JsonProperty("containFeature") GeneFeature containFeature,
                                   @JsonProperty("cdr3Equals") NucleotideSequence cdr3Equals) {
            this.chains = chains;
            this.chimerasOnly = chimerasOnly;
            this.limit = limit;
            this.ids = ids;
            this.containFeature = containFeature;
            this.cdr3Equals = cdr3Equals;
        }

        @Override
        public String actionName() {
            return "filter";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FilterConfiguration that = (FilterConfiguration) o;
            return chimerasOnly == that.chimerasOnly &&
                    limit == that.limit &&
                    Objects.equals(chains, that.chains) &&
                    Arrays.equals(ids, that.ids) &&
                    Objects.equals(containFeature, that.containFeature) &&
                    Objects.equals(cdr3Equals, that.cdr3Equals);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(chains, chimerasOnly, limit, containFeature, cdr3Equals);
            result = 31 * result + Arrays.hashCode(ids);
            return result;
        }
    }

    @Parameters(commandDescription = "Filter alignments.")
    public static final class FilterAlignmentsParameters extends ActionParametersWithResumeOption.ActionParametersWithResumeWithBinaryInput {
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
        public boolean chimerasOnly = false;

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
                    getReadIds(), chimerasOnly);
        }

        @Override
        public List<String> getInputFiles() {
            return parameters.subList(0, 1);
        }

        @Override
        public ActionConfiguration getConfiguration() {
            return new FilterConfiguration(getChains(),
                    chimerasOnly,
                    limit, getReadIds().toArray(), getContainFeature(), getCdr3Equals());
        }
    }
}
