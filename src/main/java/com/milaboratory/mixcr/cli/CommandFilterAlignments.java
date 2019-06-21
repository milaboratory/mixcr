/*
 * Copyright (c) 2014-2019, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.util.CountLimitingOutputPort;
import cc.redberry.primitives.Filter;
import com.fasterxml.jackson.annotation.*;
import com.milaboratory.cli.ActionConfiguration;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.util.CanReportProgress;
import com.milaboratory.util.SmartProgressReporter;
import gnu.trove.set.hash.TLongHashSet;
import io.repseq.core.Chains;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.milaboratory.mixcr.cli.CommandFilterAlignments.FILTER_ALIGNMENTS_COMMAND_NAME;

@Command(name = FILTER_ALIGNMENTS_COMMAND_NAME,
        sortOptions = true,
        separator = " ",
        description = "Filter alignments.")
public class CommandFilterAlignments extends ACommandWithSmartOverwriteWithSingleInputMiXCR {
    static final String FILTER_ALIGNMENTS_COMMAND_NAME = "filterAlignments";

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
    public ActionConfiguration getConfiguration() {
        return new FilterConfiguration(getChains(),
                chimerasOnly,
                limit, getReadIds() == null ? null : getReadIds().toArray(), getContainFeature(), getCdr3Equals());
    }

    @Override
    public void run1() throws Exception {
        try (VDJCAlignmentsReader reader = getInputReader();
             VDJCAlignmentsWriter writer = getOutputWriter()) {
            CanReportProgress progress = reader;
            OutputPort<VDJCAlignments> sReads = reader;
            if (limit != 0) {
                sReads = new CountLimitingOutputPort<>(sReads, limit);
                progress = SmartProgressReporter.extractProgress((CountLimitingOutputPort<?>) sReads);
            }
            writer.header(reader.getParameters(), reader.getUsedGenes(), getFullPipelineConfiguration());
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

    @JsonAutoDetect(
            fieldVisibility = JsonAutoDetect.Visibility.ANY,
            isGetterVisibility = JsonAutoDetect.Visibility.NONE,
            getterVisibility = JsonAutoDetect.Visibility.NONE)
    @JsonTypeInfo(
            use = JsonTypeInfo.Id.CLASS,
            include = JsonTypeInfo.As.PROPERTY,
            property = "type")
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
            return FILTER_ALIGNMENTS_COMMAND_NAME;
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
}
