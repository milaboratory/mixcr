package com.milaboratory.mixcr.trees;

import com.google.common.collect.Lists;
import com.google.common.primitives.Bytes;
import com.milaboratory.core.Range;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;
import io.repseq.core.ReferencePoint;
import org.apache.commons.math3.util.Pair;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.milaboratory.core.mutations.Mutations.EMPTY_NUCLEOTIDE_MUTATIONS;
import static com.milaboratory.mixcr.trees.MutationOperationsTest.generateMutations;
import static com.milaboratory.mixcr.trees.MutationsUtils.buildSequence;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class RebaseClonesTest {
    @Ignore
    @Test
    public void randomizedTestForRebaseClone() {
        int numberOfRuns = 1_000_000;
        List<Long> failedSeeds = IntStream.range(0, numberOfRuns)
                .mapToObj(it -> ThreadLocalRandom.current().nextLong())
                .parallel()
                .filter(seed -> testRebaseClone(seed, false))
                .collect(Collectors.toList());

        System.out.println("failed: " + failedSeeds.size());
        assertEquals(Collections.emptyList(), failedSeeds);
    }

    @Test
    public void reproduceRebaseClone() {
        assertFalse(testRebaseClone(7360045022198406917L, true));
        assertFalse(testRebaseClone(8861605449460417460L, true));
        assertFalse(testRebaseClone(3361027404503237374L, true));
        assertFalse(testRebaseClone(5633311090069099492L, true));
        assertFalse(testRebaseClone(-140150437646008446L, true));
        assertFalse(testRebaseClone(-3721882169827128329L, true));
        assertFalse(testRebaseClone(-6639724146754084784L, true));
        assertFalse(testRebaseClone(-154711501619107070L, true));
        assertFalse(testRebaseClone(3991336578395308109L, true));
        assertFalse(testRebaseClone(-5047483764046740699L, true));
        assertFalse(testRebaseClone(49156566332349046L, true));
        assertFalse(testRebaseClone(-6877842382590389599L, true));
        assertFalse(testRebaseClone(5739929328149910349L, true));
        assertFalse(testRebaseClone(7581006658967416418L, true));
        assertFalse(testRebaseClone(5492150036748141135L, true));
        assertFalse(testRebaseClone(8053975088522559753L, true));
        assertFalse(testRebaseClone(8812578697731451467L, true));
    }

    private boolean testRebaseClone(long seed, boolean print) {
        try {
            Random random = new Random(seed);

            NucleotideSequence VSequence = generate(random, 50 + random.nextInt(50));
            Range VRangeBeforeCDR3Begin = new Range(0, 10 + random.nextInt(10)).move(10 + random.nextInt(5));
            Range VRangeAfterCDR3Begin = new Range(0, random.nextInt(5)).move(VRangeBeforeCDR3Begin.getUpper());

            NucleotideSequence NDN = generate(random, 10 + random.nextInt(15));

            NucleotideSequence JSequence = generate(random, 50 + random.nextInt(50));
            Range JRangeBeforeCDR3End = new Range(0, random.nextInt(5)).move(10 + random.nextInt(5));
            Range JRangeAfterCDR3End = new Range(0, 10 + random.nextInt(10)).move(JRangeBeforeCDR3End.getUpper());

            Range VRange = new Range(VRangeBeforeCDR3Begin.getLower(), VRangeAfterCDR3Begin.getUpper());
            Mutations<NucleotideSequence> VMutations = generateMutations(VSequence, random, VRange);

            Range JRange = new Range(JRangeBeforeCDR3End.getLower(), JRangeAfterCDR3End.getUpper());
            Mutations<NucleotideSequence> JMutations = generateMutations(JSequence, random, JRange);

            NucleotideSequence VSequenceInCDR3 = buildSequence(VSequence, VMutations, VRangeAfterCDR3Begin, false, true);
            NucleotideSequence JSequenceInCDR3 = buildSequence(JSequence, JMutations, JRangeBeforeCDR3End, true, false);

            Range commonVRangeInCDR3 = new Range(0, VRangeAfterCDR3Begin.length() == 0 ? 0 : random.nextInt(VRangeAfterCDR3Begin.length())).move(VRangeAfterCDR3Begin.getLower());
            Range commonJRangeInCDR3 = new Range(JRangeBeforeCDR3End.length() == 0 ? 0 : -random.nextInt(JRangeBeforeCDR3End.length()), 0).move(JRangeBeforeCDR3End.getUpper());

            NucleotideSequence CDR3 = NucleotideSequence.ALPHABET.createBuilder()
                    .append(VSequenceInCDR3)
                    .append(NDN)
                    .append(JSequenceInCDR3)
                    .createAndDestroy();

            Range VRangeInCDR3 = new Range(0, commonVRangeInCDR3.length() + random.nextInt(5))
                    .move(VRangeBeforeCDR3Begin.getUpper());
            Range JRangeInCDR3 = new Range(-(commonJRangeInCDR3.length() + random.nextInt(5)), 0)
                    .move(JRangeBeforeCDR3End.getUpper());
            NucleotideSequence NDNSubsetBeforeMutation = CDR3.getRange(
                    VRangeInCDR3.length(),
                    CDR3.size() - JRangeInCDR3.length()
            );
            Mutations<NucleotideSequence> mutationsOfNDN = generateMutations(NDNSubsetBeforeMutation, random);
            RootInfo rootInfo = new RootInfo(
                    VRangeInCDR3,
                    mutationsOfNDN.mutate(NDNSubsetBeforeMutation),
                    JRangeInCDR3
            );

            NucleotideSequence builtClone = NucleotideSequence.ALPHABET.createBuilder()
                    .append(buildSequence(VSequence, VMutations, new Range(VRangeBeforeCDR3Begin.getLower(), VRangeAfterCDR3Begin.getUpper()), true, true))
                    .append(NDN)
                    .append(buildSequence(JSequence, JMutations, new Range(JRangeBeforeCDR3End.getLower(), JRangeAfterCDR3End.getUpper()), true, true))
                    .createAndDestroy();

            MutationsFromVJGermline mutationsFromVJGermline = new MutationsFromVJGermline(
                    Lists.newArrayList(
                            new MutationsWithRange(
                                    VSequence,
                                    EMPTY_NUCLEOTIDE_MUTATIONS,
                                    VMutations,
                                    new Range(VRangeBeforeCDR3Begin.getLower(), commonVRangeInCDR3.getUpper()),
                                    true, true
                            )
                    ),
                    Pair.create(VMutations, new Range(commonVRangeInCDR3.getUpper(), VRangeAfterCDR3Begin.getUpper())),
                    commonVRangeInCDR3,
                    commonJRangeInCDR3,
                    NucleotideSequence.ALPHABET.createBuilder()
                            .append(buildSequence(VSequence, VMutations, new Range(commonVRangeInCDR3.getUpper(), VRangeAfterCDR3Begin.getUpper()), false, true))
                            .append(NDN)
                            .append(buildSequence(JSequence, JMutations, new Range(JRangeBeforeCDR3End.getLower(), commonJRangeInCDR3.getLower()), true, false))
                            .createAndDestroy(),
                    Lists.newArrayList(
                            new MutationsWithRange(
                                    JSequence,
                                    EMPTY_NUCLEOTIDE_MUTATIONS,
                                    JMutations,
                                    new Range(commonJRangeInCDR3.getLower(), JRangeAfterCDR3End.getUpper()),
                                    true, true
                            )
                    ),
                    Pair.create(JMutations, new Range(JRangeBeforeCDR3End.getLower(), commonJRangeInCDR3.getLower()))
            );

            CloneWithMutationsFromReconstructedRoot rebasedClone = new ClonesRebase(VSequence, JSequence)
                    .rebaseClone(rootInfo, mutationsFromVJGermline, new CloneWrapper(null, 0));

            AncestorInfoBuilder ancestorInfoBuilder = new AncestorInfoBuilder(
                    it -> {
                        if (it == ReferencePoint.CDR3Begin) {
                            return VRangeBeforeCDR3Begin.getUpper();
                        } else {
                            return -1;
                        }
                    },
                    it -> {
                        if (it == ReferencePoint.CDR3End) {
                            return JRangeAfterCDR3End.getLower();
                        } else {
                            return -1;
                        }
                    }
            );
            MutationsDescription result = rebasedClone.getMutationsFromRoot();
            AncestorInfo ancestorInfo = ancestorInfoBuilder.buildAncestorInfo(result);
            if (print) {
                System.out.println("original: " + builtClone);
                System.out.println("  result: " + ancestorInfo.getSequence());
                System.out.println();
                System.out.println("original with marking: "
                        + buildSequence(VSequence, VMutations, VRangeBeforeCDR3Begin, true, true)
                        + " "
                        + VSequenceInCDR3
                        + " "
                        + NDN
                        + " "
                        + JSequenceInCDR3
                        + " "
                        + buildSequence(JSequence, JMutations, JRangeAfterCDR3End, true, true)
                );
                System.out.println("  result with marking: "
                        + ancestorInfo.getSequence().getRange(0, ancestorInfo.getCDR3Begin())
                        + " "
                        + ancestorInfo.getSequence().getRange(ancestorInfo.getCDR3Begin(), ancestorInfo.getCDR3Begin() + rootInfo.getVRangeInCDR3().length())
                        + " "
                        + ancestorInfo.getSequence().getRange(ancestorInfo.getCDR3Begin() + rootInfo.getVRangeInCDR3().length(), ancestorInfo.getCDR3End() - rootInfo.getJRangeInCDR3().length())
                        + " "
                        + ancestorInfo.getSequence().getRange(ancestorInfo.getCDR3End() - rootInfo.getJRangeInCDR3().length(), ancestorInfo.getCDR3End())
                        + " "
                        + ancestorInfo.getSequence().getRange(ancestorInfo.getCDR3End(), ancestorInfo.getSequence().size())
                );
                System.out.println();
                System.out.println("original CDR3: " + CDR3);
                System.out.println("  result CDR3: " + ancestorInfo.getSequence().getRange(ancestorInfo.getCDR3Begin(), ancestorInfo.getCDR3End()));
                System.out.println();
                System.out.println("     original NDN: " + NDN);
                System.out.println("      mutated NDN: " + rootInfo.getReconstructedNDN());
                System.out.println("    subset of NDN: " + NDNSubsetBeforeMutation);
                System.out.println("mutated from root: " + result.getKnownNDN().getCombinedMutations().mutate(rootInfo.getReconstructedNDN()));
                System.out.println();
            }
            assertEquals(rootInfo.getVRangeInCDR3().getUpper(), result.getVMutationsWithoutNDN().stream()
                    .mapToInt(it -> it.getSequence1Range().getUpper())
                    .max().orElseThrow(IllegalStateException::new));
            assertEquals(rootInfo.getJRangeInCDR3().getLower(), result.getJMutationsWithoutNDN().stream()
                    .mapToInt(it -> it.getSequence1Range().getLower())
                    .min().orElseThrow(IllegalStateException::new));
            assertEquals(builtClone, ancestorInfo.getSequence());
            assertEquals(CDR3, ancestorInfo.getSequence().getRange(ancestorInfo.getCDR3Begin(), ancestorInfo.getCDR3End()));
//            assertEquals(NDNSubsetBeforeMutation, result.getKnownNDN().getCombinedMutations().mutate(rootInfo.getReconstructedNDN()));
            return false;
        } catch (Throwable e) {
            if (print) {
                e.printStackTrace();
            }
            return true;
        }
    }

    private NucleotideSequence generate(Random random, int length) {
        List<Byte> chars = IntStream.range(0, length)
                .mapToObj(it -> (byte) random.nextInt(4))
                .collect(Collectors.toList());

        return new NucleotideSequence(Bytes.toArray(chars));
    }
}
