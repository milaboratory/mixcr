package com.milaboratory.mixcr.cli;

import cc.redberry.pipe.CUtils;
import cc.redberry.primitives.Filter;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.validators.PositiveInteger;
import com.milaboratory.cli.Action;
import com.milaboratory.cli.ActionHelper;
import com.milaboratory.cli.ActionParameters;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.reference.GeneFeature;
import com.milaboratory.mixcr.reference.GeneType;
import com.milaboratory.mixcr.reference.LociLibraryManager;
import com.milaboratory.mixcr.reference.Locus;
import com.milaboratory.util.SmartProgressReporter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

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
            writer.header(reader.getParameters(), reader.getUsedAlleles());
            SmartProgressReporter.startProgressReport("Filtering", reader);
            int total = 0, passed = 0;
            final AlignmentsFilter filter = parameters.getFilter();
            for (VDJCAlignments al : CUtils.it(CUtils.buffered(reader, 2048))) {
                ++total;
                if (filter.accept(al)) {
                    writer.write(al);
                    ++passed;
                }
            }
            writer.setNumberOfProcessedReads(reader.getNumberOfReads());
            System.out.printf("Written %s alignments (%s alignments considered in total)\n", passed, total);
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
        final NucleotideSequence cdr3Contains;
        final NucleotideSequence cdr3Equals;
        final Set<Locus> locus;

        public AlignmentsFilter(GeneFeature containsFeature, NucleotideSequence cdr3Contains, NucleotideSequence cdr3Equals, Set<Locus> locus) {
            this.containsFeature = containsFeature;
            this.cdr3Contains = cdr3Contains;
            this.cdr3Equals = cdr3Equals;
            this.locus = locus;
        }

        @Override
        public boolean accept(VDJCAlignments object) {
            if (object == null)
                return true;

            boolean lMatch = false;
            for (GeneType gt : GeneType.VDJC_REFERENCE) {
                final VDJCHit hit = object.getBestHit(gt);
                if (hit != null && locus.contains(hit.getAllele().getLocus())) {
                    lMatch = true;
                    break;
                }
            }
            if (!lMatch)
                return false;

            if (containsFeature != null && object.getFeature(containsFeature) == null)
                return false;

            if (cdr3Contains != null || cdr3Equals != null) {
                final NSequenceWithQuality cdr3 = object.getFeature(GeneFeature.CDR3);
                if (cdr3 == null)
                    return false;
                if (cdr3Equals != null && !cdr3.getSequence().equals(cdr3Equals))
                    return false;

                if (cdr3Contains != null && cdr3.getSequence().indexOf(cdr3Contains) == -1)
                    return false;

            }
            return true;
        }
    }

    @Parameters(commandDescription = "Filter alignments.",
            optionPrefixes = "-")
    public static final class FilterAlignmentsFilterParameters extends ActionParametersWithOutput {
        @Parameter(description = "input_file.vdjca output_file.vdjca", variableArity = true)
        public List<String> parameters = new ArrayList<>();

        @Parameter(description = "Immunological loci to align with separated by ','. Available loci: IGH, IGL, IGK, TRA, TRB, TRG, TRD.",
                names = {"-l", "--loci"})
        public String loci = "all";

        @Parameter(description = "Include alignments that contain specified feature.",
                names = {"-g", "--contains-feature"})
        public String containsFeature = null;

        @Parameter(description = "Include alignments which CDR3 contains specified sequence.",
                names = {"-c", "--cdr3-contains"})
        public String cdr3Contains = null;

        @Parameter(description = "Include alignments which CDR3 equals to specified sequence.",
                names = {"-e", "--cdr3-equals"})
        public String cdr3Equals = null;

        @Parameter(description = "Maximal number of reads to process",
                names = {"-n", "--limit"}, validateWith = PositiveInteger.class)
        public long limit = 0;

        @Override
        protected List<String> getOutputFiles() {
            return Collections.singletonList(parameters.get(1));
        }

        public Set<Locus> getLoci() {
            return Util.parseLoci(loci);
        }

        public VDJCAlignmentsReader getInput() throws IOException {
            return new VDJCAlignmentsReader(parameters.get(0), LociLibraryManager.getDefault());
        }

        public VDJCAlignmentsWriter getOutput() throws IOException {
            return new VDJCAlignmentsWriter(parameters.get(1));
        }

        public GeneFeature getContainFeature() {
            if (containsFeature == null)
                return null;
            return GeneFeature.parse(containsFeature);
        }

        public NucleotideSequence getCdr3Contains() {
            if (cdr3Contains == null)
                return null;
            return new NucleotideSequence(cdr3Contains);
        }

        public NucleotideSequence getCdr3Equals() {
            if (cdr3Equals == null)
                return null;
            return new NucleotideSequence(cdr3Equals);
        }

        public AlignmentsFilter getFilter() {
            return new AlignmentsFilter(getContainFeature(), getCdr3Contains(), getCdr3Equals(), getLoci());
        }
    }
}
