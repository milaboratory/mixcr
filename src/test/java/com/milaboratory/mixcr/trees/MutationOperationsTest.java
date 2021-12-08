package com.milaboratory.mixcr.trees;

import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class MutationOperationsTest {
    @Test
    public void commonSubstitution() {
        NucleotideSequence parent = new NucleotideSequence("ATCT");
        NucleotideSequence first_ = new NucleotideSequence("ATGG");
        NucleotideSequence second = new NucleotideSequence("ATTG");

        Mutations<NucleotideSequence> result = ClusterProcessor.intersection(
                ClusterProcessor.mutations(parent, first_),
                ClusterProcessor.mutations(parent, second)
        ).get(0).getMutations();
        assertEquals("[S3:T->G]", result.toString());
    }

    @Test
    public void commonDeletionWithMutationInBefore() {
        NucleotideSequence parent = new NucleotideSequence("ATGTA");
        NucleotideSequence first_ = new NucleotideSequence("CA");
        NucleotideSequence second = new NucleotideSequence("GA");

        List<MutationsWithRange> fromParentToFirst = ClusterProcessor.mutations(parent, first_);
        List<MutationsWithRange> fromParentToSecond = ClusterProcessor.mutations(parent, second);
        List<MutationsWithRange> result = ClusterProcessor.intersection(
                fromParentToFirst,
                fromParentToSecond
        );
        Mutations<NucleotideSequence> resultMutations = result.get(0).getMutations();
        assertEquals("[D1:T,D2:G,D3:T]", resultMutations.toString());

        assertEquals("AA", resultMutations.mutate(parent).toString());
        assertEquals("[S0:A->C]", ClusterProcessor.mutationsBetween(result, fromParentToFirst).get(0).getMutations().toString());
        assertEquals("[S0:A->G]", ClusterProcessor.mutationsBetween(result, fromParentToSecond).get(0).getMutations().toString());
    }

    @Test
    public void mutationsBetweenDeletionFromParentAndSubstitutionAfter() {
        NucleotideSequence parent = new NucleotideSequence("ATGTA");
        NucleotideSequence first_ = new NucleotideSequence("AA");
        NucleotideSequence second = new NucleotideSequence("ATGTC");

        List<MutationsWithRange> fromParentToFirst = ClusterProcessor.mutations(parent, first_);
        List<MutationsWithRange> fromParentToSecond = ClusterProcessor.mutations(parent, second);

        Mutations<NucleotideSequence> fromFirstToSecond = ClusterProcessor.mutationsBetween(fromParentToFirst, fromParentToSecond).get(0).getMutations();
        assertEquals("[I1:T,I1:G,I1:T,S1:A->C]", fromFirstToSecond.toString());
        assertEquals(second, fromParentToFirst.get(0).getMutations().combineWith(fromFirstToSecond).mutate(parent));
    }

    @Test
    public void mutationsBetweenDeletionFromParentAndSubstitutionInTheCenter() {
        NucleotideSequence parent = new NucleotideSequence("ATGTA");
        NucleotideSequence first_ = new NucleotideSequence("AA");
        NucleotideSequence second = new NucleotideSequence("ATATA");

        List<MutationsWithRange> fromParentToFirst = ClusterProcessor.mutations(parent, first_);
        List<MutationsWithRange> fromParentToSecond = ClusterProcessor.mutations(parent, second);

        Mutations<NucleotideSequence> fromFirstToSecond = ClusterProcessor.mutationsBetween(fromParentToFirst, fromParentToSecond).get(0).getMutations();
        assertEquals("[I1:T,I1:A,I1:T]", fromFirstToSecond.toString());
        assertEquals(second, fromParentToFirst.get(0).getMutations().combineWith(fromFirstToSecond).mutate(parent));
    }

    @Test
    public void commonInsertionWithMutationInTheBeginning() {
        NucleotideSequence parent = new NucleotideSequence("AA");
        NucleotideSequence first_ = new NucleotideSequence("ATGTA");
        NucleotideSequence second = new NucleotideSequence("AGGTA");

        List<MutationsWithRange> fromParentToFirst = ClusterProcessor.mutations(parent, first_);
        List<MutationsWithRange> fromParentToSecond = ClusterProcessor.mutations(parent, second);
        List<MutationsWithRange> fromParentToCommonAncestor = ClusterProcessor.intersection(
                fromParentToFirst,
                fromParentToSecond
        );
        Mutations<NucleotideSequence> commonAncestorMutations = fromParentToCommonAncestor.get(0).getMutations();
        assertEquals("[I1:N,I1:G,I1:T]", commonAncestorMutations.toString());

        NucleotideSequence commonAncestor = commonAncestorMutations.mutate(parent);
        assertEquals("ANGTA", commonAncestor.toString());
        Mutations<NucleotideSequence> fromCommonToFirst = ClusterProcessor.mutationsBetween(fromParentToCommonAncestor, fromParentToFirst).get(0).getMutations();
        Mutations<NucleotideSequence> fromCommonToSecond = ClusterProcessor.mutationsBetween(fromParentToCommonAncestor, fromParentToSecond).get(0).getMutations();
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

        List<MutationsWithRange> fromParentToFirst = ClusterProcessor.mutations(parent, first_);
        List<MutationsWithRange> fromParentToSecond = ClusterProcessor.mutations(parent, second);
        List<MutationsWithRange> fromParentToCommonAncestor = ClusterProcessor.intersection(
                fromParentToFirst,
                fromParentToSecond
        );
        Mutations<NucleotideSequence> commonAncestorMutations = fromParentToCommonAncestor.get(0).getMutations();
        assertEquals("[I1:T,I1:N,I1:T]", commonAncestorMutations.toString());

        NucleotideSequence commonAncestor = commonAncestorMutations.mutate(parent);
        assertEquals("ATNTA", commonAncestor.toString());
        Mutations<NucleotideSequence> fromCommonToFirst = ClusterProcessor.mutationsBetween(fromParentToCommonAncestor, fromParentToFirst).get(0).getMutations();
        Mutations<NucleotideSequence> fromCommonToSecond = ClusterProcessor.mutationsBetween(fromParentToCommonAncestor, fromParentToSecond).get(0).getMutations();
        assertEquals("[S2:N->G]", fromCommonToFirst.toString());
        assertEquals("[S2:N->C]", fromCommonToSecond.toString());
        assertEquals(first_, fromCommonToFirst.mutate(commonAncestor));
        assertEquals(second, fromCommonToSecond.mutate(commonAncestor));
    }
}
