/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 * 
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 * 
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
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
