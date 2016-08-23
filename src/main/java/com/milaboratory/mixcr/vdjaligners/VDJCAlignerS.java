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

import com.milaboratory.core.Target;
import com.milaboratory.core.alignment.batch.AlignmentHit;
import com.milaboratory.core.alignment.batch.AlignmentResult;
import com.milaboratory.core.io.sequence.SingleRead;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import io.repseq.core.*;

import java.util.EnumMap;
import java.util.List;

public final class VDJCAlignerS extends VDJCAlignerAbstract<SingleRead> {
    private static final ReferencePoint reqPointL = ReferencePoint.CDR3Begin.move(-3);
    private static final ReferencePoint reqPointR = ReferencePoint.CDR3End.move(3);

    public VDJCAlignerS(VDJCAlignerParameters parameters) {
        super(parameters);
    }

    @Override
    protected VDJCAlignmentResult<SingleRead> process0(SingleRead input) {
        ensureInitialized();

        // Different algorithms for
        // -OallowPartialAlignments=false and -OallowPartialAlignments=true
        return parameters.getAllowPartialAlignments() ?
                processPartial(input) :
                processStrict(input);
    }

    private VDJCAlignmentResult<SingleRead> processPartial(SingleRead input) {
        Target[] targets = parameters.getReadsLayout().createTargets(input);

        KVJResultsForSingle[] results = new KVJResultsForSingle[targets.length];
        for (int i = 0; i < results.length; i++)
            results[i] = align(targets[i]);

        KVJResultsForSingle topResult = null;

        for (KVJResultsForSingle result : results) {
            if (!result.hasNoKVNorKJHits())
                result.alignDC();
            if (topResult == null || topResult.sumScore() < result.sumScore())
                topResult = result;
        }

        if (topResult.hasNoKVNorKJHits()) {
            onFailedAlignment(input, VDJCAlignmentFailCause.NoHits);
            return new VDJCAlignmentResult<>(input);
        }

        // Checking minimal sum score
        if (topResult.sumScore() < parameters.getMinSumScore()) {
            onFailedAlignment(input, VDJCAlignmentFailCause.LowTotalScore);
            return new VDJCAlignmentResult<>(input);
        }

        // Filtering hits basing on minSumScore
        topResult.calculateHits(parameters.getMinSumScore(), parameters.getMaxHits());

        VDJCAlignments alignment = topResult.toVDJCAlignments(input.getId());

        // Final check
        if (!parameters.getAllowNoCDR3PartAlignments()) {
            // CDR3 Begin / End
            if (!alignment.getPartitionedTarget(0).getPartitioning().isAvailable(reqPointL)
                    && !alignment.getPartitionedTarget(0).getPartitioning().isAvailable(reqPointR)) {
                onFailedAlignment(input, VDJCAlignmentFailCause.NoCDR3Parts);
                return new VDJCAlignmentResult<>(input);
            }
        }

        // Read successfully aligned

        onSuccessfulAlignment(input, alignment);
        return new VDJCAlignmentResult<>(input, alignment);
    }

    private VDJCAlignmentResult<SingleRead> processStrict(SingleRead input) {
        Target[] targets = parameters.getReadsLayout().createTargets(input);

        // Algorithm below relies on this fact
        assert targets.length <= 2;

        boolean anyIsFull = false,
                anyHasJ = false,
                anyHasV = false;

        KVJResultsForSingle[] results = new KVJResultsForSingle[targets.length];
        for (int i = 0; i < results.length; i++) {
            results[i] = align(targets[i]);
            anyIsFull |= results[i].hasKVAndJHits();
            anyHasJ |= results[i].hasKJHits();
            anyHasV |= results[i].hasKVHits();
        }

        if (!anyIsFull) {
            if (!anyHasJ && !anyHasV)
                onFailedAlignment(input, VDJCAlignmentFailCause.NoHits);
            else if (!anyHasJ)
                onFailedAlignment(input, VDJCAlignmentFailCause.NoJHits);
            else
                onFailedAlignment(input, VDJCAlignmentFailCause.NoVHits);
            return new VDJCAlignmentResult<>(input);
        }

        KVJResultsForSingle topResult = null;

        // Calculating best result
        for (KVJResultsForSingle result : results)
            if (result.hasKVAndJHits())
                topResult = topResult != null ? null : result;

        if (topResult == null) { // Both results are full
            // Finalizing alignment for both results to determine who is the best
            for (KVJResultsForSingle result : results) {
                result.alignDC();
                if (topResult == null || topResult.sumScore() < result.sumScore())
                    topResult = result;
            }
        } else
            // Align C and D genes only for best result
            topResult.alignDC();

        // Checking minimal sum score
        if (topResult.sumScore() < parameters.getMinSumScore()) {
            onFailedAlignment(input, VDJCAlignmentFailCause.LowTotalScore);
            return new VDJCAlignmentResult<>(input);
        }

        // Filtering hits basing on minSumScore
        topResult.calculateHits(parameters.getMinSumScore(), parameters.getMaxHits());

        if (topResult.hasVAndJHits()) {
            VDJCAlignments alignment = topResult.toVDJCAlignments(input.getId());

            onSuccessfulAlignment(input, alignment);
            return new VDJCAlignmentResult<>(input, alignment);
        } else {
            onFailedAlignment(input, VDJCAlignmentFailCause.LowTotalScore);
            return new VDJCAlignmentResult<>(input);
        }
    }

