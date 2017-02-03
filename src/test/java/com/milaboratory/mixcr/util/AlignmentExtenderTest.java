/*
 * Copyright (c) 2014-2017, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
package com.milaboratory.mixcr.util;

import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.partialassembler.PartialAlignmentsAssemblerAligner;
import com.milaboratory.mixcr.partialassembler.PartialAlignmentsAssemblerAlignerTest;
import com.milaboratory.mixcr.partialassembler.VDJCMultiRead;
import com.milaboratory.mixcr.tests.MiXCRTestUtils;
import com.milaboratory.mixcr.tests.TargetBuilder;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignmentResult;
import com.milaboratory.mixcr.vdjaligners.VDJCParametersPresets;
import io.repseq.core.*;
import org.apache.commons.math3.random.Well44497b;
import org.junit.Assert;
import org.junit.Test;

public class AlignmentExtenderTest {
    @Test
    public void testTripleRead() throws Exception {
        Well44497b rg = new Well44497b(12312);

        final VDJCAlignerParameters rnaSeqParams = VDJCParametersPresets.getByName("rna-seq");
        final PartialAlignmentsAssemblerAligner aligner = new PartialAlignmentsAssemblerAligner(rnaSeqParams);

        final VDJCLibrary lib = VDJCLibraryRegistry.getDefault().getLibrary("default", "hs");

        for (VDJCGene gene : VDJCLibraryRegistry.getDefault().getLibrary("default", "hs").getGenes())
            if (gene.isFunctional())
                aligner.addGene(gene);

        TargetBuilder.VDJCGenes genes = new TargetBuilder.VDJCGenes(lib,
                "TRBV12-3*00", "TRBD1*00", "TRBJ1-3*00", "TRBC2*00");

        //                                 | 310  | 338   | 438
        // 250V + 60CDR3 (20V 7N 10D 3N 20J) + 28J + 100C + 100N
        final NucleotideSequence baseSeq = TargetBuilder.generateSequence(genes, "{CDR3Begin(-250)}V*270 NNNNNNN {DBegin(0)}D*10 NNN {CDR3End(-20):FR4End} {CBegin}C*100 N*100", rg);

        F4 goAssert = new F4() {
            @Override
            public void go(int len, int offset1, int offset2, int offset3) {
                NucleotideSequence seq1 = baseSeq.getRange(offset1, offset1 + len);
                NucleotideSequence seq2 = baseSeq.getRange(offset2, offset2 + len);
                NucleotideSequence seq3 = baseSeq.getRange(offset3, offset3 + len);

                VDJCAlignmentResult<VDJCMultiRead> alignment = aligner.process(PartialAlignmentsAssemblerAlignerTest.createMultiRead(seq1, seq2, seq3));
                VDJCAlignments al = alignment.alignment;
                Assert.assertNotNull(al);

                AlignmentExtender extender = new AlignmentExtender(Chains.TCR, (byte) 35,
                        rnaSeqParams.getVAlignerParameters().getParameters().getScoring(),
                        rnaSeqParams.getJAlignerParameters().getParameters().getScoring(),
                        ReferencePoint.CDR3Begin, ReferencePoint.CDR3End);

                MiXCRTestUtils.assertAlignments(al);
                MiXCRTestUtils.printAlignment(al);
                System.out.println("============================");
                VDJCAlignments processed = extender.process(al);
                MiXCRTestUtils.printAlignment(processed);
                MiXCRTestUtils.assertAlignments(processed);
            }
        };

        goAssert.go(60, 245, 307, 450);
        //goAssert.go(57, 0, 255, 320);
    }

    public interface F4 {
        void go(int len, int offset1, int offset2, int offset3);
    }
}