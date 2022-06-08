package com.milaboratory.mixcr.trees

import com.milaboratory.core.Range
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.mutations.MutationsUtil
import com.milaboratory.core.mutations.MutationsUtil.MutationNt2AADescriptor
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.core.sequence.TranslationParameters
import com.milaboratory.mixcr.trees.CloneOrFoundAncestorOld.Base.FromGermline
import com.milaboratory.mixcr.trees.CloneOrFoundAncestorOld.Base.FromParent
import com.milaboratory.mixcr.trees.CloneOrFoundAncestorOld.Base.FromReconstructedRoot
import com.milaboratory.mixcr.trees.MutationsUtils.mutationsBetween
import io.repseq.core.GeneType
import java.math.BigDecimal

@Suppress("FunctionName", "PropertyName")
sealed class CloneOrFoundAncestorOld(
    val id: Int,
    private val fromGermlineToThis: MutationsSet,
    private val fromGermlineToReconstructedRoot: MutationsSet,
    private val fromGermlineToParent: MutationsSet?,
    val distanceFromReconstructedRoot: BigDecimal?,
    val distanceFromGermline: BigDecimal
) {
    private val fromGermlineToThisAsMutationsDescription: MutationsDescription = MutationsDescription(
        fromGermlineToThis.VMutations.mutations.mapValues { (range, mutations) ->
            MutationsWithRange(fromGermlineToThis.VMutations.sequence1, mutations, range)
        },
        MutationsWithRange(
            fromGermlineToThis.VMutations.sequence1,
            fromGermlineToThis.VMutations.partInCDR3.mutations,
            fromGermlineToThis.VMutations.partInCDR3.range
        ),
        MutationsWithRange(
            fromGermlineToThis.NDNMutations.base,
            fromGermlineToThis.NDNMutations.mutations,
            Range(0, fromGermlineToThis.NDNMutations.base.size())
        ),
        MutationsWithRange(
            fromGermlineToThis.JMutations.sequence1,
            fromGermlineToThis.JMutations.partInCDR3.mutations,
            fromGermlineToThis.JMutations.partInCDR3.range
        ),
        fromGermlineToThis.JMutations.mutations.mapValues { (range, mutations) ->
            MutationsWithRange(fromGermlineToThis.JMutations.sequence1, mutations, range)
        }
    )
    private val fromReconstructedRootToThis: MutationsDescription?
    private val fromParentToThis: MutationsDescription?

    init {
        if (fromGermlineToParent != null) {
            fromReconstructedRootToThis = mutationsBetween(fromGermlineToReconstructedRoot, fromGermlineToThis)
            fromParentToThis = mutationsBetween(fromGermlineToParent, fromGermlineToThis)
        } else {
            fromReconstructedRootToThis = null
            fromParentToThis = null
        }
    }

    val CDR3: NucleotideSequence
        get() = fromGermlineToThis.VMutations.buildPartInCDR3()
            .concatenate(fromGermlineToThis.NDNMutations.buildSequence())
            .concatenate(fromGermlineToThis.JMutations.buildPartInCDR3())

    fun CDR3_VMutations(base: Base): Mutations<NucleotideSequence>? =
        getMutations(base) { it.VMutations.partInCDR3.mutations }

    fun CDR3_AA_VMutations(base: Base): Array<MutationNt2AADescriptor>? = getAAMutations(
        base,
        { it.VMutationsInCDR3WithoutNDN },
        null,
        TranslationParameters.FromLeftWithIncompleteCodon
    )

    fun CDR3_JMutations(base: Base): Mutations<NucleotideSequence>? =
        getMutations(base) { it.JMutations.partInCDR3.mutations }

    fun CDR3_AA_JMutations(base: Base): Array<MutationNt2AADescriptor>? = getAAMutations(
        base,
        { it.JMutationsInCDR3WithoutNDN },
        null,
        TranslationParameters.FromRightWithIncompleteCodon
    )

    fun CDR3_NDNMutations(base: Base): Mutations<NucleotideSequence>? = getMutations(base) { it.NDNMutations.mutations }

    private fun getMutations(
        base: Base,
        supplier: (MutationsSet) -> Mutations<NucleotideSequence>
    ): Mutations<NucleotideSequence>? = when (base) {
        FromGermline -> supplier(fromGermlineToThis)
        FromParent -> when (fromGermlineToParent) {
            null -> null
            else -> supplier(fromGermlineToParent).invert()
                .combineWith(supplier(fromGermlineToThis))
        }
        FromReconstructedRoot -> when (fromGermlineToParent) {
            null -> null
            else -> supplier(fromGermlineToReconstructedRoot).invert()
                .combineWith(supplier(fromGermlineToThis))
        }
    }

    private fun getAAMutations(
        base: Base,
        supplier: (MutationsDescription) -> MutationsWithRange,
        nucleotidesLeft: Int?,
        translationParameters: TranslationParameters
    ): Array<MutationNt2AADescriptor>? {
        val subject = fromBaseToThis(base) ?: return null
        val mutations = supplier(subject)
        return when {
            nucleotidesLeft != null && mutations.buildSequence().size() % 3 != nucleotidesLeft -> null
            else -> MutationsUtil.nt2aaDetailed(
                mutations.sequence1.getRange(mutations.range),
                mutations.mutations.move(-mutations.range.lower),
                translationParameters,
                3
            )
        }
    }

    private fun fromBaseToThis(base: Base): MutationsDescription? = when (base) {
        FromGermline -> fromGermlineToThisAsMutationsDescription
        FromParent -> fromParentToThis
        FromReconstructedRoot -> fromReconstructedRootToThis
    }

    abstract val cloneId: Int?
    abstract val count: Double?
    abstract val CGeneName: String?

    enum class Base {
        FromGermline, FromParent, FromReconstructedRoot
    }

    internal class CloneInfo(
        private val cloneWrapper: CloneWrapper,
        id: Int,
        mutationsFromRoot: MutationsSet,
        fromGermlineToReconstructedRoot: MutationsSet,
        fromGermlineToParent: MutationsSet?,
        distanceFromReconstructedRoot: BigDecimal?,
        distanceFromGermline: BigDecimal
    ) : CloneOrFoundAncestorOld(
        id,
        mutationsFromRoot,
        fromGermlineToReconstructedRoot,
        fromGermlineToParent,
        distanceFromReconstructedRoot,
        distanceFromGermline
    ) {
        override val cloneId: Int
            get() = cloneWrapper.clone.id

        override val count: Double
            get() = cloneWrapper.clone.count

        override val CGeneName: String?
            get() = cloneWrapper.clone.getBestHit(GeneType.Constant)?.gene?.name
    }

    internal class AncestorInfo(
        id: Int,
        mutationsFromRoot: MutationsSet,
        fromGermlineToReconstructedRoot: MutationsSet,
        fromGermlineToParent: MutationsSet?,
        distanceFromReconstructedRoot: BigDecimal?,
        distanceFromGermline: BigDecimal
    ) : CloneOrFoundAncestorOld(
        id,
        mutationsFromRoot,
        fromGermlineToReconstructedRoot,
        fromGermlineToParent,
        distanceFromReconstructedRoot,
        distanceFromGermline
    ) {
        override val cloneId: Int?
            get() = null
        override val count: Double?
            get() = null
        override val CGeneName: String?
            get() = null
    }
}
