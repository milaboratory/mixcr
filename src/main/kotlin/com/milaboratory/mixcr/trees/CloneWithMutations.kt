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

import com.milaboratory.core.Range
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.util.VJPair
import com.milaboratory.mixcr.util.extractAbsoluteMutations
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import io.repseq.core.ReferencePoint

class CloneWithMutationsFromReconstructedRoot(
    val mutationsSet: MutationsSet,
    /**
     * Saved for rebase on another tree.
     */
    val mutationsFromVJGermline: MutationsFromVJGermline,
    val clone: CloneWrapper
)

class CloneWithMutationsFromVJGermline(
    val mutations: MutationsFromVJGermline,
    val cloneWrapper: CloneWrapper
)

/**
 * Represent all mutations as `MutationsFromVJGermline`
 * @see MutationsFromVJGermline
 */
fun CloneWrapper.rebaseFromGermline(assemblingFeatures: Array<GeneFeature>): CloneWithMutationsFromVJGermline =
    CloneWithMutationsFromVJGermline(
        MutationsFromVJGermline(
            VJPair(
                getMutationsWithoutCDR3(Variable, assemblingFeatures).toSortedMap(),
                getMutationsWithoutCDR3(Joining, assemblingFeatures).toSortedMap()
            ),
            VJPair(
                getVMutationsWithinCDR3(this),
                getJMutationsWithinCDR3(this)
            ),
            getFeature(GeneFeature.CDR3)!!.sequence
        ),
        this
    )

private fun CloneWrapper.getMutationsWithoutCDR3(
    geneType: GeneType,
    assemblingFeatures: Array<GeneFeature>
): Map<GeneFeature, Mutations<NucleotideSequence>> {
    val hit = getHit(geneType)
    val partitioning = getPartitioning(geneType)
    return hit.alignments.flatMap { alignment ->
        assemblingFeatures
            .mapNotNull { GeneFeature.intersection(it, hit.alignedFeature) }
            .map { it.cutCDR3PartOfFeature() }
            .map { geneFeature ->
                val range = partitioning.getRange(geneFeature)
                geneFeature to alignment.absoluteMutations.extractAbsoluteMutations(
                    range,
                    alignment.sequence1Range.lower == range.lower
                )
            }
    }.toMap()
}

private fun GeneFeature.cutCDR3PartOfFeature(): GeneFeature = when {
    GeneFeature.intersection(this, GeneFeature.VCDR3Part) != null -> GeneFeature(firstPoint, ReferencePoint.CDR3Begin)
    GeneFeature.intersection(this, GeneFeature.JCDR3Part) != null -> GeneFeature(ReferencePoint.CDR3End, lastPoint)
    else -> this
}

private fun getVMutationsWithinCDR3(clone: CloneWrapper): Pair<Mutations<NucleotideSequence>, Range> {
    val hit = clone.getHit(Variable)
    val CDR3Begin = hit.gene.partitioning.getRelativePosition(hit.alignedFeature, ReferencePoint.CDR3Begin)
    val alignment = (0 until hit.alignments.size)
        .map { hit.getAlignment(it) }
        .firstOrNull { alignment ->
            alignment.sequence1Range.contains(CDR3Begin)
        }
    return when (alignment) {
        null -> Mutations.EMPTY_NUCLEOTIDE_MUTATIONS to Range(CDR3Begin, CDR3Begin)
        else -> {
            val range = Range(CDR3Begin, alignment.sequence1Range.upper)
            alignment.absoluteMutations.extractAbsoluteMutations(range, false) to range
        }
    }
}

private fun getJMutationsWithinCDR3(clone: CloneWrapper): Pair<Mutations<NucleotideSequence>, Range> {
    val hit = clone.getHit(Joining)
    val CDR3End = hit.gene.partitioning.getRelativePosition(hit.alignedFeature, ReferencePoint.CDR3End)
    val alignment = (0 until hit.alignments.size)
        .map { hit.getAlignment(it) }
        .firstOrNull { alignment ->
            alignment.sequence1Range.contains(CDR3End)
        }
    return when (alignment) {
        null -> Mutations.EMPTY_NUCLEOTIDE_MUTATIONS to Range(CDR3End, CDR3End)
        else -> {
            val range = Range(alignment.sequence1Range.lower, CDR3End)
            alignment.absoluteMutations.extractAbsoluteMutations(range, true) to range
        }
    }
}
