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

import com.milaboratory.core.alignment.AffineGapAlignmentScoring;
import com.milaboratory.core.alignment.LinearGapAlignmentScoring;
import com.milaboratory.util.GlobalObjectMappers;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class CloneFactoryParametersTest {
    @Test
    public void test1() throws Exception {
        CloneFactoryParameters paramentrs = new CloneFactoryParameters(
                new VJCClonalAlignerParameters(0.3f, 3,
                        LinearGapAlignmentScoring.getNucleotideBLASTScoring(), 3),
                new VJCClonalAlignerParameters(0.4f, 3,
                        LinearGapAlignmentScoring.getNucleotideBLASTScoring(), 5),
                new VJCClonalAlignerParameters(0.2f, 3,
                        LinearGapAlignmentScoring.getNucleotideBLASTScoring(), 9),
                new DClonalAlignerParameters(0.85f, 30.0f, 3, AffineGapAlignmentScoring.getNucleotideBLASTScoring())
        );
        String str = GlobalObjectMappers.getPretty().writeValueAsString(paramentrs);
        //System.out.println(str);
        CloneFactoryParameters deser = GlobalObjectMappers.getPretty().readValue(str, CloneFactoryParameters.class);
        assertEquals(paramentrs, deser);
        CloneFactoryParameters clone = deser.clone();
        assertEquals(paramentrs, clone);
        deser.getVParameters().setRelativeMinScore(0.34f);
        assertFalse(clone.equals(deser));
    }

    @Test
    public void test2() throws Exception {
        CloneFactoryParameters paramentrs = new CloneFactoryParameters(
                new VJCClonalAlignerParameters(0.3f, 3,
                        LinearGapAlignmentScoring.getNucleotideBLASTScoring(), 3),
                new VJCClonalAlignerParameters(0.4f, 3,
                        LinearGapAlignmentScoring.getNucleotideBLASTScoring(), 5),
                null, new DClonalAlignerParameters(0.85f, 30.0f, 3, AffineGapAlignmentScoring.getNucleotideBLASTScoring())
        );
        String str = GlobalObjectMappers.getPretty().writeValueAsString(paramentrs);
        //System.out.println(str);
        CloneFactoryParameters deser = GlobalObjectMappers.getPretty().readValue(str, CloneFactoryParameters.class);
        assertEquals(paramentrs, deser);
        CloneFactoryParameters clone = deser.clone();
        assertEquals(paramentrs, clone);
        deser.getVParameters().setRelativeMinScore(0.34f);
        assertFalse(clone.equals(deser));
    }
}
