/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 *
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 *
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.assembler;

import com.milaboratory.core.io.util.IOTestUtil;
import io.repseq.core.GeneFeature;
import org.junit.Assert;
import org.junit.Test;

public class CloneAssemblerParametersPresetsTest {
    @Test
    public void test1() throws Exception {
        Assert.assertEquals(GeneFeature.CDR3,
                CloneAssemblerParametersPresets.getByName("default").getAssemblingFeatures()[0]);
    }

    @Test
    public void test2() throws Exception {
        IOTestUtil.assertJavaSerialization(CloneAssemblerParametersPresets.getByName("default"));
    }
}
