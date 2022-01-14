package com.milaboratory.mixcr.trees;

import com.google.common.primitives.Bytes;
import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.AffineGapAlignmentScoring;
import com.milaboratory.core.alignment.Aligner;
import com.milaboratory.core.mutations.Mutation;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.mutations.MutationsBuilder;
import com.milaboratory.core.sequence.NucleotideSequence;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

@Ignore
public class MutationOperationsTest {
    @Test
    public void commonSubstitution() {
        NucleotideSequence parent = new NucleotideSequence("ATCT");
        NucleotideSequence first_ = new NucleotideSequence("ATGG");
        NucleotideSequence second = new NucleotideSequence("ATTG");

        Mutations<NucleotideSequence> result = MutationsUtils.intersection(
                mutations(parent, first_),
                mutations(parent, second),
                new Range(0, parent.size()),
                true
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
                fromParentToSecond,
                new Range(0, parent.size()),
                true
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

        Mutations<NucleotideSequence> fromParentToCommonAncestor = MutationsUtils.intersection(
                fromParentToFirst,
                fromParentToSecond,
                new Range(0, parent.size()),
                true
        );
        assertEquals("[I1:N,I1:N,I1:N]", fromParentToCommonAncestor.toString());

        NucleotideSequence commonAncestor = fromParentToCommonAncestor.mutate(parent);
        assertEquals("ANNNA", commonAncestor.toString());

        Mutations<NucleotideSequence> fromCommonToFirst = MutationsUtils.difference(fromParentToCommonAncestor, fromParentToFirst);
        assertEquals("[D1:N,D2:N,D3:N,S4:A->C]", fromCommonToFirst.toString());
        assertEquals(first_, fromCommonToFirst.mutate(commonAncestor));

        Mutations<NucleotideSequence> fromCommonToSecond = MutationsUtils.difference(fromParentToCommonAncestor, fromParentToSecond);
        assertEquals("[S1:N->T,S1:N->G,S1:N->T]", fromCommonToSecond.toString());
        assertEquals(second, fromCommonToSecond.mutate(commonAncestor));


        Mutations<NucleotideSequence> fromFirstToSecond = MutationsUtils.difference(fromParentToFirst, fromParentToSecond);
        assertEquals("[I1:T,I1:G,I1:T,S1:C->A]", fromFirstToSecond.toString());
        assertEquals(second, fromParentToFirst.combineWith(fromFirstToSecond).mutate(parent));
    }

    @Test
    public void insertionBeforeCommonSubstitution() {
        NucleotideSequence parent = new NucleotideSequence("GT");
        NucleotideSequence first_ = new NucleotideSequence("GA");
        NucleotideSequence second = new NucleotideSequence("TGA");

        Mutations<NucleotideSequence> fromParentToFirst = mutations(parent, first_);
        Mutations<NucleotideSequence> fromParentToSecond = mutations(parent, second);
        assertEquals("[S1:T->A]", fromParentToFirst.toString());
        assertEquals("[I0:T,S1:T->A]", fromParentToSecond.toString());

        Mutations<NucleotideSequence> fromParentToCommonAncestor = MutationsUtils.intersection(
                fromParentToFirst,
                fromParentToSecond,
                new Range(0, parent.size()),
                true
        );
        assertEquals("[I0:N,S1:T->A]", fromParentToCommonAncestor.toString());

        NucleotideSequence commonAncestor = fromParentToCommonAncestor.mutate(parent);
        assertEquals("NGA", commonAncestor.toString());

        Mutations<NucleotideSequence> fromCommonToFirst = MutationsUtils.difference(fromParentToCommonAncestor, fromParentToFirst);
        assertEquals("[D0:N]", fromCommonToFirst.toString());
        assertEquals(first_, fromCommonToFirst.mutate(commonAncestor));

        Mutations<NucleotideSequence> fromCommonToSecond = MutationsUtils.difference(fromParentToCommonAncestor, fromParentToSecond);
        assertEquals("[S0:N->T]", fromCommonToSecond.toString());
        assertEquals(second, fromCommonToSecond.mutate(commonAncestor));


        Mutations<NucleotideSequence> fromFirstToSecond = MutationsUtils.difference(fromParentToFirst, fromParentToSecond);
        assertEquals("[I0:T]", fromFirstToSecond.toString());
        assertEquals(second, fromParentToFirst.combineWith(fromFirstToSecond).mutate(parent));
    }

    @Test
    public void severalInsertionsInTheEnd() {
        NucleotideSequence parent = new NucleotideSequence("GG");
        NucleotideSequence first_ = new NucleotideSequence("AG");
        NucleotideSequence second = new NucleotideSequence("GGCG");

        Mutations<NucleotideSequence> fromParentToFirst = mutations(parent, first_);
        Mutations<NucleotideSequence> fromParentToSecond = mutations(parent, second);
        assertEquals("[S0:G->A]", fromParentToFirst.toString());
        assertEquals("[I2:C,I2:G]", fromParentToSecond.toString());

        Mutations<NucleotideSequence> fromParentToCommonAncestor = MutationsUtils.intersection(
                fromParentToFirst,
                fromParentToSecond,
                new Range(0, parent.size()),
                true);
        assertEquals("[I2:N,I2:N]", fromParentToCommonAncestor.toString());

        NucleotideSequence commonAncestor = fromParentToCommonAncestor.mutate(parent);
        assertEquals("GGNN", commonAncestor.toString());

        Mutations<NucleotideSequence> fromCommonToFirst = MutationsUtils.difference(fromParentToCommonAncestor, fromParentToFirst);
        assertEquals("[S0:G->A,D2:N,D3:N]", fromCommonToFirst.toString());
        assertEquals(first_, fromCommonToFirst.mutate(commonAncestor));

        Mutations<NucleotideSequence> fromCommonToSecond = MutationsUtils.difference(fromParentToCommonAncestor, fromParentToSecond);
        assertEquals("[S2:N->C,S3:N->G]", fromCommonToSecond.toString());
        assertEquals(second, fromCommonToSecond.mutate(commonAncestor));


        Mutations<NucleotideSequence> fromFirstToSecond = MutationsUtils.difference(fromParentToFirst, fromParentToSecond);
        assertEquals("[S0:A->G,I2:C,I2:G]", fromFirstToSecond.toString());
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
        NucleotideSequence first_ = new NucleotideSequence("ATCTA");
        NucleotideSequence second = new NucleotideSequence("AGCTA");

        Mutations<NucleotideSequence> fromParentToFirst = mutations(parent, first_);
        Mutations<NucleotideSequence> fromParentToSecond = mutations(parent, second);
        Mutations<NucleotideSequence> fromParentToCommonAncestor = MutationsUtils.intersection(
                fromParentToFirst,
                fromParentToSecond,
                new Range(0, parent.size()),
                true
        );
        assertEquals("[I1:N,I1:C,I1:T]", fromParentToCommonAncestor.toString());

        NucleotideSequence commonAncestor = fromParentToCommonAncestor.mutate(parent);
        assertEquals("ANCTA", commonAncestor.toString());

        Mutations<NucleotideSequence> fromCommonToFirst = MutationsUtils.difference(fromParentToCommonAncestor, fromParentToFirst);
        assertEquals("[S1:N->T]", fromCommonToFirst.toString());
        assertEquals(first_, fromCommonToFirst.mutate(commonAncestor));

        Mutations<NucleotideSequence> fromCommonToSecond = MutationsUtils.difference(fromParentToCommonAncestor, fromParentToSecond);
        assertEquals("[S1:N->G]", fromCommonToSecond.toString());
        assertEquals(second, fromCommonToSecond.mutate(commonAncestor));

        Mutations<NucleotideSequence> fromFirstToSecond = MutationsUtils.difference(fromParentToFirst, fromParentToSecond);
        assertEquals("[S1:T->G]", fromFirstToSecond.toString());
        assertEquals(second, fromParentToFirst.combineWith(fromFirstToSecond).mutate(parent));
    }

    @Test
    public void commonInsertionWithDifferentLength() {
        NucleotideSequence parent = new NucleotideSequence("AA");
        NucleotideSequence first_ = new NucleotideSequence("ATGTA");
        NucleotideSequence second = new NucleotideSequence("AGA");

        Mutations<NucleotideSequence> fromParentToFirst = mutations(parent, first_);
        Mutations<NucleotideSequence> fromParentToSecond = mutations(parent, second);
        Mutations<NucleotideSequence> fromParentToCommonAncestor = MutationsUtils.intersection(
                fromParentToFirst,
                fromParentToSecond,
                new Range(0, parent.size()),
                true
        );
        assertEquals("[I1:N,I1:G,I1:N]", fromParentToCommonAncestor.toString());

        NucleotideSequence commonAncestor = fromParentToCommonAncestor.mutate(parent);
        assertEquals("ANGNA", commonAncestor.toString());

        Mutations<NucleotideSequence> fromCommonToFirst = MutationsUtils.difference(fromParentToCommonAncestor, fromParentToFirst);
        assertEquals("[S1:N->T,S3:N->T]", fromCommonToFirst.toString());
        assertEquals(first_, fromCommonToFirst.mutate(commonAncestor));

        Mutations<NucleotideSequence> fromCommonToSecond = MutationsUtils.difference(fromParentToCommonAncestor, fromParentToSecond);
        assertEquals("[D1:N,D3:N]", fromCommonToSecond.toString());
        assertEquals(second, fromCommonToSecond.mutate(commonAncestor));

        Mutations<NucleotideSequence> fromFirstToSecond = MutationsUtils.difference(fromParentToFirst, fromParentToSecond);
        assertEquals("[D1:T,D3:T]", fromFirstToSecond.toString());
        assertEquals(second, fromParentToFirst.combineWith(fromFirstToSecond).mutate(parent));
    }

    @Test
    public void commonInsertionThatIsEndOfFirstAndStartOfSecond() {
        NucleotideSequence parent = new NucleotideSequence("AA");
        NucleotideSequence first_ = new NucleotideSequence("ATGGA");
        NucleotideSequence second = new NucleotideSequence("AGGTA");

        Mutations<NucleotideSequence> fromParentToFirst = mutations(parent, first_);
        Mutations<NucleotideSequence> fromParentToSecond = mutations(parent, second);
        Mutations<NucleotideSequence> fromParentToCommonAncestor = MutationsUtils.intersection(
                fromParentToFirst,
                fromParentToSecond,
                new Range(0, parent.size()),
                true
        );
        assertEquals("[I1:N,I1:G,I1:G,I1:N]", fromParentToCommonAncestor.toString());

        NucleotideSequence commonAncestor = fromParentToCommonAncestor.mutate(parent);
        assertEquals("ANGGNA", commonAncestor.toString());

        Mutations<NucleotideSequence> fromCommonToFirst = MutationsUtils.difference(fromParentToCommonAncestor, fromParentToFirst);
        assertEquals("[S1:N->T,D3:N]", fromCommonToFirst.toString());
        assertEquals(first_, fromCommonToFirst.mutate(commonAncestor));

        Mutations<NucleotideSequence> fromCommonToSecond = MutationsUtils.difference(fromParentToCommonAncestor, fromParentToSecond);
        assertEquals("[D1:N,S3:N->T]", fromCommonToSecond.toString());
        assertEquals(second, fromCommonToSecond.mutate(commonAncestor));

        Mutations<NucleotideSequence> fromFirstToSecond = MutationsUtils.difference(fromParentToFirst, fromParentToSecond);
        assertEquals("[S1:T->G,S3:G->T]", fromFirstToSecond.toString());
        assertEquals(second, fromParentToFirst.combineWith(fromFirstToSecond).mutate(parent));
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
                fromParentToSecond,
                new Range(0, parent.size()),
                true
        );
        assertEquals("[I1:T,I1:N,I1:T]", fromParentToCommonAncestor.toString());

        NucleotideSequence commonAncestor = fromParentToCommonAncestor.mutate(parent);
        assertEquals("ATNTA", commonAncestor.toString());

        Mutations<NucleotideSequence> fromCommonToFirst = MutationsUtils.difference(fromParentToCommonAncestor, fromParentToFirst);
        assertEquals("[S2:N->G]", fromCommonToFirst.toString());
        assertEquals(first_, fromCommonToFirst.mutate(commonAncestor));

        Mutations<NucleotideSequence> fromCommonToSecond = MutationsUtils.difference(fromParentToCommonAncestor, fromParentToSecond);
        assertEquals("[S2:N->C]", fromCommonToSecond.toString());
        assertEquals(second, fromCommonToSecond.mutate(commonAncestor));

        Mutations<NucleotideSequence> fromFirstToSecond = MutationsUtils.difference(fromParentToFirst, fromParentToSecond);
        assertEquals("[S2:G->T]", fromFirstToSecond.toString());
        assertEquals(second, fromParentToFirst.combineWith(fromFirstToSecond).mutate(parent));
    }

