package com.milaboratory.mixcr.trees;

import com.milaboratory.core.alignment.AffineGapAlignmentScoring;
import com.milaboratory.core.alignment.Aligner;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MutationOperationsTest {
    @Test
    public void commonSubstitution() {
        NucleotideSequence parent = new NucleotideSequence("ATCT");
        NucleotideSequence first_ = new NucleotideSequence("ATGG");
        NucleotideSequence second = new NucleotideSequence("ATTG");

        Mutations<NucleotideSequence> result = MutationsUtils.intersection(
                mutations(parent, first_),
                mutations(parent, second)
        );
        assertEquals("[S3:T->G]", result.toString());
    }

    @Test
    public void commonDeletionWithMutationInBefore() {
        NucleotideSequence parent = new NucleotideSequence("ATGTA");
        NucleotideSequence first_ = new NucleotideSequence("CA");
        NucleotideSequence second = new NucleotideSequence("GA");

        Mutations<NucleotideSequence> fromParentToFirst = mutations(parent, first_);
        Mutations<NucleotideSequence> fromParentToSecond = mutations(parent, second);
        Mutations<NucleotideSequence> result = MutationsUtils.intersection(
                fromParentToFirst,
                fromParentToSecond
        );
        assertEquals("[D1:T,D2:G,D3:T]", result.toString());

        assertEquals("AA", result.mutate(parent).toString());
        assertEquals("[S0:A->C]", MutationsUtils.difference(result, fromParentToFirst).toString());
        assertEquals("[S0:A->G]", MutationsUtils.difference(result, fromParentToSecond).toString());
    }

    @Test
    public void mutationsBetweenInsertionIntoParentAndSubstitutionAfter() {
        NucleotideSequence parent = new NucleotideSequence("AA");
        NucleotideSequence first_ = new NucleotideSequence("AC");
        NucleotideSequence second = new NucleotideSequence("ATGTA");

        Mutations<NucleotideSequence> fromParentToFirst = mutations(parent, first_);
        Mutations<NucleotideSequence> fromParentToSecond = mutations(parent, second);
        assertEquals("[S1:A->C]", fromParentToFirst.toString());
        assertEquals("[I1:T,I1:G,I1:T]", fromParentToSecond.toString());

        Mutations<NucleotideSequence> fromFirstToSecond = MutationsUtils.difference(fromParentToFirst, fromParentToSecond);
        assertEquals("[I1:T,I1:G,I1:T,S1:A->C]", fromFirstToSecond.toString());
        assertEquals(second, fromParentToFirst.combineWith(fromFirstToSecond).mutate(parent));
    }

    @Test
    public void mutationsBetweenDeletionFromParentAndSubstitutionAfter() {
        NucleotideSequence parent = new NucleotideSequence("ATGTA");
        NucleotideSequence first_ = new NucleotideSequence("AA");
        NucleotideSequence second = new NucleotideSequence("ATGTC");

        Mutations<NucleotideSequence> fromParentToFirst = mutations(parent, first_);
        Mutations<NucleotideSequence> fromParentToSecond = mutations(parent, second);

        Mutations<NucleotideSequence> fromFirstToSecond = MutationsUtils.difference(fromParentToFirst, fromParentToSecond);
        assertEquals("[I1:T,I1:G,I1:T,S1:A->C]", fromFirstToSecond.toString());
        assertEquals(second, fromParentToFirst.combineWith(fromFirstToSecond).mutate(parent));
    }

    @Test
    public void mutationsBetweenDeletionFromParentAndSubstitutionInTheCenter() {
        NucleotideSequence parent = new NucleotideSequence("ATGTA");
        NucleotideSequence first_ = new NucleotideSequence("AA");
        NucleotideSequence second = new NucleotideSequence("ATATA");

        Mutations<NucleotideSequence> fromParentToFirst = mutations(parent, first_);
        Mutations<NucleotideSequence> fromParentToSecond = mutations(parent, second);

        Mutations<NucleotideSequence> fromFirstToSecond = MutationsUtils.difference(fromParentToFirst, fromParentToSecond);
        assertEquals("[I1:T,I1:A,I1:T]", fromFirstToSecond.toString());
        assertEquals(second, fromParentToFirst.combineWith(fromFirstToSecond).mutate(parent));
    }

    @Test
    public void commonInsertionWithMutationInTheBeginning() {
        NucleotideSequence parent = new NucleotideSequence("AA");
        NucleotideSequence first_ = new NucleotideSequence("ATGTA");
        NucleotideSequence second = new NucleotideSequence("AGGTA");

        Mutations<NucleotideSequence> fromParentToFirst = mutations(parent, first_);
        Mutations<NucleotideSequence> fromParentToSecond = mutations(parent, second);
        Mutations<NucleotideSequence> fromParentToCommonAncestor = MutationsUtils.intersection(
                fromParentToFirst,
                fromParentToSecond
        );
        assertEquals("[I1:N,I1:G,I1:T]", fromParentToCommonAncestor.toString());

        NucleotideSequence commonAncestor = fromParentToCommonAncestor.mutate(parent);
        assertEquals("ANGTA", commonAncestor.toString());
        Mutations<NucleotideSequence> fromCommonToFirst = MutationsUtils.difference(fromParentToCommonAncestor, fromParentToFirst);
        Mutations<NucleotideSequence> fromCommonToSecond = MutationsUtils.difference(fromParentToCommonAncestor, fromParentToSecond);
        assertEquals("[S1:N->T]", fromCommonToFirst.toString());
        assertEquals("[S1:N->G]", fromCommonToSecond.toString());
        assertEquals(first_, fromCommonToFirst.mutate(commonAncestor));
        assertEquals(second, fromCommonToSecond.mutate(commonAncestor));
    }

    @Test
    public void commonInsertionWithMutationInTheCenter() {
        NucleotideSequence parent = new NucleotideSequence("AA");
        NucleotideSequence first_ = new NucleotideSequence("ATGTA");
        NucleotideSequence second = new NucleotideSequence("ATCTA");

        Mutations<NucleotideSequence> fromParentToFirst = mutations(parent, first_);
        Mutations<NucleotideSequence> fromParentToSecond = mutations(parent, second);
        Mutations<NucleotideSequence> fromParentToCommonAncestor = MutationsUtils.intersection(
                fromParentToFirst,
                fromParentToSecond
        );
        assertEquals("[I1:T,I1:N,I1:T]", fromParentToCommonAncestor.toString());

        NucleotideSequence commonAncestor = fromParentToCommonAncestor.mutate(parent);
        assertEquals("ATNTA", commonAncestor.toString());
        Mutations<NucleotideSequence> fromCommonToFirst = MutationsUtils.difference(fromParentToCommonAncestor, fromParentToFirst);
        Mutations<NucleotideSequence> fromCommonToSecond = MutationsUtils.difference(fromParentToCommonAncestor, fromParentToSecond);
        assertEquals("[S2:N->G]", fromCommonToFirst.toString());
        assertEquals("[S2:N->C]", fromCommonToSecond.toString());
        assertEquals(first_, fromCommonToFirst.mutate(commonAncestor));
        assertEquals(second, fromCommonToSecond.mutate(commonAncestor));
    }
    
    private Mutations<NucleotideSequence> mutations(NucleotideSequence first, NucleotideSequence second) {
        return Aligner.alignGlobal(
                AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                first,
                second
        ).getAbsoluteMutations();
    }
}
