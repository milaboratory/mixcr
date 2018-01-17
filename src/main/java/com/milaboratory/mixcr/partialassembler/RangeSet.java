/*
 * Copyright (c) 2014-2017, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
package com.milaboratory.mixcr.partialassembler;

import com.milaboratory.core.Range;

import java.util.*;

public final class RangeSet implements Iterable<Range> {
    public static final RangeSet EMPTY = new RangeSet();
    private final List<Range> ranges;

    private RangeSet(List<Range> ranges) {
        this.ranges = ranges;
    }

    @SuppressWarnings("unchecked")
    public RangeSet() {
        this.ranges = Collections.EMPTY_LIST;
    }

    public RangeSet(Range range) {
        if (range.isReverse())
            throw new IllegalArgumentException();
        this.ranges = Collections.singletonList(range);
    }

    public RangeSet add(Range range) {
        if (range.isEmpty())
            return this;
        if (range.isReverse())
            throw new IllegalArgumentException();
        List<Range> result = new ArrayList<>(ranges.size() + 1);
        for (Range r : ranges)
            if (r.intersectsWithOrTouches(range)) {
                range = r.tryMerge(range);
                assert range != null;
            } else
                result.add(r);
        result.add(range);
        Collections.sort(result);
        return new RangeSet(result);
    }

    public RangeSet add(RangeSet rangeSet) {
        RangeSet result = this;
        for (Range r : rangeSet)
            result = result.add(r);
        return result;
    }

    public RangeSet intersection(Range range) {
        List<Range> result = new ArrayList<>(4);
        for (Range r : this) {
            Range intersection = range.intersection(r);
            if (intersection != null && !intersection.isEmpty())
                result.add(intersection);
        }
        return new RangeSet(result);
    }

    public RangeSet subtract(Range range) {
        List<Range> result = new ArrayList<>(ranges.size() + 1);
        for (Range r : this)
            result.addAll(r.without(range));
        return new RangeSet(result);
    }

    public int totalLength() {
        int result = 0;
        for (Range r : this)
            result += r.length();
        return result;
    }

    @Override
    public Iterator<Range> iterator() {
        return ranges.iterator();
    }

    public int size() {
        return ranges.size();
    }

    public Range get(int index) {
        return ranges.get(index);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RangeSet)) return false;

        RangeSet rangeSet = (RangeSet) o;

        return ranges.equals(rangeSet.ranges);
    }

    @Override
    public int hashCode() {
        return ranges.hashCode();
    }

    @Override
    public String toString() {
        return ranges.toString();
    }

    static RangeSet create(Range... ranges) {
        if (ranges.length == 0)
            return new RangeSet();
        RangeSet result = new RangeSet(ranges[0]);
        for (int i = 1; i < ranges.length; i++)
            result = result.add(ranges[i]);
        return result;
    }

    static RangeSet create(int... positions) {
        return create(rangesA(positions));
    }

    static RangeSet createUnsafe(Range... ranges) {
        return new RangeSet(Arrays.asList(ranges));
    }

    static RangeSet createUnsafe(int... positions) {
        return new RangeSet(rangesL(positions));
    }

    private static Range[] rangesA(int... positions) {
        if (positions.length % 2 != 0)
            throw new IllegalArgumentException();
        Range[] ranges = new Range[positions.length / 2];
        for (int i = 0; i < positions.length / 2; i++)
            ranges[i] = new Range(positions[i * 2], positions[i * 2 + 1]);
        return ranges;
    }

    private static List<Range> rangesL(int... positions) {
        return Arrays.asList(rangesA(positions));
    }
}
