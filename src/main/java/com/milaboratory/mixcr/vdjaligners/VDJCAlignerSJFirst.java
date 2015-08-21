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

import com.milaboratory.core.alignment.KAlignmentResult;
import com.milaboratory.core.io.sequence.SingleRead;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.reference.GeneType;

import java.util.EnumMap;
import java.util.List;

public final class VDJCAlignerSJFirst extends VDJCAlignerAbstract<SingleRead> {

    public VDJCAlignerSJFirst(VDJCAlignerParameters parameters) {
        super(parameters);
    }

    @Override
    public VDJCAlignmentResult<SingleRead> process(SingleRead input) {
        ensureInitialized();

        NucleotideSequence target = input.getData().getSequence();
        NucleotideSequence targetRC = target.getReverseComplement();

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

        if (!vjResultForward.isFull())
            topResult = vjResultReverse;

        if (!vjResultReverse.isFull())
            topResult = vjResultForward;

        // Both results are full
        if (topResult == null)
            if (vjResultReverse.getTotalTopScore() >= vjResultForward.getTotalTopScore())
                topResult = vjResultReverse;
            else
                topResult = vjResultForward;

        if (topResult.vResult.getBestHit().getAlignment().getScore() +
                topResult.jResult.getBestHit().getAlignment().getScore() < parameters.getMinSumScore()) {
            onFailedAlignment(input, VDJCAlignmentFailCause.LowTotalScore);
            return new VDJCAlignmentResult<>(input);
        }

        topResult.calculateHits(parameters.getMinSumScore(), parameters.getMaxHits());

        if (topResult.hasVJHits()) {
            EnumMap<GeneType, VDJCHit[]> hits = new EnumMap<>(GeneType.class);

            if (singleDAligner != null) {
                //Alignment of D gene
                int from = topResult.vHits[0].getAlignment().getSequence2Range().getTo(),
                        to = topResult.jHits[0].getAlignment().getSequence2Range().getFrom();
                List<PreVDJCHit> dResult = singleDAligner.align0(topResult.isRC ? targetRC : target, from, to);
                hits.put(GeneType.Diversity, PreVDJCHit.convert(getDAllelesToAlign(),
                        parameters.getFeatureToAlign(GeneType.Diversity), dResult));
            }

            hits.put(GeneType.Variable, topResult.getVHits(getVAllelesToAlign(),
                    parameters.getFeatureToAlign(GeneType.Variable)));
            hits.put(GeneType.Joining, topResult.getJHits(getJAllelesToAlign(),
                    parameters.getFeatureToAlign(GeneType.Joining)));

            if (cAligner != null) {
                int from = topResult.jHits[0].getAlignment().getSequence2Range().getTo();
                KAlignmentResult res = cAligner.align(topResult.isRC ? targetRC : target, from, target.size());

                hits.put(GeneType.Constant,
                        KVJResultsForSingle.createHits(res.getHits(), getCAllelesToAlign(),
                                parameters.getFeatureToAlign(GeneType.Constant)));
            }

            VDJCAlignments alignment = new VDJCAlignments(
                    input.getId(), hits, topResult.isRC ?
                    new NSequenceWithQuality(targetRC, input.getData().getQuality().reverse()) :
                    new NSequenceWithQuality(target, input.getData().getQuality())
            );

            onSuccessfulAlignment(input, alignment);
            return new VDJCAlignmentResult<>(input, alignment);
        } else {
            onFailedAlignment(input, VDJCAlignmentFailCause.LowTotalScore);
            return new VDJCAlignmentResult<>(input);
        }
    }

    private KVJResultsForSingle align(NucleotideSequence read, boolean isRC) {
        ensureInitialized();

        KAlignmentResult vResult, jResult;

        switch (parameters.getVJAlignmentOrder()) {
            case VThenJ:
                vResult = vAligner.align(read);

                //If there is no results for V return
                if (!vResult.hasHits())
                    return new KVJResultsForSingle(vResult, null, isRC);

                //Searching for J gene
                jResult = jAligner.align(read,
                        vResult.getBestHit().getAlignment().getSequence2Range().getTo(),
                        read.size());

                //Returning result
                return new KVJResultsForSingle(vResult, jResult, isRC);
            case JThenV:
                jResult = jAligner.align(read);

                //If there is no results for J return
                if (!jResult.hasHits())
                    return new KVJResultsForSingle(null, jResult, isRC);

                //Searching for V gene
                vResult = vAligner.align(read, 0,
                        jResult.getBestHit().getAlignment().getSequence2Range().getFrom());

                //Returning result
                return new KVJResultsForSingle(vResult, jResult, isRC);
        }

        throw new IllegalArgumentException("vjAlignmentOrder not set.");
    }
}

