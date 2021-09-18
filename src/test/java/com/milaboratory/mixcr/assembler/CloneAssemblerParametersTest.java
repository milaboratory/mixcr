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
import com.milaboratory.core.alignment.LinearGapAlignmentScoring;
import com.milaboratory.core.sequence.quality.QualityAggregationType;
import com.milaboratory.core.tree.TreeSearchParameters;
import com.milaboratory.util.GlobalObjectMappers;
import io.repseq.core.GeneFeature;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class CloneAssemblerParametersTest {
    @Test
    public void test1() throws Exception {
        CloneFactoryParameters factoryParameters = new CloneFactoryParameters(
                new VJCClonalAlignerParameters(0.3f,
                        LinearGapAlignmentScoring.getNucleotideBLASTScoring(), 3),
                new VJCClonalAlignerParameters(0.4f,
                        LinearGapAlignmentScoring.getNucleotideBLASTScoring(), 5),
                null, new DClonalAlignerParameters(0.85f, 30.0f, 3, AffineGapAlignmentScoring.getNucleotideBLASTScoring())
        );

        CloneAssemblerParameters params = new CloneAssemblerParameters(new GeneFeature[]{GeneFeature.FR1, GeneFeature.CDR3}, 12,
                QualityAggregationType.Average,
                new CloneClusteringParameters(2, 1, TreeSearchParameters.ONE_MISMATCH, new RelativeConcentrationFilter(1.0E-6)),
                factoryParameters, true, true, false, 0.4, 2.0, 2.0, true, (byte) 20, .8, "2", (byte) 15);

        String str = GlobalObjectMappers.PRETTY.writeValueAsString(params);
        //System.out.println(str);
        CloneAssemblerParameters deser = GlobalObjectMappers.PRETTY.readValue(str, CloneAssemblerParameters.class);
        assertEquals(params, deser);
        CloneAssemblerParameters clone = deser.clone();
        assertEquals(params, clone);
        deser.getCloneFactoryParameters().getVParameters().setRelativeMinScore(0.34f);
        assertFalse(clone.equals(deser));
        clone = params.clone();
        clone.setMappingThreshold("2of2");
        assertEquals(clone, params);
    }

    @Test
    public void test2() throws Exception {
        CloneFactoryParameters factoryParameters = new CloneFactoryParameters(
                new VJCClonalAlignerParameters(0.3f,
                        LinearGapAlignmentScoring.getNucleotideBLASTScoring(), 3),
                new VJCClonalAlignerParameters(0.4f,
                        LinearGapAlignmentScoring.getNucleotideBLASTScoring(), 5),
                null, new DClonalAlignerParameters(0.85f, 30.0f, 3, AffineGapAlignmentScoring.getNucleotideBLASTScoring())
        );

        CloneAssemblerParameters params = new CloneAssemblerParameters(new GeneFeature[]{GeneFeature.FR1, GeneFeature.CDR3}, 12,
                QualityAggregationType.Average,
                new CloneClusteringParameters(2, 1, TreeSearchParameters.ONE_MISMATCH, new RelativeConcentrationFilter(1.0E-6)),
                factoryParameters, true, true, false, 0.4, 2.0, 2.0, true, (byte) 20, .8, "2of6", (byte) 15);

        String str = GlobalObjectMappers.PRETTY.writeValueAsString(params);
        //System.out.println(str);
        CloneAssemblerParameters deser = GlobalObjectMappers.PRETTY.readValue(str, CloneAssemblerParameters.class);
        assertEquals(params, deser);
        CloneAssemblerParameters clone = deser.clone();
        assertEquals(params, clone);
        deser.getCloneFactoryParameters().getVParameters().setRelativeMinScore(0.34f);
        assertFalse(clone.equals(deser));
    }
}
