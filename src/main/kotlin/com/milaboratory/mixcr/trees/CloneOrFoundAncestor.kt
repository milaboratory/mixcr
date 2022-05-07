package com.milaboratory.mixcr.trees

import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.mutations.MutationsUtil
import com.milaboratory.core.mutations.MutationsUtil.MutationNt2AADescriptor
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.core.sequence.TranslationParameters
import io.repseq.core.GeneType
import java.math.BigDecimal

@Suppress("FunctionName", "PropertyName")
abstract class CloneOrFoundAncestor protected constructor(
    val id: Int,
    private val fromGermlineToThis: MutationsDescription,
    private val fromGermlineToReconstructedRoot: MutationsDescription,
    private val fromGermlineToParent: MutationsDescription?,
    val distanceFromReconstructedRoot: BigDecimal?,
    val distanceFromGermline: BigDecimal
) {
    private val fromReconstructedRootToThis: MutationsDescription?
    private val fromParentToThis: MutationsDescription?

    init {
        if (fromGermlineToParent != null) {
            fromReconstructedRootToThis =
                MutationsUtils.mutationsBetween(fromGermlineToReconstructedRoot, fromGermlineToThis)
            fromParentToThis = MutationsUtils.mutationsBetween(fromGermlineToParent, fromGermlineToThis)
        } else {
            fromReconstructedRootToThis = null
            fromParentToThis = null
        }
    }

    val CDR3: NucleotideSequence
        get() = fromGermlineToThis.VMutationsInCDR3WithoutNDN.buildSequence()
            .concatenate(fromGermlineToThis.knownNDN.buildSequence())
            .concatenate(fromGermlineToThis.JMutationsInCDR3WithoutNDN.buildSequence())

    fun CDR3_VMutations(base: Base): Mutations<NucleotideSequence>? =
        getMutations(base) { it.VMutationsInCDR3WithoutNDN }

    fun CDR3_AA_VMutations(base: Base): Array<MutationNt2AADescriptor>? = getAAMutations(
        base,
        { it.VMutationsInCDR3WithoutNDN },
        null,
        TranslationParameters.FromLeftWithIncompleteCodon
    )

    fun CDR3_JMutations(base: Base): Mutations<NucleotideSequence>? =
        getMutations(base) { it.JMutationsInCDR3WithoutNDN }

    fun CDR3_AA_JMutations(base: Base): Array<MutationNt2AADescriptor>? = getAAMutations(
        base,
        { it.JMutationsInCDR3WithoutNDN },
        null,
        TranslationParameters.FromRightWithIncompleteCodon
    )

    fun CDR3_NDNMutations(base: Base): Mutations<NucleotideSequence>? = getMutations(base) { it.knownNDN }

    private fun getMutations(
        base: Base,
        supplier: (MutationsDescription) -> MutationsWithRange
    ): Mutations<NucleotideSequence>? = when (base) {
        Base.FromGermline -> supplier(fromGermlineToThis).mutationsForRange()
        Base.FromParent -> when (fromGermlineToParent) {
            null -> null
            else -> supplier(fromGermlineToParent).mutationsForRange().invert()
                .combineWith(supplier(fromGermlineToThis).mutationsForRange())
        }
        Base.FromReconstructedRoot -> when (fromGermlineToParent) {
            null -> null
            else -> supplier(fromGermlineToReconstructedRoot).mutationsForRange().invert()
                .combineWith(supplier(fromGermlineToThis).mutationsForRange())
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
                mutations.sequence1.getRange(mutations.rangeInfo.range),
                mutations.mutationsForRange().move(-mutations.rangeInfo.range.lower),
                translationParameters,
                3
            )
        }
    }

    private fun fromBaseToThis(base: Base): MutationsDescription? = when (base) {
        Base.FromGermline -> fromGermlineToThis
        Base.FromParent -> fromParentToThis
        Base.FromReconstructedRoot -> fromReconstructedRootToThis
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
        mutationsFromRoot: MutationsDescription,
        fromGermlineToReconstructedRoot: MutationsDescription,
        fromGermlineToParent: MutationsDescription?,
        distanceFromReconstructedRoot: BigDecimal?,
        distanceFromGermline: BigDecimal
    ) : CloneOrFoundAncestor(
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
        mutationsFromRoot: MutationsDescription,
        fromGermlineToReconstructedRoot: MutationsDescription,
        fromGermlineToParent: MutationsDescription?,
        distanceFromReconstructedRoot: BigDecimal?,
        distanceFromGermline: BigDecimal
    ) : CloneOrFoundAncestor(
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
