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
package com.milaboratory.mixcr.basictypes;

import com.milaboratory.core.Range;
import com.milaboratory.mixcr.reference.GeneFeature;
import com.milaboratory.mixcr.reference.ReferencePoint;

public abstract class SequencePartitioning {
    public abstract int getPosition(ReferencePoint referencePoint);

    public boolean isAvailable(ReferencePoint referencePoint) {
        return getPosition(referencePoint) >= 0;
    }

    public boolean isAvailable(GeneFeature feature) {
        for (GeneFeature.ReferenceRange region : feature)
            if (!isAvailable(region))
                return false;
        return true;
    }

    public Range getRange(GeneFeature feature) {
        if (feature.isComposite())
            throw new IllegalArgumentException();

        return getRange(feature.getReferenceRange(0));
    }

    public Range[] getRanges(GeneFeature feature) {
        Range[] result = new Range[feature.size()];
        for (int i = 0; i < feature.size(); ++i) {
            if ((result[i] = getRange(feature.getReferenceRange(i))) == null)
                return null;
            if (i != 0 && result[i - 1].intersectsWith(result[i]) &&
                    result[i].isReverse() == result[i - 1].isReverse())
                throw new IllegalArgumentException("Inconsistent feature partition.");
        }
        return result;
    }

    public int getLength(GeneFeature feature) {
        int length = 0, l;
        for (GeneFeature.ReferenceRange r : feature) {
            if ((l = getLength(r)) == -1)
                return -1;
            length += l;
        }
        return length;
    }

    protected Range getRange(GeneFeature.ReferenceRange refRange) {
        int begin = getPosition(refRange.begin);
        if (begin < 0)
            return null;
        int end = getPosition(refRange.end);
        if (end < 0)
            return null;
        return new Range(begin, end);
    }

    protected int getLength(GeneFeature.ReferenceRange refRange) {
        int begin = getPosition(refRange.begin);
        if (begin < 0)
            return -1;
        int end = getPosition(refRange.end);
        if (end < 0)
            return -1;
        return Math.abs(end - begin);
    }

    protected boolean isAvailable(GeneFeature.ReferenceRange refRange) {
        int begin = getPosition(refRange.begin);
        if (begin < 0)
            return false;
        int end = getPosition(refRange.end);
        if (end < 0)
            return false;
        return true;
    }

    /**
     * Returns a relative range of specified {@code subfeature} in specified {@code feature} or null if this is not
     * available.
     *
     * @param feature    gene feature
     * @param subfeature a part of feature
     * @return relative range of specified {@code subfeature} in specified {@code feature} or null if this is not
     * available
     */
    public Range getRelativeRange(GeneFeature feature, GeneFeature subfeature) {
        Range[] featureRanges = getRanges(feature);
        if (featureRanges == null)
            return null;
        Range[] subfeatureRanges = getRanges(subfeature);
        if (subfeatureRanges == null)
            return null;
        int offset = 0, begin = -1, end = -1;
        int subfeaturePointer = 0;
        int state = 0; // 0 - before; 1 - rightOnBegin; 2 - inside
        for (Range range : featureRanges) {
            int from = subfeatureRanges[subfeaturePointer].getFrom();
            if (state == 0
                    && range.containsBoundary(from)) {
                state = 1;
                begin = offset + range.convertBoundaryToRelativePosition(from);
            }

            int to = subfeatureRanges[subfeaturePointer].getTo();
            if (state > 0
                    && subfeaturePointer == subfeatureRanges.length - 1) {
                if (!range.containsBoundary(to))
                    return null;
                end = offset + range.convertBoundaryToRelativePosition(to);
                break;
            }

            if (state == 1) {
                if (to != range.getTo())
                    return null;
                state = 2;
                ++subfeaturePointer;
            } else if (state == 2) {
                if (!subfeatureRanges[subfeaturePointer].equals(range))
                    return null;
                ++subfeaturePointer;
            }

            offset += range.length();
        }
        if (begin == -1 || end == -1)
            return null;
        return new Range(begin, end);
    }

    /**
     * Returns a relative position of specified {@code referencePoint} in specified {@code feature} or -1 if this
     * position is not available.
     *
     * @param feature        gene feature
     * @param referencePoint reference point
     * @return a relative position of specified {@code referencePoint} in specified {@code feature} or -1 if this
     * position is not available
     */
    public int getRelativePosition(GeneFeature feature, ReferencePoint referencePoint) {
        int absolutePosition = getPosition(referencePoint);
        if (absolutePosition == -1)
            return -1;
        Range[] ranges = getRanges(feature);
        if (ranges == null)
            return -1;

        int relativePosition = 0;
        for (Range range : ranges)
            if (range.containsBoundary(absolutePosition))
                return relativePosition + range.convertBoundaryToRelativePosition(absolutePosition);
            else relativePosition += range.length();
        return -1;
    }
}
