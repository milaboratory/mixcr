package com.milaboratory.mixcr.util;

import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.reference.GeneType;
import com.milaboratory.mixcr.reference.Locus;
import com.milaboratory.mixcr.vdjaligners.VDJCAligner;
import org.junit.Assert;
import org.junit.Test;

import java.util.Set;

/**
 * Created by poslavsky on 01/09/15.
 */
public class RunMiXCRTest {
    @Test
    public void test1() throws Exception {
        RunMiXCR.RunMiXCRAnalysis params = new RunMiXCR.RunMiXCRAnalysis(
                RunMiXCR.class.getResource("/sequences/test_R1.fastq").getFile(),
                RunMiXCR.class.getResource("/sequences/test_R2.fastq").getFile());

        RunMiXCR.AlignResult align = RunMiXCR.align(params);
        RunMiXCR.AssembleResult assemble = RunMiXCR.assemble(align);

        for (Clone clone : assemble.cloneSet.getClones()) {
            Set<Locus> vjLoci = VDJCAligner.getPossibleDLoci(clone.getHits(GeneType.Variable), clone.getHits(GeneType.Joining));
            for (VDJCHit dHit : clone.getHits(GeneType.Diversity))
                Assert.assertTrue(vjLoci.contains(dHit.getAllele().getLocus()));
        }
    }
}