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
package com.milaboratory.mixcr.export;

import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.MultiAlignmentHelper;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.basictypes.VDJCObject;
import com.milaboratory.mixcr.export.AirrColumns.ComplexReferencePoint;
import com.milaboratory.util.BitArray;
import com.milaboratory.util.StringUtil;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGene;
import io.repseq.util.IMGTPadding;

import java.util.*;

import static io.repseq.core.GeneFeature.CDR3;
import static io.repseq.core.GeneType.*;
import static io.repseq.core.ReferencePoint.*;

public final class AirrUtil {
    public static final char PADDING_CHARACTER = '.';
    private static final GeneFeature V_CORE = new GeneFeature(FR1Begin, FR3End);

    private AirrUtil() {
    }

    // /** Germline FR4 end position rounded down to the triplet boundary relative to aligned feature */
    // private static int imgtFR4End(VDJCHit jHit) {
    //     ReferencePoints partitioning = jHit.getGene().getPartitioning();
    //     GeneFeature af = jHit.getAlignedFeature();
    //     int fr4End = partitioning.getRelativePosition(af, FR4End);
    //     if (fr4End < 0) // in case gene has no anchor point for the position
    //         return -1;
    //     int cdr3End = partitioning.getRelativePosition(af, CDR3End);
    //     if (cdr3End < 0) // in case gene has no anchor point for the position
    //         return -1;
    //     return cdr3End + (fr4End - cdr3End) / 3 * 3;
    // }

    private static final int NULL_PADDING_LENGTH = Integer.MIN_VALUE;

