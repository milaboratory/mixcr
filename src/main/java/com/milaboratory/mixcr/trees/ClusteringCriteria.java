package com.milaboratory.mixcr.trees;

import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.mutations.Mutation;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.mutations.MutationsBuilder;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.ReferencePoint;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.function.ToIntFunction;
import java.util.stream.IntStream;

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
        return clone -> Arrays.hashCode(new int[]{
                clusteringHashCode().applyAsInt(clone),
                numberOfMutations(clone, GeneType.Variable) + numberOfMutations(clone, GeneType.Joining)
        });
    }

    default Comparator<Clone> clusteringComparatorWithNumberOfMutations() {
        return clusteringComparator()
                .thenComparing(clone -> numberOfMutations(clone, GeneType.Variable) + numberOfMutations(clone, GeneType.Joining));
    }

    class DefaultClusteringCriteria implements ClusteringCriteria {
        @Override
        public ToIntFunction<Clone> clusteringHashCode() {
            return clone -> Objects.hash(
                    clone.getBestHitGene(GeneType.Variable).getId().getName(),
                    clone.getBestHitGene(GeneType.Joining).getId().getName(),
                    Optional.ofNullable(clone.getBestHitGene(GeneType.Diversity)).map(it -> it.getId().getName()).orElse(""),
                    clone.ntLengthOf(GeneFeature.CDR3)
            );
        }

        @Override
        public Comparator<Clone> clusteringComparator() {
            return Comparator
                    .<Clone, String>comparing(c -> c.getBestHitGene(GeneType.Variable).getId().getName())
                    .thenComparing(c -> c.getBestHitGene(GeneType.Joining).getId().getName())
                    .thenComparing(c -> Optional.ofNullable(c.getBestHitGene(GeneType.Diversity)).map(it -> it.getId().getName()).orElse(""))
                    .thenComparing(c -> c.ntLengthOf(GeneFeature.CDR3));
        }
    }

    /**
     * Returns the number of mutations from the reference
     */
    static int numberOfMutations(Clone clone, GeneType geneType) {
        return getAbsoluteMutationsWithoutCDR3(clone, geneType).size();
    }

    //TODO use extractAbsoluteMutationsForRange
    static Mutations<NucleotideSequence> getAbsoluteMutationsWithoutCDR3(Clone clone, GeneType geneType) {
        VDJCHit bestHit = clone.getBestHit(geneType);


        int[] filteredMutations = IntStream.range(0, bestHit.getAlignments().length)
                .flatMap(index -> {
                    Alignment<NucleotideSequence> alignment = bestHit.getAlignment(index);
                    Range CDR3Range = CDR3Sequence1Range(bestHit, index);

                    Mutations<NucleotideSequence> mutations = alignment.getAbsoluteMutations();
                    return IntStream.range(0, mutations.size())
                            .map(mutations::getMutation)
                            .filter(mutation -> !CDR3Range.contains(Mutation.getPosition(mutation)));
                })
                .toArray();

        return new MutationsBuilder<>(NucleotideSequence.ALPHABET, false, filteredMutations, filteredMutations.length).createAndDestroy();
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
