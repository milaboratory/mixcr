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
        Assert.assertEquals(501, auto.compute(501, 200_000, 300_000, 400_000));
        Assert.assertTrue(501 < auto.compute(501, 200_000, 300_000, 400_000, 500_000));
    }
}
