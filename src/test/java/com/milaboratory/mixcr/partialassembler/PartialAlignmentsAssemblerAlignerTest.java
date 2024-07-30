/*
 * Copyright (c) 2014-2024, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
package com.milaboratory.mixcr.partialassembler;

import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.tests.MiXCRTestUtils;
import com.milaboratory.mixcr.tests.TargetBuilder;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.mixcr.vdjaligners.VDJCParametersPresets;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGene;
import io.repseq.core.VDJCLibrary;
import io.repseq.core.VDJCLibraryRegistry;
import org.apache.commons.math3.random.Well44497b;
import org.junit.Assert;
import org.junit.Test;

public class PartialAlignmentsAssemblerAlignerTest {
    @Test
    public void basicTest1() {
        Well44497b rg = new Well44497b(12312);

        VDJCAlignerParameters rnaSeqParams = VDJCParametersPresets.getByName("rna-seq");
        PartialAlignmentsAssemblerAligner aligner = new PartialAlignmentsAssemblerAligner(rnaSeqParams);

        VDJCLibrary lib = VDJCLibraryRegistry.getDefault().getLibrary("default", "hs");

        for (VDJCGene gene : VDJCLibraryRegistry.getDefault().getLibrary("default", "hs").getPrimaryGenes())
            if (gene.isFunctional())
                aligner.addGene(gene);

        TargetBuilder.VDJCGenes genes = new TargetBuilder.VDJCGenes(lib,
                "TRBV12-3*00", "TRBD1*00", "TRBJ1-3*00", "TRBC2*00");

        //                                 | 305
        // 250V + 55CDR3 (20V 7N 10D 3N 15J) + 28J + 100C
        NucleotideSequence baseSeq = TargetBuilder.generateSequence(genes, "{CDR3Begin(-250)}V*270 NNNNNNN {DBegin(0)}D*10 NNN {CDR3End(-15):FR4End} {CBegin}C*100", rg);
        
        int len = 70;
        NucleotideSequence seq1 = baseSeq.getRange(0, len);
        NucleotideSequence seq2 = baseSeq.getRange(245, 245 + len);
        NucleotideSequence seq3 = baseSeq.getRange(319, 319 + len);

        VDJCMultiRead read = MiXCRTestUtils.createMultiRead(seq1, seq2, seq3);
        VDJCAlignments al = aligner.process(read);
        Assert.assertNotNull(al);
        assertInHits(genes.v, al);
        assertInHits(genes.d, al);
        assertInHits(genes.j, al);
        assertInHits(genes.c, al);

        VDJCHit bestV = al.getBestHit(GeneType.Variable);
        VDJCHit bestD = al.getBestHit(GeneType.Diversity);
        VDJCHit bestJ = al.getBestHit(GeneType.Joining);
        VDJCHit bestC = al.getBestHit(GeneType.Constant);

        System.out.println(bestV.getGene().getName());
        System.out.println(bestD.getGene().getName());
        System.out.println(bestJ.getGene().getName());
        System.out.println(bestC.getGene().getName());

        Assert.assertNotNull(bestV.getAlignment(0));
        Assert.assertNotNull(bestV.getAlignment(1));
        Assert.assertNull(bestV.getAlignment(2));

        Assert.assertNull(bestD.getAlignment(0));
        Assert.assertNotNull(bestD.getAlignment(1));
        Assert.assertNull(bestD.getAlignment(2));

        Assert.assertNull(bestJ.getAlignment(0));
        Assert.assertNotNull(bestJ.getAlignment(1));
        Assert.assertNotNull(bestJ.getAlignment(2));

        Assert.assertNull(bestC.getAlignment(0));
        Assert.assertNull(bestC.getAlignment(1));
        Assert.assertNotNull(bestC.getAlignment(2));
    }

    public void assertInHits(VDJCGene gene, VDJCAlignments al) {
        boolean inHits = false;
        for (VDJCHit hit : al.getHits(gene.getGeneType()))
            if (hit.getGene().getId().equals(gene.getId())) {
                inHits = true;
                break;
            }
        Assert.assertTrue(gene.getId().toString() + " in hits.", inHits);
    }

}
