package com.milaboratory.mixcr.trees;

import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.AffineGapAlignmentScoring;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.alignment.AlignmentUtils;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.ReferencePoint;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.repseq.core.ReferencePoint.CDR3Begin;
import static io.repseq.core.ReferencePoint.CDR3End;

/**
 *
 */
public interface ClusteringCriteria {
    /**
     * Returns the hash code of the feature which is used to group clonotypes
     */
    ToIntFunction<Clone> clusteringHashCode();

    /**
     * Comparator for clonotypes with the same hash code but from different clusters
     */
    Comparator<Clone> clusteringComparator();

    default ToIntFunction<Clone> clusteringHashCodeWithNumberOfMutations() {
        return clone -> clusteringHashCode().applyAsInt(clone);
    }

    default Comparator<Clone> clusteringComparatorWithNumberOfMutations() {
        return clusteringComparator()
                .thenComparing(Comparator.<Clone>comparingDouble(clone ->
                        score(getMutationsWithoutCDR3(clone, GeneType.Variable)) + score(getMutationsWithoutCDR3(clone, GeneType.Joining))
                ).reversed());
    }

    class DefaultClusteringCriteria implements ClusteringCriteria {
        @Override
        public ToIntFunction<Clone> clusteringHashCode() {
            return clone -> Objects.hash(
                    clone.getBestHitGene(GeneType.Variable).getId().getName(),
                    clone.getBestHitGene(GeneType.Joining).getId().getName(),
                    //TODO remove
                    clone.ntLengthOf(GeneFeature.CDR3)
            );
        }

        @Override
        public Comparator<Clone> clusteringComparator() {
            return Comparator
                    .<Clone, String>comparing(c -> c.getBestHitGene(GeneType.Variable).getId().getName())
                    .thenComparing(c -> c.getBestHitGene(GeneType.Joining).getId().getName())
                    //TODO remove
                    .thenComparing(c -> c.ntLengthOf(GeneFeature.CDR3));
        }
    }

    /**
     * sum score of given mutations
     */
    static double score(List<MutationsWithRange> mutationsWithRanges) {
        return mutationsWithRanges.stream()
                .mapToDouble(ClusteringCriteria::score)
                .sum();
    }

    static int score(MutationsWithRange mutations) {
        return AlignmentUtils.calculateScore(
                mutations.getFromBaseToParent().mutate(mutations.getSequence1()),
                mutations.getFromParentToThis(),
                AffineGapAlignmentScoring.getNucleotideBLASTScoring()
        );
    }

    static List<MutationsWithRange> getMutationsWithoutCDR3(Clone clone, GeneType geneType) {
        VDJCHit bestHit = clone.getBestHit(geneType);
        return IntStream.range(0, bestHit.getAlignments().length)
                .boxed()
                .flatMap(index -> {
                    Alignment<NucleotideSequence> alignment = bestHit.getAlignment(index);
                    Range CDR3Range = CDR3Sequence1Range(bestHit, index);
                    Mutations<NucleotideSequence> mutations = alignment.getAbsoluteMutations();
                    List<Range> rangesWithoutCDR3 = alignment.getSequence1Range().without(CDR3Range);
                    if (rangesWithoutCDR3.size() > 0) {
                        if (rangesWithoutCDR3.size() > 1) {
                            throw new IllegalStateException();
                        }
                        return Stream.of(new MutationsWithRange(
                                alignment.getSequence1(),
                                Mutations.EMPTY_NUCLEOTIDE_MUTATIONS,
                                mutations,
                                rangesWithoutCDR3.get(0),
                                true
                        ));
                    } else {
                        return Stream.empty();
                    }
                })
                .collect(Collectors.toList());
    }

    static Range CDR3Sequence1Range(VDJCHit hit, int target) {
        int from = getRelativePosition(hit, CDR3Begin);
        if (from == -1) {
            from = hit.getAlignment(target).getSequence1Range().getLower();
        }
        int to = getRelativePosition(hit, CDR3End);
        if (to == -1) {
            to = hit.getAlignment(target).getSequence1Range().getUpper();
        }
        return new Range(from, to);
    }

    static int getRelativePosition(VDJCHit hit, ReferencePoint referencePoint) {
        return hit.getGene().getPartitioning().getRelativePosition(hit.getAlignedFeature(), referencePoint);
    }
}
