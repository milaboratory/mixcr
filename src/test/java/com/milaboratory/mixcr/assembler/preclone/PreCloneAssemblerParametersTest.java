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
package com.milaboratory.mixcr.assembler.preclone;

import com.milaboratory.test.TestUtil;
import org.junit.Test;

import static org.junit.Assert.*;

public class PreCloneAssemblerParametersTest {
    @Test
    public void testJson() {
        TestUtil.assertJson(PreCloneAssemblerParameters.getDefaultParameters(false), true);
        TestUtil.assertJson(PreCloneAssemblerParameters.getDefaultParameters(true), true);
    }
}