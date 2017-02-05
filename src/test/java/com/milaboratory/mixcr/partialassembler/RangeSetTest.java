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
import org.junit.Assert;
import org.junit.Test;

import static com.milaboratory.mixcr.partialassembler.RangeSet.createUnsafe;

public class RangeSetTest {
    @Test
    public void testAdd1() throws Exception {
        RangeSet s0 = new RangeSet();
        Assert.assertEquals(s0, new RangeSet());

        RangeSet s1 = s0.add(new Range(10, 20));
        Assert.assertEquals(s1, createUnsafe(10, 20));

        RangeSet s2 = s1.add(new Range(20, 30));
        Assert.assertEquals(s2, createUnsafe(10, 30));

        RangeSet s3 = s1.add(new Range(21, 30));
        Assert.assertEquals(s3, createUnsafe(10, 20, 21, 30));

        RangeSet s4 = s3.add(new Range(21, 30));
        Assert.assertEquals(s4, createUnsafe(10, 20, 21, 30));

        RangeSet s5 = s3.add(new Range(25, 35));
        Assert.assertEquals(s5, createUnsafe(10, 20, 21, 35));

        RangeSet s6 = s5.add(new Range(20, 21));
        Assert.assertEquals(s6, createUnsafe(10, 35));

        RangeSet s7 = s5.add(new Range(19, 22));
        Assert.assertEquals(s7, createUnsafe(10, 35));

        RangeSet s10 = createUnsafe(10, 20, 30, 40, 50, 60, 70, 80);

        RangeSet s11 = s10.add(new Range(19, 50));
        Assert.assertEquals(s11, createUnsafe(10, 60, 70, 80));
    }

    @Test
    public void testIntersection1() throws Exception {
        RangeSet s10 = createUnsafe(10, 20, 30, 40, 50, 60, 70, 80);

        RangeSet s12 = s10.intersection(new Range(19, 55));
        Assert.assertEquals(s12, createUnsafe(19, 20, 30, 40, 50, 55));
    }

    @Test
    public void testSubtract1() throws Exception {
        RangeSet s10 = createUnsafe(10, 20, 30, 40, 50, 60, 70, 80);

        RangeSet s11 = s10.subtract(new Range(19, 55));
        Assert.assertEquals(s11, createUnsafe(10, 19, 55, 60, 70, 80));

        RangeSet s12 = s10.intersection(new Range(19, 55));
        Assert.assertEquals(s10, s11.add(s12));
    }
}