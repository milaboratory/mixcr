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
package com.milaboratory.mixcr.tests;

import cc.redberry.pipe.CUtils;
import com.milaboratory.core.sequence.AminoAcidAlphabet;
import com.milaboratory.core.sequence.AminoAcidSequence;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.mixcr.basictypes.*;
import io.repseq.core.GeneFeature;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Paths;

public class BackwardCompatibilityTests {
    @Test
    public void testAlignments() throws Exception {
        // assertGoodVDJCA("/backward_compatibility/3.0.4/test.vdjca", 8);
    }

    public static void assertGoodVDJCA(String resource, int size) throws IOException {
        try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(BackwardCompatibilityTests.class
                .getResource(resource).getFile())) {
            int countGood = 0;
            for (VDJCAlignments vdjcAlignments : CUtils.it(reader)) {
                NSequenceWithQuality cdr3NQ = vdjcAlignments.getFeature(GeneFeature.CDR3);
                if (cdr3NQ == null)
                    continue;
                AminoAcidSequence aaCDR3 = AminoAcidSequence.translateFromCenter(cdr3NQ.getSequence());
                if (aaCDR3.codeAt(0) == AminoAcidAlphabet.C &&
                        aaCDR3.codeAt(aaCDR3.size() - 1) == AminoAcidAlphabet.F)
                    ++countGood;
            }
            Assert.assertEquals(size, countGood);
        }
    }

    @Test
    public void testCloneset() throws Exception {
        // assertGoodCLNS("/backward_compatibility/3.0.4/test.clna", 2, 2, 2);
    }

    public static void assertGoodCLNS(String resource, int size, int good, double sumCount) throws IOException {
        CloneSet cloneSet = CloneSetIO.read(Paths.get(BackwardCompatibilityTests.class
                .getResource(resource).getFile()));
        Assert.assertEquals(size, cloneSet.size());
        int countGood = 0;
        for (Clone clone : cloneSet.getClones()) {
            sumCount -= clone.getCount();
            AminoAcidSequence aaCDR3 = AminoAcidSequence.translateFromCenter(clone.getFeature(GeneFeature.CDR3).getSequence());
            if (aaCDR3.codeAt(0) == AminoAcidAlphabet.C &&
                    aaCDR3.codeAt(aaCDR3.size() - 1) == AminoAcidAlphabet.F) {
                ++countGood;
            }
        }
        Assert.assertEquals(0, sumCount, 0.01);
        Assert.assertEquals(good, countGood);
    }
}
