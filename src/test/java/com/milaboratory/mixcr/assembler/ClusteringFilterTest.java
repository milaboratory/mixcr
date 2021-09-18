/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 * 
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 * 
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.assembler;

import com.milaboratory.util.GlobalObjectMappers;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ClusteringFilterTest {
    @Test
    public void test1() throws Exception {
        ClusteringFilter paramentrs = new RelativeConcentrationFilter(1E-6);
        String str = GlobalObjectMappers.PRETTY.writeValueAsString(paramentrs);
        ClusteringFilter deser = GlobalObjectMappers.PRETTY.readValue(str, ClusteringFilter.class);
        assertEquals(paramentrs, deser);
    }
}
