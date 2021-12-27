package com.milaboratory.mixcr.trees;

import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.alignment.AlignmentScoring;
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
import java.util.stream.IntStream;

import static io.repseq.core.GeneType.Joining;
import static io.repseq.core.GeneType.Variable;
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

    default Comparator<Clone> clusteringComparatorWithNumberOfMutations(AlignmentScoring<NucleotideSequence> VScoring, AlignmentScoring<NucleotideSequence> JScoring) {
        return clusteringComparator()
                .thenComparing(Comparator.<Clone>comparingDouble(clone ->
                        mutationsScoreWithoutCDR3(clone, Variable, VScoring) + mutationsScoreWithoutCDR3(clone, Joining, JScoring)
                ).reversed());
    }

    class DefaultClusteringCriteria implements ClusteringCriteria {
        @Override
        public ToIntFunction<Clone> clusteringHashCode() {
            return clone -> Objects.hash(
                    clone.getBestHitGene(Variable).getId().getName(),
                    clone.getBestHitGene(Joining).getId().getName(),
                    //TODO remove
                    clone.ntLengthOf(GeneFeature.CDR3)
            );
        }

        @Override
        public Comparator<Clone> clusteringComparator() {
            return Comparator
                    .<Clone, String>comparing(c -> c.getBestHitGene(Variable).getId().getName())
                    .thenComparing(c -> c.getBestHitGene(Joining).getId().getName())
                    //TODO remove
                    .thenComparing(c -> c.ntLengthOf(GeneFeature.CDR3));
        }
    }

    static double mutationsScoreWithoutCDR3(Clone clone, GeneType geneType, AlignmentScoring<NucleotideSequence> scoring) {
        VDJCHit bestHit = clone.getBestHit(geneType);
        return IntStream.range(0, bestHit.getAlignments().length)
                .mapToDouble(index -> {
                    Alignment<NucleotideSequence> alignment = bestHit.getAlignment(index);
                    Range CDR3Range = CDR3Sequence1Range(bestHit, index);
                    Mutations<NucleotideSequence> mutations = alignment.getAbsoluteMutations();
                    List<Range> rangesWithoutCDR3 = alignment.getSequence1Range().without(CDR3Range);
                    if (rangesWithoutCDR3.size() > 0) {
                        if (rangesWithoutCDR3.size() > 1) {
                            throw new IllegalStateException();
                        }
                        return AlignmentUtils.calculateScore(
                                alignment.getSequence1(),
                                mutations,
                                scoring
                        );
                    } else {
                        return 0.0;
                    }
                })
                .sum();
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
