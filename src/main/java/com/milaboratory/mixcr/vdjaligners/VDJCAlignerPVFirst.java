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
package com.milaboratory.mixcr.vdjaligners;

import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.alignment.AlignmentUtils;
import com.milaboratory.core.alignment.KAlignmentHit;
import com.milaboratory.core.alignment.KAlignmentResult;
import com.milaboratory.core.io.sequence.PairedRead;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.reference.Allele;
import com.milaboratory.mixcr.reference.GeneFeature;
import com.milaboratory.mixcr.reference.GeneType;
import com.milaboratory.mixcr.reference.ReferencePoint;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class VDJCAlignerPVFirst extends VDJCAlignerAbstract<PairedRead> {
    public VDJCAlignerPVFirst(VDJCAlignerParameters parameters) {
        super(parameters);
    }

    @Override
    public VDJCAlignmentResult<PairedRead> process(PairedRead input) {
        ensureInitialized();

        PairedTarget[] targets = getTargets(input);

        // Creates helper classes for each PTarget
        PAlignmentHelper[] helpers = createInitialHelpers(targets);

        // Main alignment logic
        for (PAlignmentHelper helper : helpers) {
            if (!helper.hasVHits())
                continue;

            // Sorting and filtering hits with low V-end (FR3, CDR3) score
            helper.sortAndFilterBasedOnVEndScore();

            // Calculating best V hits (basing on filtered list of V hits)
            helper.updateBestV();

            // Perform J alignments
            helper.performJAlignment();
        }

        // Calculates which PTarget was aligned with the highest score
        PAlignmentHelper bestHelper = helpers[0];
        if (bestHelper.score() < helpers[1].score())
            bestHelper = helpers[1];

        // If V or J hits are absent
        if (!bestHelper.hasHits()) {
            if (!bestHelper.hasVHits())
                onFailedAlignment(input, VDJCAlignmentFailCause.NoVHits);
            else
                onFailedAlignment(input, VDJCAlignmentFailCause.NoJHits);
            return new VDJCAlignmentResult<>(input);
        }

        // Calculates if this score is bigger then the threshold
        if (bestHelper.score() < parameters.getMinSumScore()) {
            onFailedAlignment(input, VDJCAlignmentFailCause.LowTotalScore);
            return new VDJCAlignmentResult<>(input);
        }

        //if (bestHelper.hasHits())

        // Finally filtering hits inside this helper to meet minSumScore and maxHits limits
        bestHelper.filterHits(parameters.getMinSumScore(), parameters.getMaxHits());

        //else {
        //    onFailedAlignment(input, VDJCAlignmentFailCause.LowTotalScore);
        //    // If hits for V or J are missing
        //    return new VDJCAlignmentResult<>(input);
        //}

        // If hits for V or J are missing after filtration
        if (!bestHelper.isGood()) {
            onFailedAlignment(input, VDJCAlignmentFailCause.LowTotalScore);
            return new VDJCAlignmentResult<>(input);
        }

        VDJCAlignments alignments = bestHelper.createResult(input.getId(), this);

        onSuccessfulAlignment(input, alignments);

        return new VDJCAlignmentResult<>(input, alignments);
    }

    PairedTarget[] getTargets(PairedRead read) {
        return parameters.getReadsLayout().createTargets(read);
    }

    PAlignmentHelper[] createInitialHelpers(PairedTarget[] target) {
        return new PAlignmentHelper[]{
                createInitialHelper(target[0]),
                createInitialHelper(target[1])
        };
    }

    PAlignmentHelper createInitialHelper(PairedTarget target) {
        return new PAlignmentHelper(target,
                vAligner.align(target.targets[0].getSequence()),
                vAligner.align(target.targets[1].getSequence())
        );
    }

    static final PreVDJCHit[] zeroArray = new PreVDJCHit[0];
    static final KAlignmentHit<?>[] zeroKArray = new KAlignmentHit[0];

    final class PAlignmentHelper {
        final PairedTarget target;
        final KAlignmentResult<?>[] vResults;
        KAlignmentResult<?>[] jResults;
        PairedHit[] vHits, jHits;
        PairedHit bestVHits;

        PAlignmentHelper(PairedTarget target, KAlignmentResult... vResults) {
            this.target = target;
            this.vResults = vResults;
            this.vHits = extractDoubleHits(vResults);
            //this.bestVHits = new PairedHit(
            //        vResults[0].getBestHit(),
            //        vResults[1].getBestHit()
            //);
        }

        void sortAndFilterBasedOnVEndScore() {
            // Calculating vEndScores
            for (PairedHit hit : vHits)
                hit.calculateVEndScore(VDJCAlignerPVFirst.this);

            // Sorting based on v-end score (score of alignment of FR3 and CDR3
            Arrays.sort(vHits, V_END_SCORE_COMPARATOR);

            // Retrieving maximal value
            float maxVEndScore = vHits[0].vEndScore;

            // Calculating threshold
            float threshold = maxVEndScore * parameters.getRelativeMinVFR3CDR3Score();

            // Filtering
            for (int i = 0; i < vHits.length; ++i)
                if (vHits[i].vEndScore < threshold) {
                    vHits = Arrays.copyOfRange(vHits, 0, i);
                    break;
                }

            // Calculate normal score for each read for further processing
            // and sort according to this score
            calculateScoreAndSort(vHits);
        }

        /**
         * Calculates best V hits for each read
         */
        void updateBestV() {
            KAlignmentHit hit0 = null, hit1 = null;

            for (PairedHit hit : vHits) {
                if (hit.hit0 != null &&
                        (hit0 == null ||
                                hit0.getAlignment().getScore() > hit.hit0.getAlignment().getScore()))
                    hit0 = hit.hit0;
                if (hit.hit1 != null &&
                        (hit1 == null ||
                                hit1.getAlignment().getScore() > hit.hit1.getAlignment().getScore()))
                    hit1 = hit.hit1;
            }

            // Setting best hits for current array of hits (after filtration)
            bestVHits = new PairedHit(hit0, hit1);
        }

        boolean hasVHits() {
            return vHits != null && vHits.length > 0;
        }

        boolean hasHits() {
            return vHits != null && jHits != null &&
                    vHits.length > 0 && jHits.length > 0;
        }

        boolean isGood() {
            return hasHits() && hasVJOnTheSameTarget();
        }

        private boolean hasVJOnTheSameTarget() {
            for (int i = 0; i < 2; i++)
                if (vHits[0].get(i) != null && jHits[0].get(i) != null)
                    return true;
            return false;
        }

        /**
         * Converts two KAlignmentResults to an array of paired hits (each paired hit for a particular V of J gene)
         */
        PairedHit[] extractDoubleHits(KAlignmentResult<?>... results) {
            TIntObjectHashMap<PairedHit> hits = new TIntObjectHashMap<>();
            addHits(hits, results[0], 0);
            addHits(hits, results[1], 1);

            return hits.valueCollection().toArray(new PairedHit[hits.size()]);
        }

        /**
         * Returns sum score for this targets.
         */
        float score() {
            return (vHits.length > 0 ? vHits[0].sumScore : 0.0f) +
                    (jHits != null && jHits.length > 0 ? jHits[0].sumScore : 0.0f);
        }

        void addHits(TIntObjectHashMap<PairedHit> hits, KAlignmentResult<?> result, int index) {
            if (result == null)
                return;

            for (KAlignmentHit hit : result) {
                PairedHit val =
                        index == 0 ?
                                null :
                                hits.get(hit.getId());

                if (val == null)
                    hits.put(hit.getId(), val = new PairedHit());

                val.set(index, hit);
            }
        }


        /**
         * Converts this object to a final VDJAlignment object.
         */
        VDJCAlignments createResult(long readId, VDJCAlignerPVFirst aligner) {
            VDJCHit[] vHits = convert(this.vHits, GeneType.Variable, aligner);
            VDJCHit[] jHits = convert(this.jHits, GeneType.Joining, aligner);
            VDJCHit[] dHits = null, cHits = null;


            //Alignment of D gene
            if (singleDAligner != null) {
                PreVDJCHit[][] preDHits = new PreVDJCHit[2][];
                Arrays.fill(preDHits, zeroArray);

                for (int i = 0; i < 2; ++i) {
                    //for target0
                    Alignment<NucleotideSequence> vAlignment = vHits[0].getAlignment(i);
                    Alignment<NucleotideSequence> jAlignment = jHits[0].getAlignment(i);
                    if (vAlignment == null || jAlignment == null)
                        continue;
                    int from = vAlignment.getSequence2Range().getTo(),
                            to = jAlignment.getSequence2Range().getFrom();
                    if (from > to)
                        continue;
                    List<PreVDJCHit> temp = singleDAligner.align0(target.targets[i].getSequence(),
                            getPossibleDLoci(vHits,jHits), from, to);
                    preDHits[i] = temp.toArray(new PreVDJCHit[temp.size()]);
                }

                dHits = PreVDJCHit.combine(getDAllelesToAlign(),
                        parameters.getFeatureToAlign(GeneType.Diversity), preDHits);
            }

            //Alignment of C gene
            if (cAligner != null) {
                KAlignmentHit[][] results = new KAlignmentHit[2][];
                Arrays.fill(results, zeroKArray);
                for (int i = 0; i < 2; ++i) {
                    //for target0
                    Alignment<NucleotideSequence> jAlignment = jHits[0].getAlignment(i);
                    if (jAlignment == null)
                        continue;
                    int from = jAlignment.getSequence2Range().getTo();
                    List<KAlignmentHit> temp = cAligner.align(target.targets[i].getSequence(), from, target.targets[i].size()).getHits();
                    results[i] = temp.toArray(new KAlignmentHit[temp.size()]);
                }
                cHits = combine(getCAllelesToAlign(),
                        parameters.getFeatureToAlign(GeneType.Constant), results);
            }

            return new VDJCAlignments(readId, vHits, dHits, jHits, cHits, target.targets);
        }

        /**
         * Preforms J alignment after V alignments are built.
         */
        void performJAlignment() {
            jHits = extractDoubleHits(jResults = new KAlignmentResult[]{
                    performJAlignment(0),
                    performJAlignment(1)
            });

            calculateScoreAndSort(jHits);
        }

        /**
         * Preforms J alignment for a single read
         */
        KAlignmentResult performJAlignment(int index) {
            KAlignmentHit vHit = bestVHits.get(index);

            if (vHit == null)
                return null;

            Allele allele = getVAllelesToAlign().get(vHit.getId());

            final NucleotideSequence targetSequence = target.targets[index].getSequence();

            if (vHit.getAlignment().getSequence1Range().getTo() <=
                    allele.getPartitioning().getRelativePosition(
                            parameters.getFeatureToAlign(GeneType.Variable),
                            ReferencePoint.FR3Begin)
                    || vHit.getAlignment().getSequence2Range().getTo() == targetSequence.size())
                return null;

            return jAligner.align(targetSequence,
                    vHit.getAlignment().getSequence2Range().getTo(),
                    targetSequence.size());
        }

        /**
         * Filters hit to finally meet maxHit and minScore limits.
         */
        public void filterHits(float minTotalScore, int maxHits) {
            final float minVScore = Math.max(parameters.getRelativeMinVScore() * vHits[0].sumScore,
                    minTotalScore - jHits[0].sumScore);
            this.vHits = extractHits(minVScore, vHits, maxHits);

            if (vHits.length > 0)
                this.jHits = extractHits(minTotalScore - vHits[0].sumScore, jHits, maxHits);
        }

        /**
         * Filters hit to finally meet maxHit and minScore limits.
         */
        private PairedHit[] extractHits(float minScore, PairedHit[] result, int maxHits) {
            int count = 0;
            for (PairedHit hit : result)
                if (hit.sumScore > minScore) {
                    if (++count >= maxHits)
                        break;
                } else
                    break;

            return Arrays.copyOfRange(result, 0, count);
        }
    }

    /**
     * Converts array of "internal" PairedHits to a double array of KAlignmentHits to pass this value to a VDJAlignment
     * constructor (VDJAlignmentImmediate).
     */
    static KAlignmentHit[][] toArray(PairedHit[] hits) {
        KAlignmentHit[][] hitsArray = new KAlignmentHit[hits.length][];
        for (int i = 0; i < hits.length; ++i)
            hitsArray[i] = new KAlignmentHit[]{hits[i].hit0, hits[i].hit1};
        return hitsArray;
    }

    /**
     * Calculates normal "sum" score for each hit and sort hits according to this score.
     */
    static void calculateScoreAndSort(PairedHit[] hits) {
        for (PairedHit hit : hits)
            hit.calculateScore();
        Arrays.sort(hits, SCORE_COMPARATOR);
    }

    /**
     * Internal storage of paired hit. Combines information from two hits for right and left reads of a paired-end
     * read.
     */
    static final class PairedHit {
        KAlignmentHit<?> hit0, hit1;
        float sumScore = -1, vEndScore = -1;

        PairedHit() {
        }

        PairedHit(KAlignmentHit hit0, KAlignmentHit hit1) {
            this.hit0 = hit0;
            this.hit1 = hit1;
        }

        /**
         * Calculates alignment score only for FR3 and CDR3 part of V gene.
         */
        void calculateVEndScore(VDJCAlignerPVFirst aligner) {
            if (hit0 != null)
                vEndScore = aligner.calculateVEndScore(hit0);

            if (hit1 != null) {
                float sc = aligner.calculateVEndScore(hit1);
                if (vEndScore < sc)
                    vEndScore = sc;
            }
        }

        /**
         * Calculates normal "sum" score for this paired hit.
         */
        void calculateScore() {
            sumScore =
                    (hit0 == null ? 0.0f : hit0.getAlignment().getScore()) +
                            (hit1 == null ? 0.0f : hit1.getAlignment().getScore());
        }

        /**
         * To use this hit as an array of two single hits.
         */
        void set(int i, KAlignmentHit hit) {
            assert i == 0 || i == 1;
            if (i == 0)
                this.hit0 = hit;
            else
                this.hit1 = hit;
        }

        /**
         * To use this hit as an array of two single hits.
         */
        KAlignmentHit get(int i) {
            assert i == 0 || i == 1;

            if (i == 0)
                return hit0;
            else
                return hit1;
        }

        /**
         * Converts this object to a VDJCHit
         */
        VDJCHit convert(GeneType geneType, VDJCAlignerPVFirst aligner) {
            Alignment<NucleotideSequence>[] alignments = new Alignment[2];

            int alleleId = -1;
            if (hit0 != null) {
                alleleId = hit0.getId();
                alignments[0] = hit0.getAlignment();
            }

            if (hit1 != null) {
                assert alleleId == -1 || hit1.getId() == alleleId;
                alleleId = hit1.getId();
                alignments[1] = hit1.getAlignment();
            }

            Allele allele = aligner.getAllele(geneType, alleleId);

            return new VDJCHit(allele, alignments,
                    aligner.getParameters().getFeatureToAlign(geneType));
        }
    }

    private static VDJCHit[] convert(PairedHit[] preHits,
                                     GeneType geneType, VDJCAlignerPVFirst aligner) {
        VDJCHit[] hits = new VDJCHit[preHits.length];
        for (int i = 0; i < preHits.length; i++)
            hits[i] = preHits[i].convert(geneType, aligner);
        return hits;
    }

    /**
     * Calculates alignment score only for FR3 and CDR3 part of V gene.
     */
    float calculateVEndScore(KAlignmentHit<?> hit) {
        final Allele allele = getVAllelesToAlign().get(hit.getId());
        final int boundary = allele.getPartitioning().getRelativePosition(
                parameters.getFeatureToAlign(GeneType.Variable),
                ReferencePoint.FR3Begin);
        final Alignment<NucleotideSequence> alignment = hit.getAlignment();

        if (alignment.getSequence1Range().getUpper() <= boundary)
            return 0.0f;

        if (alignment.getSequence1Range().getLower() >= boundary)
            return alignment.getScore();

        final Range range = new Range(boundary, alignment.getSequence1Range().getUpper());
        Mutations<NucleotideSequence> vEndMutations = alignment.getAbsoluteMutations()
                .extractMutationsForRange(range);

        return AlignmentUtils.calculateScore(parameters.getVAlignerParameters().getParameters().getScoring(),
                range.length(), vEndMutations);
    }

    static final Comparator<PairedHit> V_END_SCORE_COMPARATOR = new Comparator<PairedHit>() {
        @Override
        public int compare(PairedHit o1, PairedHit o2) {
            return Float.compare(o2.vEndScore, o1.vEndScore);
        }
    };

    static final Comparator<PairedHit> SCORE_COMPARATOR = new Comparator<PairedHit>() {
        @Override
        public int compare(PairedHit o1, PairedHit o2) {
            return Float.compare(o2.sumScore, o1.sumScore);
        }
    };

    static VDJCHit[] combine(final List<Allele> alleles, final GeneFeature feature, final KAlignmentHit<?>[][] hits) {
        for (int i = 0; i < hits.length; i++)
            Arrays.sort(hits[i], ALLELE_ID_COMPARATOR);
        ArrayList<VDJCHit> result = new ArrayList<>();
        final int[] pointers = new int[hits.length];
        Alignment<NucleotideSequence>[] alignments;
        int i, minId;
        while (true) {
            minId = Integer.MAX_VALUE;
            for (i = 0; i < pointers.length; ++i)
                if (pointers[i] < hits[i].length && minId > hits[i][pointers[i]].getId())
                    minId = hits[i][pointers[i]].getId();

            if (minId == Integer.MAX_VALUE)
                break;

            alignments = new Alignment[hits.length];
            for (i = 0; i < pointers.length; ++i)
                if (pointers[i] < hits[i].length && minId == hits[i][pointers[i]].getId()) {
                    alignments[i] = hits[i][pointers[i]].getAlignment();
                    ++pointers[i];
                }

            result.add(new VDJCHit(alleles.get(minId), alignments, feature));
        }
        VDJCHit[] vdjcHits = result.toArray(new VDJCHit[result.size()]);
        Arrays.sort(vdjcHits);
        return vdjcHits;
    }

    public static final Comparator<KAlignmentHit> ALLELE_ID_COMPARATOR = new Comparator<KAlignmentHit>() {
        @Override
        public int compare(KAlignmentHit o1, KAlignmentHit o2) {
            return Integer.compare(o1.getId(), o2.getId());
        }
    };
}
