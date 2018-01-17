/*
 * Copyright (c) 2014-2016, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.milaboratory.cli.Action;
import com.milaboratory.cli.ActionHelper;
import com.milaboratory.cli.ActionParameters;
import com.milaboratory.cli.ActionParametersWithOutput;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.CloneSet;
import com.milaboratory.mixcr.basictypes.CloneSetIO;
import com.milaboratory.mixcr.basictypes.IOUtil;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGeneId;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.*;

public class ActionClonesDiff implements Action {
    private final DiffParameters params = new DiffParameters();

    @Override
    public void go(ActionHelper helper) throws Exception {
        try (InputStream is1 = IOUtil.createIS(params.get1());
             InputStream is2 = IOUtil.createIS(params.get2());
             PrintStream report = params.report().equals(".") ? System.out : new PrintStream(new FileOutputStream(params.report()))) {

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

                throw new ParameterException(error);
            }

            cRec.clones[i] = clone;
        }
    }

    @Override
    public String command() {
        return "clonesDiff";
    }

    @Override
    public ActionParameters params() {
        return params;
    }

    private VDJCGeneId getBestGene(Clone clone, GeneType geneType) {
        return clone.getBestHit(geneType) == null ? null : clone.getBestHit(geneType).getGene().getId();
    }

    private CKey getKey(Clone clone) {
        final NucleotideSequence[] clonalSequence = new NucleotideSequence[clone.numberOfTargets()];
        for (int i = 0; i < clonalSequence.length; i++)
            clonalSequence[i] = clone.getTarget(i).getSequence();

        final VDJCGeneId v = params.useV() ? getBestGene(clone, GeneType.Variable) : null;

        final VDJCGeneId j = params.useJ() ? getBestGene(clone, GeneType.Joining) : null;

        final VDJCGeneId c = params.useC() ? getBestGene(clone, GeneType.Constant) : null;

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

    @Parameters(commandDescription = "Calculates the difference between two .clns files")
    public static class DiffParameters extends ActionParametersWithOutput {
        @Parameter(description = "input1.clns input2.clns [report]")
        public List<String> parameters = new ArrayList<>();

        @Parameter(names = {"-v"}, description = "Use V gene in clone comparison (include it as a clone key along " +
                "with a clone sequence).")
        public Boolean v;

        @Parameter(names = {"-j"}, description = "Use J gene in clone comparison (include it as a clone key along " +
                "with a clone sequence).")
        public Boolean j;

        @Parameter(names = {"-c"}, description = "Use C gene in clone comparison (include it as a clone key along " +
                "with a clone sequence).")
        public Boolean c;

        public boolean useV() {
            return v != null && v;
        }

        public boolean useJ() {
            return j != null && j;
        }

        public boolean useC() {
            return c != null && c;
        }

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


        String get1() {
            return parameters.get(0);
        }

        String get2() {
            return parameters.get(1);
        }

        String report() {
            return parameters.size() == 2 ? "." : parameters.get(2);
        }

        @Override
        @SuppressWarnings("unckecked")
        protected List<String> getOutputFiles() {
            return parameters.size() == 2 ?
                    Collections.EMPTY_LIST :
                    Collections.singletonList(parameters.get(parameters.size() - 1));
        }

        @Override
        public void validate() {
            super.validate();
            if (parameters.size() < 2 || parameters.size() > 3)
                throw new IllegalArgumentException("Wrong number of parameters.");
        }
    }
}
