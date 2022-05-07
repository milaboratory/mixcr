@file:Suppress("LocalVariableName")

package com.milaboratory.mixcr.trees

import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.google.common.primitives.Bytes
import com.milaboratory.core.Range
import com.milaboratory.core.alignment.AffineGapAlignmentScoring
import com.milaboratory.core.alignment.Aligner
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.core.sequence.Seq
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.trees.MutationsGenerator.generateMutations
import com.milaboratory.mixcr.trees.MutationsUtils.NDNScoring
import com.milaboratory.mixcr.trees.MutationsUtils.buildSequence
import com.milaboratory.mixcr.trees.MutationsUtils.projectRange
import com.milaboratory.mixcr.util.RangeInfo
import io.repseq.core.GeneType
import org.junit.Assert
import org.junit.Assert.assertFalse
import org.junit.Ignore
import org.junit.Test
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.stream.Collectors
import java.util.stream.IntStream

class RebaseClonesTest {
    @Ignore
    @Test
    fun randomizedTestForRebaseMutations() {
        val numberOfRuns = 1000000
        val failedSeeds = IntStream.range(0, numberOfRuns)
            .mapToObj { ThreadLocalRandom.current().nextLong() }
            .parallel()
            .filter { seed: Long -> testRebaseMutations(seed, false) }
            .collect(Collectors.toList())
        println("failed: " + failedSeeds.size)
        Assert.assertEquals(emptyList<Any>(), failedSeeds)
    }

    @Test
    fun reproduceRebaseMutations() {
        assertFalse(testRebaseMutations(2717362330381213098L, true))
        assertFalse(testRebaseMutations(-7736026003531838642L, true))
        assertFalse(testRebaseMutations(-2276640640846890955L, true))
        assertFalse(testRebaseMutations(-4625731613403327929L, true))
    }

    //    @Ignore
    @Test
    fun randomizedTestForRebaseClone() {
        val numberOfRuns = 100000
        val failedSeeds = IntStream.range(0, numberOfRuns)
            .mapToObj { ThreadLocalRandom.current().nextLong() }
            .parallel()
            .filter { seed: Long -> testRebaseClone(seed, false) }
            .collect(Collectors.toList())
        println("failed: " + failedSeeds.size)
        Assert.assertEquals(emptyList<Any>(), failedSeeds)
    }

    @Test
    fun reproduceRebaseClone() {
        assertFalse(testRebaseClone(-6528292659028221478L, true))
        assertFalse(testRebaseClone(-1959168467592812968L, true))
        assertFalse(testRebaseClone(4887507527711339190L, true))
        assertFalse(testRebaseClone(2049978999466120864L, true))
        assertFalse(testRebaseClone(-7534105378312308262L, true))
        assertFalse(testRebaseClone(4510972677298188920L, true))
        assertFalse(testRebaseClone(1729663315728681110L, true))
        assertFalse(testRebaseClone(4608235439778868248L, true))
        assertFalse(testRebaseClone(7155779204574879033L, true))
        assertFalse(testRebaseClone(-4567604316340909864L, true))
        assertFalse(testRebaseClone(7360045022198406917L, true))
        assertFalse(testRebaseClone(8861605449460417460L, true))
        assertFalse(testRebaseClone(3361027404503237374L, true))
        assertFalse(testRebaseClone(5633311090069099492L, true))
        assertFalse(testRebaseClone(-140150437646008446L, true))
        assertFalse(testRebaseClone(-3721882169827128329L, true))
        assertFalse(testRebaseClone(-6639724146754084784L, true))
        assertFalse(testRebaseClone(-154711501619107070L, true))
        assertFalse(testRebaseClone(3991336578395308109L, true))
        assertFalse(testRebaseClone(-5047483764046740699L, true))
        assertFalse(testRebaseClone(49156566332349046L, true))
        assertFalse(testRebaseClone(-6877842382590389599L, true))
        assertFalse(testRebaseClone(5739929328149910349L, true))
        assertFalse(testRebaseClone(7581006658967416418L, true))
        assertFalse(testRebaseClone(5492150036748141135L, true))
        assertFalse(testRebaseClone(8053975088522559753L, true))
        assertFalse(testRebaseClone(8812578697731451467L, true))
    }

