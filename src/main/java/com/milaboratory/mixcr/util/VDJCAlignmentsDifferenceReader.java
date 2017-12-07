package com.milaboratory.mixcr.util;

import cc.redberry.pipe.OutputPort;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Objects;

/**
 * @author Dmitry Bolotin
 * @author Stanislav Poslavsky
 */
public class VDJCAlignmentsDifferenceReader
        implements OutputPort<VDJCAlignmentsDifferenceReader.Diff> {
    final OutputPort<VDJCAlignments> first, second;
    final GeneFeature featureToCompare;
    final int hitsCompareLevel;

    public VDJCAlignmentsDifferenceReader(OutputPort<VDJCAlignments> first, OutputPort<VDJCAlignments> second,
                                          GeneFeature featureToCompare,
                                          int hitsCompareLevel) {
        this.first = first;
        this.second = second;
        this.featureToCompare = featureToCompare;
        this.hitsCompareLevel = hitsCompareLevel;
    }

    public VDJCAlignmentsDifferenceReader(String first, String second,
                                          GeneFeature featureToCompare,
                                          int hitsCompareLevel) throws IOException {
        this(new VDJCAlignmentsReader(first),
                new VDJCAlignmentsReader(second),
                featureToCompare, hitsCompareLevel);
    }

    private volatile boolean firstIteration = true;
    private Diff previous;

    @Override
    public Diff take() {
        if (firstIteration) {
            firstIteration = false;
            return previous = compare(first.take(), second.take());
        }

        switch (previous.status) {
            case AlignmentsAreDifferent:
            case AlignmentsAreSame:
                return previous = compare(first.take(), second.take());
            case AlignmentPresentOnlyInFirst:
                return previous = compare(first.take(), previous.second);
            case AlignmentPresentOnlyInSecond:
                return previous = compare(previous.first, second.take());
            default:
                throw new RuntimeException();
        }
    }

    private Diff compare(VDJCAlignments first, VDJCAlignments second) {
        // fixme correct in 2.2
        if (first == null && second == null)
            return null;
        else if (first == null)
            return new Diff(first, second, DiffStatus.AlignmentPresentOnlyInSecond);
        else if (second == null)
            return new Diff(first, second, DiffStatus.AlignmentPresentOnlyInFirst);
        else if (first.getMinReadId() < second.getMinReadId())
            return new Diff(first, second, DiffStatus.AlignmentPresentOnlyInFirst);
        else if (first.getMinReadId() > second.getMinReadId())
            return new Diff(first, second, DiffStatus.AlignmentPresentOnlyInSecond);
        else {
            boolean diffGeneFeature = true;
            if (featureToCompare != null)
                diffGeneFeature = !Objects.equals(first.getFeature(featureToCompare), second.getFeature(featureToCompare));
            EnumMap<GeneType, Boolean> diffHits = new EnumMap<>(GeneType.class);
            boolean same = !diffGeneFeature;
            for (GeneType geneType : GeneType.VDJC_REFERENCE) {
                boolean b = sameHits(first.getHits(geneType), second.getHits(geneType), hitsCompareLevel);
                diffHits.put(geneType, !b);
                if (!b) same = false;
            }

            if (same)
                return new Diff(first, second, DiffStatus.AlignmentsAreSame);
            else
                return new Diff(first, second, DiffStatus.AlignmentsAreDifferent, new DiffReason(diffGeneFeature, diffHits));
        }
    }

    static boolean sameHits(VDJCHit[] first, VDJCHit[] second, int level) {
        if (level == 0) return true;
        if (first == null) return second == null;
        if (second == null) return false;
        if (first.length == 0) return second.length == 0;

        for (int i = 0; i < level && i < first.length; ++i)
            for (int j = 0; j < level && j < second.length; ++j)
                if (first[i].getGene().getId().equals(second[j].getGene().getId()))
                    return true;
        return false;
    }

    public static final class Diff {
        public final VDJCAlignments first, second;
        public final DiffStatus status;
        public final DiffReason reason;

        public Diff(VDJCAlignments first, VDJCAlignments second, DiffStatus status) {
            this(first, second, status, null);
        }

        public Diff(VDJCAlignments first, VDJCAlignments second, DiffStatus status, DiffReason reason) {
            this.first = first;
            this.second = second;
            this.status = status;
            this.reason = reason;
        }
    }

    public enum DiffStatus {
        AlignmentPresentOnlyInFirst,
        AlignmentPresentOnlyInSecond,
        AlignmentsAreDifferent,
        AlignmentsAreSame
    }

    public static class DiffReason {
        public final boolean diffGeneFeature;
        public final EnumMap<GeneType, Boolean> diffHits;

        public DiffReason(boolean diffGeneFeature, EnumMap<GeneType, Boolean> diffHits) {
            this.diffGeneFeature = diffGeneFeature;
            this.diffHits = diffHits;
        }
    }
}
