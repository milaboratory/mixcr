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

import static com.milaboratory.core.alignment.AlignmentTestUtils.assertAlignment;

public class MiXCRTestUtils {
    public static void assertAlignments(VDJCAlignments alignments) {
        for (GeneType gt : GeneType.VDJC_REFERENCE) {
            for (VDJCHit hit : alignments.getHits(gt)) {
                for (int targetIndex = 0; targetIndex < alignments.numberOfTargets(); targetIndex++) {
                    Alignment<NucleotideSequence> al = hit.getAlignment(targetIndex);
                    if (al == null)
                        continue;
                    NucleotideSequence sequence = alignments.getTarget(targetIndex).getSequence();
                    assertAlignment(al, sequence);
                }
            }
        }
    }

    public static void printAlignment(VDJCAlignments alignments) {
        for (int i = 0; i < alignments.numberOfTargets(); i++) {
            // fixme
            // if (alignments.getTargetDescriptions() != null)
            //     System.out.println(">>> Description: " + alignments.getTargetDescriptions()[i] + "\n");

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