    /**
     * Pre-calculates AIRR style alignment data
     *
     * @param object    source data
     * @param targetId  target to build alignment from (AIRR format has no support for multi target sequence structures)
     * @param vdjRegion if true alignment will be forced to the boundaries of VDJRegion (in IMGT's sense, with FR4End bound to the reading frame)
     * @return pre-calculated alignment data
     */
    public static AirrAlignment calculateAirrAlignment(VDJCObject object, int targetId, boolean vdjRegion) {
        NucleotideSequence target = object.getTarget(targetId).getSequence();
        MultiAlignmentHelper.Settings settings = new MultiAlignmentHelper.Settings(
                false, false, false, ' ', ' ');
        List<Alignment<NucleotideSequence>> alignments = new ArrayList<>();
        List<GeneType> actualGeneTypes = new ArrayList<>();

        boolean validPaddings = true;
        // padding insertion positions are in germline coordinates
        EnumMap<GeneType, List<IMGTPadding>> paddingsByGeneType = new EnumMap<>(GeneType.class);

        // positive numbers means imgt gaps must be added, negative - letters must be removed from the
        // corresponding side, compared to IMGT's VDJRegion
        // these numbers represent number of nucleotides in the germline sequence to fill up the sequence,
        // IMGTPadding's calculated must additionally be added
        int leftVDJPadding = NULL_PADDING_LENGTH, rightVDJPadding = NULL_PADDING_LENGTH;
        // for positive values above these sequences represent a germline sequence ranges to be appended to tha alignment
        Range leftVDJExtraRange = null, rightVDJExtraRange = null;
        // and the sequences themselves
        NucleotideSequence leftVDJExtra = null, rightVDJExtra = null;
        // these positions are initially in germline coordinates, conversion to alignment position happens below
        int cdr3Begin = -1, cdr3End = -1;

        EnumMap<GeneType, VDJCHit> bestHits = new EnumMap<>(GeneType.class);

        for (GeneType gt : vdjRegion ? VDJ_REFERENCE : VDJC_REFERENCE) {
            VDJCHit bestHit = object.getBestHit(gt);
            if (bestHit == null)
                continue;
            Alignment<NucleotideSequence> al = bestHit.getAlignment(targetId);
            if (al == null)
                continue;

            VDJCGene gene = bestHit.getGene();
            actualGeneTypes.add(gt);

            GeneFeature refGeneFeature = bestHit.getAlignedFeature();

            // Incomplete V gene feature correction
            {
                GeneFeature extensionFeature;
                if (gt == Variable
                        && FR1Begin.compareTo(refGeneFeature.getFirstPoint()) < 0
                        && gene.getPartitioning().isAvailable(extensionFeature = new GeneFeature(FR1Begin, refGeneFeature.getFirstPoint()))) {
                    NucleotideSequence leftExtension = gene.getFeature(extensionFeature);
                    refGeneFeature = new GeneFeature(extensionFeature, refGeneFeature);
                    al = new Alignment<>(
                            leftExtension.concatenate(al.getSequence1()),
                            al.getAbsoluteMutations().move(leftExtension.size()),
                            al.getSequence1Range().move(leftExtension.size()),
                            al.getSequence2Range(),
                            al.getScore()
                    );
                    // TODO update best hit ???
                }
            }

            bestHits.put(gt, bestHit);
            alignments.add(al.invert(target));

            // IMGT related code below

            NucleotideSequence refGeneSequence = al.getSequence1();
            if (gt == Variable) {
                cdr3Begin = gene.getPartitioning().getRelativePosition(refGeneFeature, CDR3Begin);
                int fr1Begin = gene.getPartitioning().getRelativePosition(refGeneFeature, FR1Begin);
                if (fr1Begin >= 0) { // in case gene has no anchor point for the position
                    leftVDJPadding = al.getSequence1Range().getFrom() - fr1Begin;
                    if (leftVDJPadding > 0) {
                        leftVDJExtraRange = new Range(fr1Begin, al.getSequence1Range().getFrom());
                        leftVDJExtra = refGeneSequence.getRange(leftVDJExtraRange);
                    }
                }
            }
            if (gt == Joining) {
                cdr3End = gene.getPartitioning().getRelativePosition(refGeneFeature, CDR3End);
                int fr4End = gene.getPartitioning().getRelativePosition(refGeneFeature, FR4End);
                if (fr4End >= 0) { // in case gene has no anchor point for the position
                    rightVDJPadding = fr4End - al.getSequence1Range().getTo();
                    if (rightVDJPadding > 0) {
                        rightVDJExtraRange = new Range(al.getSequence1Range().getTo(), fr4End);
                        rightVDJExtra = refGeneSequence.getRange(al.getSequence1Range().getTo(), fr4End);
                    }
                }
            }

            if (!validPaddings)
                continue;

            List<IMGTPadding> germlinePaddings = IMGTPadding.calculateForSequence(gene.getPartitioning(),
                    refGeneFeature);
            if (germlinePaddings == null)
                validPaddings = false;

            paddingsByGeneType.put(gt, germlinePaddings);
        }

        // noinspection unchecked
        MultiAlignmentHelper helper = MultiAlignmentHelper.build(settings, new Range(0, target.size()), target, alignments.toArray(new Alignment[0]));

        // merging alignments
        // output data
        String sequence = helper.getSubject();
        // output data
        StringBuilder germlineBuilder = new StringBuilder();
        int size = helper.size();
        // output data
        int[] germlinePosition = new int[size];
        Arrays.fill(germlinePosition, -1);
        // output data
        GeneType[] geneType = new GeneType[size];
        // output data
        boolean[] match = new boolean[size];
        int firstAligned = -1, lastAligned = 0;
        outer:
        for (int i = 0; i < size; i++) {
            for (int gti = 0; gti < actualGeneTypes.size(); gti++) {
                if (helper.getQuery(gti).charAt(i) != ' ') {
                    GeneType gt = actualGeneTypes.get(gti);
                    germlineBuilder.append(helper.getQuery(gti).charAt(i));
                    germlinePosition[i] = helper.getAbsQueryPositionAt(gti, i);
                    geneType[i] = gt;
                    match[i] = helper.getMatch()[gti].get(i);

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
        germlinePosition = Arrays.copyOfRange(germlinePosition, firstAligned, lastAligned + 1);
        geneType = Arrays.copyOfRange(geneType, firstAligned, lastAligned + 1);
        match = Arrays.copyOfRange(match, firstAligned, lastAligned + 1);
        size = lastAligned - firstAligned + 1;

        assert sequence.length() == size;

        if (vdjRegion) {
            if (leftVDJPadding == NULL_PADDING_LENGTH || rightVDJPadding == NULL_PADDING_LENGTH) {
                // impossible to construct VDJRegion-bound AIRR alignment
                return null;
            } else {
                // Processing left side
                if (leftVDJPadding > 0) {
                    assert leftVDJPadding == leftVDJExtra.size();
                    assert leftVDJPadding == leftVDJExtraRange.length();

                    sequence = StringUtil.chars(PADDING_CHARACTER, leftVDJPadding) + sequence;
                    germline = leftVDJExtra + germline;

                    int[] newGermlinePosition = new int[leftVDJPadding + size];
                    System.arraycopy(germlinePosition, 0, newGermlinePosition,
                            leftVDJPadding, size);
                    GeneType[] newGeneType = new GeneType[leftVDJPadding + size];
                    System.arraycopy(geneType, 0, newGeneType,
                            leftVDJPadding, size);
                    boolean[] newMatch = new boolean[leftVDJPadding + size];
                    System.arraycopy(match, 0, newMatch,
                            leftVDJPadding, size);
                    for (int p = leftVDJExtraRange.getFrom(), i = 0; p < leftVDJExtraRange.getTo(); ++p, ++i) {
                        newGermlinePosition[i] = p;
                        newGeneType[i] = Variable;
                        newMatch[i] = true;
                    }
                    germlinePosition = newGermlinePosition;
                    geneType = newGeneType;
                    match = newMatch;
                } else if (leftVDJPadding != 0) {
                    sequence = sequence.substring(-leftVDJPadding);
                    germline = germline.substring(-leftVDJPadding);
                    germlinePosition = Arrays.copyOfRange(germlinePosition, -leftVDJPadding, germlinePosition.length);
                    geneType = Arrays.copyOfRange(geneType, -leftVDJPadding, geneType.length);
                    match = Arrays.copyOfRange(match, -leftVDJPadding, match.length);
                }
                size += leftVDJPadding;
                assert sequence.length() == size;

                if (rightVDJPadding > 0) {
                    assert rightVDJPadding == rightVDJExtra.size();
                    assert rightVDJPadding == rightVDJExtraRange.length();

                    sequence = sequence + StringUtil.chars(PADDING_CHARACTER, rightVDJPadding);
                    germline = germline + rightVDJExtra;
                    germlinePosition = Arrays.copyOf(germlinePosition, size + rightVDJPadding);
                    geneType = Arrays.copyOf(geneType, size + rightVDJPadding);
                    match = Arrays.copyOf(match, size + rightVDJPadding);
                    // size here is "previous size"
                    for (int p = rightVDJExtraRange.getFrom(), i = size; p < rightVDJExtraRange.getTo(); ++p, ++i) {
                        germlinePosition[i] = p;
                        geneType[i] = Joining;
                        match[i] = true;
                    }
                } else if (rightVDJPadding != 0) {
                    sequence = sequence.substring(0, size + rightVDJPadding); // note that rightVDJPadding < 0
                    germline = germline.substring(0, size + rightVDJPadding);
                    germlinePosition = Arrays.copyOf(germlinePosition, size + rightVDJPadding);
                    geneType = Arrays.copyOf(geneType, size + rightVDJPadding);
                    match = Arrays.copyOf(match, size + rightVDJPadding);
                }
                size += rightVDJPadding;
                assert germline.length() == size;
            }
        }

        int cdr3Length = object.ntLengthOf(CDR3);

        List<IMGTPadding> paddings = null;
        if (validPaddings) {
            paddings = new ArrayList<>();
            outer:
            for (Map.Entry<GeneType, List<IMGTPadding>> e : paddingsByGeneType.entrySet()) {
                GeneType gt = e.getKey();
                List<IMGTPadding> germlinePaddings = e.getValue();
                for (IMGTPadding germlinePadding : germlinePaddings) {
                    int pos = projectPosition(germlinePosition, geneType,
                            germlinePadding.getInsertionPosition(), gt);
                    if (pos == -1) {
                        paddings = null;
                        break outer;
                    }
                    paddings.add(new IMGTPadding(pos, germlinePadding.getPaddingLength()));
                }
            }

            if (paddings == null || cdr3Begin == -1 || cdr3End == -1 || cdr3Length < 0)
                paddings = null;
            else {
                cdr3Begin = projectPosition(germlinePosition, geneType, cdr3Begin, Variable);
                cdr3End = projectPosition(germlinePosition, geneType, cdr3End, Joining);
                int paddedLength = IMGTPadding.getPaddedLengthNt(CDR3, cdr3Length);
                if (cdr3Begin == -1 || cdr3End == -1 || paddedLength == -1)
                    paddings = null;
                else {
                    int position = cdr3Begin + IMGTPadding.insertPosition(cdr3End - cdr3Begin);
                    paddings.add(new IMGTPadding(position, paddedLength - cdr3Length));
                }
            }
        }

        if (paddings != null)
            Collections.sort(paddings);

        return new AirrAlignment(bestHits, sequence, germline, geneType, germlinePosition, new BitArray(match), paddings);
    }

    private static int projectPosition(int[] germlinePositions, GeneType[] geneTypes,
                                       int position, GeneType geneType) {
        boolean onLeft = false;
        for (int i = 0; i < germlinePositions.length; i++)
            if (geneTypes[i] == geneType) {
                if (germlinePositions[i] < position)
                    onLeft = true;
                if (onLeft && germlinePositions[i] >= position)
                    return i;
            }
        return -1;
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
            if (obj.getPartitionedTarget(i).getPartitioning().isAvailable(CDR3))
                return i;
        int maxAlignmentLength = -1;
        int targetWithMaxAlignmentLength = -1;
        for (int i = 0; i < obj.numberOfTargets(); i++) {
            int alignmentLength = 0;
            for (GeneType gt : VDJC_REFERENCE) {
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
        private final EnumMap<GeneType, VDJCHit> bestHits;
        private final String sequence, germline;
        private final GeneType[] geneType;
        private final int[] germlinePosition;
        private final BitArray match;
        private final EnumMap<GeneType, Range> ranges;
        private final List<IMGTPadding> paddings;

        public AirrAlignment(EnumMap<GeneType, VDJCHit> bestHits,
                             String sequence, String germline,
                             GeneType[] geneType, int[] germlinePosition,
                             BitArray match, List<IMGTPadding> paddings) {
            if (sequence.length() != germline.length())
                throw new IllegalArgumentException();
            if (germline.length() != geneType.length)
                throw new IllegalArgumentException();
            if (geneType.length != germlinePosition.length)
                throw new IllegalArgumentException();
            if (germlinePosition.length != match.size())
                throw new IllegalArgumentException();

            this.bestHits = bestHits;
            this.sequence = sequence;
            this.germline = germline;
            this.geneType = geneType;
            this.germlinePosition = germlinePosition;
            this.match = match;
            EnumMap<GeneType, Range> ranges = new EnumMap<>(GeneType.class);
            for (GeneType gt : VDJC_REFERENCE) {
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
            this.paddings = paddings;
        }

        private String padded(String input) {
            if (paddings == null)
                return null;
            return IMGTPadding.applyPadding(paddings, PADDING_CHARACTER, input);
        }

        private int projectToPadded(int unpaddedPosition) {
            if (paddings == null)
                return -1;
            int result = unpaddedPosition;
            for (IMGTPadding p : paddings)
                if (p.getInsertionPosition() >= unpaddedPosition)
                    return result;
                else
                    result += p.getPaddingLength();
            return result;
        }

        public String getSequence(boolean withPadding) {
            return withPadding ? padded(sequence) : sequence;
        }

        public String getGermline(boolean withPadding) {
            return withPadding ? padded(germline) : germline;
        }

        public int projectGermlinePosition(GeneType gt, int position, boolean withPadding) {
            position = projectPosition(germlinePosition, geneType, position, gt);
            if (position == -1)
                return -1;
            return withPadding ? projectToPadded(position) : position;
        }

        public int getPosition(ComplexReferencePoint point, boolean withPadding) {
            for (GeneType gt : VDJC_REFERENCE) {
                VDJCHit hit = bestHits.get(gt);
                if (hit == null)
                    continue;
                int position = point.getPosition(hit.getGene().getPartitioning(), hit.getAlignedFeature());
                if (position == -1)
                    continue;
                return projectGermlinePosition(gt, position, withPadding);
            }
            return -1;
        }

        public String getSequence(ComplexReferencePoint from, ComplexReferencePoint to, boolean withPadding) {
            int fromPosition = getPosition(from, withPadding);
            int toPosition = getPosition(to, withPadding);
            if (fromPosition == -1 || toPosition == -1)
                return null;
            return getSequence(withPadding).substring(fromPosition, toPosition);
        }

        public Range getRange(GeneType gt, boolean withPadding) {
            Range range = ranges.get(gt);
            if (range == null)
                return null;

            if (withPadding) {
                return paddings == null
                        ? null
                        : new Range(
                        projectToPadded(range.getLower()),
                        projectToPadded(range.getUpper()));
            } else
                return range;
        }
    }
}
