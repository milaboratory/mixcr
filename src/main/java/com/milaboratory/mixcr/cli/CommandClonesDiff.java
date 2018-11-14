package com.milaboratory.mixcr.cli;

import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.CloneSet;
import com.milaboratory.mixcr.basictypes.CloneSetIO;
import com.milaboratory.mixcr.basictypes.IOUtil;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGeneId;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.*;

@Command(name = "clonesDiff",
        separator = " ",
        sortOptions = true,
        description = "Calculates the difference between two .clns files.")
public class CommandClonesDiff extends ACommandWithOutputMiXCR {
    @Parameters(description = "input1.clns")
    public String in1;

    @Parameters(description = "input2.clns")
    public String in2;

    @Parameters(description = "[report]", arity = "0..1")
    public String report = null;

    @Option(names = {"-v"}, description = "Use V gene in clone comparison (include it as a clone key along " +
            "with a clone sequence).")
    public boolean v = false;

    @Option(names = {"-j"}, description = "Use J gene in clone comparison (include it as a clone key along " +
            "with a clone sequence).")
    public boolean j = false;

    @Option(names = {"-c"}, description = "Use C gene in clone comparison (include it as a clone key along " +
            "with a clone sequence).")
    public boolean c = false;

    public boolean useV() { return v; }

    public boolean useJ() { return j; }

    public boolean useC() { return c; }

    //@Parameter(names = {"-o1", "--only-in-first"}, description = "output for alignments contained only " +
    //        "in the first .vdjca file")
    //public String onlyFirst;
    //@Parameter(names = {"-o2", "--only-in-second"}, description = "output for alignments contained only " +
    //        "in the second .vdjca file")
    //public String onlySecond;
    //@Parameter(names = {"-d1", "--diff-from-first"}, description = "output for alignments from the first file " +
    //        "that are different from those alignments in the second file")
    //public String diff1;
    //@Parameter(names = {"-d2", "--diff-from-second"}, description = "output for alignments from the second file " +
    //        "that are different from those alignments in the first file")
    //public String diff2;
    //@Parameter(names = {"-g", "--gene-feature"}, description = "Gene feature to compare")
    //public String geneFeatureToMatch = "CDR3";
    //@Parameter(names = {"-l", "--top-hits-level"}, description = "Number of top hits to search for match")
    //public int hitsCompareLevel = 1;


    @Override
    protected List<String> getOutputFiles() {
        return report == null ? Collections.emptyList() : Collections.singletonList(report);
    }

    @Override
    public void run0() throws Exception {
        try (InputStream is1 = IOUtil.createIS(in1);
             InputStream is2 = IOUtil.createIS(in2);
             PrintStream report = this.report == null ? System.out : new PrintStream(new FileOutputStream(this.report))) {

            CloneSet cs1 = CloneSetIO.readClns(is1);
            CloneSet cs2 = CloneSetIO.readClns(is2);

            HashMap<CKey, CRec> recs = new HashMap<>();

            populate(recs, cs1, 0);
            populate(recs, cs2, 1);

            int newClones1 = 0, newClones2 = 0;
            long newClones1Reads = 0, newClones2Reads = 0;

            for (CRec cRec : recs.values()) {
                if (cRec.clones[0] == null) {
                    newClones2++;
                    newClones2Reads += cRec.clones[1].getCount();
                }

                if (cRec.clones[1] == null) {
                    newClones1++;
                    newClones1Reads += cRec.clones[0].getCount();
                }
            }

            report.println("Unique clones in cloneset 1: " + newClones1 + " (" + Util.PERCENT_FORMAT.format(100.0 * newClones1 / cs1.size()) + "%)");
            report.println("Reads in unique clones in cloneset 1: " + newClones1Reads + " (" + Util.PERCENT_FORMAT.format(100.0 * newClones1Reads / cs1.getTotalCount()) + "%)");

            report.println("Unique clones in cloneset 2: " + newClones2 + " (" + Util.PERCENT_FORMAT.format(100.0 * newClones2 / cs2.size()) + "%)");
            report.println("Reads in unique clones in cloneset 2: " + newClones2Reads + " (" + Util.PERCENT_FORMAT.format(100.0 * newClones2Reads / cs2.getTotalCount()) + "%)");
        }
    }

    private void populate(Map<CKey, CRec> recs, CloneSet cs, int i) {
        for (Clone clone : cs) {
            CKey key = getKey(clone);
            CRec cRec = recs.get(key);
            if (cRec == null)
                recs.put(key, cRec = new CRec());

            if (cRec.clones[i] != null) {
                String error = "";
                char letter = 'X';
                if (!Objects.equals(
                        getBestGene(cRec.clones[i], GeneType.Variable),
                        getBestGene(clone, GeneType.Variable)))
                    letter = 'v';
                if (!Objects.equals(
                        getBestGene(cRec.clones[i], GeneType.Joining),
                        getBestGene(clone, GeneType.Joining)))
                    letter = 'j';
                if (!Objects.equals(
                        getBestGene(cRec.clones[i], GeneType.Constant),
                        getBestGene(clone, GeneType.Constant)))
                    letter = 'c';

                if (letter != 'X')
                    error = "Error: clones with the same key present in one of the clonesets. Seems that clones were assembled " +
                            "using -OseparateBy" + Character.toUpperCase(letter) + "=true option, please add -" + letter + " option to this command.";

                throwValidationException(error);
            }

            cRec.clones[i] = clone;
        }
    }

    private VDJCGeneId getBestGene(Clone clone, GeneType geneType) {
        return clone.getBestHit(geneType) == null ? null : clone.getBestHit(geneType).getGene().getId();
    }

    private CKey getKey(Clone clone) {
        final NucleotideSequence[] clonalSequence = new NucleotideSequence[clone.numberOfTargets()];
        for (int i = 0; i < clonalSequence.length; i++)
            clonalSequence[i] = clone.getTarget(i).getSequence();

        final VDJCGeneId v = useV() ? getBestGene(clone, GeneType.Variable) : null;

        final VDJCGeneId j = useJ() ? getBestGene(clone, GeneType.Joining) : null;

        final VDJCGeneId c = useC() ? getBestGene(clone, GeneType.Constant) : null;

        return new CKey(clonalSequence, v, j, c);
    }

    private static final class CRec {
        final Clone[] clones = new Clone[2];
    }

    private static final class CKey {
        final NucleotideSequence[] clonalSequence;
        final VDJCGeneId v, j, c;

        public CKey(NucleotideSequence[] clonalSequence, VDJCGeneId v, VDJCGeneId j, VDJCGeneId c) {
            this.clonalSequence = clonalSequence;
            this.v = v;
            this.j = j;
            this.c = c;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CKey)) return false;

            CKey cKey = (CKey) o;

            // Probably incorrect - comparing Object[] arrays with Arrays.equals
            if (!Arrays.equals(clonalSequence, cKey.clonalSequence)) return false;
            if (v != null ? !v.equals(cKey.v) : cKey.v != null) return false;
            if (j != null ? !j.equals(cKey.j) : cKey.j != null) return false;
            return c != null ? c.equals(cKey.c) : cKey.c == null;

        }

        @Override
        public int hashCode() {
            int result = Arrays.hashCode(clonalSequence);
            result = 31 * result + (v != null ? v.hashCode() : 0);
            result = 31 * result + (j != null ? j.hashCode() : 0);
            result = 31 * result + (c != null ? c.hashCode() : 0);
            return result;
        }
    }
}
