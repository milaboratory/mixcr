/*
 * Copyright (c) 2014-2015, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
 * (here and after addressed as Inventors)
 * All Rights Reserved
 *
 * Permission to use, copy, modify and distribute any part of this program for
 * educational, research and non-profit purposes, by non-profit institutions
 * only, without fee, and without a written agreement is hereby granted,
 * provided that the above copyright notice, this paragraph and the following
 * three paragraphs appear in all copies.
 *
 * Those desiring to incorporate this work into commercial products or use for
 * commercial purposes should contact the Inventors using one of the following
 * email addresses: chudakovdm@mail.ru, chudakovdm@gmail.com
 *
 * IN NO EVENT SHALL THE INVENTORS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
 * SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
 * ARISING OUT OF THE USE OF THIS SOFTWARE, EVEN IF THE INVENTORS HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * THE SOFTWARE PROVIDED HEREIN IS ON AN "AS IS" BASIS, AND THE INVENTORS HAS
 * NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR
 * MODIFICATIONS. THE INVENTORS MAKES NO REPRESENTATIONS AND EXTENDS NO
 * WARRANTIES OF ANY KIND, EITHER IMPLIED OR EXPRESS, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY OR FITNESS FOR A
 * PARTICULAR PURPOSE, OR THAT THE USE OF THE SOFTWARE WILL NOT INFRINGE ANY
 * PATENT, TRADEMARK OR OTHER RIGHTS.
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
                QualityMergingAlgorithm.SumSubtraction, null, 12, null, 0.12, Unweighted), false, 5, 120, 10, true);

        String str = GlobalObjectMappers.PRETTY.writeValueAsString(paramentrs);
        VDJCAlignerParameters deser = GlobalObjectMappers.PRETTY.readValue(str, VDJCAlignerParameters.class);
        assertEquals(paramentrs, deser);
        VDJCAlignerParameters clone = deser.clone();
        assertEquals(paramentrs, clone);
    }
}