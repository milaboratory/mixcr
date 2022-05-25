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
