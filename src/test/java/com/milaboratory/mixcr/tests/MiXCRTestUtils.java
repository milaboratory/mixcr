/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 *
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 *
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.tests;

import com.milaboratory.core.alignment.AlignerTest;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.alignment.MultiAlignmentHelper;
import com.milaboratory.core.io.sequence.SingleRead;
import com.milaboratory.core.io.sequence.SingleReadImpl;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequenceQuality;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsFormatter;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.partialassembler.VDJCMultiRead;
import io.repseq.core.GeneType;

public class MiXCRTestUtils {
    public static void assertAlignments(VDJCAlignments alignments) {
        for (GeneType gt : GeneType.VDJC_REFERENCE) {
            for (VDJCHit hit : alignments.getHits(gt)) {
                for (int targetIndex = 0; targetIndex < alignments.numberOfTargets(); targetIndex++) {
                    Alignment<NucleotideSequence> al = hit.getAlignment(targetIndex);
                    if(al == null)
                        continue;
                    NucleotideSequence sequence = alignments.getTarget(targetIndex).getSequence();
                    AlignerTest.assertAlignment(al, sequence);
                }
            }
        }
    }

    public static void printAlignment(VDJCAlignments alignments) {
        for (int i = 0; i < alignments.numberOfTargets(); i++) {
//            fixme
//            if (alignments.getTargetDescriptions() != null)
//                System.out.println(">>> Description: " + alignments.getTargetDescriptions()[i] + "\n");

            MultiAlignmentHelper targetAsMultiAlignment = VDJCAlignmentsFormatter.getTargetAsMultiAlignment(alignments, i);
            if (targetAsMultiAlignment == null)
                continue;
            MultiAlignmentHelper[] split = targetAsMultiAlignment.split(80);
            for (MultiAlignmentHelper spl : split) {
                System.out.println(spl);
                System.out.println();
            }
        }
    }

    public static VDJCMultiRead createMultiRead(NucleotideSequence... seq) {
        SingleRead[] sr = new SingleRead[seq.length];

        for (int i = 0; i < sr.length; i++)
            sr[i] = new SingleReadImpl(0, new NSequenceWithQuality(seq[i], SequenceQuality.getUniformQuality((byte) 35, seq[i].size())), "");

        return new VDJCMultiRead(sr);
    }
}
