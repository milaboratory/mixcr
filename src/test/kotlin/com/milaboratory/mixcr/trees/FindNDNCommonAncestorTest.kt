package com.milaboratory.mixcr.trees

import com.milaboratory.core.alignment.AffineGapAlignmentScoring
import com.milaboratory.core.alignment.Aligner
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.trees.MutationsUtils.concreteNDNChild
import com.milaboratory.mixcr.trees.MutationsUtils.findNDNCommonAncestor
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.stream.IntStream

class FindNDNCommonAncestorTest {
    @Test
    fun twoLettersFormWildcard() {
        val base = base(4)
        val first = build("TTTT", base)
        val second = build("TTTG", base)
        assertEquals("TTTK", findNDNCommonAncestor(first, second).mutate(base).toString())
        assertEquals("TTTK", findNDNCommonAncestor(second, first).mutate(base).toString())
    }

    @Test
    fun wildcardAndLetterFormLetter() {
        val base = base(4)
        val first = build("TTTK", base)
        val second = build("TTTG", base)
        assertEquals("TTTG", findNDNCommonAncestor(first, second).mutate(base).toString())
        assertEquals("TTTG", findNDNCommonAncestor(second, first).mutate(base).toString())
    }

    @Test
    fun wildcardAndLetterFormWiderWildcard() {
        val base = base(4)
        val first = build("TTTK", base)
        val second = build("TTTC", base)
        assertEquals("TTTB", findNDNCommonAncestor(first, second).mutate(base).toString())
        assertEquals("TTTB", findNDNCommonAncestor(second, first).mutate(base).toString())
    }

    @Test
    fun twoWildcardsFormWiderWildcard() {
        val base = base(4)
        val first = build("TTTK", base)
        val second = build("TTTY", base)
        assertEquals("TTTB", findNDNCommonAncestor(first, second).mutate(base).toString())
        assertEquals("TTTB", findNDNCommonAncestor(second, first).mutate(base).toString())
    }

    @Test
    fun twoWildcardsFormNarrowerWildcard() {
        val base = base(4)
        val first = build("TTTB", base)
        val second = build("TTTY", base)
        assertEquals("TTTY", findNDNCommonAncestor(first, second).mutate(base).toString())
        assertEquals("TTTY", findNDNCommonAncestor(second, first).mutate(base).toString())
    }

    /**
     * <pre>
     * G
     * |
     * S-----
     * |    |
     * ---  G
     * | |
     * G C
     *
     * =>
     *
     * G
     * |
     * G-----
     * |    |
     * ---  G
     * | |
     * G C
    </pre> *
     */
    @Test
    fun wideningFromLetterToWildcardMayBeConcertized() {
        val base = base(4)
        val parent = build("TTTG", base)
        val child = build("TTTS", base)
        val result = concreteNDNChild(parent, child)
        assertEquals("TTTG", result.mutate(base).toString())
    }

    /**
     * <pre>
     * T
     * |
     * B-----
     * |    |
     * ---  T
     * | |
     * T S
     * |
     * ---
     * | |
     * G S
     *
     * =>
     *
     * T
     * |
     * T-----
     * |    |
     * ---  T
     * | |
     * T B
     * |
     * ---
     * | |
     * G S
    </pre> *
     */
    @Test
    fun narrowingFromWildcardToWildcardMayBeConcertized() {
        val base = base(4)
        val parent = build("TTTT", base)
        val child = build("TTTS", base)
        val result = concreteNDNChild(parent, child)
        assertEquals("TTTB", result.mutate(base).toString())
    }

    private fun build(target: String, base: NucleotideSequence): Mutations<NucleotideSequence> {
        return Aligner.alignGlobal(
            AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
            base,
            NucleotideSequence(target)
        ).absoluteMutations
    }

    private fun base(baseLength: Int): NucleotideSequence {
        val fromBuilder = NucleotideSequence.ALPHABET.createBuilder()
        IntStream.range(0, baseLength).forEach { fromBuilder.append(NucleotideSequence.N) }
        return NucleotideSequence("NNNN")
    }
}
