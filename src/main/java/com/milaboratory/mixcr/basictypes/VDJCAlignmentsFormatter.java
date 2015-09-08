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
package com.milaboratory.mixcr.basictypes;

import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.alignment.MultiAlignmentHelper;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.reference.GeneType;
import com.milaboratory.mixcr.reference.ReferencePoint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by dbolotin on 03/09/15.
 */
public class VDJCAlignmentsFormatter {
    public static MultiAlignmentHelper getTargetAsMultiAlignment(VDJCAlignments vdjcaAlignments, int targetId) {
        NSequenceWithQuality target = vdjcaAlignments.getTarget(targetId);
        NucleotideSequence targetSeq = target.getSequence();
        SequencePartitioning partitioning = vdjcaAlignments.getPartitionedTarget(targetId).getPartitioning();

        List<Alignment<NucleotideSequence>> alignments = new ArrayList<>();
        List<String> alignmentLeftComments = new ArrayList<>();
        List<String> alignmentRightComments = new ArrayList<>();
        for (GeneType gt : GeneType.values())
            for (VDJCHit hit : vdjcaAlignments.getHits(gt)) {
                Alignment<NucleotideSequence> alignment = hit.getAlignment(targetId);
                if (alignment == null)
                    continue;
                alignment = alignment.invert(targetSeq);
                alignments.add(alignment);
                alignmentLeftComments.add(hit.getAllele().getName());
                alignmentRightComments.add(" " + hit.getAlignment(targetId).getScore());
            }

        if (alignments.isEmpty())
            return null;

        MultiAlignmentHelper helper = MultiAlignmentHelper.build(MultiAlignmentHelper.DEFAULT_SETTINGS,
                new Range(0, target.size()), alignments.toArray(new Alignment[alignments.size()]));

        char[] markers = new char[helper.size()];
        Arrays.fill(markers, ' ');
        for (PointToDraw point : points)
            point.draw(partitioning, helper, markers);

        helper.addAnnotationString("", new String(markers));
        helper.addSubjectQuality("Quality", target.getQuality());
        helper.setSubjectLeftTitle("Target" + targetId);
        helper.setSubjectRightTitle(" Score");
        for (int i = 0; i < alignmentLeftComments.size(); i++) {
            helper.setQueryLeftTitle(i, alignmentLeftComments.get(i));
            helper.setQueryRightTitle(i, alignmentRightComments.get(i));
        }
        return helper;
    }

    public static final PointToDraw[] points = new PointToDraw[]{
            pd(ReferencePoint.UTR5End, "5'UTR><L1"),
            pd(ReferencePoint.L1End, "L1>"),
            pd(ReferencePoint.L2Begin, "<L2"),
            pd(ReferencePoint.FR1Begin, "L2><FR1"),
            pd(ReferencePoint.CDR1Begin, "FR1><CDR1"),
            pd(ReferencePoint.FR2Begin, "CDR1><FR2"),
            pd(ReferencePoint.CDR2Begin, "FR2><CDR2"),
            pd(ReferencePoint.FR3Begin, "CDR2><FR3"),
            pd(ReferencePoint.CDR3Begin, "FR3><CDR3"),
            pd(ReferencePoint.CDR3End, "CDR3><FR4"),
            pd(ReferencePoint.FR4End, "FR4>")
    };

    private static PointToDraw pd(ReferencePoint rp, String marker) {
        int offset = marker.indexOf('>');
        if (offset >= 0)
            return new PointToDraw(rp, marker, -1 - offset);
        offset = marker.indexOf('<');
        if (offset >= 0)
            return new PointToDraw(rp, marker, -offset);
        return new PointToDraw(rp, marker, 0);
    }

    private static class PointToDraw {
        final ReferencePoint rp;
        final String marker;
        final int markerOffset;

        public PointToDraw(ReferencePoint rp, String marker, int markerOffset) {
            this.rp = rp;
            this.marker = marker;
            this.markerOffset = markerOffset;
        }

        public void draw(SequencePartitioning partitioning, MultiAlignmentHelper helper, char[] line) {
            int positionInTarget = partitioning.getPosition(rp);
            if (positionInTarget < 0)
                return;
            int positionInHelper = -1;
            for (int i = 0; i < helper.size(); i++)
                if (positionInTarget == helper.getAbsSubjectPositionAt(i)) {
                    positionInHelper = i;
                    break;
                }
            if (positionInHelper == -1)
                return;

            for (int i = 0; i < marker.length(); i++) {
                int positionInLine = positionInHelper + markerOffset + i;
                if (positionInLine < 0 || positionInLine >= line.length)
                    continue;
                line[positionInLine] = marker.charAt(i);
            }
        }
    }
}
