package com.milaboratory.mixcr.trees;

import com.google.common.collect.Lists;
import com.google.common.primitives.Bytes;
import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.AffineGapAlignmentScoring;
import com.milaboratory.core.alignment.Aligner;
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
import static com.milaboratory.mixcr.trees.CalculationOfCommonMutationsTest.generateMutations;
import static com.milaboratory.mixcr.trees.MutationsUtils.buildSequence;
import static com.milaboratory.mixcr.trees.MutationsUtils.projectRange;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class RebaseClonesTest {
    @Ignore
    @Test
    public void randomizedTestForRebaseMutations() {
        int numberOfRuns = 1_000_000;
        List<Long> failedSeeds = IntStream.range(0, numberOfRuns)
                .mapToObj(it -> ThreadLocalRandom.current().nextLong())
                .parallel()
                .filter(seed -> testRebaseMutations(seed, false))
                .collect(Collectors.toList());

        System.out.println("failed: " + failedSeeds.size());
        assertEquals(Collections.emptyList(), failedSeeds);
    }

    @Test
    public void reproduceRebaseMutations() {
        assertFalse(testRebaseMutations(2717362330381213098L, true));
        assertFalse(testRebaseMutations(-7736026003531838642L, true));
        assertFalse(testRebaseMutations(-2276640640846890955L, true));
        assertFalse(testRebaseMutations(-4625731613403327929L, true));
    }

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
        assertFalse(testRebaseClone(-6528292659028221478L, true));
        assertFalse(testRebaseClone(-1959168467592812968L, true));
        assertFalse(testRebaseClone(4887507527711339190L, true));
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

    private boolean testRebaseMutations(long seed, boolean print) {
        try {
            Random random = new Random(seed);

            NucleotideSequence VSequence = generate(random, 50 + random.nextInt(50));
            Range VRangeBeforeCDR3Begin = new Range(0, 10 + random.nextInt(10)).move(10 + random.nextInt(5));
            Range VRangeAfterCDR3Begin = new Range(0, random.nextInt(5)).move(VRangeBeforeCDR3Begin.getUpper());

            NucleotideSequence JSequence = generate(random, 50 + random.nextInt(50));
            Range JRangeBeforeCDR3End = new Range(0, random.nextInt(5)).move(10 + random.nextInt(5));
            Range JRangeAfterCDR3End = new Range(0, 10 + random.nextInt(10)).move(JRangeBeforeCDR3End.getUpper());

            Range VRange = new Range(VRangeBeforeCDR3Begin.getLower(), VRangeAfterCDR3Begin.getUpper());
            Mutations<NucleotideSequence> VMutations = generateMutations(VSequence, random, VRange);

            Range JRange = new Range(JRangeBeforeCDR3End.getLower(), JRangeAfterCDR3End.getUpper());
            Mutations<NucleotideSequence> JMutations = generateMutations(JSequence, random, JRange);

            NucleotideSequence NDN = generate(random, 10 + random.nextInt(10));

            RootInfo originalRootInfo = new RootInfo(
                    VRangeAfterCDR3Begin,
                    generate(random, NDN.size() - 3 + random.nextInt(6)),
                    JRangeAfterCDR3End
            );

            MutationsDescription original = new MutationsDescription(
                    Lists.newArrayList(
                            new MutationsWithRange(
                                    VSequence,
                                    VMutations,
                                    new RangeInfo(VRangeBeforeCDR3Begin, true)
                            )
                    ),
                    new MutationsWithRange(
                            VSequence,
                            VMutations,
                            new RangeInfo(originalRootInfo.getVRangeInCDR3(), false)
                    ),
                    new MutationsWithRange(
                            originalRootInfo.getReconstructedNDN(),
                            Aligner.alignGlobal(
                                    AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                                    originalRootInfo.getReconstructedNDN(),
                                    NDN
                            ).getAbsoluteMutations(),
                            new RangeInfo(new Range(0, originalRootInfo.getReconstructedNDN().size()), true)
                    ),
                    new MutationsWithRange(
                            JSequence,
                            JMutations,
                            new RangeInfo(originalRootInfo.getJRangeInCDR3(), false)
                    ),
                    Lists.newArrayList(
                            new MutationsWithRange(
                                    JSequence,
                                    JMutations,
                                    new RangeInfo(JRangeAfterCDR3End, true)
                            )
                    )
            );

            ClonesRebase clonesRebase = new ClonesRebase(VSequence, AffineGapAlignmentScoring.getNucleotideBLASTScoring(), MutationsUtils.NDNScoring(), JSequence, AffineGapAlignmentScoring.getNucleotideBLASTScoring());
            int VBorderExpand = -2 + random.nextInt(4);
            if (originalRootInfo.getVRangeInCDR3().length() + VBorderExpand < 0) {
                VBorderExpand = originalRootInfo.getVRangeInCDR3().length();
            }
            int JBorderExpand = -2 + random.nextInt(4);
            if (originalRootInfo.getJRangeInCDR3().length() + JBorderExpand < 0) {
                JBorderExpand = originalRootInfo.getJRangeInCDR3().length();
            }
            RootInfo rebaseToRootInfo = new RootInfo(
                    originalRootInfo.getVRangeInCDR3().expand(0, VBorderExpand),
                    generate(random, originalRootInfo.getReconstructedNDN().size() - VBorderExpand - JBorderExpand),
                    originalRootInfo.getJRangeInCDR3().expand(JBorderExpand, 0)
            );

            MutationsDescription result = clonesRebase.rebaseMutations(original, originalRootInfo, rebaseToRootInfo);

            if (print) {
                System.out.println(" original rootInfo: " + originalRootInfo);
                System.out.println("rebase to rootInfo: " + rebaseToRootInfo);
                System.out.println("original CDR3: " + original.getVMutationsInCDR3WithoutNDN().buildSequence() + " " + original.getKnownNDN().buildSequence() + " " + original.getJMutationsInCDR3WithoutNDN().buildSequence());
                System.out.println("  result CDR3: " + result.getVMutationsInCDR3WithoutNDN().buildSequence() + " " + result.getKnownNDN().buildSequence() + " " + result.getJMutationsInCDR3WithoutNDN().buildSequence());
            }
            assertEquals(buildSequences(original.getVMutationsWithoutCDR3()), buildSequences(result.getVMutationsWithoutCDR3()));
            assertEquals(buildCDR3(original), buildCDR3(result));
            assertEquals(buildSequences(original.getJMutationsWithoutCDR3()), buildSequences(result.getJMutationsWithoutCDR3()));
            assertEquals(rebaseToRootInfo.getVRangeInCDR3(), result.getVMutationsInCDR3WithoutNDN().getRangeInfo().getRange());
//            assertEquals(rebaseToRootInfo.getReconstructedNDN(), result.getKnownNDN().getSequence1());
            assertEquals(rebaseToRootInfo.getJRangeInCDR3(), result.getJMutationsInCDR3WithoutNDN().getRangeInfo().getRange());

            return false;
        } catch (Throwable e) {
            if (print) {
                e.printStackTrace();
            }
            return true;
        }
    }

    private NucleotideSequence buildCDR3(MutationsDescription original) {
        return original.getVMutationsInCDR3WithoutNDN().buildSequence()
                .concatenate(original.getKnownNDN().buildSequence())
                .concatenate(original.getJMutationsInCDR3WithoutNDN().buildSequence());
    }

    private NucleotideSequence buildSequences(List<MutationsWithRange> mutations) {
        return mutations.stream()
                .map(MutationsWithRange::buildSequence)
                .reduce(NucleotideSequence.EMPTY, NucleotideSequence::concatenate);
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

            NucleotideSequence VSequenceInCDR3 = buildSequence(VSequence, VMutations, new RangeInfo(VRangeAfterCDR3Begin, false));
            NucleotideSequence JSequenceInCDR3 = buildSequence(JSequence, JMutations, new RangeInfo(JRangeBeforeCDR3End, true));

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
            Range projectedVRangeInCDR3 = projectRange(VMutations, new RangeInfo(VRangeInCDR3, false));
            Range projectedJRangeInCDR3 = projectRange(JMutations, new RangeInfo(JRangeInCDR3, true));
            Range NDNSubsetRangeBeforeMutation = new Range(
                    projectedVRangeInCDR3.length(),
                    CDR3.size() - projectedJRangeInCDR3.length()
            );
            NucleotideSequence NDNSubsetBeforeMutation = CDR3.getRange(NDNSubsetRangeBeforeMutation);
            Mutations<NucleotideSequence> mutationsOfNDN = generateMutations(NDNSubsetBeforeMutation, random);
            RootInfo rootInfo = new RootInfo(
                    VRangeInCDR3,
                    mutationsOfNDN.mutate(NDNSubsetBeforeMutation),
                    JRangeInCDR3
            );

            NucleotideSequence builtClone = NucleotideSequence.ALPHABET.createBuilder()
                    .append(buildSequence(VSequence, VMutations, new RangeInfo(VRangeBeforeCDR3Begin, true)))
                    .append(VSequenceInCDR3)
                    .append(NDN)
                    .append(JSequenceInCDR3)
                    .append(buildSequence(JSequence, JMutations, new RangeInfo(JRangeAfterCDR3End, false)))
                    .createAndDestroy();

            MutationsFromVJGermline mutationsFromVJGermline = new MutationsFromVJGermline(
                    Lists.newArrayList(
                            new MutationsWithRange(
                                    VSequence,
                                    VMutations,
                                    new RangeInfo(VRangeBeforeCDR3Begin, true)
                            )
                    ),
                    new MutationsWithRange(
                            VSequence,
                            VMutations,
                            new RangeInfo(commonVRangeInCDR3, false)
                    ),
                    Pair.create(VMutations, new Range(commonVRangeInCDR3.getUpper(), VRangeAfterCDR3Begin.getUpper())),
                    NucleotideSequence.ALPHABET.createBuilder()
                            .append(buildSequence(VSequence, VMutations, new RangeInfo(new Range(commonVRangeInCDR3.getUpper(), VRangeAfterCDR3Begin.getUpper()), false)))
                            .append(NDN)
                            .append(buildSequence(JSequence, JMutations, new RangeInfo(new Range(JRangeBeforeCDR3End.getLower(), commonJRangeInCDR3.getLower()), true)))
                            .createAndDestroy(),
                    Pair.create(JMutations, new Range(JRangeBeforeCDR3End.getLower(), commonJRangeInCDR3.getLower())),
                    new MutationsWithRange(
                            JSequence,
                            JMutations,
                            new RangeInfo(commonJRangeInCDR3, false)
                    ),
                    Lists.newArrayList(
                            new MutationsWithRange(
                                    JSequence,
                                    JMutations,
                                    new RangeInfo(JRangeAfterCDR3End, false)
                            )
                    )
            );

            CloneWithMutationsFromReconstructedRoot rebasedClone = new ClonesRebase(VSequence, AffineGapAlignmentScoring.getNucleotideBLASTScoring(), AffineGapAlignmentScoring.getNucleotideBLASTScoring(), JSequence, AffineGapAlignmentScoring.getNucleotideBLASTScoring())
                    .rebaseClone(rootInfo, mutationsFromVJGermline, new CloneWrapper(null, 0));

            AncestorInfoBuilder ancestorInfoBuilder = new AncestorInfoBuilder();
            MutationsDescription result = rebasedClone.getMutationsFromRoot();
            AncestorInfo ancestorInfo = ancestorInfoBuilder.buildAncestorInfo(result);

            Range VPartLeftInRootRange = VRangeAfterCDR3Begin.intersection(rootInfo.getVRangeInCDR3());
            VPartLeftInRootRange = VPartLeftInRootRange == null ? new Range(VRangeAfterCDR3Begin.getLower(), VRangeAfterCDR3Begin.getLower()) : VPartLeftInRootRange;
            NucleotideSequence VPartLeftInRoot = buildSequence(VSequence, VMutations, new RangeInfo(VPartLeftInRootRange, false));

            Range JPartLeftInRootRange = JRangeBeforeCDR3End.intersection(rootInfo.getJRangeInCDR3());
            JPartLeftInRootRange = JPartLeftInRootRange == null ? new Range(JRangeBeforeCDR3End.getUpper(), JRangeBeforeCDR3End.getUpper()) : JPartLeftInRootRange;
            NucleotideSequence JPartLeftInRoot = buildSequence(JSequence, JMutations, new RangeInfo(JPartLeftInRootRange, true));

            Range VPartGotFromNDNRange = new Range(
                    VPartLeftInRootRange.length(),
                    rootInfo.getVRangeInCDR3().length()
            ).move(VPartLeftInRoot.size() - VPartLeftInRootRange.length());
            Range JPartGotFromNDNRange = new Range(
                    CDR3.size() - rootInfo.getJRangeInCDR3().length(),
                    CDR3.size() - JPartLeftInRootRange.length()
            ).move(JPartLeftInRootRange.length() - JPartLeftInRoot.size());

            NucleotideSequence VPartGotFromNDN = VPartGotFromNDNRange.isReverse() ? new NucleotideSequence("") : CDR3.getRange(VPartGotFromNDNRange);
            NucleotideSequence JPartGotFromNDN = JPartGotFromNDNRange.isReverse() ? new NucleotideSequence("") : CDR3.getRange(JPartGotFromNDNRange);

            if (print) {
                System.out.println("original: " + builtClone);
                System.out.println("  result: " + ancestorInfo.getSequence());
                System.out.println();
                System.out.println("   original with marking: "
                        + buildSequence(VSequence, VMutations, new RangeInfo(VRangeBeforeCDR3Begin, true))
                        + " "
                        + VSequenceInCDR3
                        + " "
                        + NDN
                        + " "
                        + JSequenceInCDR3
                        + " "
                        + buildSequence(JSequence, JMutations, new RangeInfo(JRangeAfterCDR3End, false))
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
                        + buildSequence(VSequence, VMutations, new RangeInfo(VRangeBeforeCDR3Begin, true))
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
                        + buildSequence(JSequence, JMutations, new RangeInfo(JRangeAfterCDR3End, false))
                );
                System.out.println("          rebase on root: "
                        + buildSequence(VSequence, EMPTY_NUCLEOTIDE_MUTATIONS, new RangeInfo(VRangeBeforeCDR3Begin, true))
                        + " "
                        + buildSequence(VSequence, EMPTY_NUCLEOTIDE_MUTATIONS, new RangeInfo(rootInfo.getVRangeInCDR3(), false))
                        + " "
                        + rootInfo.getReconstructedNDN()
                        + " "
                        + buildSequence(JSequence, EMPTY_NUCLEOTIDE_MUTATIONS, new RangeInfo(rootInfo.getJRangeInCDR3(), true))
                        + " "
                        + buildSequence(JSequence, EMPTY_NUCLEOTIDE_MUTATIONS, new RangeInfo(JRangeAfterCDR3End, false))
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
                    .mapToInt(it -> it.getRangeInfo().getRange().getUpper())
                    .max().orElseThrow(IllegalStateException::new));
            assertEquals(rootInfo.getVRangeInCDR3(), result.getVMutationsInCDR3WithoutNDN().getRangeInfo().getRange());

            assertEquals(rootInfo.getJRangeInCDR3().getUpper(), result.getJMutationsWithoutCDR3().stream()
                    .mapToInt(it -> it.getRangeInfo().getRange().getLower())
                    .min().orElseThrow(IllegalStateException::new));
            assertEquals(rootInfo.getJRangeInCDR3(), result.getJMutationsInCDR3WithoutNDN().getRangeInfo().getRange());

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
