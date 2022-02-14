package com.milaboratory.mixcr.export;

import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.alignment.MultiAlignmentHelper;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.basictypes.VDJCObject;
import com.milaboratory.util.BitArray;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;

public final class AirrUtil {
    private AirrUtil() {
    }

    public static AirrAlignment calculateAirAlignment(VDJCObject object, int targetId) {
        NucleotideSequence target = object.getTarget(targetId).getSequence();
        MultiAlignmentHelper.Settings settings = new MultiAlignmentHelper.Settings(
                false, false, false, ' ', ' ');
        List<Alignment<NucleotideSequence>> alignments = new ArrayList<>();
        List<GeneType> actualGeneTypes = new ArrayList<>();
        for (GeneType gt : GeneType.VDJC_REFERENCE) {
            VDJCHit bestHit = object.getBestHit(gt);
            if (bestHit == null)
                continue;
            Alignment<NucleotideSequence> alignment = bestHit.getAlignment(targetId);
            if (alignment == null)
                continue;
            actualGeneTypes.add(gt);
            alignments.add(alignment.invert(target));
        }
        // noinspection unchecked
        MultiAlignmentHelper helper = MultiAlignmentHelper.build(settings, new Range(0, target.size()), target, alignments.toArray(new Alignment[0]));

        // merging alignments
        String sequence = helper.getSubject();
        StringBuilder germlineBuilder = new StringBuilder();
        GeneType[] geneType = new GeneType[helper.size()];
        int[] germlinePosition = new int[helper.size()];
        Arrays.fill(germlinePosition, -1);
        BitArray match = new BitArray(helper.size());
        int firstAligned = -1, lastAligned = 0;
        outer:
        for (int i = 0; i < helper.size(); i++) {
            for (int gti = 0; gti < actualGeneTypes.size(); gti++) {
                if (helper.getQuery(gti).charAt(i) != ' ') {
                    GeneType gt = actualGeneTypes.get(gti);
                    germlineBuilder.append(helper.getQuery(gti).charAt(i));
                    geneType[i] = gt;
                    germlinePosition[i] = helper.getAbsQueryPositionAt(gti, i);
                    match.set(i, helper.getMatch()[gti].get(i));

                    if (firstAligned == -1)
                        firstAligned = i;
                    lastAligned = i;

                    continue outer;
                }
            }
            germlineBuilder.append('N');
        }

        // trimming unaligned
        sequence = sequence.substring(firstAligned, lastAligned + 1);
        String germline = germlineBuilder.substring(firstAligned, lastAligned + 1);

        return new AirrAlignment(sequence, germline, geneType, germlinePosition, match);
    }

    /**
     * Selects the most appropriate target for export in multi-target cases.
     *
     * Selection criteria is the following:
     * - target containing CDR3 wins
     * - if no CDR3 target with the longest total alignment length over best hits wins
     * - if no alignments are present 0 is returned
     */
    public static int bestTarget(VDJCObject obj) {
        for (int i = 0; i < obj.numberOfTargets(); i++)
            if (obj.getPartitionedTarget(i).getPartitioning().isAvailable(GeneFeature.CDR3))
                return i;
        int maxAlignmentLength = -1;
        int targetWithMaxAlignmentLength = -1;
        for (int i = 0; i < obj.numberOfTargets(); i++) {
            int alignmentLength = 0;
            for (GeneType gt : GeneType.VDJC_REFERENCE) {
                VDJCHit bh = obj.getBestHit(gt);
                if (bh == null)
                    continue;
                Alignment<NucleotideSequence> al = bh.getAlignment(i);
                if (al == null)
                    continue;
                alignmentLength += al.getSequence2Range().length();
            }
            if (alignmentLength > maxAlignmentLength) {
                maxAlignmentLength = alignmentLength;
                targetWithMaxAlignmentLength = i;
            }
        }
        return targetWithMaxAlignmentLength;
    }

    public static class AirrAlignment {
        final String sequence, germline;
        final GeneType[] geneType;
        final int[] germlinePosition;
        final BitArray match;
        final EnumMap<GeneType, Range> ranges;

        public AirrAlignment(String sequence, String germline,
                             GeneType[] geneType, int[] germlinePosition,
                             BitArray match) {
            this.sequence = sequence;
            this.germline = germline;
            this.geneType = geneType;
            this.germlinePosition = germlinePosition;
            this.match = match;
            EnumMap<GeneType, Range> ranges = new EnumMap<>(GeneType.class);
            for (GeneType gt : GeneType.VDJC_REFERENCE) {
                int min = -1;
                int max = -1;
                for (int i = 0; i < geneType.length; i++)
                    if (geneType[i] == gt) {
                        if (min == -1)
                            min = i;
                        max = i;
                    }
                if (min != -1)
                    ranges.put(gt, new Range(min, max + 1));
            }
            this.ranges = ranges;
        }
    }
}
