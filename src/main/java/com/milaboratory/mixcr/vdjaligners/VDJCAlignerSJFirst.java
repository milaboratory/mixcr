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

import com.milaboratory.core.alignment.kaligner1.KAlignmentHit;
import com.milaboratory.core.alignment.kaligner1.KAlignmentResult;
import com.milaboratory.core.io.sequence.SingleRead;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.reference.Allele;
import com.milaboratory.mixcr.reference.GeneFeature;
import com.milaboratory.mixcr.reference.GeneType;
import com.milaboratory.mixcr.reference.Locus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class VDJCAlignerSJFirst extends VDJCAlignerAbstract<SingleRead> {

    public VDJCAlignerSJFirst(VDJCAlignerParameters parameters) {
        super(parameters);
    }

    @Override
    public VDJCAlignmentResult<SingleRead> process(SingleRead input) {
        ensureInitialized();

        NSequenceWithQuality target = input.getData();
        NSequenceWithQuality targetRC = target.getReverseComplement();

        KVJResultsForSingle vjResultForward = align(target, false);
        KVJResultsForSingle vjResultReverse = align(targetRC, true);

        if (!vjResultForward.isFull() && !vjResultReverse.isFull()) {
            if (!vjResultForward.hasKJHits() && !vjResultReverse.hasKJHits())
                onFailedAlignment(input, VDJCAlignmentFailCause.NoJHits);
            else
                onFailedAlignment(input, VDJCAlignmentFailCause.NoVHits);
            return new VDJCAlignmentResult<>(input);
        }

        KVJResultsForSingle topResult = null;

        // Calculating best result

        if (!vjResultForward.isFull())
            topResult = vjResultReverse;

        if (!vjResultReverse.isFull())
            topResult = vjResultForward;

        // Both results are full
        if (topResult == null) {
            // Finalizing alignment for both results to determine who is the best
            vjResultReverse.alignDC();
            vjResultForward.alignDC();

            // Choosing best result based on total score
            if (vjResultReverse.sumScore() >= vjResultForward.sumScore())
                topResult = vjResultReverse;
            else
                topResult = vjResultForward;
        } else
            // Align C and D genes only for best result
            topResult.alignDC();

        // Checking minimal sum score
        if (topResult.sumScore() < parameters.getMinSumScore()) {
            onFailedAlignment(input, VDJCAlignmentFailCause.LowTotalScore);
            return new VDJCAlignmentResult<>(input);
        }

        topResult.calculateHits(parameters.getMinSumScore(), parameters.getMaxHits());

        if (topResult.hasVJHits()) {
            VDJCAlignments alignment = topResult.toVDJCAlignments(input.getId());

            onSuccessfulAlignment(input, alignment);
            return new VDJCAlignmentResult<>(input, alignment);
        } else {
            onFailedAlignment(input, VDJCAlignmentFailCause.LowTotalScore);
            return new VDJCAlignmentResult<>(input);
        }
    }

    private KVJResultsForSingle align(NSequenceWithQuality input, boolean isRC) {
        NucleotideSequence sequence = input.getSequence();

        ensureInitialized();

        KAlignmentResult vResult, jResult;

        switch (parameters.getVJAlignmentOrder()) {
            case VThenJ:
                vResult = vAligner.align(sequence);

                //If there is no results for V return
                if (!vResult.hasHits())
                    return new KVJResultsForSingle(input, vResult, null, isRC);

                //Searching for J gene
                jResult = jAligner.align(sequence,
                        vResult.getBestHit().getAlignment().getSequence2Range().getTo(),
                        sequence.size());

                //Returning result
                return new KVJResultsForSingle(input, vResult, jResult, isRC);
            case JThenV:
                jResult = jAligner.align(sequence);

                //If there is no results for J return
                if (!jResult.hasHits())
                    return new KVJResultsForSingle(input, null, jResult, isRC);

                //Searching for V gene
                vResult = vAligner.align(sequence, 0,
                        jResult.getBestHit().getAlignment().getSequence2Range().getFrom());

                //Returning result
                return new KVJResultsForSingle(input, vResult, jResult, isRC);
        }

        throw new IllegalArgumentException("vjAlignmentOrder not set.");
    }

    final class KVJResultsForSingle {
        final NSequenceWithQuality target;
        final KAlignmentResult<?> vResult, jResult;
        final boolean isRC;
        KAlignmentHit[] vHits, jHits;
        VDJCHit[] dHits = null, cHits = null;

        public KVJResultsForSingle(NSequenceWithQuality target, KAlignmentResult<?> vResult, KAlignmentResult<?> jResult, boolean isRC) {
            this.target = target;
            this.vResult = vResult;
            this.jResult = jResult;
            this.isRC = isRC;
        }

        public void calculateHits(float minTotalScore, int maxHits) {
            float preThreshold = minTotalScore - sumScore();
            this.vHits = extractHits(preThreshold + vResult.getBestHit().getAlignment().getScore(), vResult, maxHits);
            this.jHits = extractHits(preThreshold + jResult.getBestHit().getAlignment().getScore(), jResult, maxHits);
        }

        public void alignDC() {
            NucleotideSequence sequence = target.getSequence();

            if (singleDAligner != null) {
                //Alignment of D gene
                int from = vResult.getBestHit().getAlignment().getSequence2Range().getTo(),
                        to = jResult.getBestHit().getAlignment().getSequence2Range().getFrom();
                List<PreVDJCHit> dResult = singleDAligner.align0(sequence,
                        getPossibleDLoci(), from, to);
                dHits = PreVDJCHit.convert(getDAllelesToAlign(),
                        parameters.getFeatureToAlign(GeneType.Diversity), dResult);
            }

            if (cAligner != null) {
                int from = jResult.getBestHit().getAlignment().getSequence2Range().getTo();
                KAlignmentResult res = cAligner.align(sequence, from, target.size());

                cHits = createHits(res.getHits(), getCAllelesToAlign(), parameters.getFeatureToAlign(GeneType.Constant));
            }
        }

        public boolean isEmpty() {
            return (vResult == null || !vResult.hasHits()) &&
                    (jResult == null || !jResult.hasHits());
        }

        public boolean isFull() {
            return vResult != null && jResult != null &&
                    vResult.hasHits() && jResult.hasHits();
        }

        public boolean hasKVHits() {
            return vResult != null && vResult.hasHits();
        }

        public boolean hasKJHits() {
            return jResult != null && jResult.hasHits();
        }

        public boolean hasVJHits() {
            return vHits != null && vHits.length > 0 &&
                    jHits != null && jHits.length > 0;
        }

        public VDJCHit[] getVHits(List<Allele> alleles, GeneFeature feature) {
            return createHits(vHits, alleles, feature);
        }

        public VDJCHit[] getJHits(List<Allele> alleles, GeneFeature feature) {
            return createHits(jHits, alleles, feature);
        }

        public float sumScore() {
            float score = 0.0f;
            if (vResult != null && vResult.hasHits())
                score += vResult.getBestHit().getAlignment().getScore();
            if (jResult != null && jResult.hasHits())
                score += jResult.getBestHit().getAlignment().getScore();
            if (parameters.doIncludeDScore() && dHits != null && dHits.length > 0)
                score += dHits[0].getScore();
            if (parameters.doIncludeCScore() && cHits != null && cHits.length > 0)
                score += cHits[0].getScore();
            return score;
        }

        public Set<Locus> getPossibleDLoci() {
            EnumSet<Locus> loci = EnumSet.noneOf(Locus.class);
            for (KAlignmentHit vHit : vResult.getHits())
                loci.add(getAllele(GeneType.Variable, vHit.getId()).getLocus());
            for (KAlignmentHit jHit : jResult.getHits())
                loci.add(getAllele(GeneType.Joining, jHit.getId()).getLocus());
            return loci;
        }

        public VDJCAlignments toVDJCAlignments(long inputId) {
            EnumMap<GeneType, VDJCHit[]> hits = new EnumMap<>(GeneType.class);

            hits.put(GeneType.Variable, getVHits(getVAllelesToAlign(),
                    parameters.getFeatureToAlign(GeneType.Variable)));
            hits.put(GeneType.Joining, getJHits(getJAllelesToAlign(),
                    parameters.getFeatureToAlign(GeneType.Joining)));

            if (dHits != null)
                hits.put(GeneType.Diversity, dHits);

            if (cHits != null)
                hits.put(GeneType.Constant, cHits);

            return new VDJCAlignments(inputId, hits, target);
        }
    }

    public static VDJCHit[] createHits(KAlignmentHit[] kHits, List<Allele> alleles, GeneFeature feature) {
        VDJCHit[] hits = new VDJCHit[kHits.length];
        for (int i = 0; i < kHits.length; i++)
            hits[i] = new VDJCHit(alleles.get(kHits[i].getId()), kHits[i].getAlignment(), feature);
        return hits;
    }

    public static VDJCHit[] createHits(List<KAlignmentHit> kHits, List<Allele> alleles, GeneFeature feature) {
        VDJCHit[] hits = new VDJCHit[kHits.size()];
        for (int i = 0; i < kHits.size(); i++)
            hits[i] = new VDJCHit(alleles.get(kHits.get(i).getId()), kHits.get(i).getAlignment(), feature);
        return hits;
    }

    private static KAlignmentHit<?>[] extractHits(float minScore, KAlignmentResult<?> result, int maxHits) {
        int count = 0;
        for (KAlignmentHit hit : result.getHits())
            if (hit.getAlignment().getScore() > minScore) {
                if (++count >= maxHits)
                    break;
            } else
                break;

        KAlignmentHit[] res = new KAlignmentHit[count];
        for (int i = 0; i < count; ++i)
            res[i] = result.getHits().get(i);

        return res;
    }
}

