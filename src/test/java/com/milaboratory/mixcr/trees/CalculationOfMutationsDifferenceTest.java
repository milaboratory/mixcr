package com.milaboratory.mixcr.trees;

import com.google.common.primitives.Bytes;
import com.milaboratory.core.Range;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.milaboratory.mixcr.trees.MutationsUtils.mutationsBetween;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class CalculationOfMutationsDifferenceTest {
    @Test
    public void dontIncludeInsertionsFirstInsertionsInParent() {
        NucleotideSequence grand_ = new NucleotideSequence("TTTT");
        NucleotideSequence parent = new NucleotideSequence("TTTTG");
        NucleotideSequence child_ = new NucleotideSequence("ATTTTA");
        MutationsWithRange fromGrandToParent = new MutationsWithRange(
                grand_,
                new Mutations<>(NucleotideSequence.ALPHABET, "I0G,I4G"),
                new RangeInfo(new Range(0, grand_.size()), false)
        );
        assertEquals(parent, fromGrandToParent.buildSequence());
        MutationsWithRange fromGrandToChild = new MutationsWithRange(
                grand_,
                new Mutations<>(NucleotideSequence.ALPHABET, "I0A,I4A"),
                new RangeInfo(new Range(0, grand_.size()), true)
        );
        assertEquals(child_, fromGrandToChild.buildSequence());
        MutationsWithRange result = mutationsBetween(fromGrandToParent, fromGrandToChild);
        assertEquals(child_, result.buildSequence());
    }

    @Test
    public void dontIncludeFirstInsertionsInChild() {
        NucleotideSequence grand_ = new NucleotideSequence("TTTT");
        NucleotideSequence parent = new NucleotideSequence("GTTTTG");
        NucleotideSequence child_ = new NucleotideSequence("TTTTA");
        MutationsWithRange fromGrandToParent = new MutationsWithRange(
                grand_,
                new Mutations<>(NucleotideSequence.ALPHABET, "I0G,I4G"),
                new RangeInfo(new Range(0, grand_.size()), true)
        );
        assertEquals(parent, fromGrandToParent.buildSequence());
        MutationsWithRange fromGrandToChild = new MutationsWithRange(
                grand_,
                new Mutations<>(NucleotideSequence.ALPHABET, "I0A,I4A"),
                new RangeInfo(new Range(0, grand_.size()), false)
        );
        assertEquals(child_, fromGrandToChild.buildSequence());
        MutationsWithRange result = mutationsBetween(fromGrandToParent, fromGrandToChild);
        assertEquals(child_, result.buildSequence());
    }

    @Test
    public void deletionOfFirstLetterInChildAndIncludeFirst() {
        NucleotideSequence grand_ = new NucleotideSequence("TTTT");
        NucleotideSequence parent = new NucleotideSequence("TTTG");
        NucleotideSequence child_ = new NucleotideSequence("TTT");
        MutationsWithRange fromGrandToParent = new MutationsWithRange(
                grand_,
                new Mutations<>(NucleotideSequence.ALPHABET, "ST3G"),
                new RangeInfo(new Range(0, grand_.size()), false)
        );
        assertEquals(parent, fromGrandToParent.buildSequence());
        MutationsWithRange fromGrandToChild = new MutationsWithRange(
                grand_,
                new Mutations<>(NucleotideSequence.ALPHABET, "DT0"),
                new RangeInfo(new Range(0, grand_.size()), false)
        );
        assertEquals(child_, fromGrandToChild.buildSequence());
        MutationsWithRange result = mutationsBetween(fromGrandToParent, fromGrandToChild);
        assertEquals(child_, result.buildSequence());
    }

    @Test
    public void deletionOfFirstLetterInChildAndDontIncludeFirstInsertions() {
        NucleotideSequence grand_ = new NucleotideSequence("TTTT");
        NucleotideSequence parent = new NucleotideSequence("TTTG");
        NucleotideSequence child_ = new NucleotideSequence("TTT");
        MutationsWithRange fromGrandToParent = new MutationsWithRange(
                grand_,
                new Mutations<>(NucleotideSequence.ALPHABET, "ST3G"),
                new RangeInfo(new Range(0, grand_.size()), false)
        );
        assertEquals(parent, fromGrandToParent.buildSequence());
        MutationsWithRange fromGrandToChild = new MutationsWithRange(
                grand_,
                new Mutations<>(NucleotideSequence.ALPHABET, "DT0"),
                new RangeInfo(new Range(0, grand_.size()), false)
        );
        assertEquals(child_, fromGrandToChild.buildSequence());
        MutationsWithRange result = mutationsBetween(fromGrandToParent, fromGrandToChild);
        assertEquals(child_, result.buildSequence());
    }

    @Test
    public void insertionsInDifferentPlaces() {
        NucleotideSequence grand_ = new NucleotideSequence("CCCC");
        NucleotideSequence parent = new NucleotideSequence("CTCCC");
        NucleotideSequence child_ = new NucleotideSequence("CCCAC");
        MutationsWithRange fromGrandToParent = new MutationsWithRange(
                grand_,
                new Mutations<>(NucleotideSequence.ALPHABET, "I1T"),
                new RangeInfo(new Range(0, grand_.size()), false)
        );
        assertEquals(parent, fromGrandToParent.buildSequence());
        MutationsWithRange fromGrandToChild = new MutationsWithRange(
                grand_,
                new Mutations<>(NucleotideSequence.ALPHABET, "I3A"),
                new RangeInfo(new Range(0, grand_.size()), false)
        );
        assertEquals(child_, fromGrandToChild.buildSequence());
        MutationsWithRange result = mutationsBetween(fromGrandToParent, fromGrandToChild);
        assertEquals(child_, result.buildSequence());
    }

    @Test
    public void substitutionsWithSubRangeFromOne() {
        NucleotideSequence grand_ = new NucleotideSequence("CCGCCG");
        NucleotideSequence parent = new NucleotideSequence("CACCG");
        NucleotideSequence child_ = new NucleotideSequence("CGCCA");
        MutationsWithRange fromGrandToParent = new MutationsWithRange(
                grand_,
                new Mutations<>(NucleotideSequence.ALPHABET, "SG2A"),
                new RangeInfo(new Range(1, grand_.size()), false)
        );
        assertEquals(parent, fromGrandToParent.buildSequence());
        MutationsWithRange fromGrandToChild = new MutationsWithRange(
                grand_,
                new Mutations<>(NucleotideSequence.ALPHABET, "SG5A"),
                new RangeInfo(new Range(1, grand_.size()), false)
        );
        assertEquals(child_, fromGrandToChild.buildSequence());
        MutationsWithRange result = mutationsBetween(fromGrandToParent, fromGrandToChild);
        assertEquals(child_, result.buildSequence());
    }

    @Test
    public void deletionInTheStartOfSubRangeFromOneAndDontIncludeFirstInsertions() {
        NucleotideSequence grand_ = new NucleotideSequence("GTTGT");
        NucleotideSequence parent = new NucleotideSequence("ATG");
        NucleotideSequence child_ = new NucleotideSequence("TG");
        MutationsWithRange fromGrandToParent = new MutationsWithRange(
                grand_,
                new Mutations<>(NucleotideSequence.ALPHABET, "ST1A"),
                new RangeInfo(new Range(1, 4), false)
        );
        assertEquals(parent, fromGrandToParent.buildSequence());
        MutationsWithRange fromGrandToChild = new MutationsWithRange(
                grand_,
                new Mutations<>(NucleotideSequence.ALPHABET, "DT1"),
                new RangeInfo(new Range(1, 4), false)
        );
        assertEquals(child_, fromGrandToChild.buildSequence());
        MutationsWithRange result = mutationsBetween(fromGrandToParent, fromGrandToChild);
        assertEquals(child_, result.buildSequence());
    }

    @Test
    public void reproduceRandom() {
        assertFalse(testRandom(5657551124671488951L, true));
    }

    @Ignore
    @Test
    public void randomizedTest() {
        int numberOfRuns = 1_000_000;
        List<Long> failedSeeds = IntStream.range(0, numberOfRuns)
                .mapToObj(it -> ThreadLocalRandom.current().nextLong())
                .parallel()
                .filter(seed -> testRandom(seed, false))
                .collect(Collectors.toList());
        System.out.println("failed: " + failedSeeds.size());
        assertEquals(Collections.emptyList(), failedSeeds);
    }

    private boolean testRandom(long seed, boolean print) {
        try {
            Random random = new Random(seed);
            NucleotideSequence grand = generate(random);
            Range subRange = new Range(random.nextInt(2), grand.size() - random.nextInt(2));
            MutationsWithRange fromGrandToParent = new MutationsWithRange(
                    grand,
                    CalculationOfCommonMutationsTest.generateMutations(grand, random),
                    new RangeInfo(subRange, random.nextBoolean())
            );
            MutationsWithRange fromGrandToChild = new MutationsWithRange(
                    grand,
                    CalculationOfCommonMutationsTest.generateMutations(grand, random),
                    new RangeInfo(subRange, random.nextBoolean())
            );
            NucleotideSequence child = fromGrandToChild.buildSequence();
            if (print) {
                System.out.println("grand: " + grand);
                System.out.println("parent: " + fromGrandToParent.buildSequence());
                System.out.println("child: " + child);
                System.out.println();
                System.out.println("from grand to parent: " + fromGrandToParent.getMutations());
                System.out.println("from grand to parent range info: " + fromGrandToParent.getRangeInfo());
                System.out.println();
                System.out.println("from grand to child: " + fromGrandToChild.getMutations());
                System.out.println("from grand to child range info: " + fromGrandToChild.getRangeInfo());
                System.out.println();
            }
            MutationsWithRange result = mutationsBetween(fromGrandToParent, fromGrandToChild);
            if (print) {
                System.out.println("result: " + result.getMutations());
                System.out.println("result range info: " + result.getRangeInfo());
                System.out.println();
            }
            assertEquals(child, result.buildSequence());
            return false;
        } catch (Throwable e) {
            if (print) {
                e.printStackTrace();
            }
            return true;
        }
    }

    private NucleotideSequence generate(Random random) {
        List<Byte> chars = IntStream.range(0, 5 + random.nextInt(5))
                .mapToObj(it -> (byte) random.nextInt(4))
                .collect(Collectors.toList());

        return new NucleotideSequence(Bytes.toArray(chars));
    }
}