    @Ignore
    @Test
    public void randomizedTest() {
        int numberOfRuns = 1_000_000;
        List<Long> failedSeeds = IntStream.range(0, numberOfRuns)
                .mapToObj(it -> ThreadLocalRandom.current().nextLong())
                .parallel()
                .filter(seed -> testRandomMutations(seed, false))
                .collect(Collectors.toList());

        assertEquals(Collections.emptyList(), failedSeeds);
    }

    @Test
    public void reproduceRandom() {
        testRandomMutations(1440251408855117555L, true);
    }

    private boolean testRandomMutations(long seed, boolean print) {
        try {
            Random random = new Random(seed);
            List<Byte> chars = IntStream.range(0, 5 + random.nextInt(15))
                    .mapToObj(it -> (byte) random.nextInt(4))
                    .collect(Collectors.toList());

            NucleotideSequence parent = new NucleotideSequence(Bytes.toArray(chars));
            NucleotideSequence first = generateMutations(parent, random).mutate(parent);
            NucleotideSequence second = generateMutations(parent, random).mutate(parent);
            if (print) {
                System.out.println("Parent:");
                System.out.println(parent);
                System.out.println("First:");
                System.out.println(first);
                System.out.println("Second:");
                System.out.println(second);
                System.out.println();
            }

            Mutations<NucleotideSequence> fromParentToFirst = mutations(parent, first);
            Mutations<NucleotideSequence> fromParentToSecond = mutations(parent, second);
            Mutations<NucleotideSequence> fromParentToCommonAncestor = MutationsUtils.intersection(
                    fromParentToFirst,
                    fromParentToSecond,
                    new Range(0, parent.size()),
                    true
            );
            if (print) {
                System.out.println("Parent -> First:");
                System.out.println(fromParentToFirst);
                System.out.println("Parent -> Second:");
                System.out.println(fromParentToSecond);
                System.out.println("Parent -> Common:");
                System.out.println(fromParentToCommonAncestor);
                System.out.println();
            }

            NucleotideSequence commonAncestor = fromParentToCommonAncestor.mutate(parent);
            Mutations<NucleotideSequence> fromCommonToFirst = MutationsUtils.difference(fromParentToCommonAncestor, fromParentToFirst);
            Mutations<NucleotideSequence> fromCommonToSecond = MutationsUtils.difference(fromParentToCommonAncestor, fromParentToSecond);
            Mutations<NucleotideSequence> fromFirstToSecond = MutationsUtils.difference(fromParentToFirst, fromParentToSecond);
            Mutations<NucleotideSequence> fromSecondToFirst = MutationsUtils.difference(fromParentToSecond, fromParentToFirst);

            if (print) {
                System.out.println("Common ancestor:");
                System.out.println(commonAncestor);
                System.out.println();

                System.out.println("Common -> First:");
                System.out.println(fromCommonToFirst);
                System.out.println("Common -> Second:");
                System.out.println(fromCommonToSecond);
                System.out.println("First -> Second:");
                System.out.println(fromFirstToSecond);
                System.out.println("Second -> First:");
                System.out.println(fromSecondToFirst);
            }

            assertEquals(first, fromParentToFirst.mutate(parent));
            assertEquals(second, fromParentToSecond.mutate(parent));
            assertEquals(commonAncestor, fromParentToCommonAncestor.mutate(parent));
            assertEquals(first, fromCommonToFirst.mutate(commonAncestor));
            assertEquals(second, fromCommonToSecond.mutate(commonAncestor));
            assertEquals(first, fromSecondToFirst.mutate(second));
            assertEquals(second, fromFirstToSecond.mutate(first));
            return false;
        } catch (Throwable e) {
            if (!print) {
                return true;
            } else {
                throw e;
            }
        }
    }

