/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 * 
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 * 
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.util;

import junit.framework.Assert;
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
