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
