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
package com.milaboratory.mixcr.cli;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.core.io.sequence.SequenceRead;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerEventListener;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignmentFailCause;
import io.repseq.core.GeneType;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public final class AlignerReport extends AbstractActionReport implements VDJCAlignerEventListener {
    private final ChainUsageStats chainStats = new ChainUsageStats();
    private final AtomicLongArray fails = new AtomicLongArray(VDJCAlignmentFailCause.values().length);
    private final AtomicLong successes = new AtomicLong(0);
    private final AtomicLong chimeras = new AtomicLong(0);
    private final AtomicLong alignedSequenceOverlap = new AtomicLong(0);
    private final AtomicLong alignedAlignmentOverlap = new AtomicLong(0);
    private final AtomicLong nonAlignedOverlap = new AtomicLong(0);
    private final AtomicLong topHitConflict = new AtomicLong(0);
    private final AtomicLong vChimeras = new AtomicLong(0);
    private final AtomicLong jChimeras = new AtomicLong(0);

    public AlignerReport() {
    }

    @Override
    public String getAction() {
        return "align";
    }

    public long getFails(VDJCAlignmentFailCause cause) {
        return fails.get(cause.ordinal());
    }

    @JsonProperty("totalReadsProcessed")
    public long getTotal() {
        long total = 0;
        for (int i = 0; i < fails.length(); ++i)
            total += fails.get(i);
        total += successes.get();
        return total;
    }

    @JsonProperty("aligned")
    public long getSuccess() {
        return successes.get();
    }

    @JsonProperty("notAligned")
    public long getNonAlignedTotal() {
        long val = 0;
        for (int i = 0; i < fails.length(); i++)
            val += fails.get(i);
        return val;
    }

    @JsonProperty("notAlignedReasons")
    public Map<String, Long> getFailsMap() {
        Map<String, Long> map = new HashMap<>();
        for (VDJCAlignmentFailCause cause : VDJCAlignmentFailCause.values())
            map.put(cause.toString(), fails.get(cause.ordinal()));
        return map;
    }

    @JsonProperty("chimeras")
    public long getChimeras() {
        return chimeras.get();
    }

    @JsonProperty("overlapped")
    public long getOverlapped() {
        return getAlignedOverlaps() + nonAlignedOverlap.get();
    }

    public long getFailsNoVHits() {
        return getFails(VDJCAlignmentFailCause.NoVHits);
    }

    public long getFailsNoJHits() {
        return getFails(VDJCAlignmentFailCause.NoJHits);
    }

    public long getFailsLowTotalScore() {
        return getFails(VDJCAlignmentFailCause.LowTotalScore);
    }

    public long getSuccesses() {
        return successes.get();
    }

    @JsonProperty("alignmentAidedOverlaps")
    public long getAlignmentOverlaps() {
        return alignedAlignmentOverlap.get();
    }

    @JsonProperty("overlappedAligned")
    public long getAlignedOverlaps() {
        return alignedSequenceOverlap.get() + alignedAlignmentOverlap.get();
    }

    @JsonProperty("overlappedNotAligned")
    public long getNonAlignedOverlaps() {
        return nonAlignedOverlap.get();
    }

    @JsonProperty("pairedEndAlignmentConflicts")
    public long getTopHitSequenceConflicts() {
        return topHitConflict.get();
    }

    @JsonProperty("vChimeras")
    public long getVChimeras() {
        return vChimeras.get();
    }

    @JsonProperty("jChimeras")
    public long getJChimeras() {
        return jChimeras.get();
    }

    @Override
    public void onFailedAlignment(SequenceRead read, VDJCAlignmentFailCause cause) {
        fails.incrementAndGet(cause.ordinal());
    }

    @Override
    public void onSuccessfulAlignment(SequenceRead read, VDJCAlignments alignment) {
        successes.incrementAndGet();
        chainStats.increment(alignment);
    }

    @Override
    public void onSuccessfulSequenceOverlap(SequenceRead read, VDJCAlignments alignments) {
        if (alignments == null)
            nonAlignedOverlap.incrementAndGet();
        else
            alignedSequenceOverlap.incrementAndGet();
    }

    @Override
    public void onSuccessfulAlignmentOverlap(SequenceRead read, VDJCAlignments alignments) {
        if (alignments == null)
            throw new IllegalArgumentException();
        alignedAlignmentOverlap.incrementAndGet();
    }

    @Override
    public void onTopHitSequenceConflict(SequenceRead read, VDJCAlignments alignments, GeneType geneType) {
        topHitConflict.incrementAndGet();
    }

    @Override
    public void onSegmentChimeraDetected(GeneType geneType, SequenceRead read, VDJCAlignments alignments) {
        switch (geneType) {
            case Variable:
                vChimeras.incrementAndGet();
                return;
            case Joining:
                jChimeras.incrementAndGet();
                return;
            default:
                throw new IllegalArgumentException(geneType.toString());
        }
    }

    public void onChimera() {
        chimeras.incrementAndGet();
    }

    @Override
    public void writeReport(ReportHelper helper) {
        // Writing common analysis information
        writeSuperReport(helper);

        long total = getTotal();
        long success = getSuccess();
        helper.writeField("Total sequencing reads", total);
        helper.writePercentAndAbsoluteField("Successfully aligned reads", success, total);

        if (getChimeras() != 0)
            helper.writePercentAndAbsoluteField("Chimeras", getChimeras(), total);

        if (getTopHitSequenceConflicts() != 0)
            helper.writePercentAndAbsoluteField("Paired-end alignment conflicts eliminated", getTopHitSequenceConflicts(), total);

        for (VDJCAlignmentFailCause cause : VDJCAlignmentFailCause.values())
            if (fails.get(cause.ordinal()) != 0)
                helper.writePercentAndAbsoluteField(cause.reportLine, fails.get(cause.ordinal()), total);

        helper.writePercentAndAbsoluteField("Overlapped", getOverlapped(), total);
        helper.writePercentAndAbsoluteField("Overlapped and aligned", getAlignedOverlaps(), total);
        helper.writePercentAndAbsoluteField("Alignment-aided overlaps", getAlignmentOverlaps(), getAlignedOverlaps());
        helper.writePercentAndAbsoluteField("Overlapped and not aligned", getNonAlignedOverlaps(), total);

        if (getVChimeras() != 0)
            helper.writePercentAndAbsoluteField("V gene chimeras", getVChimeras(), total);

        if (getJChimeras() != 0)
            helper.writePercentAndAbsoluteField("J gene chimeras", getJChimeras(), total);

        // Writing distribution by chains
        chainStats.writeReport(helper);
    }
}
