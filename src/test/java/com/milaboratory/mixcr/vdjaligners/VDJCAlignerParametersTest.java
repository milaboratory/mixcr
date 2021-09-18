/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 * 
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 * 
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.vdjaligners;

import com.milaboratory.core.PairedEndReadsLayout;
import com.milaboratory.core.alignment.AffineGapAlignmentScoring;
import com.milaboratory.core.alignment.LinearGapAlignmentScoring;
import com.milaboratory.core.alignment.kaligner1.KAlignerParameters;
import com.milaboratory.core.merger.MergerParameters;
import com.milaboratory.core.merger.QualityMergingAlgorithm;
import com.milaboratory.util.GlobalObjectMappers;
import io.repseq.core.GeneFeature;
import org.junit.Test;

import static com.milaboratory.core.merger.MergerParameters.IdentityType.Unweighted;
import static org.junit.Assert.assertEquals;

public class VDJCAlignerParametersTest {
    @Test
    public void test1() throws Exception {
        VDJCAlignerParameters paramentrs = new VDJCAlignerParameters(
                new KGeneAlignmentParameters(GeneFeature.VRegion, 120, 0.87f,
                        new KAlignerParameters(5, false, false,
                                1.5f, 0.75f, 1.0f, -0.1f, -0.3f, 4, 10, 15, 2, -10, 40.0f, 0.87f, 7,
                                LinearGapAlignmentScoring.getNucleotideBLASTScoring())),
                new DAlignerParameters(GeneFeature.DRegion,
                        0.85f, 30.0f, 3, AffineGapAlignmentScoring.getNucleotideBLASTScoring()),
                new KGeneAlignmentParameters(GeneFeature.JRegion, 120, 0.87f,
                        new KAlignerParameters(5, false, false,
                                1.5f, 0.75f, 1.0f, -0.1f, -0.3f, 4, 10, 15, 2, -10, 40.0f, 0.87f, 7,
                                LinearGapAlignmentScoring.getNucleotideBLASTScoring())),
                new KGeneAlignmentParameters(GeneFeature.CExon1, 120, 0.87f,
                        new KAlignerParameters(5, false, false,
                                1.5f, 0.75f, 1.0f, -0.1f, -0.3f, 4, 10, 15, 2, -10, 40.0f, 0.87f, 7,
                                LinearGapAlignmentScoring.getNucleotideBLASTScoring())),
                VJAlignmentOrder.JThenV,
                false, false,
                120.0f, 5, 0.7f, false, false, false, PairedEndReadsLayout.Opposite, new MergerParameters(
                QualityMergingAlgorithm.SumSubtraction, null, 12, null, 0.12, Unweighted), false, 5, 120, 10, true, true);

        String str = GlobalObjectMappers.PRETTY.writeValueAsString(paramentrs);
        VDJCAlignerParameters deser = GlobalObjectMappers.PRETTY.readValue(str, VDJCAlignerParameters.class);
        assertEquals(paramentrs, deser);
        VDJCAlignerParameters clone = deser.clone();
        assertEquals(paramentrs, clone);
    }
}
