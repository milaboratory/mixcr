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

import com.milaboratory.core.alignment.LinearGapAlignmentScoring;
import com.milaboratory.core.alignment.kaligner1.KAlignerParameters;
import com.milaboratory.util.GlobalObjectMappers;
import io.repseq.core.GeneFeature;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class KGeneAlignmentParametersTest {
    @Test
    public void test1() throws Exception {
        KGeneAlignmentParameters paramentrs = new KGeneAlignmentParameters(GeneFeature.VRegion, 120, 0.84f, 10,
                new KAlignerParameters(5, false, false,
                        1.5f, 0.75f, 1.0f, -0.1f, -0.3f, 4, 10, 15, 2, -10, 40.0f, 0.87f, 7,
                        LinearGapAlignmentScoring.getNucleotideBLASTScoring()));
        String str = GlobalObjectMappers.getPretty().writeValueAsString(paramentrs);
        System.out.println(str);
        KGeneAlignmentParameters deser = GlobalObjectMappers.getPretty().readValue(str, KGeneAlignmentParameters.class);
        assertEquals(paramentrs, deser);
        KGeneAlignmentParameters clone = deser.clone();
        assertEquals(paramentrs, clone);
    }
}