    // TODO all this ifs can be simplified
    private KVJResultsForSingle align(Target target) {
        NucleotideSequence sequence = target.targets[0].getSequence();

        ensureInitialized();

        AlignmentResult<AlignmentHit<NucleotideSequence, VDJCGene>> vResult, jResult;

        switch (parameters.getVJAlignmentOrder()) {
            case VThenJ:
                vResult = vAligner.align(sequence);

                // If there is no results for V return
                if (!vResult.hasHits())
                    return new KVJResultsForSingle(target,
                            vResult, // V result is empty
                            // If -OallowPartialAlignments=true try align J gene
                            parameters.getAllowPartialAlignments() ?
                                    jAligner.align(sequence) : null);

                // Returning result
                return new KVJResultsForSingle(target, vResult,
                        jAligner.align(sequence,
                                vResult.getBestHit().getAlignment().getSequence2Range().getTo(),
                                sequence.size(),
                                getFilter(GeneType.Joining, vResult)));
            case JThenV:
                jResult = jAligner.align(sequence);

                // If there is no results for J return
                if (!jResult.hasHits())
                    return new KVJResultsForSingle(target,
                            // If -OallowPartialAlignments=true try align V gene
                            parameters.getAllowPartialAlignments() ?
                                    vAligner.align(sequence) : null,
                            jResult); // J result is empty

                // Returning result
                return new KVJResultsForSingle(target, vAligner.align(sequence, 0,
                        jResult.getBestHit().getAlignment().getSequence2Range().getFrom(),
                        getFilter(GeneType.Variable, jResult)),
                        jResult);
        }

        throw new IllegalArgumentException("vjAlignmentOrder not set.");
    }

    final class KVJResultsForSingle {
        final Target target;
        final AlignmentResult<AlignmentHit<NucleotideSequence, VDJCGene>> vResult, jResult;
        AlignmentHit<NucleotideSequence, VDJCGene>[] vHits, jHits;
        VDJCHit[] dHits = null, cHits = null;

        public KVJResultsForSingle(Target target,
                                   AlignmentResult<AlignmentHit<NucleotideSequence, VDJCGene>> vResult,
                                   AlignmentResult<AlignmentHit<NucleotideSequence, VDJCGene>> jResult) {
            this.target = target;
            this.vResult = vResult;
            this.jResult = jResult;
        }

        @SuppressWarnings("unchecked")
        public void calculateHits(float minTotalScore, int maxHits) {
            float preThreshold = minTotalScore - sumScore();
            this.vHits = hasKVHits() ? extractHits(preThreshold + vResult.getBestHit().getAlignment().getScore(),
                    vResult, maxHits) : new AlignmentHit[0];
            this.jHits = hasKJHits() ? extractHits(preThreshold + jResult.getBestHit().getAlignment().getScore(),
                    jResult, maxHits) : new AlignmentHit[0];
        }

