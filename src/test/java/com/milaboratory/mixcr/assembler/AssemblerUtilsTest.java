/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 * 
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 * 
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.assembler;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class AssemblerUtilsTest {
    @Test
    public void test1() throws Exception {
        long variants = AssemblerUtils.mappingVariantsCount(3, 3);
        AssemblerUtils.MappingThresholdCalculator mt = new AssemblerUtils.MappingThresholdCalculator(variants, 200);
        Assert.assertEquals(1, mt.getThreshold(1));
        Assert.assertEquals(3, mt.getThreshold(3));
        Assert.assertEquals(1, mt.getThreshold(5));
        Assert.assertEquals(0, mt.getThreshold(30));
    }
}
