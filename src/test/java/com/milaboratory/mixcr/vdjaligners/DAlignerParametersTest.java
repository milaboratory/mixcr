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
package com.milaboratory.mixcr.vdjaligners;

import com.milaboratory.core.alignment.AffineGapAlignmentScoring;
import com.milaboratory.core.io.util.IOTestUtil;
import io.repseq.core.GeneFeature;
import com.milaboratory.util.GlobalObjectMappers;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DAlignerParametersTest {
    @Test
    public void test1() throws Exception {
        DAlignerParameters paramentrs = new DAlignerParameters(GeneFeature.DRegion,
                0.85f, 30.0f, 3, AffineGapAlignmentScoring.getNucleotideBLASTScoring());
        String str = GlobalObjectMappers.getPretty().writeValueAsString(paramentrs);
        DAlignerParameters deser = GlobalObjectMappers.getPretty().readValue(str, DAlignerParameters.class);
        assertEquals(paramentrs, deser);
        DAlignerParameters clone = deser.clone();
        assertEquals(paramentrs, clone);
    }

    @Test
    public void test2() throws Exception {
        DAlignerParameters se = new DAlignerParameters(GeneFeature.DRegion,
                0.85f, 30.0f, 3, AffineGapAlignmentScoring.getNucleotideBLASTScoring());
        IOTestUtil.assertJavaSerialization(se);
    }
}