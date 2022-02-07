package com.milaboratory.mixcr.trees;

import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.AlignmentScoring;
import com.milaboratory.core.alignment.AlignmentUtils;
import com.milaboratory.core.mutations.Mutation;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.util.ClonesAlignmentRanges;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.ToIntFunction;
import java.util.stream.IntStream;

import static io.repseq.core.GeneType.Joining;
import static io.repseq.core.GeneType.Variable;

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
        return Arrays.stream(bestHit.getAlignments())
                .mapToDouble(alignment -> {
                    Range CDR3Range = ClonesAlignmentRanges.CDR3Sequence1Range(bestHit, alignment);
                    Mutations<NucleotideSequence> mutationsWithoutCDR3;
                    if (CDR3Range != null) {
                        mutationsWithoutCDR3 = new Mutations<>(
                                NucleotideSequence.ALPHABET,
                                IntStream.of(alignment.getAbsoluteMutations().getRAWMutations())
                                        .filter(it -> {
                                            int position = Mutation.getPosition(it);
                                            return !CDR3Range.contains(position);
                                        })
                                        .toArray()
                        );
                    } else {
                        mutationsWithoutCDR3 = alignment.getAbsoluteMutations();
                    }
                    return AlignmentUtils.calculateScore(
                            alignment.getSequence1(),
                            mutationsWithoutCDR3,
                            scoring
                    );
                })
                .sum();
    }

}