        public void alignDC() {
            NucleotideSequence sequence = target.targets[0].getSequence();

            if (singleDAligner != null && hasKVAndJHits()) {
                //Alignment of D gene
                int from = vResult.getBestHit().getAlignment().getSequence2Range().getTo(),
                        to = jResult.getBestHit().getAlignment().getSequence2Range().getFrom();
                List<PreVDJCHit> dResult = singleDAligner.align0(sequence, getPossibleDLoci(), from, to);
                dHits = PreVDJCHit.convert(getDGenesToAlign(),
                        parameters.getFeatureToAlign(GeneType.Diversity), dResult);
            }

            boolean doCAlignment = cAligner != null;

            if (parameters.getAllowNoCDR3PartAlignments())
                doCAlignment &= hasKVAndJHits() || hasKJHits();
            else
                doCAlignment &= hasKJHits();

            if (doCAlignment) {
                int from = hasKJHits() ?
                        jResult.getBestHit().getAlignment().getSequence2Range().getTo()
                        : 0;
                AlignmentResult<AlignmentHit<NucleotideSequence, VDJCGene>> res = cAligner
                        .align(sequence, from, sequence.size(),
                                getFilter(GeneType.Constant, vResult, jResult));

                cHits = createHits(res.getHits(), parameters.getFeatureToAlign(GeneType.Constant));
            } else
                cHits = new VDJCHit[0];
        }

        public boolean hasNoKVNorKJHits() {
            return (vResult == null || !vResult.hasHits()) &&
                    (jResult == null || !jResult.hasHits());
        }

        public boolean hasKVAndJHits() {
            return vResult != null && jResult != null &&
                    vResult.hasHits() && jResult.hasHits();
        }

        public boolean hasKVHits() {
            return vResult != null && vResult.hasHits();
        }

        public boolean hasKJHits() {
            return jResult != null && jResult.hasHits();
        }

        public boolean hasVAndJHits() {
            return vHits != null && vHits.length > 0 &&
                    jHits != null && jHits.length > 0;
        }

        public VDJCHit[] getVHits(GeneFeature feature) {
            return createHits(vHits, feature);
        }

        public VDJCHit[] getJHits(GeneFeature feature) {
            return createHits(jHits, feature);
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

        public Chains getPossibleDLoci() {
            Chains chains = new Chains();
            for (AlignmentHit<NucleotideSequence, VDJCGene> vHit : vResult.getHits())
                chains = chains.merge(vHit.getRecordPayload().getChains());
            for (AlignmentHit<NucleotideSequence, VDJCGene> jHit : jResult.getHits())
                chains = chains.merge(jHit.getRecordPayload().getChains());
            return chains;
        }

        public VDJCAlignments toVDJCAlignments(long inputId) {
            EnumMap<GeneType, VDJCHit[]> hits = new EnumMap<>(GeneType.class);

            hits.put(GeneType.Variable, getVHits(parameters.getFeatureToAlign(GeneType.Variable)));
            hits.put(GeneType.Joining, getJHits(parameters.getFeatureToAlign(GeneType.Joining)));

            if (dHits != null)
                hits.put(GeneType.Diversity, dHits);

            if (cHits != null)
                hits.put(GeneType.Constant, cHits);

            return new VDJCAlignments(inputId, hits, target.targets[0]);
        }
    }

    public static VDJCHit[] createHits(AlignmentHit<NucleotideSequence, VDJCGene>[] kHits, GeneFeature feature) {
        VDJCHit[] hits = new VDJCHit[kHits.length];
        for (int i = 0; i < kHits.length; i++)
            hits[i] = new VDJCHit(kHits[i].getRecordPayload(), kHits[i].getAlignment(), feature);
        return hits;
    }

    public static VDJCHit[] createHits(List<AlignmentHit<NucleotideSequence, VDJCGene>> kHits, GeneFeature feature) {
        VDJCHit[] hits = new VDJCHit[kHits.size()];
        for (int i = 0; i < kHits.size(); i++)
            hits[i] = new VDJCHit(kHits.get(i).getRecordPayload(), kHits.get(i).getAlignment(), feature);
        return hits;
    }

    private static AlignmentHit<NucleotideSequence, VDJCGene>[] extractHits(float minScore,
                                                                            AlignmentResult<AlignmentHit<NucleotideSequence, VDJCGene>> result,
                                                                            int maxHits) {
        int count = 0;
        for (AlignmentHit<NucleotideSequence, VDJCGene> hit : result.getHits())
            if (hit.getAlignment().getScore() >= minScore) {
                if (++count >= maxHits)
                    break;
            } else
                break;

        AlignmentHit<NucleotideSequence, VDJCGene>[] res = new AlignmentHit[count];
        for (int i = 0; i < count; ++i)
            res[i] = result.getHits().get(i);

        return res;
    }
}

