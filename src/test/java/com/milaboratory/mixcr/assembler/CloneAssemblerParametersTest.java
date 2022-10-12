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

        CloneAssemblerParameters params = new CloneAssemblerParameters(
                new GeneFeature[]{GeneFeature.FR1, GeneFeature.CDR3}, 12,
                QualityAggregationType.Average,
                new CloneClusteringParameters(2, 1, -1, TreeSearchParameters.ONE_MISMATCH, new RelativeConcentrationFilter(1.0E-6)),
                factoryParameters, true, true, false,
                0.4, 2.0, 2.0, true, (byte) 20, .8, "2", (byte) 15, null);

        String str = GlobalObjectMappers.getPretty().writeValueAsString(params);
        //System.out.println(str);
        CloneAssemblerParameters deser = GlobalObjectMappers.getPretty().readValue(str, CloneAssemblerParameters.class);
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
                new CloneClusteringParameters(2, 1, -1, TreeSearchParameters.ONE_MISMATCH, new RelativeConcentrationFilter(1.0E-6)),
                factoryParameters, true, true, false, 0.4, 2.0, 2.0, true, (byte) 20, .8, "2of6", (byte) 15, null);

        String str = GlobalObjectMappers.getPretty().writeValueAsString(params);
        //System.out.println(str);
        CloneAssemblerParameters deser = GlobalObjectMappers.getPretty().readValue(str, CloneAssemblerParameters.class);
        assertEquals(params, deser);
        CloneAssemblerParameters clone = deser.clone();
        assertEquals(params, clone);
        deser.getCloneFactoryParameters().getVParameters().setRelativeMinScore(0.34f);
        assertFalse(clone.equals(deser));
    }
}