/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 * 
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 * 
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.assembler;

import com.fasterxml.jackson.databind.JsonNode;
import com.milaboratory.core.tree.TreeSearchParameters;
import com.milaboratory.util.GlobalObjectMappers;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CloneClusteringParametersTest {
    @Test
    public void test1() throws Exception {
        CloneClusteringParameters paramentrs = new CloneClusteringParameters(2, 1, TreeSearchParameters.ONE_MISMATCH, new RelativeConcentrationFilter(1.0E-6));
        String str = GlobalObjectMappers.PRETTY.writeValueAsString(paramentrs);
        CloneClusteringParameters deser = GlobalObjectMappers.PRETTY.readValue(str, CloneClusteringParameters.class);
        assertEquals(paramentrs, deser);
        CloneClusteringParameters clone = deser.clone();
        assertEquals(paramentrs, clone);
    }
}
