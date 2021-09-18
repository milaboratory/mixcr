/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 * 
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 * 
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.assembler;

import com.milaboratory.core.alignment.AffineGapAlignmentScoring;
import com.milaboratory.core.alignment.BandedAlignerParameters;
import com.milaboratory.core.alignment.LinearGapAlignmentScoring;
import com.milaboratory.mixcr.vdjaligners.DAlignerParameters;
import com.milaboratory.util.GlobalObjectMappers;
import io.repseq.core.GeneFeature;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class CloneFactoryParametersTest {
    @Test
    public void test1() throws Exception {
        CloneFactoryParameters paramentrs = new CloneFactoryParameters(
                new VJCClonalAlignerParameters(0.3f,
                        LinearGapAlignmentScoring.getNucleotideBLASTScoring(), 3),
                new VJCClonalAlignerParameters(0.4f,
                        LinearGapAlignmentScoring.getNucleotideBLASTScoring(), 5),
                new VJCClonalAlignerParameters(0.2f,
                        LinearGapAlignmentScoring.getNucleotideBLASTScoring(), 9),
                new DClonalAlignerParameters(0.85f, 30.0f, 3, AffineGapAlignmentScoring.getNucleotideBLASTScoring())
        );
        String str = GlobalObjectMappers.PRETTY.writeValueAsString(paramentrs);
        //System.out.println(str);
        CloneFactoryParameters deser = GlobalObjectMappers.PRETTY.readValue(str, CloneFactoryParameters.class);
        assertEquals(paramentrs, deser);
        CloneFactoryParameters clone = deser.clone();
        assertEquals(paramentrs, clone);
        deser.getVParameters().setRelativeMinScore(0.34f);
        assertFalse(clone.equals(deser));
    }

    @Test
    public void test2() throws Exception {
        CloneFactoryParameters paramentrs = new CloneFactoryParameters(
                new VJCClonalAlignerParameters(0.3f,
                        LinearGapAlignmentScoring.getNucleotideBLASTScoring(), 3),
                new VJCClonalAlignerParameters(0.4f,
                        LinearGapAlignmentScoring.getNucleotideBLASTScoring(), 5),
                null, new DClonalAlignerParameters(0.85f, 30.0f, 3, AffineGapAlignmentScoring.getNucleotideBLASTScoring())
        );
        String str = GlobalObjectMappers.PRETTY.writeValueAsString(paramentrs);
        //System.out.println(str);
        CloneFactoryParameters deser = GlobalObjectMappers.PRETTY.readValue(str, CloneFactoryParameters.class);
        assertEquals(paramentrs, deser);
        CloneFactoryParameters clone = deser.clone();
        assertEquals(paramentrs, clone);
        deser.getVParameters().setRelativeMinScore(0.34f);
        assertFalse(clone.equals(deser));
    }
}
