package com.milaboratory.mixcr.trees;

import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.AlignmentScoring;
import com.milaboratory.core.alignment.AlignmentUtils;
import com.milaboratory.core.mutations.Mutation;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;
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
    ToIntFunction<CloneWrapper> clusteringHashCode();

    /**
     * Comparator for clonotypes with the same hash code but from different clusters
     */
    Comparator<CloneWrapper> clusteringComparator();

    default Comparator<CloneWrapper> clusteringComparatorWithNumberOfMutations(AlignmentScoring<NucleotideSequence> VScoring, AlignmentScoring<NucleotideSequence> JScoring) {
        return clusteringComparator()
                .thenComparing(Comparator.<CloneWrapper>comparingDouble(clone ->
                        mutationsScoreWithoutCDR3(clone, Variable, VScoring)
                                + mutationsScoreWithoutCDR3(clone, Joining, JScoring)
                ).reversed());
    }

    class DefaultClusteringCriteria implements ClusteringCriteria {
        @Override
        public ToIntFunction<CloneWrapper> clusteringHashCode() {
            return clone -> Objects.hash(
                    clone.VJBase,
                    //TODO remove
                    clone.clone.ntLengthOf(GeneFeature.CDR3)
            );
        }

        @Override
        public Comparator<CloneWrapper> clusteringComparator() {
            return Comparator
                    .<CloneWrapper, String>comparing(c -> c.VJBase.VGeneName)
                    .thenComparing(c -> c.VJBase.JGeneName)
                    //TODO remove
                    .thenComparing(c -> c.clone.ntLengthOf(GeneFeature.CDR3));
        }
    }

    static double mutationsScoreWithoutCDR3(CloneWrapper clone, GeneType geneType, AlignmentScoring<NucleotideSequence> scoring) {
        VDJCHit hit = clone.getHit(geneType);
        return Arrays.stream(hit.getAlignments())
                .mapToDouble(alignment -> {
                    Range CDR3Range = ClonesAlignmentRanges.CDR3Sequence1Range(hit, alignment);
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
