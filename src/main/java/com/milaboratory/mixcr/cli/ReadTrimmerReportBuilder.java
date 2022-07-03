/*
 *
 * Copyright (c) 2022, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/miplots/blob/main/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
package com.milaboratory.mixcr.cli;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.core.Range;
import com.milaboratory.core.io.sequence.SequenceRead;
import com.milaboratory.core.sequence.quality.ReadTrimmerListener;
import com.milaboratory.util.ReportBuilder;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public class ReadTrimmerReportBuilder implements ReadTrimmerListener, ReportBuilder {
    final AtomicLong totalAlignments = new AtomicLong();
    final AtomicLongArray bySideEvents = new AtomicLongArray(4);
    final AtomicLongArray bySideNucleotides = new AtomicLongArray(4);

    public long getAlignments() {
        return totalAlignments.get();
    }

    @JsonProperty("r1LeftTrimmedEvents")
    public long getR1LeftTrimmedEvents() {
        return bySideEvents.get(0);
    }

    public void setR1LeftTrimmedEvents(long v) {
        bySideEvents.set(0, v);
    }

    @JsonProperty("r1RightTrimmedEvents")
    public long getR1RightTrimmedEvents() {
        return bySideEvents.get(1);
    }

    public void setR1RightTrimmedEvents(long v) {
        bySideEvents.set(1, v);
    }

    @JsonProperty("r2LeftTrimmedEvents")
    public long getR2LeftTrimmedEvents() {
        return bySideEvents.get(2);
    }

    public void setR2LeftTrimmedEvents(long v) {
        bySideEvents.set(2, v);
    }

    @JsonProperty("r2RightTrimmedEvents")
    public long getR2RightTrimmedEvents() {
        return bySideEvents.get(3);
    }

    public void setR2RightTrimmedEvents(long v) {
        bySideEvents.set(3, v);
    }

    @JsonProperty("r1LeftTrimmedNucleotides")
    public long getR1LeftTrimmedNucleotides() {
        return bySideNucleotides.get(0);
    }

    public void setR1LeftTrimmedNucleotides(long v) {
        bySideNucleotides.set(0, v);
    }

    @JsonProperty("r1RightTrimmedNucleotides")
    public long getR1RightTrimmedNucleotides() {
        return bySideNucleotides.get(1);
    }

    public void setR1RightTrimmedNucleotides(long v) {
        bySideNucleotides.set(1, v);
    }

    @JsonProperty("r2LeftTrimmedNucleotides")
    public long getR2LeftTrimmedNucleotides() {
        return bySideNucleotides.get(2);
    }

    public void setR2LeftTrimmedNucleotides(long v) {
        bySideNucleotides.set(2, v);
    }

    @JsonProperty("r2RightTrimmedNucleotides")
    public long getR2RightTrimmedNucleotides() {
        return bySideNucleotides.get(3);
    }

    public void setR2RightTrimmedNucleotides(long v) {
        bySideNucleotides.set(3, v);
    }

    @Override
    public void onSequence(SequenceRead originalRead, int readIndex, Range range, boolean trimmed) {
        if (readIndex == 0)
            totalAlignments.incrementAndGet();

        int originalLength = originalRead.getRead(readIndex).getData().size();

        if (range == null) {
            bySideEvents.incrementAndGet(readIndex * 2);
            bySideEvents.incrementAndGet(readIndex * 2 + 1);
            bySideNucleotides.addAndGet(readIndex * 2, originalLength / 2);
            bySideNucleotides.addAndGet(readIndex * 2, originalLength - (originalLength / 2));
        } else {
            if (range.getLower() > 0) {
                bySideEvents.incrementAndGet(readIndex * 2);
                bySideNucleotides.addAndGet(readIndex * 2, range.getLower());
            }
            if (range.getUpper() < originalLength) {
                bySideEvents.incrementAndGet(readIndex * 2 + 1);
                bySideNucleotides.addAndGet(readIndex * 2, originalLength - range.getUpper());
            }
        }
    }

    @Override
    public ReadTrimmerReport buildReport() {
        return new ReadTrimmerReport(
                totalAlignments.get(),
                getR1LeftTrimmedEvents(),
                getR1RightTrimmedEvents(),
                getR2LeftTrimmedEvents(),
                getR2RightTrimmedEvents(),
                getR1LeftTrimmedNucleotides(),
                getR1RightTrimmedNucleotides(),
                getR2LeftTrimmedNucleotides(),
                getR2RightTrimmedNucleotides()
        );
    }
}
