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
package com.milaboratory.mixcr.tests;

import com.milaboratory.core.sequence.NucleotideSequence;
import io.repseq.core.VDJCLibrary;
import io.repseq.core.VDJCLibraryRegistry;
import org.apache.commons.math3.random.Well44497b;
import org.junit.Assert;
import org.junit.Test;

public class TargetBuilderTest {
    @Test
    public void testPreProcess1() throws Exception {
        String model = "{CDR3Begin(-20)}VVVVVVVVVVVvVVVvVVVVVVVVVVVVVNNNNNNN{DBegin(0)}DDDDDDDDDDNNN{CDR3End(-10)}JJJJJJJJJJJJJJJJJJJJ";
        String model1 = "{CDR3Begin(-20)}VVVVVVVVVVVvVVVv*3VVVVVVVVVVVVVNNNNNNN{DBegin(0)}DDDDDDDDDDNNN{CDR3End(-10)}JJJJJJJJJJJJJJJJJJJJ";
        String model1R = "{CDR3Begin(-20)}VVVVVVVVVVVvVVVvvvVVVVVVVVVVVVVNNNNNNN{DBegin(0)}DDDDDDDDDDNNN{CDR3End(-10)}JJJJJJJJJJJJJJJJJJJJ";
        String model2 = "{CDR3Begin(-20)}VVVVVVVVVVVvVVVV*10VVVVVVVVVVVVVNNNNNNN{DBegin(0)}DDDDDDDDDDNNN{CDR3End(-10)}JJJJJJJJJJJJJJJJJJJJ";
        String model2R = "{CDR3Begin(-20)}VVVVVVVVVVVvVVVVVVVVVVVVVVVVVVVVVVVVVVNNNNNNN{DBegin(0)}DDDDDDDDDDNNN{CDR3End(-10)}JJJJJJJJJJJJJJJJJJJJ";
        Assert.assertEquals(model, TargetBuilder.preProcessModel(model));
        Assert.assertEquals(model1R, TargetBuilder.preProcessModel(model1));
        Assert.assertEquals(model2R, TargetBuilder.preProcessModel(model2));
    }

    @Test
    public void testGenerate1() throws Exception {
        VDJCLibrary library = VDJCLibraryRegistry.getDefault().getLibrary("default", "hs");
        TargetBuilder.VDJCGenes genes = new TargetBuilder.VDJCGenes(library,
                "TRBV12-1*00", "TRBD1*00", "TRBJ1-3*00", "TRBC2*00");

        String model = "{CDR3Begin(-20)}VVVVVVVVVVVvVVVvVVVVVVVVVVVVVNNNNNNN{DBegin(0)}DDDDDDDDDDNN{CDR3End(-10)}JJJJJJJJJJJJJJJJJJJJ";
        NucleotideSequence nucleotideSequence = TargetBuilder.generateSequence(genes, model, new Well44497b());
        Assert.assertEquals(36+12+20, nucleotideSequence.size());
    }
}
