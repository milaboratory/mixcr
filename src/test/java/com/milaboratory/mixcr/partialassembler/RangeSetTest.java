/*
 * Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
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