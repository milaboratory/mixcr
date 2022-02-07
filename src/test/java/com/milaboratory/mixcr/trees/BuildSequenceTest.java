package com.milaboratory.mixcr.trees;

import com.google.common.primitives.Bytes;
import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.Aligner;
import com.milaboratory.core.alignment.LinearGapAlignmentScoring;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.util.RangeInfo;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.milaboratory.mixcr.trees.MutationsUtils.buildSequence;
import static com.milaboratory.mixcr.trees.MutationsUtils.projectRange;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class BuildSequenceTest {
    @Ignore
    @Test
    public void randomizedTestOfBuildingSequence() {
        int numberOfRuns = 1_000_000;
        List<Long> failedSeeds = IntStream.range(0, numberOfRuns)
                .mapToObj(it -> ThreadLocalRandom.current().nextLong())
                .parallel()
                .filter(seed -> testBuildSequence(seed, false))
                .collect(Collectors.toList());

        assertEquals(Collections.emptyList(), failedSeeds);
    }

    @Test
    public void reproduceRandomTestOfBuildingSequence() {
        assertFalse(testBuildSequence(3301598077971287922L, true));
    }

    @Ignore
    @Test
    public void randomizedTestOfProjectRange() {
        int numberOfRuns = 1_000_000;
        List<Long> failedSeeds = IntStream.range(0, numberOfRuns)
                .mapToObj(it -> ThreadLocalRandom.current().nextLong())
                .parallel()
                .filter(seed -> testProjectRange(seed, false))
                .collect(Collectors.toList());
        System.out.println("failed: " + failedSeeds.size());
        assertEquals(Collections.emptyList(), failedSeeds);
    }

    @Test
    public void reproduceRandomTestOfProjectRange() {
        assertFalse(testProjectRange(4865476048882002489L, true));
    }

    @Test
    public void mutationsForEmptyRangeWithInsertion() {
        NucleotideSequence parent = new NucleotideSequence("CCCTTT");
        Mutations<NucleotideSequence> mutations = new Mutations<>(NucleotideSequence.ALPHABET, "I3A");
        NucleotideSequence child = mutations.mutate(parent);
        assertEquals("CCCATTT", child.toString());
        assertEquals("[I3:A]", new RangeInfo(new Range(3, 3), true).extractAbsoluteMutations(mutations).toString());
        assertEquals("[]", new RangeInfo(new Range(3, 3), false).extractAbsoluteMutations(mutations).toString());
    }

    @Test
    public void insertionOnBoundary() {
        NucleotideSequence parent = new NucleotideSequence("CCCTTT");
        Mutations<NucleotideSequence> mutations = new Mutations<>(NucleotideSequence.ALPHABET, "I3A");
        NucleotideSequence child = mutations.mutate(parent);
        assertEquals("CCCATTT", child.toString());
        assertEquals("CCCA", buildSequence(parent, mutations, new RangeInfo(new Range(0, 3), false)).toString());
        assertEquals("TTT", buildSequence(parent, mutations, new RangeInfo(new Range(3, 6), false)).toString());
        assertEquals("ATTT", buildSequence(parent, mutations, new RangeInfo(new Range(3, 6), true)).toString());
    }

    @Test
    public void insertionInTheEnd() {
        NucleotideSequence parent = new NucleotideSequence("CCCTTT");
        Mutations<NucleotideSequence> mutations = new Mutations<>(NucleotideSequence.ALPHABET, "I6A");
        NucleotideSequence child = mutations.mutate(parent);
        assertEquals("CCCTTTA", child.toString());
        assertEquals("CCC", buildSequence(parent, mutations, new RangeInfo(new Range(0, 3), false)).toString());
        assertEquals("TTTA", buildSequence(parent, mutations, new RangeInfo(new Range(3, 6), false)).toString());
    }

    @Test
    public void insertionInTheBeginning() {
        NucleotideSequence parent = new NucleotideSequence("CCCTTT");
        Mutations<NucleotideSequence> mutations = new Mutations<>(NucleotideSequence.ALPHABET, "I0A");
        NucleotideSequence child = mutations.mutate(parent);
        assertEquals("ACCCTTT", child.toString());
        assertEquals("CCC", buildSequence(parent, mutations, new RangeInfo(new Range(0, 3), false)).toString());
        assertEquals("ACCC", buildSequence(parent, mutations, new RangeInfo(new Range(0, 3), true)).toString());
        assertEquals("TTT", buildSequence(parent, mutations, new RangeInfo(new Range(3, 6), false)).toString());
    }

    @Test
    public void deletionBeforeBeginOfRangeWithIncludedFirstInserts() {
        NucleotideSequence parent = new NucleotideSequence("AGTT");
        Mutations<NucleotideSequence> mutations = new Mutations<>(NucleotideSequence.ALPHABET, "DG1");
        NucleotideSequence child = mutations.mutate(parent);
        assertEquals("ATT", child.toString());
        assertEquals("T", buildSequence(parent, mutations, new RangeInfo(new Range(2, 3), true)).toString());
    }

    @Test
    public void deletionOfFirstLetterWithIncludedFirstInsertsSubsetStartsInTheBeginning() {
        NucleotideSequence parent = new NucleotideSequence("ATTT");
        Mutations<NucleotideSequence> mutations = new Mutations<>(NucleotideSequence.ALPHABET, "DA0");
        NucleotideSequence child = mutations.mutate(parent);
        assertEquals("TTT", child.toString());
        assertEquals("TTT", buildSequence(parent, mutations, new RangeInfo(new Range(0, 4), true)).toString());
    }

    @Test
    public void deletionOfSeveralFirstLettersWithIncludedFirstInsertsSubsetStartsInTheBeginning() {
        NucleotideSequence parent = new NucleotideSequence("AATTT");
        Mutations<NucleotideSequence> mutations = new Mutations<>(NucleotideSequence.ALPHABET, "DA0,DA1");
        NucleotideSequence child = mutations.mutate(parent);
        assertEquals("TTT", child.toString());
        assertEquals("TTT", buildSequence(parent, mutations, new RangeInfo(new Range(0, 5), true)).toString());
    }

    @Test
    public void deletionOfFirstLetterWithIncludedFirstInsertsSubsetStartsInAMiddle() {
        NucleotideSequence parent = new NucleotideSequence("GGATTT");
        Mutations<NucleotideSequence> mutations = new Mutations<>(NucleotideSequence.ALPHABET, "DA2");
        NucleotideSequence child = mutations.mutate(parent);
        assertEquals("GGTTT", child.toString());
        assertEquals("TTT", buildSequence(parent, mutations, new RangeInfo(new Range(2, 6), true)).toString());
    }

    @Test
    public void insertionOfSeveralLettersOnBoundary() {
        NucleotideSequence parent = new NucleotideSequence("CCCTTT");
        Mutations<NucleotideSequence> mutations = new Mutations<>(NucleotideSequence.ALPHABET, "I3A,I3A,I3A");
        NucleotideSequence child = mutations.mutate(parent);
        assertEquals("CCCAAATTT", child.toString());
        assertEquals("CCCAAA", buildSequence(parent, mutations, new RangeInfo(new Range(0, 3), false)).toString());
        assertEquals("TTT", buildSequence(parent, mutations, new RangeInfo(new Range(3, 6), false)).toString());
    }

    @Test
    public void deletionToTheLeftFromBoundary() {
        NucleotideSequence parent = new NucleotideSequence("CCATTT");
        Mutations<NucleotideSequence> mutations = new Mutations<>(NucleotideSequence.ALPHABET, "DA2");
        NucleotideSequence child = mutations.mutate(parent);
        assertEquals("CCTTT", child.toString());
        assertEquals("CC", buildSequence(parent, mutations, new RangeInfo(new Range(0, 3), false)).toString());
        assertEquals("TTT", buildSequence(parent, mutations, new RangeInfo(new Range(3, 6), false)).toString());
    }

    @Test
    public void deletionToTheRightFromBoundary() {
        NucleotideSequence parent = new NucleotideSequence("CCCATT");
        Mutations<NucleotideSequence> mutations = new Mutations<>(NucleotideSequence.ALPHABET, "DA3");
        NucleotideSequence child = mutations.mutate(parent);
        assertEquals("CCCTT", child.toString());
        assertEquals("CCC", buildSequence(parent, mutations, new RangeInfo(new Range(0, 3), false)).toString());
        assertEquals("TT", buildSequence(parent, mutations, new RangeInfo(new Range(3, 6), false)).toString());
    }

    @Test
    public void deletionToTheRightAndToTheLeftFromBoundary() {
        NucleotideSequence parent = new NucleotideSequence("CCAATT");
        Mutations<NucleotideSequence> mutations = new Mutations<>(NucleotideSequence.ALPHABET, "DA2,DA3");
        NucleotideSequence child = mutations.mutate(parent);
        assertEquals("CCTT", child.toString());
        assertEquals("CC", buildSequence(parent, mutations, new RangeInfo(new Range(0, 3), false)).toString());
        assertEquals("TT", buildSequence(parent, mutations, new RangeInfo(new Range(3, 6), false)).toString());
    }

    @Test
    public void deletionToTheLeftFromBoundaryAndInsertionOnBoundary() {
        NucleotideSequence parent = new NucleotideSequence("CCATTT");
        Mutations<NucleotideSequence> mutations = new Mutations<>(NucleotideSequence.ALPHABET, "DA2,I3G");
        NucleotideSequence child = mutations.mutate(parent);
        assertEquals("CCGTTT", child.toString());
        assertEquals("CCG", buildSequence(parent, mutations, new RangeInfo(new Range(0, 3), false)).toString());
        assertEquals("TTT", buildSequence(parent, mutations, new RangeInfo(new Range(3, 6), false)).toString());
        assertEquals("GTTT", buildSequence(parent, mutations, new RangeInfo(new Range(3, 6), true)).toString());
    }

    private boolean testBuildSequence(long seed, boolean print) {
        try {
            Random random = new Random(seed);
            NucleotideSequence part1 = generate(random, 5 + random.nextInt(5));
            NucleotideSequence part2 = generate(random, 5 + random.nextInt(5));
            NucleotideSequence part3 = generate(random, 5 + random.nextInt(5));
            NucleotideSequence mutatedPart1 = MutationsGenerator.generateMutations(part1, random).mutate(part1);
            NucleotideSequence mutatedPart2 = MutationsGenerator.generateMutations(part2, random).mutate(part2);
            NucleotideSequence mutatedPart3 = MutationsGenerator.generateMutations(part3, random).mutate(part3);
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
                    LinearGapAlignmentScoring.getNucleotideBLASTScoring(),
                    parent,
                    child
            ).getAbsoluteMutations();

            if (print) {
                System.out.println("parent(" + parent.size() + "):");
                System.out.println(parent);
                System.out.println("child(" + child.size() + "):");
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
            final Range sequence1Range1 = new Range(0, part1.size());
            NucleotideSequence resultPart1 = buildSequence(parent, mutations, new RangeInfo(sequence1Range1, true));
            final Range sequence1Range2 = new Range(part1.size(), part1.size() + part2.size());
            NucleotideSequence resultPart2 = buildSequence(parent, mutations, new RangeInfo(sequence1Range2, false));
            final Range sequence1Range3 = new Range(part1.size() + part2.size(), parent.size());
            NucleotideSequence resultPart3 = buildSequence(parent, mutations, new RangeInfo(sequence1Range3, false));
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

    private boolean testProjectRange(long seed, boolean print) {
        try {
            Random random = new Random(seed);
            NucleotideSequence parent = generate(random, 15);
            RangeInfo range1 = new RangeInfo(new Range(0, 5), true);
            RangeInfo range2 = new RangeInfo(new Range(5, 10), false);
            RangeInfo range3 = new RangeInfo(new Range(10, 15), false);

            Mutations<NucleotideSequence> mutations = MutationsGenerator.generateMutations(parent, random);
            NucleotideSequence child = mutations.mutate(parent);
            NucleotideSequence mutatedPart1 = child.getRange(projectRange(mutations, range1));
            NucleotideSequence mutatedPart2 = child.getRange(projectRange(mutations, range2));
            NucleotideSequence mutatedPart3 = child.getRange(projectRange(mutations, range3));
            NucleotideSequence result = mutatedPart1.concatenate(mutatedPart2).concatenate(mutatedPart3);
            if (print) {
                System.out.println("parent: " + parent);
                System.out.println(" child: " + child);
                System.out.println("result: " + result);
                System.out.println();
                System.out.println("mutations: " + mutations);
                System.out.println();
                System.out.println("range1: " + range1);
                System.out.println("range2: " + range2);
                System.out.println("range3: " + range3);
                System.out.println();
                System.out.println("       part1: " + parent.getRange(range1.getRange()));
                System.out.println("mutatedPart1: " + mutatedPart1);
                System.out.println("       part2: " + parent.getRange(range2.getRange()));
                System.out.println("mutatedPart2: " + mutatedPart2);
                System.out.println("       part3: " + parent.getRange(range3.getRange()));
                System.out.println("mutatedPart3: " + mutatedPart3);
                System.out.println();
            }
            return !child.equals(result);
        } catch (Exception e) {
            if (print) {
                e.printStackTrace();
            }
            return true;
        }
    }

    private NucleotideSequence generate(Random random, int size) {
        List<Byte> chars = IntStream.range(0, size)
                .mapToObj(it -> (byte) random.nextInt(4))
                .collect(Collectors.toList());

        return new NucleotideSequence(Bytes.toArray(chars));
    }
}
