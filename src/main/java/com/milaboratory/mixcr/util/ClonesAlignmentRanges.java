package com.milaboratory.mixcr.util;

import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.mutations.Mutation;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.trees.CloneWrapper;
import io.repseq.core.GeneType;
import io.repseq.core.ReferencePoint;
import org.apache.commons.math3.util.Pair;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.repseq.core.ReferencePoint.CDR3Begin;
import static io.repseq.core.ReferencePoint.CDR3End;

public class ClonesAlignmentRanges {
    private final List<Range> commonRanges;
    private final GeneType geneType;

    public ClonesAlignmentRanges(List<Range> commonRanges, GeneType geneType) {
        this.commonRanges = commonRanges;
        this.geneType = geneType;
    }

    public List<Range> getCommonRanges() {
        return commonRanges;
    }

    public static Range CDR3Sequence1Range(VDJCHit hit, Alignment<NucleotideSequence> alignment) {
        int from = getRelativePosition(hit, CDR3Begin);
        int to = getRelativePosition(hit, CDR3End);
        if (from == -1 && to == -1) {
            return null;
        }
        if (from == -1) {
            from = alignment.getSequence1Range().getLower();
        }
        if (to == -1) {
            to = alignment.getSequence1Range().getUpper();
        }
        return new Range(from, to);
    }

    private static int getRelativePosition(VDJCHit hit, ReferencePoint referencePoint) {
        return hit.getGene().getPartitioning().getRelativePosition(hit.getAlignedFeature(), referencePoint);
    }

    public Range cutRange(Range range) {
        return commonRanges.stream()
                .filter(it -> it.intersectsWith(range))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("range is not represented in common ranges"));
    }

    public boolean containsMutation(int mutation) {
        int position = Mutation.getPosition(mutation);
        return commonRanges.stream().anyMatch(it -> it.contains(position));
    }

    public boolean containsCloneWrapper(CloneWrapper clone) {
        return Arrays.stream(clone.getHit(geneType).getAlignments())
                .map(Alignment::getSequence1Range)
                .allMatch(range -> commonRanges.stream().anyMatch(range::contains));
    }

    public boolean containsClone(Clone clone) {
        return Arrays.stream(clone.getBestHit(geneType).getAlignments())
                .map(Alignment::getSequence1Range)
                .allMatch(range -> commonRanges.stream().anyMatch(range::contains));
    }

    public static <T> ClonesAlignmentRanges commonAlignmentRanges(List<T> clones, double minPortionOfClones, GeneType geneType, Function<T, VDJCHit> hitSupplier) {
        if (minPortionOfClones < 0.5) {
            throw new IllegalArgumentException("if minPortionOfClones < 0.5 than there may be ranges with intersections that exist in all clones");
        }
        // allow excluding minimum one clone in small clusters, but not breaking algorithm condition to filtering on more than a half of clones
        int threshold = Math.max((int) Math.floor(clones.size() * minPortionOfClones), (int) Math.ceil(clones.size() / 2.0));
        Map<Range, Long> rangeCounts = clones.stream()
                .flatMap(clone -> {
                    VDJCHit bestHit = hitSupplier.apply(clone);
                    return Arrays.stream(bestHit.getAlignments())
                            .map(alignment -> {
                                Range CDR3Range = CDR3Sequence1Range(bestHit, alignment);
                                if (CDR3Range == null) {
                                    return alignment.getSequence1Range();
                                } else {
                                    List<Range> withoutCDR3 = alignment.getSequence1Range().without(CDR3Range);
                                    if (withoutCDR3.size() != 1) {
                                        throw new IllegalStateException();
                                    } else {
                                        return withoutCDR3.get(0);
                                    }
                                }
                            });
                })
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        List<Range> rangesWithMinPortionOfClones = rangeCounts.keySet().stream()
                .map(range -> Pair.create(
                        range,
                        rangeCounts.entrySet().stream()
                                .filter(e -> e.getKey().contains(range))
                                .mapToLong(Map.Entry::getValue)
                                .sum()
                ))
                .filter(it -> it.getSecond() >= threshold)
                .map(Pair::getKey)
                .collect(Collectors.toList());
        List<Range> result = rangesWithMinPortionOfClones.stream()
                .filter(range -> rangesWithMinPortionOfClones.stream()
                        .filter(it -> !it.equals(range))
                        .noneMatch(it -> it.contains(range)))
                .collect(Collectors.toList());
        boolean noIntersections = result.stream().noneMatch(range -> result.stream()
                .filter(it -> !it.equals(range))
                .anyMatch(range::intersectsWith)
        );
        if (!noIntersections) {
            throw new IllegalStateException("there are intersections");
        }
        return new ClonesAlignmentRanges(result, geneType);
    }
}