    private fun testRebaseMutations(seed: Long, print: Boolean): Boolean {
        return try {
            val random = Random(seed)
            val VSequence = generate(random, 50 + random.nextInt(50))
            val VRangeBeforeCDR3Begin = Range(0, 10 + random.nextInt(10)).move(10 + random.nextInt(5))
            val VRangeAfterCDR3Begin = Range(0, random.nextInt(5)).move(VRangeBeforeCDR3Begin.upper)
            val JSequence = generate(random, 50 + random.nextInt(50))
            val JRangeBeforeCDR3End = Range(0, random.nextInt(5)).move(10 + random.nextInt(5))
            val JRangeAfterCDR3End = Range(0, 10 + random.nextInt(10)).move(JRangeBeforeCDR3End.upper)
            val VRange = Range(VRangeBeforeCDR3Begin.lower, VRangeAfterCDR3Begin.upper)
            val VMutations = generateMutations(VSequence, random, VRange)
            val JRange = Range(JRangeBeforeCDR3End.lower, JRangeAfterCDR3End.upper)
            val JMutations = generateMutations(JSequence, random, JRange)
            val NDN = generate(random, 10 + random.nextInt(10))
            val originalRootInfo = RootInfo(
                VRangeAfterCDR3Begin,
                generate(random, NDN.size() - 3 + random.nextInt(6)),
                JRangeAfterCDR3End,
                VJBase("VSome", "JSome", 20)
            )
            val original = MutationsDescription(
                Lists.newArrayList(
                    MutationsWithRange(
                        VSequence,
                        VMutations,
                        RangeInfo(VRangeBeforeCDR3Begin, true)
                    )
                ),
                MutationsWithRange(
                    VSequence,
                    VMutations,
                    RangeInfo(originalRootInfo.VRangeInCDR3, false)
                ),
                MutationsWithRange(
                    originalRootInfo.reconstructedNDN,
                    Aligner.alignGlobal(
                        AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                        originalRootInfo.reconstructedNDN,
                        NDN
                    ).absoluteMutations,
                    RangeInfo(Range(0, originalRootInfo.reconstructedNDN.size()), true)
                ),
                MutationsWithRange(
                    JSequence,
                    JMutations,
                    RangeInfo(originalRootInfo.JRangeInCDR3, false)
                ),
                Lists.newArrayList(
                    MutationsWithRange(
                        JSequence,
                        JMutations,
                        RangeInfo(JRangeAfterCDR3End, true)
                    )
                )
            )
            val clonesRebase = ClonesRebase(
                VSequence,
                AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                NDNScoring(),
                JSequence,
                AffineGapAlignmentScoring.getNucleotideBLASTScoring()
            )
            var VBorderExpand = -2 + random.nextInt(4)
            if (originalRootInfo.VRangeInCDR3.length() + VBorderExpand < 0) {
                VBorderExpand = originalRootInfo.VRangeInCDR3.length()
            }
            var JBorderExpand = -2 + random.nextInt(4)
            if (originalRootInfo.JRangeInCDR3.length() + JBorderExpand < 0) {
                JBorderExpand = originalRootInfo.JRangeInCDR3.length()
            }
            val rebaseToRootInfo = RootInfo(
                originalRootInfo.VRangeInCDR3.expand(0, VBorderExpand),
                generate(random, originalRootInfo.reconstructedNDN.size() - VBorderExpand - JBorderExpand),
                originalRootInfo.JRangeInCDR3.expand(JBorderExpand, 0),
                VJBase("VSome", "JSome", 20)
            )
            val result = clonesRebase.rebaseMutations(original, originalRootInfo, rebaseToRootInfo)
            if (print) {
                println(" original rootInfo: $originalRootInfo")
                println("rebase to rootInfo: $rebaseToRootInfo")
                println("original CDR3: " + original.VMutationsInCDR3WithoutNDN.buildSequence() + " " + original.knownNDN.buildSequence() + " " + original.JMutationsInCDR3WithoutNDN.buildSequence())
                println("  result CDR3: " + result.VMutationsInCDR3WithoutNDN.buildSequence() + " " + result.knownNDN.buildSequence() + " " + result.JMutationsInCDR3WithoutNDN.buildSequence())
            }
            Assert.assertEquals(
                buildSequences(original.VMutationsWithoutCDR3),
                buildSequences(result.VMutationsWithoutCDR3)
            )
            Assert.assertEquals(buildCDR3(original), buildCDR3(result))
            Assert.assertEquals(
                buildSequences(original.JMutationsWithoutCDR3),
                buildSequences(result.JMutationsWithoutCDR3)
            )
            Assert.assertEquals(rebaseToRootInfo.VRangeInCDR3, result.VMutationsInCDR3WithoutNDN.rangeInfo.range)
            Assert.assertEquals(rebaseToRootInfo.reconstructedNDN, result.knownNDN.sequence1)
            Assert.assertEquals(rebaseToRootInfo.JRangeInCDR3, result.JMutationsInCDR3WithoutNDN.rangeInfo.range)
            false
        } catch (e: Throwable) {
            if (print) {
                e.printStackTrace()
            }
            true
        }
    }

