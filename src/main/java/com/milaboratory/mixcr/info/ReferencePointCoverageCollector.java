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
package com.milaboratory.mixcr.info;

import com.milaboratory.core.Range;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.cli.Util;
import io.repseq.core.ReferencePoint;

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Created by dbolotin on 04/08/15.
 */
public class ReferencePointCoverageCollector implements AlignmentInfoCollector {
    final ReferencePoint refPoint;
    final AtomicLong totalCount = new AtomicLong();
    final AtomicLongArray leftHist, rightHist;

    public ReferencePointCoverageCollector(ReferencePoint refPoint, int leftThreshold, int rightThreshold) {
        if (refPoint.isAttachedToAlignmentBound())
            throw new IllegalArgumentException();

        this.refPoint = refPoint;

        this.leftHist = new AtomicLongArray(leftThreshold);
        this.rightHist = new AtomicLongArray(rightThreshold);
    }

    @Override
    public void writeResult(PrintStream writer) {
        writer.println();
        writer.println("Coverage of " + refPoint + ":");
        for (int i = leftHist.length() - 1; i > 0; --i)
            writer.println("-" + i + "\t" + leftHist.get(i) + "\t" + Util.PERCENT_FORMAT.format(100.0 * leftHist.get(i) / totalCount.get()) + "%");
        for (int i = 0; i < rightHist.length(); ++i)
            writer.println(i + "\t" + rightHist.get(i) + "\t" + Util.PERCENT_FORMAT.format(100.0 * rightHist.get(i) / totalCount.get()) + "%");
    }

    @Override
    public void put(VDJCAlignments alignments) {
        totalCount.incrementAndGet();

        VDJCHit hit = alignments.getBestHit(refPoint.getGeneType());

        int left = -1, right = -1;
        for (int i = 0; i < alignments.numberOfTargets(); ++i) {
            int position = hit.getPosition(i, refPoint);
            if (position == -1)
                continue;
            Range alignmentRange = hit.getAlignment(i).getSequence2Range();
            left = Math.max(position - alignmentRange.getLower(), left);
            right = Math.max(alignmentRange.getUpper() - position, right);
        }

        if (left == -1)
            return;

        left = Math.min(leftHist.length() - 1, left);
        leftHist.incrementAndGet(left);
        right = Math.min(rightHist.length() - 1, right);
        rightHist.incrementAndGet(right);
    }

    @Override
    public void end() {
        endHist(leftHist);
        endHist(rightHist);
    }

    private static void endHist(AtomicLongArray hist) {
        long cumulative = 0;
        for (int i = hist.length() - 1; i >= 0; --i)
            hist.set(i, cumulative += hist.get(i));
    }
}
