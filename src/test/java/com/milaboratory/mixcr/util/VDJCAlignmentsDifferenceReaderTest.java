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

import cc.redberry.pipe.CUtils;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import io.repseq.core.GeneFeature;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * Created by poslavsky on 29/01/16.
 */
public class VDJCAlignmentsDifferenceReaderTest {
    @Test
    public void test1() throws Exception {
        RunMiXCR.RunMiXCRAnalysis params = new RunMiXCR.RunMiXCRAnalysis(
                RunMiXCR.class.getResource("/sequences/test_R1.fastq").getFile(),
                RunMiXCR.class.getResource("/sequences/test_R2.fastq").getFile()
        );
        List<VDJCAlignments> source1 = RunMiXCR.align(params).alignments;
        List<VDJCAlignments> source2 = RunMiXCR.align(params).alignments;


        int onlyInFirst = 0, onlyInSecond = 0, diff = 0, same = 0;
        VDJCAlignmentsDifferenceReader reader = new VDJCAlignmentsDifferenceReader(
                CUtils.asOutputPort(source1),
                CUtils.asOutputPort(source2),
                GeneFeature.CDR3,
                1);
        for (VDJCAlignmentsDifferenceReader.Diff d : CUtils.it(reader)) {
            switch (d.status) {
                case AlignmentPresentOnlyInFirst: ++onlyInFirst; break;
                case AlignmentPresentOnlyInSecond: ++onlyInSecond; break;
                case AlignmentsAreDifferent: ++diff; break;
                case AlignmentsAreSame: ++same; break;
            }
        }

        System.out.println(onlyInFirst);
        System.out.println(onlyInSecond);
        System.out.println(diff);
        System.out.println(same);

        Assert.assertEquals(source1.size(), onlyInFirst + diff + same);
        Assert.assertEquals(source2.size(), onlyInSecond + diff + same);
    }
}