    private fun buildCDR3(original: MutationsDescription): NucleotideSequence {
        return original.VMutationsInCDR3WithoutNDN.buildSequence()
            .concatenate(original.knownNDN.buildSequence())
            .concatenate(original.JMutationsInCDR3WithoutNDN.buildSequence())
    }

    private fun buildSequences(mutations: List<MutationsWithRange>): NucleotideSequence {
        return mutations.stream()
            .map { obj: MutationsWithRange -> obj.buildSequence() }
            .reduce(NucleotideSequence.EMPTY) { obj: NucleotideSequence, other -> obj.concatenate(other) }
    }

    private fun testRebaseClone(seed: Long, print: Boolean): Boolean {
        return try {
            val random = Random(seed)
            val VSequence = generate(random, 50 + random.nextInt(50))
            val VRangeBeforeCDR3Begin = Range(0, 10 + random.nextInt(10)).move(10 + random.nextInt(5))
            val VRangeAfterCDR3Begin = Range(0, random.nextInt(5)).move(VRangeBeforeCDR3Begin.upper)
            val NDN = generate(random, 10 + random.nextInt(15))
            val JSequence = generate(random, 50 + random.nextInt(50))
            val JRangeBeforeCDR3End = Range(0, random.nextInt(5)).move(10 + random.nextInt(5))
            val JRangeAfterCDR3End = Range(0, 10 + random.nextInt(10)).move(JRangeBeforeCDR3End.upper)
            val VRange = Range(VRangeBeforeCDR3Begin.lower, VRangeAfterCDR3Begin.upper)
            val VMutations = generateMutations(VSequence, random, VRange)
            val JRange = Range(JRangeBeforeCDR3End.lower, JRangeAfterCDR3End.upper)
            val JMutations = generateMutations(JSequence, random, JRange)
            val VSequenceInCDR3: NucleotideSequence =
                buildSequence(VSequence, VMutations, RangeInfo(VRangeAfterCDR3Begin, false))
            val JSequenceInCDR3: NucleotideSequence =
                buildSequence(JSequence, JMutations, RangeInfo(JRangeBeforeCDR3End, true))
            val commonVRangeInCDR3 = Range(
                0,
                if (VRangeAfterCDR3Begin.length() == 0) 0 else random.nextInt(VRangeAfterCDR3Begin.length())
            ).move(VRangeAfterCDR3Begin.lower)
            val commonJRangeInCDR3 = Range(
                if (JRangeBeforeCDR3End.length() == 0) 0 else -random.nextInt(JRangeBeforeCDR3End.length()),
                0
            ).move(JRangeBeforeCDR3End.upper)
            val CDR3 = NucleotideSequence.ALPHABET.createBuilder()
                .append(VSequenceInCDR3)
                .append(NDN)
                .append(JSequenceInCDR3)
                .createAndDestroy()
            val VRangeInCDR3 = Range(0, commonVRangeInCDR3.length() + random.nextInt(5))
                .move(VRangeBeforeCDR3Begin.upper)
            val JRangeInCDR3 = Range(-(commonJRangeInCDR3.length() + random.nextInt(5)), 0)
                .move(JRangeBeforeCDR3End.upper)
            val projectedVRangeInCDR3: Range = projectRange(VMutations, RangeInfo(VRangeInCDR3, false))
            val projectedJRangeInCDR3: Range = projectRange(JMutations, RangeInfo(JRangeInCDR3, true))
            val NDNSubsetRangeBeforeMutation = Range(
                projectedVRangeInCDR3.length(),
                CDR3.size() - projectedJRangeInCDR3.length()
            )
            val NDNSubsetBeforeMutation = CDR3.getRange(NDNSubsetRangeBeforeMutation)
            val mutationsOfNDN: Mutations<NucleotideSequence> = generateMutations(NDNSubsetBeforeMutation, random)
            val rootInfo = RootInfo(
                VRangeInCDR3,
                mutationsOfNDN.mutate(NDNSubsetBeforeMutation),
                JRangeInCDR3,
                VJBase("VSome", "JSome", 20)
            )
            val builtClone: NucleotideSequence = NucleotideSequence.ALPHABET.createBuilder()
                .append(buildSequence(VSequence, VMutations, RangeInfo(VRangeBeforeCDR3Begin, true)))
                .append(VSequenceInCDR3)
                .append(NDN)
                .append(JSequenceInCDR3)
                .append(buildSequence(JSequence, JMutations, RangeInfo(JRangeAfterCDR3End, false)))
                .createAndDestroy()
            val mutationsFromVJGermline = MutationsFromVJGermline(
                Lists.newArrayList(
                    MutationsWithRange(
                        VSequence,
                        VMutations,
                        RangeInfo(VRangeBeforeCDR3Begin, true)
                    )
                ),
                MutationsWithRange(
                    VSequence,
                    VMutations,
                    RangeInfo(commonVRangeInCDR3, false)
                ),
                Pair(VMutations, Range(commonVRangeInCDR3.upper, VRangeAfterCDR3Begin.upper)),
                NucleotideSequence.ALPHABET.createBuilder()
                    .append(
                        buildSequence(
                            VSequence,
                            VMutations,
                            RangeInfo(Range(commonVRangeInCDR3.upper, VRangeAfterCDR3Begin.upper), false)
                        )
                    )
                    .append(NDN)
                    .append(
                        buildSequence(
                            JSequence,
                            JMutations,
                            RangeInfo(Range(JRangeBeforeCDR3End.lower, commonJRangeInCDR3.lower), true)
                        )
                    )
                    .createAndDestroy(),
                Pair(JMutations, Range(JRangeBeforeCDR3End.lower, commonJRangeInCDR3.lower)),
                MutationsWithRange(
                    JSequence,
                    JMutations,
                    RangeInfo(commonJRangeInCDR3, false)
                ),
                Lists.newArrayList(
                    MutationsWithRange(
                        JSequence,
                        JMutations,
                        RangeInfo(JRangeAfterCDR3End, false)
                    )
                )
            )
            val rebasedClone = ClonesRebase(
                VSequence,
                AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                JSequence,
                AffineGapAlignmentScoring.getNucleotideBLASTScoring()
            )
                .rebaseClone(
                    rootInfo,
                    mutationsFromVJGermline,
                    CloneWrapper(
                        Clone(emptyArray(), Maps.newEnumMap(GeneType::class.java), null, 0.0, 0, 0),
                        0,
                        VJBase("BGeneName", "JGeneName", 20)
                    )
                )
            val ancestorInfoBuilder = AncestorInfoBuilder()
            val result = rebasedClone.mutationsFromRoot
            val ancestorInfo = ancestorInfoBuilder.buildAncestorInfo(result)
            var VPartLeftInRootRange = VRangeAfterCDR3Begin.intersection(rootInfo.VRangeInCDR3)
            VPartLeftInRootRange = VPartLeftInRootRange ?: Range(VRangeAfterCDR3Begin.lower, VRangeAfterCDR3Begin.lower)
            val VPartLeftInRoot: NucleotideSequence =
                buildSequence(VSequence, VMutations, RangeInfo(VPartLeftInRootRange, false))
            var JPartLeftInRootRange = JRangeBeforeCDR3End.intersection(rootInfo.JRangeInCDR3)
            JPartLeftInRootRange = JPartLeftInRootRange ?: Range(JRangeBeforeCDR3End.upper, JRangeBeforeCDR3End.upper)
            val JPartLeftInRoot: NucleotideSequence =
                buildSequence(JSequence, JMutations, RangeInfo(JPartLeftInRootRange, true))
            val VPartGotFromNDNRange = Range(
                VPartLeftInRootRange.length(),
                rootInfo.VRangeInCDR3.length()
            ).move(VPartLeftInRoot.size() - VPartLeftInRootRange.length())
            val JPartGotFromNDNRange = Range(
                CDR3.size() - rootInfo.JRangeInCDR3.length(),
                CDR3.size() - JPartLeftInRootRange.length()
            ).move(JPartLeftInRootRange.length() - JPartLeftInRoot.size())
            val VPartGotFromNDN =
                if (VPartGotFromNDNRange.isReverse) NucleotideSequence("") else CDR3.getRange(VPartGotFromNDNRange)
            val JPartGotFromNDN =
                if (JPartGotFromNDNRange.isReverse) NucleotideSequence("") else CDR3.getRange(JPartGotFromNDNRange)
            if (print) {
                println("original: $builtClone")
                println("  result: " + ancestorInfo.sequence)
                println()
                System.out.println(
                    ("   original with marking: "
                        + buildSequence(VSequence, VMutations, RangeInfo(VRangeBeforeCDR3Begin, true))) + " "
                        + VSequenceInCDR3
                        + " "
                        + NDN
                        + " "
                        + JSequenceInCDR3
                        + " "
                        + buildSequence(JSequence, JMutations, RangeInfo(JRangeAfterCDR3End, false))
                )
                println("     result with marking: "
                    + result.VMutationsWithoutCDR3.stream().map { obj: MutationsWithRange -> obj.buildSequence() }
                    .map { obj: NucleotideSequence? -> obj?.toString() ?: "" }.collect(Collectors.joining())
                    + " "
                    + result.VMutationsInCDR3WithoutNDN.buildSequence()
                    + " "
                    + result.knownNDN.buildSequence()
                    + " "
                    + result.JMutationsInCDR3WithoutNDN.buildSequence()
                    + " "
                    + result.JMutationsWithoutCDR3.stream().map { obj: MutationsWithRange -> obj.buildSequence() }
                    .map { obj: NucleotideSequence? -> obj?.toString() ?: "" }.collect(Collectors.joining())
                )
                println(
                    ("root mutated in germline: "
                        + buildSequence(VSequence, VMutations, RangeInfo(VRangeBeforeCDR3Begin, true))) + " "
                        + VPartLeftInRoot
                        + " "
                        + VPartGotFromNDN
                        + " "
                        + rootInfo.reconstructedNDN
                        + " "
                        + JPartGotFromNDN
                        + " "
                        + JPartLeftInRoot
                        + " "
                        + buildSequence(JSequence, JMutations, RangeInfo(JRangeAfterCDR3End, false))
                )
                println(
                    ((("          rebase on root: "
                        + buildSequence(
                        VSequence,
                        Mutations.EMPTY_NUCLEOTIDE_MUTATIONS,
                        RangeInfo(VRangeBeforeCDR3Begin, true)
                    )) + " "
                        + buildSequence(
                        VSequence,
                        Mutations.EMPTY_NUCLEOTIDE_MUTATIONS,
                        RangeInfo(rootInfo.VRangeInCDR3, false)
                    )
                        ) + " "
                        + rootInfo.reconstructedNDN
                        + " "
                        + buildSequence(
                        JSequence,
                        Mutations.EMPTY_NUCLEOTIDE_MUTATIONS,
                        RangeInfo(rootInfo.JRangeInCDR3, true)
                    )
                        ) + " "
                        + buildSequence(
                        JSequence,
                        Mutations.EMPTY_NUCLEOTIDE_MUTATIONS,
                        RangeInfo(JRangeAfterCDR3End, false)
                    )
                )
                println()
                println("original CDR3: $CDR3")
                println(
                    "  result CDR3: " + (ancestorInfo.sequence as Seq<NucleotideSequence>).getRange(
                        ancestorInfo.CDR3Begin,
                        ancestorInfo.CDR3End
                    )
                )
                println()
                println("     original NDN: $NDN")
                println("      mutated NDN: " + rootInfo.reconstructedNDN)
                println("    subset of NDN: $NDNSubsetBeforeMutation")
                println("mutated from root: " + result.knownNDN.buildSequence())
                println()
            }
            Assert.assertEquals(rootInfo.VRangeInCDR3.lower.toLong(), result.VMutationsWithoutCDR3.stream()
                .mapToInt { it.rangeInfo.range.upper }
                .max().orElseThrow { IllegalStateException() }.toLong()
            )
            Assert.assertEquals(rootInfo.VRangeInCDR3, result.VMutationsInCDR3WithoutNDN.rangeInfo.range)
            Assert.assertEquals(rootInfo.JRangeInCDR3.upper.toLong(), result.JMutationsWithoutCDR3.stream()
                .mapToInt { it.rangeInfo.range.lower }
                .min().orElseThrow { IllegalStateException() }.toLong()
            )
            Assert.assertEquals(rootInfo.JRangeInCDR3, result.JMutationsInCDR3WithoutNDN.rangeInfo.range)
            Assert.assertEquals(builtClone, ancestorInfo.sequence)
            Assert.assertEquals(CDR3, ancestorInfo.sequence.getRange(ancestorInfo.CDR3Begin, ancestorInfo.CDR3End))
            Assert.assertEquals(
                VPartLeftInRoot.concatenate(VPartGotFromNDN),
                result.VMutationsInCDR3WithoutNDN.buildSequence()
            )
            Assert.assertEquals(NDNSubsetBeforeMutation, result.knownNDN.buildSequence())
            Assert.assertEquals(
                JPartGotFromNDN.concatenate(JPartLeftInRoot),
                result.JMutationsInCDR3WithoutNDN.buildSequence()
            )
            false
        } catch (e: Throwable) {
            if (print) {
                e.printStackTrace()
            }
            true
        }
    }

    private fun generate(random: Random, length: Int): NucleotideSequence {
        val chars = IntStream.range(0, length)
            .mapToObj { random.nextInt(4).toByte() }
            .collect(Collectors.toList())
        return NucleotideSequence(Bytes.toArray(chars))
    }
}
