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

import org.junit.Assert;
import org.junit.Test;

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
}
