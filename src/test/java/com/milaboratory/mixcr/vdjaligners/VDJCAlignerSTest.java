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

import cc.redberry.pipe.CUtils;
import com.milaboratory.core.io.sequence.SingleRead;
import com.milaboratory.core.io.sequence.fastq.SingleFastqReader;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter;
import com.milaboratory.mixcr.reference.*;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public class VDJCAlignerSTest {
    @Test
    public void testSerialization1() throws Exception {
        VDJCAlignerParameters parameters =
                VDJCParametersPresets.getByName("default");
        LociLibrary ll = LociLibraryManager.getDefault().getLibrary("mi");
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        List<VDJCAlignments> alignemntsList = new ArrayList<>();
        int header;
        try (SingleFastqReader reader =
                     new SingleFastqReader(
                             VDJCAlignerSTest.class.getClassLoader()
                                     .getResourceAsStream("sequences/sample_IGH_R1.fastq"), true)) {
            VDJCAlignerS aligner = new VDJCAlignerS(parameters);
            for (Allele allele : ll.getLocus(Species.HomoSapiens, Locus.IGH).getAllAlleles())
                if (parameters.containsRequiredFeature(allele))
                    aligner.addAllele(allele);
            try (VDJCAlignmentsWriter writer = new VDJCAlignmentsWriter(bos)) {
                writer.header(aligner);
                header = bos.size();
                for (SingleRead read : CUtils.it(reader)) {
                    VDJCAlignmentResult<SingleRead> result = aligner.process(read);
                    if (result.alignment != null) {
                        writer.write(result.alignment);
                        alignemntsList.add(result.alignment);
                    }
                }
            }
        }
        Assert.assertTrue(alignemntsList.size() > 10);
        System.out.println("Bytes per alignment: " + (bos.size() - header) / alignemntsList.size());
        try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(new ByteArrayInputStream(bos.toByteArray()), ll)) {
            int i = 0;
            for (VDJCAlignments alignments : CUtils.it(reader))
                Assert.assertEquals(alignemntsList.get(i++), alignments);
        }
    }

//    @Test
//    public void testSerializationAndFilter() throws Exception {
//        Assume.assumeTrue(TestUtil.lt());
//        VDJCAlignerParameters parameters =
//                VDJCParametersPresets.getByName("default");
//        LociLibrary ll = LociLibraryManager.getDefault().getLibrary("mi");
//        ByteArrayOutputStream bos = new ByteArrayOutputStream();
//        List<VDJCAlignments> alignemntsList = new ArrayList<>();
//        int header;
//        try (SingleFastqReader reader =
//                     new SingleFastqReader(
//                             VDJCAlignerSTest.class.getClassLoader()
//                                     .getResourceAsStream("sequences/sample_IGH_R1.fastq"))) {
//            VDJCAlignerS aligner = new VDJCAlignerS(parameters);
//            for (Allele allele : ll.getLocus(Species.HomoSapiens, Locus.IGH).getAllAlleles())
//                if (parameters.containsRequiredFeature(allele))
//                    aligner.addAllele(allele);
//            int accepted = 0;
//            AFilter filter = AFilter.build("l = length(CDR3); targetAlignedTop(0, V) && l > 50");
//            try (VDJCAlignmentsWriter writer = new VDJCAlignmentsWriter(bos)) {
//                writer.header(aligner);
//                header = bos.size();
//                for (SingleRead read : CUtils.it(reader)) {
//                    VDJCAlignmentResult<SingleRead> result = aligner.process(read);
//                    if (result.alignment != null) {
//                        if (filter.accept(result.alignment))
//                            ++accepted;
//                        writer.write(result.alignment);
//                        alignemntsList.add(result.alignment);
//                    }
//                }
//            }
//            Assert.assertEquals(33, accepted, 5);
//        }
//        Assert.assertTrue(alignemntsList.size() > 10);
//        System.out.println("Bytes per alignment: " + (bos.size() - header) / alignemntsList.size());
//        try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(new ByteArrayInputStream(bos.toByteArray()), ll)) {
//            int i = 0;
//            for (VDJCAlignments alignments : CUtils.it(reader))
//                Assert.assertEquals(alignemntsList.get(i++), alignments);
//        }
//    }
}