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

import com.milaboratory.core.io.sequence.SequenceRead;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerEventListener;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignmentFailCause;
import com.milaboratory.mixcr.vdjaligners.VJAlignmentOrder;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public final class AlignerReport implements VDJCAlignerEventListener, ReportWriter {
    private final VJAlignmentOrder order;
    private final AtomicLongArray fails = new AtomicLongArray(VDJCAlignmentFailCause.values().length);
    private final AtomicLong successes = new AtomicLong(0);
    private final AtomicLong hasDifferentVJLoci = new AtomicLong(0);
    private final AtomicLong alignedOverlap = new AtomicLong(0);
    private final AtomicLong nonAlignedOverlap = new AtomicLong(0);

    public AlignerReport(VJAlignmentOrder order) {
        this.order = order;
    }

    public long getFails(VDJCAlignmentFailCause cause) {
        return fails.get(cause.ordinal());
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

    public long getAlignedOverlap() {
        return alignedOverlap.get();
    }

    public long getNonAlignedOverlap() {
        return nonAlignedOverlap.get();
    }

    @Override
    public void onFailedAlignment(SequenceRead read, VDJCAlignmentFailCause cause) {
        fails.incrementAndGet(cause.ordinal());
    }

    @Override
    public void onSuccessfulAlignment(SequenceRead read, VDJCAlignments alignment) {
        successes.incrementAndGet();
    }

    @Override
    public void onSuccessfulOverlap(SequenceRead read, VDJCAlignments alignments) {
        if (alignments == null)
            nonAlignedOverlap.incrementAndGet();
        else
            alignedOverlap.incrementAndGet();
    }

    public void onAlignmentWithDifferentVJLoci() {
        hasDifferentVJLoci.incrementAndGet();
    }

    @Override
    public void writeReport(ReportHelper helper) {
        long total = getTotal();
        long success = successes.get();
        helper.writeField("Total sequencing reads", total);
        helper.writeField("Successfully aligned reads", success);
        helper.writePercentField("Successfully aligned, percent", success, total);

        if (hasDifferentVJLoci.get() != 0)
            helper.writePercentField("Alignment with different V and J immunological chain genes",
                    hasDifferentVJLoci.get(), total);

        for (VDJCAlignmentFailCause cause : VDJCAlignmentFailCause.values())
            if (fails.get(cause.ordinal()) != 0)
                helper.writePercentField(cause.reportLine, fails.get(cause.ordinal()), total);

        helper.writePercentField("Overlapped, percent", alignedOverlap.get() + nonAlignedOverlap.get(), total);
        helper.writePercentField("Overlapped and aligned, percent", alignedOverlap.get(), total);
        helper.writePercentField("Overlapped and not aligned, percent", nonAlignedOverlap.get(), total);
    }

    public long getTotal() {
        long total = 0;
        for (int i = 0; i < fails.length(); ++i)
            total += fails.get(i);
        total += successes.get();
        return total;
    }
}
