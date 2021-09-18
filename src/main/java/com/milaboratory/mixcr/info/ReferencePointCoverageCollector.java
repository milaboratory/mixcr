/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 * 
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 * 
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
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
