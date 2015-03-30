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
package com.milaboratory.mixcr.reference;

import com.milaboratory.core.Range;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;

public class AlleleTest {
    final NucleotideSequence seq = new NucleotideSequence("AT" +
            "TAG" +
            "ACG" +
            "CGAT" +
            "GT" +
            "GAC" +
            "TG" +
            "CGC" +
            "TGT" +
            "AGTA" +
            "GCTGCGTATGACAT");
    ReferenceAllele referenceAllele;
    AllelicVariant variantAllele;
    LociLibrary ll;
    LocusContainer container;

    @Before
    public void setUp() throws Exception {
        SequenceBase base = new SequenceBase();
        base.put("A", 0, seq);
        int[] referencePoints = {0, 2, 5, 8,
                12, 14, 17, 19, 22, 25, 29, 29};

        ll = mock(LociLibrary.class);
        when(ll.getBase()).thenReturn(base);
        container = mock(LocusContainer.class);
        when(container.getLibrary()).thenReturn(ll);
        when(container.getSpeciesAndLocus()).thenReturn(new SpeciesAndLocus(1, Locus.TRB));
        when(container.getUUID()).thenReturn(UUID.randomUUID());

        Gene gene = new Gene(0, "Gene", GeneGroup.TRBV, container);

        ReferencePoints refPoints = new ReferencePoints(0, referencePoints);
        referenceAllele = new ReferenceAllele(gene, "Vasily", true, "A", refPoints);
        variantAllele = new AllelicVariant("Vasily", true, GeneFeature.VRegion, referenceAllele,
                Mutations.decode("DT1DG2", NucleotideSequence.ALPHABET));
        //container, 0, gene, true, true, "Vasiliy", "A", referencePoints, null, null);
    }

    @After
    public void tearDown() throws Exception {
        verifyNoMoreInteractions(ignoreStubs(ll, container));
    }

    @Test
    public void test1() throws Exception {
        assertEquals(seq.getRange(2, 5).concatenate(seq.getRange(8, 29)),
                referenceAllele.getFeature(GeneFeature.VTranscriptWithout5UTR));
    }

    @Test
    public void test2() throws Exception {
        assertEquals(seq.getRange(12, 14),
                referenceAllele.getFeature(GeneFeature.FR1));
    }

    @Test
    public void test3() throws Exception {
        assertEquals(seq.getRange(0, 5).concatenate(seq.getRange(8, 29)),
                referenceAllele.getFeature(GeneFeature.VTranscript));
    }

    @Test
    public void test4() throws Exception {
        Assert.assertEquals(new ReferencePoints(0, new int[]{-1, 0, 3, 3, 7, 9, 12, 14, 17, 20, 24, 24}),
                referenceAllele.getPartitioning().getRelativeReferencePoints(GeneFeature.VTranscriptWithout5UTR));
    }

    @Test
    public void test5() throws Exception {
        Assert.assertEquals(new ReferencePoints(0, new int[]{0, 2, 5, 5, 9, 11, 14, 16, 19, 22, 26, 26}),
                referenceAllele.getPartitioning().getRelativeReferencePoints(GeneFeature.VTranscript));
    }

    @Test
    public void test6() throws Exception {
        assertEquals(new Range(5, 11),
                referenceAllele.getPartitioning().getRelativeRange(GeneFeature.VTranscriptWithout5UTR,
                        new GeneFeature(GeneFeature.FR1, -2, +2))
        );
    }

    @Test
    public void test6_1() throws Exception {
        assertEquals(new Range(0, referenceAllele.getPartitioning().getLength(GeneFeature.VTranscriptWithout5UTR)),
                referenceAllele.getPartitioning().getRelativeRange(GeneFeature.VTranscriptWithout5UTR,
                        GeneFeature.VTranscriptWithout5UTR)
        );
    }

    @Test
    public void test6_2() throws Exception {
        assertEquals(new Range(2, referenceAllele.getPartitioning().getLength(GeneFeature.VTranscriptWithout5UTR) - 2),
                referenceAllele.getPartitioning().getRelativeRange(GeneFeature.VTranscriptWithout5UTR,
                        new GeneFeature(GeneFeature.VTranscriptWithout5UTR, 2, -2))
        );
    }

    @Test
    public void test7() throws Exception {
        assertEquals(5,
                referenceAllele.getPartitioning().getRelativePosition(GeneFeature.VTranscriptWithout5UTR,
                        new ReferencePoint(ReferencePoint.L2End, -2))
        );
    }

    @Test
    public void test8() throws Exception {
        assertEquals(-1,
                referenceAllele.getPartitioning().getRelativePosition(GeneFeature.VTranscriptWithout5UTR,
                        new ReferencePoint(ReferencePoint.L2End, -100))
        );

        assertEquals(-1,
                referenceAllele.getPartitioning().getRelativePosition(GeneFeature.VTranscriptWithout5UTR,
                        new ReferencePoint(ReferencePoint.L2End, +400))
        );
    }

    @Test
    public void test9() throws Exception {
        assertNull(referenceAllele.getPartitioning().getRelativeRange(GeneFeature.VTranscriptWithout5UTR,
                        new GeneFeature(GeneFeature.FR1, -2, +50))
        );
    }

    @Test
    public void test10() throws Exception {
        assertEquals(seq.getRange(29, 34),
                referenceAllele.getFeature(new GeneFeature(ReferencePoint.VEnd, 0, 5)));
    }

    @Test
    public void test11() throws Exception {
        assertEquals(seq.getRange(12, 13),
                variantAllele.getFeature(GeneFeature.FR1));
    }

    @Test
    public void test12() throws Exception {
        assertEquals(seq.getRange(15, 17),
                variantAllele.getFeature(GeneFeature.CDR1));
    }

    @Test
    public void test13() throws Exception {
        assertEquals(new Range(1, 2),
                referenceAllele.getPartitioning().getRelativeRange(new GeneFeature(GeneFeature.Exon1, 1, 0),
                        new GeneFeature(ReferencePoint.L1End, -1, 0)));
        assertNull(referenceAllele.getPartitioning().getRelativeRange(new GeneFeature(GeneFeature.Exon1, 3, 0),
                new GeneFeature(ReferencePoint.L1End, -1, 0)));
    }
}
