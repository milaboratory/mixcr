/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 *
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 *
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.vdjaligners;

import cc.redberry.pipe.CUtils;
import com.milaboratory.core.io.sequence.PairedRead;
import com.milaboratory.core.io.sequence.fastq.PairedFastqReader;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import io.repseq.core.Chains;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGene;
import io.repseq.core.VDJCLibraryRegistry;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by dbolotin on 05/08/15.
 */
public class VDJCAlignerWithMergeTest {
    @Test
    public void test1() throws Exception {
        VDJCAlignerParameters parameters =
                VDJCParametersPresets
                        .getByName("default");

        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        List<VDJCAlignments> alignemntsList = new ArrayList<>();

        int header;

        int total = 0;
        int leftHit = 0;

        try (PairedFastqReader reader =
                     new PairedFastqReader(
                             VDJCAlignerSTest.class.getClassLoader()
                                     .getResourceAsStream("sequences/sample_IGH_R1.fastq"),
                             VDJCAlignerSTest.class.getClassLoader()
                                     .getResourceAsStream("sequences/sample_IGH_R2.fastq"), true)) {

            VDJCAlignerWithMerge aligner = new VDJCAlignerWithMerge(parameters);

            for (VDJCGene gene : VDJCLibraryRegistry.getDefault().getLibrary("default", "hs").getGenes(Chains.IGH)) {
                if (parameters.containsRequiredFeature(gene))
                    aligner.addGene(gene);
            }

            for (PairedRead read : CUtils.it(reader)) {
                ++total;
                VDJCAlignmentResult<PairedRead> result = aligner.process(read);
                if (result.alignment != null) {
                    alignemntsList.add(result.alignment);
                    for (VDJCHit hit : result.alignment.getHits(GeneType.Variable))
                        if (hit.getAlignment(0) != null && hit.getAlignment(1) != null)
                            ++leftHit;
                }
            }
        }

        //for (VDJCAlignments alignments : alignemntsList) {
        //    for (int i = 0; i < alignments.numberOfTargets(); i++) {
        //        System.out.println(VDJCAlignmentsFormatter.getTargetAsMultiAlignment(alignments, i));
        //    }
        //}

        System.out.println(alignemntsList.size());
        System.out.println(total);
        System.out.println(leftHit);
        Assert.assertTrue(alignemntsList.size() > 10);

        //System.out.println("Bytes per alignment: " + (bos.size() - header) / alignemntsList.size());
        //
        //try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(new ByteArrayInputStream(bos.toByteArray()), ll)) {
        //    int i = 0;
        //    for (VDJCAlignments alignments : CUtils.it(reader))
        //        Assert.assertEquals(alignemntsList.get(i++), alignments);
        //}
    }
}
