package com.milaboratory.mixcr.trees;

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

        Mutations<NucleotideSequence> result = ClusterProcessor.intersection(
                ClusterProcessor.mutations(parent, first_),
                ClusterProcessor.mutations(parent, second)
        );
        assertEquals("[S3:T->G]", result.toString());
    }

    @Test
    public void commonInsertionWithMutationInTheBeginning() {
        NucleotideSequence parent = new NucleotideSequence("AA");
        NucleotideSequence first_ = new NucleotideSequence("ATGTA");
        NucleotideSequence second = new NucleotideSequence("AGGTA");

        Mutations<NucleotideSequence> fromParentToFirst = ClusterProcessor.mutations(parent, first_);
        Mutations<NucleotideSequence> fromParentToSecond = ClusterProcessor.mutations(parent, second);
        Mutations<NucleotideSequence> result = ClusterProcessor.intersection(
                fromParentToFirst,
                fromParentToSecond
        );
        assertEquals("[I1:N,I1:G,I1:T]", result.toString());

        assertEquals("ANGTA", result.mutate(parent).toString());
        assertEquals("[S1:N->T]", ClusterProcessor.difference(result, fromParentToFirst).toString());
        assertEquals("[S1:N->G]", ClusterProcessor.difference(result, fromParentToSecond).toString());
    }

    @Test
    public void commonInsertionWithMutationInTheCenter() {
        NucleotideSequence parent = new NucleotideSequence("AA");
        NucleotideSequence first_ = new NucleotideSequence("ATGTA");
        NucleotideSequence second = new NucleotideSequence("ATCTA");

        Mutations<NucleotideSequence> fromParentToFirst = ClusterProcessor.mutations(parent, first_);
        Mutations<NucleotideSequence> fromParentToSecond = ClusterProcessor.mutations(parent, second);
        Mutations<NucleotideSequence> result = ClusterProcessor.intersection(
                fromParentToFirst,
                fromParentToSecond
        );
        assertEquals("[I1:T,I1:N,I1:T]", result.toString());

        assertEquals("ATNTA", result.mutate(parent).toString());
        assertEquals("[S2:N->G]", ClusterProcessor.difference(result, fromParentToFirst).toString());
        assertEquals("[S2:N->C]", ClusterProcessor.difference(result, fromParentToSecond).toString());
    }
}
