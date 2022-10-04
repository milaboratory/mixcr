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
package com.milaboratory.mixcr.util;

import com.milaboratory.core.sequence.NSQTuple;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.SequenceHistory;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.partialassembler.PartialAlignmentsAssemblerAligner;
import com.milaboratory.mixcr.partialassembler.VDJCMultiRead;
import com.milaboratory.mixcr.tests.MiXCRTestUtils;
import com.milaboratory.mixcr.tests.TargetBuilder;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.mixcr.vdjaligners.VDJCParametersPresets;
import io.repseq.core.*;
import org.apache.commons.math3.random.Well44497b;
import org.junit.Assert;
import org.junit.Test;

public class VDJCObjectExtenderTest {
    @Test
    public void testTripleRead() throws Exception {
        final boolean print = true;
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
            public VDJCAlignments go(int len, int offset1, int offset2, int offset3) {
                NucleotideSequence seq1 = baseSeq.getRange(offset1, offset1 + len);
                NucleotideSequence seq2 = baseSeq.getRange(offset2, offset2 + len);
                NucleotideSequence seq3 = offset3 == -1 ? null : baseSeq.getRange(offset3, offset3 + len);

                VDJCMultiRead read = offset3 == -1 ?
                        MiXCRTestUtils.createMultiRead(seq1, seq2) :
                        MiXCRTestUtils.createMultiRead(seq1, seq2, seq3);

                SequenceHistory[] histories = new SequenceHistory[read.numberOfReads()];
                for (int i = 0; i < histories.length; i++)
                    histories[i] = new SequenceHistory.RawSequence(read.getId(), (byte) i,
                            false, read.getRead(i).getData().size());

                NSQTuple data = read.toTuple();
                VDJCAlignments al = aligner.process(data, read);
                al = al.setHistory(histories, new NSQTuple[]{data});
                Assert.assertNotNull(al);

                VDJCObjectExtender<VDJCAlignments> extender = new VDJCObjectExtender<>(Chains.TCR, (byte) 35,
                        rnaSeqParams.getVAlignerParameters().getParameters().getScoring(),
                        rnaSeqParams.getJAlignerParameters().getParameters().getScoring(),
                        100, 70,
                        ReferencePoint.CDR3Begin, ReferencePoint.CDR3End);

                MiXCRTestUtils.assertAlignments(al);

                if (print) {
                    MiXCRTestUtils.printAlignment(al);
                    System.out.println();
                    System.out.println("-------------------------------------------");
                    System.out.println();
                }

                VDJCAlignments processed = extender.process(al);

                if (print) {
                    MiXCRTestUtils.printAlignment(processed);
                    System.out.println();
                    System.out.println("===========================================");
                    System.out.println();
                    System.out.println();
                }

                MiXCRTestUtils.assertAlignments(processed);
                Assert.assertEquals(al.getFeature(GeneFeature.VDJunction), processed.getFeature(GeneFeature.VDJunction));
                Assert.assertEquals(al.getFeature(GeneFeature.DJJunction), processed.getFeature(GeneFeature.DJJunction));
                Assert.assertEquals(al.getFeature(GeneFeature.VJJunction), processed.getFeature(GeneFeature.VJJunction));

                return processed;
            }
        };

        VDJCAlignments a1 = goAssert.go(60, 245, 307, 450);
        Assert.assertEquals(2, a1.numberOfTargets());

        VDJCAlignments a2 = goAssert.go(60, 245, 315, 450);
        Assert.assertEquals(3, a2.numberOfTargets());

        VDJCAlignments a3 = goAssert.go(60, 245, 315, -1);
        Assert.assertEquals(2, a3.numberOfTargets());

        VDJCAlignments a4 = goAssert.go(60, 245, 307, -1);
        Assert.assertEquals(1, a4.numberOfTargets());

        VDJCAlignments a5 = goAssert.go(53, 252, 307, -1);
        Assert.assertEquals(1, a5.numberOfTargets());

        VDJCAlignments a6 = goAssert.go(53, 252, 307, 450);
        Assert.assertEquals(2, a6.numberOfTargets());
    }

    public interface F4 {
        VDJCAlignments go(int len, int offset1, int offset2, int offset3);
    }
}