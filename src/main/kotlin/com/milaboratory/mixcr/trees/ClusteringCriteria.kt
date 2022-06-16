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
@file:Suppress("LocalVariableName")

package com.milaboratory.mixcr.trees

import com.milaboratory.core.alignment.AlignmentScoring
import com.milaboratory.core.alignment.AlignmentUtils
import com.milaboratory.core.mutations.Mutation
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.util.ClonesAlignmentRanges
import com.milaboratory.mixcr.util.asMutations
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import java.util.*
import java.util.function.ToIntFunction

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
    fun clusteringComparator(): Comparator<CloneWrapper>
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
        override fun clusteringHashCode(): ToIntFunction<CloneWrapper> = ToIntFunction { clone ->
            Objects.hash(
                clone.VJBase,
                clone.clone.ntLengthOf(GeneFeature.CDR3)
            )
        }

        override fun clusteringComparator(): Comparator<CloneWrapper> = Comparator
            .comparing { c: CloneWrapper -> c.VJBase.VGeneId.name }
            .thenComparing { c -> c.VJBase.JGeneId.name }
            .thenComparing { c -> c.clone.ntLengthOf(GeneFeature.CDR3) }
    }

    companion object {
        fun mutationsScoreWithoutCDR3(
            clone: CloneWrapper,
            geneType: GeneType,
            scoring: AlignmentScoring<NucleotideSequence>
        ): Double {
            val hit = clone.getHit(geneType)
            return hit.alignments.sumOf { alignment ->
                val CDR3Range = ClonesAlignmentRanges.CDR3Sequence1Range(hit, alignment)
                val mutationsWithoutCDR3 = when {
                    CDR3Range != null -> alignment.absoluteMutations.rawMutations.asSequence()
                        .filter {
                            val position = Mutation.getPosition(it)
                            !CDR3Range.contains(position)
                        }
                        .asMutations(NucleotideSequence.ALPHABET)
                    else -> alignment.absoluteMutations
                }
                AlignmentUtils.calculateScore(
                    alignment.sequence1,
                    mutationsWithoutCDR3,
                    scoring
                ).toDouble()
            }
        }
    }
}