    static Mutations<NucleotideSequence> generateMutations(NucleotideSequence parent, Random random) {
        return generateMutations(parent, random, new Range(0, parent.size()));
    }

    static Mutations<NucleotideSequence> generateMutations(NucleotideSequence parent, Random random, Range range) {
        MutationsBuilder<NucleotideSequence> result = new MutationsBuilder<>(NucleotideSequence.ALPHABET);

        byte[] parentChars = parent.getSequence().asArray();
        for (int i = range.getFrom(); i < range.getTo() - 1; i++) {
            int count = random.nextInt(20);
            switch (count) {
                case 0:
                    int insertionsCount = random.nextInt(3);
                    for (int i1 = 0; i1 < insertionsCount; i1++) {
                        result.append(Mutation.createInsertion(i, (byte) random.nextInt(4)));
                    }
                    break;
                case 1:
                    result.append(Mutation.createDeletion(i, parentChars[i]));
                    break;
                case 2:
                case 3:
                case 4:
                    byte replaceWith = (byte) random.nextInt(4);
                    if (parentChars[i] != replaceWith) {
                        result.append(Mutation.createSubstitution(i, parentChars[i], replaceWith));
                    }
                    break;
            }
        }

        return result.createAndDestroy();
//        return Aligner.alignGlobal(
//                LinearGapAlignmentScoring.getNucleotideBLASTScoring(),
//                parent,
//                result.createAndDestroy().mutate(parent)
//        ).getAbsoluteMutations();
    }

    private Mutations<NucleotideSequence> mutations(NucleotideSequence first, NucleotideSequence second) {
        return Aligner.alignGlobal(
                AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                first,
                second
        ).getAbsoluteMutations();
    }
}
