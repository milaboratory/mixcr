package com.milaboratory.mixcr.trees;

import com.milaboratory.core.alignment.AffineGapAlignmentScoring;
import com.milaboratory.core.alignment.Aligner;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequenceBuilder;
import org.junit.Test;

import java.util.stream.IntStream;

import static com.milaboratory.mixcr.trees.MutationsUtils.findNDNCommonAncestor;
import static org.junit.Assert.assertEquals;

public class FindNDNCommonAncestorTest {
    @Test
    public void twoLettersFormWildcard() {
        NucleotideSequence base = base(4);
        Mutations<NucleotideSequence> first = build("TTTT", base);
        Mutations<NucleotideSequence> second = build("TTTG", base);

        assertEquals("TTTK", findNDNCommonAncestor(first, second).mutate(base).toString());
        assertEquals("TTTK", findNDNCommonAncestor(second, first).mutate(base).toString());
    }

    @Test
    public void wildcardAndLetterFormLetter() {
        NucleotideSequence base = base(4);
        Mutations<NucleotideSequence> first = build("TTTK", base);
        Mutations<NucleotideSequence> second = build("TTTG", base);

        assertEquals("TTTG", findNDNCommonAncestor(first, second).mutate(base).toString());
        assertEquals("TTTG", findNDNCommonAncestor(second, first).mutate(base).toString());
    }

    @Test
    public void wildcardAndLetterFormWiderWildcard() {
        NucleotideSequence base = base(4);
        Mutations<NucleotideSequence> first = build("TTTK", base);
        Mutations<NucleotideSequence> second = build("TTTC", base);

        assertEquals("TTTB", findNDNCommonAncestor(first, second).mutate(base).toString());
        assertEquals("TTTB", findNDNCommonAncestor(second, first).mutate(base).toString());
    }

    @Test
    public void twoWildcardsFormWiderWildcard() {
        NucleotideSequence base = base(4);
        Mutations<NucleotideSequence> first = build("TTTK", base);
        Mutations<NucleotideSequence> second = build("TTTY", base);

        assertEquals("TTTB", findNDNCommonAncestor(first, second).mutate(base).toString());
        assertEquals("TTTB", findNDNCommonAncestor(second, first).mutate(base).toString());
    }

    @Test
    public void twoWildcardsFormNarrowerWildcard() {
        NucleotideSequence base = base(4);
        Mutations<NucleotideSequence> first = build("TTTB", base);
        Mutations<NucleotideSequence> second = build("TTTY", base);

        assertEquals("TTTY", findNDNCommonAncestor(first, second).mutate(base).toString());
        assertEquals("TTTY", findNDNCommonAncestor(second, first).mutate(base).toString());
    }

    /**
     * <pre>
     *     G
     *     |
     *   S----
     *   |   |
     *  ---  G
     *  | |
     *  G C
     *
     *  =>
     *
     *     G
     *     |
     *   G----
     *   |   |
     *  ---  G
     *  | |
     *  G C
     * </pre>
     */
    @Test
    public void wideningFromLetterToWildcardMayBeConcertized() {
        NucleotideSequence base = base(4);
        Mutations<NucleotideSequence> parent = build("TTTG", base);
        Mutations<NucleotideSequence> child = build("TTTS", base);

        Mutations<NucleotideSequence> result = MutationsUtils.concreteNDNChild(parent, child);
        assertEquals("TTTG", result.mutate(base).toString());
    }

    /**
     * <pre>
     *     T
     *     |
     *   B----
     *   |   |
     *  ---  T
     *  | |
     *  T S
     *    |
     *   ---
     *   | |
     *   G S
     *
     *  =>
     *
     *     T
     *     |
     *   T----
     *   |   |
     *  ---  T
     *  | |
     *  T B
     *    |
     *   ---
     *   | |
     *   G S
     * </pre>
     */
    @Test
    public void narrowingFromWildcardToWildcardMayBeConcertized() {
        NucleotideSequence base = base(4);
        Mutations<NucleotideSequence> parent = build("TTTT", base);
        Mutations<NucleotideSequence> child = build("TTTS", base);

        Mutations<NucleotideSequence> result = MutationsUtils.concreteNDNChild(parent, child);
        assertEquals("TTTB", result.mutate(base).toString());
    }

    private Mutations<NucleotideSequence> build(String target, NucleotideSequence base) {
        return Aligner.alignGlobal(
                AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                base,
                new NucleotideSequence(target)
        ).getAbsoluteMutations();
    }

    private NucleotideSequence base(int baseLength) {
        SequenceBuilder<NucleotideSequence> fromBuilder = NucleotideSequence.ALPHABET.createBuilder();
        IntStream.range(0, baseLength).forEach(it -> fromBuilder.append(NucleotideSequence.N));
        return new NucleotideSequence("NNNN");
    }
}
