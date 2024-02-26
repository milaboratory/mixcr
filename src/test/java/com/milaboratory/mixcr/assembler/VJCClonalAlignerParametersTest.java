/*
 * Copyright (c) 2014-2024, MiLaboratories Inc. All Rights Reserved
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

import com.milaboratory.core.alignment.LinearGapAlignmentScoring;
import com.milaboratory.test.TestUtil;
import com.milaboratory.util.GlobalObjectMappers;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VJCClonalAlignerParametersTest {
    @Test
    public void test1() throws Exception {
        VJCClonalAlignerParameters paramentrs = new VJCClonalAlignerParameters(0.3f, 3,
                LinearGapAlignmentScoring.getNucleotideBLASTScoring(), 3);
        String str = GlobalObjectMappers.getPretty().writeValueAsString(paramentrs);
        VJCClonalAlignerParameters deser = GlobalObjectMappers.getPretty().readValue(str, VJCClonalAlignerParameters.class);
        assertEquals(paramentrs, deser);
        assertEquals(paramentrs, deser.clone());
    }

    @Test
    public void test2() throws Exception {
        TestUtil.assertJson(new VJCClonalAlignerParameters(0.3f, 3,
                LinearGapAlignmentScoring.getNucleotideBLASTScoring(), 3), true);

        TestUtil.assertJson(new VJCClonalAlignerParameters(0.3f, 3,
                LinearGapAlignmentScoring.getNucleotideBLASTScoring(), null, 2, 3), true);

        TestUtil.assertJson(new VJCClonalAlignerParameters(0.3f, 3,
                LinearGapAlignmentScoring.getNucleotideBLASTScoring(), 1, 2, 3), true);
    }
}
