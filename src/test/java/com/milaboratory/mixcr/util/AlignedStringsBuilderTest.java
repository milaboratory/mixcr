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
package com.milaboratory.mixcr.util;

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by dbolotin on 13/11/15.
 */
public class AlignedStringsBuilderTest {
    @Test
    public void test1() throws Exception {
        AlignedStringsBuilder builder = new AlignedStringsBuilder();
        builder
                .row("atta", "gac", "cagata")
                .row("taaaassdfaa", "gacaa", "caata");
        String expected = "atta        gac   cagata\n" +
                "taaaassdfaa gacaa caata\n";
        Assert.assertEquals(expected, builder.toString());
    }
}
