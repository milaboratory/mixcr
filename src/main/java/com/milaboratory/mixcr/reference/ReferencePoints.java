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
package com.milaboratory.mixcr.reference;


import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.SequencePartitioning;

import java.util.Arrays;

/**
 * @author Dmitry Bolotin
 * @author Stanislav Poslavsky
 */
public final class ReferencePoints extends SequencePartitioning implements java.io.Serializable {
    final int[] points;

    public ReferencePoints(int[] points) {
        if (points.length != BasicReferencePoint.TOTAL_NUMBER_OF_REFERENCE_POINTS)
            throw new IllegalArgumentException("Illegal length of array.");
        checkReferencePoints(points);
        this.points = points;
    }

    public ReferencePoints(int start, int[] points) {
        checkReferencePoints(points);
        int[] array = new int[BasicReferencePoint.TOTAL_NUMBER_OF_REFERENCE_POINTS];
        Arrays.fill(array, -1);
        System.arraycopy(points, 0, array, start, points.length);
        this.points = array;
    }

    public int numberOfDefinedPoints() {
        int ret = 0;
        for (int point : points) {
            if (point >= 0)
                ++ret;
        }
        return ret;
    }

    private static void checkReferencePoints(int[] points) {
        Boolean reversed = null;

        int first = -1;

        for (int ref : points)
            if (ref < -1)
                throw new IllegalArgumentException("Illegal input: " + ref);
            else if (ref > 0)
                if (first == -1)
                    first = ref;
                else if (first != ref) {
                    reversed = first > ref;
                    break;
                }

        if (reversed == null)
            return;

        int previousPoint = -1;
        for (int point : points) {
            if (point == -1)
                continue;

            if (previousPoint == -1) {
                previousPoint = point;
                continue;
            }

            if (previousPoint != point &&
                    reversed ^ previousPoint > point)
                throw new IllegalArgumentException("Non-monotonic sequence of reference points.");

            previousPoint = point;
        }
    }

    private int getPosition(int referencePointIndex) {
        if (referencePointIndex < 0
                || referencePointIndex >= points.length)
            return -1;
        return points[referencePointIndex];
    }

    public int getFirstAvailablePosition() {
        for (int i : points)
            if (i >= 0)
                return i;
        throw new IllegalStateException();
    }

    public int getLastAvailablePosition() {
        for (int i = points.length - 1; i >= 0; --i)
            if (points[i] >= 0)
                return points[i];
        throw new IllegalStateException();
    }

    @Override
    public int getPosition(ReferencePoint referencePoint) {
        int point = getPosition(referencePoint.getIndex());
        if (point < 0)
            return -1;
        return point + referencePoint.getOffset();
    }

    ReferencePoints getRelativeReferencePoints(GeneFeature geneFeature) {
        int[] newPoints = new int[points.length];
        for (int i = 0; i < points.length; ++i)
            newPoints[i] = getRelativePosition(geneFeature, new ReferencePoint(BasicReferencePoint.getByIndex(i)));
        return new ReferencePoints(newPoints);
    }

    ReferencePoints applyMutations(Mutations<NucleotideSequence> mutations) {
        int[] newPoints = new int[points.length];
        for (int i = 0; i < points.length; ++i)
            if (points[i] == -1)
                newPoints[i] = -1;
            else if ((newPoints[i] = mutations.convertPosition(points[i])) < -1)
                newPoints[i] = ~newPoints[i];
        return new ReferencePoints(newPoints);
    }

    GeneFeature getWrappingGeneFeature() {
        int start = 0, end = points.length - 1;
        for (; start < points.length && points[start] == -1; ++start) ;
        for (; end >= start && points[end] == -1; --end) ;
        if (points[start] == -1)
            return null;
        return new GeneFeature(new ReferencePoint(BasicReferencePoint.getByIndex(start)),
                new ReferencePoint(BasicReferencePoint.getByIndex(end)));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ReferencePoints that = (ReferencePoints) o;
        return Arrays.equals(points, that.points);
    }

    @Override
    public int hashCode() {
        int hash = 31;
        for (int i = 0; i < BasicReferencePoint.TOTAL_NUMBER_OF_REFERENCE_POINTS; ++i)
            hash = getPosition(i) + hash * 17;
        return hash;
    }

    @Override
    public String toString() {
        return Arrays.toString(points);
    }
}
