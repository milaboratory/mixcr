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
package com.milaboratory.mixcr.postanalysis.downsampling;

import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

/**
 *
 */
public class DownsampleValueChooserTest {
    @Test
    public void testAuto1() {
        DownsampleValueChooser.Auto auto = new DownsampleValueChooser.Auto();
        Assert.assertEquals(200_000, auto.compute(501, 200_000, 300_000, 400_000));
        Assert.assertTrue(501 < auto.compute(501, 200_000, 300_000, 400_000, 500_000));
    }

    @Test
    public void testAuto2() {
        DownsampleValueChooser.Auto auto = new DownsampleValueChooser.Auto();
        Assert.assertEquals(10001, auto.compute(501, 1000, 10001, 10002, 10002, 10002, 10001, 10001, 200_000, 300_000, 400_000));
    }

    @Test
    public void testAuto3() {
        DownsampleValueChooser.Auto auto = new DownsampleValueChooser.Auto();
        long[] data = {1186, 3688, 1133, 15871, 3309, 4032, 192, 4232, 15348, 8652, 17298, 18658, 3315, 18855, 1460, 121, 9002, 5353, 6918, 7625, 2655, 14741, 9466, 31871, 1308, 15526, 10111, 9161, 3689, 9654, 14307, 1250, 9190, 18037, 10492, 5763, 8683, 259, 170, 1031, 3111, 5538, 11322, 10907, 3386, 12563, 5160, 7135, 4552, 8092, 7389, 7306, 2883, 9298, 23437, 830, 2144, 329, 3806, 900};
        System.out.println(new Percentile(20).evaluate(Arrays.stream(data).mapToDouble(l -> l).toArray()));
        System.out.println(auto.compute(data));
    }
}
