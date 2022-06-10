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
package com.milaboratory.mixcr.postanalysis.stat;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class HolmBonferroniTest {
    @Test
    public void test1() {
        List<Double> result = HolmBonferroni.run(
                Arrays.asList(0.001, 0.1, 0.1, 0.1, 0.02, 0.02, 0.02, 1E-4),
                d -> d,
                0.05);
        Assert.assertEquals(Arrays.asList(1E-4, 0.001), result);
    }
}