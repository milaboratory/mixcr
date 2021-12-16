package com.milaboratory.mixcr.trees;

import com.google.common.primitives.Bytes;
import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.AffineGapAlignmentScoring;
import com.milaboratory.core.alignment.Aligner;
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

import static com.milaboratory.mixcr.trees.MutationOperationsTest.mutate;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class BuildSequenceTest {
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
        assertFalse(testRandomMutations(1663201082536575037L, true));
    }

    private boolean testRandomMutations(long seed, boolean print) {
        try {
            Random random = new Random(seed);
            NucleotideSequence part1 = generate(random);
            NucleotideSequence part2 = generate(random);
            NucleotideSequence part3 = generate(random);
            NucleotideSequence mutatedPart1 = mutate(part1, random);
            NucleotideSequence mutatedPart2 = mutate(part2, random);
            NucleotideSequence mutatedPart3 = mutate(part3, random);
            NucleotideSequence parent = NucleotideSequence.ALPHABET.createBuilder()
                    .append(part1)
                    .append(part2)
                    .append(part3)
                    .createAndDestroy();

            NucleotideSequence child = NucleotideSequence.ALPHABET.createBuilder()
                    .append(mutatedPart1)
                    .append(mutatedPart2)
                    .append(mutatedPart3)
                    .createAndDestroy();

            Mutations<NucleotideSequence> mutations = Aligner.alignGlobal(
                    AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                    parent,
                    child
            ).getAbsoluteMutations();

            if (print) {
                System.out.println("parent:");
                System.out.println(parent);
                System.out.println("child:");
                System.out.println(child);
                System.out.println("mutations:");
                System.out.println(mutations);
                System.out.println("part1:");
                System.out.println(part1 + " => " + mutatedPart1);
                System.out.println("part2:");
                System.out.println(part2 + " => " + mutatedPart2);
                System.out.println("part3:");
                System.out.println(part3 + " => " + mutatedPart3);
            }
            final Range sequence1Range = new Range(0, part1.size());
            NucleotideSequence resultPart1 = MutationsUtils.buildSequence(parent, mutations, sequence1Range, false);
            final Range sequence1Range1 = new Range(part1.size(), part1.size() + part2.size());
            NucleotideSequence resultPart2 = MutationsUtils.buildSequence(parent, mutations, sequence1Range1, false);
            final Range sequence1Range2 = new Range(part1.size() + part2.size(), parent.size());
            NucleotideSequence resultPart3 = MutationsUtils.buildSequence(parent, mutations, sequence1Range2, true);
            NucleotideSequence result = NucleotideSequence.ALPHABET.createBuilder()
                    .append(resultPart1)
                    .append(resultPart2)
                    .append(resultPart3)
                    .createAndDestroy();
            if (print) {
                System.out.println("resultPart1:");
                System.out.println(resultPart1 + " (" + mutatedPart1 + ")");
                System.out.println("resultPart2:");
                System.out.println(resultPart2 + " (" + mutatedPart2 + ")");
                System.out.println("resultPart3:");
                System.out.println(resultPart3 + " (" + mutatedPart3 + ")");
                System.out.println("result:");
                System.out.println(result);
                System.out.println("expected:");
                System.out.println(child);
            }
            return !result.equals(child);
        } catch (Exception e) {
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
