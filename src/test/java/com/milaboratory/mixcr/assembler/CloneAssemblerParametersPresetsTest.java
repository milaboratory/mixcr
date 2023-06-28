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
}