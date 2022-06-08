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
package com.milaboratory.mixcr.cli;

import com.milaboratory.core.alignment.AffineGapAlignmentScoring;
import com.milaboratory.core.alignment.LinearGapAlignmentScoring;
import com.milaboratory.core.alignment.kaligner1.KAlignerParameters;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.quality.QualityAggregationType;
import com.milaboratory.core.tree.TreeSearchParameters;
import com.milaboratory.mixcr.assembler.*;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.mixcr.vdjaligners.VDJCParametersPresets;
import com.milaboratory.util.JsonOverrider;
import io.repseq.core.GeneFeature;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class JsonOverriderTest {
    @Test
    public void test1() throws Exception {
        KAlignerParameters parameters = KAlignerParameters.getByName("default");
        KAlignerParameters override = JsonOverrider.override(
                parameters,
                KAlignerParameters.class,
                "floatingLeftBound=true",
                "scoring.subsMatrix=simple(match=4,mismatch=-9)");
        KAlignerParameters expected = parameters.clone().setFloatingLeftBound(true)
                .setScoring(new LinearGapAlignmentScoring(NucleotideSequence.ALPHABET, 4, -9, parameters.getScoring().getGapPenalty()));
        Assert.assertEquals(expected, override);
    }

    @Test
    public void test1a() throws Exception {
        KAlignerParameters parameters = KAlignerParameters.getByName("default");
        KAlignerParameters override = JsonOverrider.override(
                parameters,
                KAlignerParameters.class,
                "floatingLeftBound=true",
                "scoring.subsMatrix='simple(match=4,mismatch=-9)'");
        KAlignerParameters expected = parameters.clone().setFloatingLeftBound(true)
                .setScoring(new LinearGapAlignmentScoring(NucleotideSequence.ALPHABET, 4, -9, parameters.getScoring().getGapPenalty()));
        Assert.assertEquals(expected, override);
    }

    @Test
    public void test2() throws Exception {
        KAlignerParameters parameters = KAlignerParameters.getByName("default");
        KAlignerParameters override = JsonOverrider.override(
                parameters,
                KAlignerParameters.class,
                "floatingLeftBound=true",
                "subsMatrix=simple(match=4,mismatch=-9)");
        KAlignerParameters expected = parameters.clone().setFloatingLeftBound(true)
                .setScoring(new LinearGapAlignmentScoring(NucleotideSequence.ALPHABET, 4, -9, parameters.getScoring().getGapPenalty()));
        Assert.assertEquals(expected, override);
    }

    @Test
    public void testArray1() throws Exception {
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
                factoryParameters, true, true, false, 0.4, 2.0, 2.0, true, (byte) 20, .8, "2of6", (byte) 15);

        CloneAssemblerParameters override = JsonOverrider.override(
                params,
                CloneAssemblerParameters.class,
                "assemblingFeatures=[CDR1(-5,+6),CDR2]");

        CloneAssemblerParameters expected = new CloneAssemblerParameters(new GeneFeature[]{new GeneFeature(GeneFeature.CDR1, -5, +6), GeneFeature.CDR2}, 12,
                QualityAggregationType.Average,
                new CloneClusteringParameters(2, 1, -1, TreeSearchParameters.ONE_MISMATCH, new RelativeConcentrationFilter(1.0E-6)),
                factoryParameters, true, true, false, 0.4, 2.0, 2.0, true, (byte) 20, .8, "2of6", (byte) 15);


        Assert.assertEquals(expected, override);
    }

    @Test
    public void testCloneFactoryParameters2() throws Exception {
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
                factoryParameters, true, true, false, 0.4, 2.0, 2.0, true, (byte) 20, .8, "2of6", (byte) 15);

        CloneAssemblerParameters override = JsonOverrider.override(
                params,
                CloneAssemblerParameters.class,
                "dParameters.absoluteMinScore=101");

        CloneFactoryParameters expectedFactoryParameters = new CloneFactoryParameters(
                new VJCClonalAlignerParameters(0.3f,
                        LinearGapAlignmentScoring.getNucleotideBLASTScoring(), 3),
                new VJCClonalAlignerParameters(0.4f,
                        LinearGapAlignmentScoring.getNucleotideBLASTScoring(), 5),
                null, new DClonalAlignerParameters(0.85f, 101f, 3, AffineGapAlignmentScoring.getNucleotideBLASTScoring())
        );

        Assert.assertEquals(expectedFactoryParameters, override.getCloneFactoryParameters());
    }

    @Test
    public void test3() throws Exception {
        GeneFeature jRegion = GeneFeature.parse("JRegion");
        System.out.println(jRegion);
        System.out.println(GeneFeature.encode(jRegion));

        VDJCAlignerParameters params = VDJCParametersPresets.getByName("default");
        Map<String, String> overrides = new HashMap<String, String>() {{
            put("vParameters.geneFeatureToAlign", "VTranscript");
        }};

        Assert.assertNotNull(JsonOverrider.override(params, VDJCAlignerParameters.class, overrides));
    }
}
