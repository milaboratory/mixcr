/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 * 
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 * 
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.assembler;

import com.milaboratory.core.alignment.LinearGapAlignmentScoring;
import com.milaboratory.test.TestUtil;
import com.milaboratory.util.GlobalObjectMappers;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VJCClonalAlignerParametersTest {
    @Test
    public void test1() throws Exception {
        VJCClonalAlignerParameters paramentrs = new VJCClonalAlignerParameters(0.3f,
                LinearGapAlignmentScoring.getNucleotideBLASTScoring(), 3);
        String str = GlobalObjectMappers.PRETTY.writeValueAsString(paramentrs);
        VJCClonalAlignerParameters deser = GlobalObjectMappers.PRETTY.readValue(str, VJCClonalAlignerParameters.class);
        assertEquals(paramentrs, deser);
        assertEquals(paramentrs, deser.clone());
    }

    @Test
    public void test2() throws Exception {
        TestUtil.assertJson(new VJCClonalAlignerParameters(0.3f,
                LinearGapAlignmentScoring.getNucleotideBLASTScoring(), 3), true);

        TestUtil.assertJson(new VJCClonalAlignerParameters(0.3f,
                LinearGapAlignmentScoring.getNucleotideBLASTScoring(), null, 2, 3), true);

        TestUtil.assertJson(new VJCClonalAlignerParameters(0.3f,
                LinearGapAlignmentScoring.getNucleotideBLASTScoring(), 1, 2, 3), true);
    }
}
