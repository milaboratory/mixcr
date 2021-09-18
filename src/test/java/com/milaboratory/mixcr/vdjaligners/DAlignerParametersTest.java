/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 * 
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 * 
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
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
        String str = GlobalObjectMappers.PRETTY.writeValueAsString(paramentrs);
        DAlignerParameters deser = GlobalObjectMappers.PRETTY.readValue(str, DAlignerParameters.class);
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
