package com.milaboratory.mixcr.trees

import com.milaboratory.core.alignment.Alignment
import com.milaboratory.core.alignment.AlignmentScoring
import com.milaboratory.core.alignment.AlignmentUtils
import com.milaboratory.core.mutations.Mutation
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.util.ClonesAlignmentRanges
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import java.util.*
import java.util.function.ToIntFunction
import java.util.stream.IntStream

/**
 *
 */
interface ClusteringCriteria {
    /**
     * Returns the hash code of the feature which is used to group clonotypes
     */
    fun clusteringHashCode(): ToIntFunction<CloneWrapper>

    /**
     * Comparator for clonotypes with the same hash code but from different clusters
     */
    fun clusteringComparator(): Comparator<CloneWrapper?>
    fun clusteringComparatorWithNumberOfMutations(
        VScoring: AlignmentScoring<NucleotideSequence>,
        JScoring: AlignmentScoring<NucleotideSequence>
    ): Comparator<CloneWrapper?>? {
        return clusteringComparator()
            .thenComparing(Comparator.comparingDouble { clone: CloneWrapper ->
                (mutationsScoreWithoutCDR3(clone, GeneType.Variable, VScoring)
                    + mutationsScoreWithoutCDR3(clone, GeneType.Joining, JScoring))
            }.reversed())
    }

    class DefaultClusteringCriteria : ClusteringCriteria {
        override fun clusteringHashCode(): ToIntFunction<CloneWrapper> {
            return ToIntFunction { clone: CloneWrapper ->
                Objects.hash(
                    clone.VJBase,  //TODO remove
                    clone.clone.ntLengthOf(GeneFeature.CDR3)
                )
            }
        }

        override fun clusteringComparator(): Comparator<CloneWrapper?> {
            return Comparator
                .comparing { c: CloneWrapper? -> c!!.VJBase.VGeneName }
                .thenComparing { c: CloneWrapper? -> c!!.VJBase.JGeneName } //TODO remove
                .thenComparing { c: CloneWrapper? -> c!!.clone.ntLengthOf(GeneFeature.CDR3) }
        }
    }

    companion object {
        fun mutationsScoreWithoutCDR3(
            clone: CloneWrapper,
            geneType: GeneType,
            scoring: AlignmentScoring<NucleotideSequence>
        ): Double {
            val hit = clone.getHit(geneType)
            return Arrays.stream(hit.alignments)
                .mapToDouble { alignment: Alignment<NucleotideSequence?> ->
                    val CDR3Range = ClonesAlignmentRanges.CDR3Sequence1Range(hit, alignment)
                    val mutationsWithoutCDR3 = if (CDR3Range != null) {
                        Mutations(
                            NucleotideSequence.ALPHABET,
                            *IntStream.of(*alignment.absoluteMutations.rawMutations)
                                .filter { it: Int ->
                                    val position = Mutation.getPosition(it)
                                    !CDR3Range.contains(position)
                                }
                                .toArray()
                        )
                    } else {
                        alignment.absoluteMutations
                    }
                    AlignmentUtils.calculateScore(
                        alignment.sequence1,
                        mutationsWithoutCDR3,
                        scoring
                    ).toDouble()
                }
                .sum()
        }
    }
}
