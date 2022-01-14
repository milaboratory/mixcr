package com.milaboratory.mixcr.trees;

import com.google.common.collect.Lists;
import com.google.common.primitives.Bytes;
import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.AffineGapAlignmentScoring;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;
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
import static com.milaboratory.mixcr.trees.MutationsUtils.projectRange;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class RebaseClonesTest {
    @Ignore
    @Test
    public void randomizedTestForRebaseClone() {
        int numberOfRuns = 100_000;
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
        assertFalse(testRebaseClone(2049978999466120864L, true));
        assertFalse(testRebaseClone(-7534105378312308262L, true));
        assertFalse(testRebaseClone(4510972677298188920L, true));
        assertFalse(testRebaseClone(1729663315728681110L, true));
        assertFalse(testRebaseClone(4608235439778868248L, true));
        assertFalse(testRebaseClone(7155779204574879033L, true));
        assertFalse(testRebaseClone(-4567604316340909864L, true));
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
            Range NDNSubsetRangeBeforeMutation = new Range(
                    projectRange(VMutations, VRangeInCDR3, false, true).length(),
                    CDR3.size() - projectRange(JMutations, JRangeInCDR3, true, false).length()
            );
            NucleotideSequence NDNSubsetBeforeMutation = CDR3.getRange(NDNSubsetRangeBeforeMutation);
            Mutations<NucleotideSequence> mutationsOfNDN = generateMutations(NDNSubsetBeforeMutation, random);
            RootInfo rootInfo = new RootInfo(
                    VRangeInCDR3,
                    mutationsOfNDN.mutate(NDNSubsetBeforeMutation),
                    JRangeInCDR3
            );

            NucleotideSequence builtClone = NucleotideSequence.ALPHABET.createBuilder()
                    .append(buildSequence(VSequence, VMutations, VRangeBeforeCDR3Begin, true, true))
                    .append(VSequenceInCDR3)
                    .append(NDN)
                    .append(JSequenceInCDR3)
                    .append(buildSequence(JSequence, JMutations, JRangeAfterCDR3End, true, true))
                    .createAndDestroy();

            MutationsFromVJGermline mutationsFromVJGermline = new MutationsFromVJGermline(
                    Lists.newArrayList(
                            new MutationsWithRange(
                                    VSequence,
                                    VMutations,
                                    VRangeBeforeCDR3Begin,
                                    true, true
                            )
                    ),
                    new MutationsWithRange(
                            VSequence,
                            VMutations,
                            commonVRangeInCDR3,
                            false, true
                    ),
                    Pair.create(VMutations, new Range(commonVRangeInCDR3.getUpper(), VRangeAfterCDR3Begin.getUpper())),
                    NucleotideSequence.ALPHABET.createBuilder()
                            .append(buildSequence(VSequence, VMutations, new Range(commonVRangeInCDR3.getUpper(), VRangeAfterCDR3Begin.getUpper()), false, true))
                            .append(NDN)
                            .append(buildSequence(JSequence, JMutations, new Range(JRangeBeforeCDR3End.getLower(), commonJRangeInCDR3.getLower()), true, false))
                            .createAndDestroy(),
                    Pair.create(JMutations, new Range(JRangeBeforeCDR3End.getLower(), commonJRangeInCDR3.getLower())),
                    new MutationsWithRange(
                            JSequence,
                            JMutations,
                            commonJRangeInCDR3,
                            true, false
                    ),
                    Lists.newArrayList(
                            new MutationsWithRange(
                                    JSequence,
                                    JMutations,
                                    JRangeAfterCDR3End,
                                    true, true
                            )
                    )
            );

            CloneWithMutationsFromReconstructedRoot rebasedClone = new ClonesRebase(VSequence, JSequence, AffineGapAlignmentScoring.getNucleotideBLASTScoring())
                    .rebaseClone(rootInfo, mutationsFromVJGermline, new CloneWrapper(null, 0));

            AncestorInfoBuilder ancestorInfoBuilder = new AncestorInfoBuilder();
            MutationsDescription result = rebasedClone.getMutationsFromRoot();
            AncestorInfo ancestorInfo = ancestorInfoBuilder.buildAncestorInfo(result);

            Range VPartLeftInRootRange = VRangeAfterCDR3Begin.intersection(rootInfo.getVRangeInCDR3());
            VPartLeftInRootRange = VPartLeftInRootRange == null ? new Range(VRangeAfterCDR3Begin.getLower(), VRangeAfterCDR3Begin.getLower()) : VPartLeftInRootRange;
            NucleotideSequence VPartLeftInRoot = buildSequence(VSequence, VMutations, VPartLeftInRootRange, false, true);

            Range JPartLeftInRootRange = JRangeBeforeCDR3End.intersection(rootInfo.getJRangeInCDR3());
            JPartLeftInRootRange = JPartLeftInRootRange == null ? new Range(JRangeBeforeCDR3End.getUpper(), JRangeBeforeCDR3End.getUpper()) : JPartLeftInRootRange;
            NucleotideSequence JPartLeftInRoot = buildSequence(JSequence, JMutations, JPartLeftInRootRange, true, false);

            Range VPartGotFromNDNRange = new Range(VPartLeftInRootRange.length(), rootInfo.getVRangeInCDR3().length()).move(VPartLeftInRoot.size() - VPartLeftInRootRange.length());
            Range JPartGotFromNDNRange = new Range(CDR3.size() - rootInfo.getJRangeInCDR3().length(), CDR3.size() - JPartLeftInRootRange.length()).move(JPartLeftInRootRange.length() - JPartLeftInRoot.size());

            NucleotideSequence VPartGotFromNDN = VPartGotFromNDNRange.isReverse() ? new NucleotideSequence("") : CDR3.getRange(VPartGotFromNDNRange);
            NucleotideSequence JPartGotFromNDN = JPartGotFromNDNRange.isReverse() ? new NucleotideSequence("") : CDR3.getRange(JPartGotFromNDNRange);

            if (print) {
                System.out.println("original: " + builtClone);
                System.out.println("  result: " + ancestorInfo.getSequence());
                System.out.println();
                System.out.println("   original with marking: "
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
                System.out.println("     result with marking: "
                        + result.getVMutationsWithoutCDR3().stream().map(MutationsWithRange::buildSequence).map(String::valueOf).collect(Collectors.joining())
                        + " "
                        + result.getVMutationsInCDR3WithoutNDN().buildSequence()
                        + " "
                        + result.getKnownNDN().buildSequence()
                        + " "
                        + result.getJMutationsInCDR3WithoutNDN().buildSequence()
                        + " "
                        + result.getJMutationsWithoutCDR3().stream().map(MutationsWithRange::buildSequence).map(String::valueOf).collect(Collectors.joining())
                );
                System.out.println("root mutated in germline: "
                        + buildSequence(VSequence, VMutations, VRangeBeforeCDR3Begin, true, true)
                        + " "
                        + VPartLeftInRoot
                        + " "
                        + VPartGotFromNDN
                        + " "
                        + rootInfo.getReconstructedNDN()
                        + " "
                        + JPartGotFromNDN
                        + " "
                        + JPartLeftInRoot
                        + " "
                        + buildSequence(JSequence, JMutations, JRangeAfterCDR3End, true, true)
                );
                System.out.println("          rebase on root: "
                        + buildSequence(VSequence, EMPTY_NUCLEOTIDE_MUTATIONS, VRangeBeforeCDR3Begin, true, true)
                        + " "
                        + buildSequence(VSequence, EMPTY_NUCLEOTIDE_MUTATIONS, rootInfo.getVRangeInCDR3(), true, true)
                        + " "
                        + rootInfo.getReconstructedNDN()
                        + " "
                        + buildSequence(JSequence, EMPTY_NUCLEOTIDE_MUTATIONS, rootInfo.getJRangeInCDR3(), true, true)
                        + " "
                        + buildSequence(JSequence, EMPTY_NUCLEOTIDE_MUTATIONS, JRangeAfterCDR3End, true, true)
                );
                System.out.println();
                System.out.println("original CDR3: " + CDR3);
                System.out.println("  result CDR3: " + ancestorInfo.getSequence().getRange(ancestorInfo.getCDR3Begin(), ancestorInfo.getCDR3End()));
                System.out.println();
                System.out.println("     original NDN: " + NDN);
                System.out.println("      mutated NDN: " + rootInfo.getReconstructedNDN());
                System.out.println("    subset of NDN: " + NDNSubsetBeforeMutation);
                System.out.println("mutated from root: " + result.getKnownNDN().buildSequence());
                System.out.println();
            }
            assertEquals(rootInfo.getVRangeInCDR3().getLower(), result.getVMutationsWithoutCDR3().stream()
                    .mapToInt(it -> it.getSequence1Range().getUpper())
                    .max().orElseThrow(IllegalStateException::new));
            assertEquals(rootInfo.getVRangeInCDR3(), result.getVMutationsInCDR3WithoutNDN().getSequence1Range());

            assertEquals(rootInfo.getJRangeInCDR3().getUpper(), result.getJMutationsWithoutCDR3().stream()
                    .mapToInt(it -> it.getSequence1Range().getLower())
                    .min().orElseThrow(IllegalStateException::new));
            assertEquals(rootInfo.getJRangeInCDR3(), result.getJMutationsInCDR3WithoutNDN().getSequence1Range());

            assertEquals(builtClone, ancestorInfo.getSequence());
            assertEquals(CDR3, ancestorInfo.getSequence().getRange(ancestorInfo.getCDR3Begin(), ancestorInfo.getCDR3End()));
            assertEquals(
                    VPartLeftInRoot.concatenate(VPartGotFromNDN),
                    result.getVMutationsInCDR3WithoutNDN().buildSequence()
            );
            assertEquals(NDNSubsetBeforeMutation, result.getKnownNDN().buildSequence());
            assertEquals(
                    JPartGotFromNDN.concatenate(JPartLeftInRoot),
                    result.getJMutationsInCDR3WithoutNDN().buildSequence()
            );
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
