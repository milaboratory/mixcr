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

import com.milaboratory.util.GlobalObjectMappers;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ClusteringFilterTest {
    @Test
    public void test1() throws Exception {
        ClusteringFilter paramentrs = new AdvancedClusteringFilter(1E-3, 1E-3, 1E-4);
        String str = GlobalObjectMappers.getPretty().writeValueAsString(paramentrs);
        ClusteringFilter deser = GlobalObjectMappers.getPretty().readValue(str, ClusteringFilter.class);
        assertEquals(paramentrs, deser);
    }

    @Test
    public void test2() throws Exception {
        ClusteringFilter paramentrs = new RelativeConcentrationFilter(1E-3);
        String str = GlobalObjectMappers.getPretty().writeValueAsString(paramentrs);
        ClusteringFilter deser = GlobalObjectMappers.getPretty().readValue(str, ClusteringFilter.class);
        assertEquals(paramentrs, deser);
    }
}