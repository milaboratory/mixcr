/*
 * Copyright (c) 2014-2019, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
 * commercial purposes should contact MiLaboratory LLC, which owns exclusive
 * rights for distribution of this program for commercial purposes, using the
 * following email address: licensing@milaboratory.com.
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
//        "/Users/poslavsky/Projects/milab/temp/al_1.vdjca",
//        "/Users/poslavsky/Projects/milab/temp/al_2.vdjca"

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