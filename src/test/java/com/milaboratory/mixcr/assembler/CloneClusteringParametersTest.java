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

import com.milaboratory.core.tree.TreeSearchParameters;
import com.milaboratory.util.GlobalObjectMappers;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CloneClusteringParametersTest {
    @Test
    public void test1() throws Exception {
        CloneClusteringParameters paramentrs = new CloneClusteringParameters(2, 1, TreeSearchParameters.ONE_MISMATCH, new AdvancedClusteringFilter(1E-3, 1E-3, 1E-4));
        String str = GlobalObjectMappers.getPretty().writeValueAsString(paramentrs);
        CloneClusteringParameters deser = GlobalObjectMappers.getPretty().readValue(str, CloneClusteringParameters.class);
        assertEquals(paramentrs, deser);
        CloneClusteringParameters clone = deser.clone();
        assertEquals(paramentrs, clone);
    }
}