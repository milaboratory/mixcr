@file:Suppress("LocalVariableName")

package com.milaboratory.mixcr.trees

import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.google.common.primitives.Bytes
import com.milaboratory.core.Range
import com.milaboratory.core.alignment.AffineGapAlignmentScoring
import com.milaboratory.core.alignment.Aligner
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.mutations.Mutations.EMPTY_NUCLEOTIDE_MUTATIONS
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.core.sequence.Seq
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.trees.MutationsGenerator.generateMutations
import com.milaboratory.mixcr.trees.MutationsUtils.NDNScoring
import com.milaboratory.mixcr.trees.MutationsUtils.buildSequence
import com.milaboratory.mixcr.trees.MutationsUtils.projectRange
import com.milaboratory.mixcr.util.RangeInfo
import com.milaboratory.mixcr.util.extractAbsoluteMutations
import com.milaboratory.util.RangeMap
import io.repseq.core.GeneType
import org.junit.Assert.assertEquals
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
        assertEquals(emptyList<Any>(), failedSeeds)
    }

    @Test
    fun reproduceRebaseMutations() {
        assertFalse(testRebaseMutations(2717362330381213098L, true))
        assertFalse(testRebaseMutations(-7736026003531838642L, true))
        assertFalse(testRebaseMutations(-2276640640846890955L, true))
        assertFalse(testRebaseMutations(-4625731613403327929L, true))
    }

    @Ignore
    @Test
    fun randomizedTestForRebaseClone() {
        val numberOfRuns = 100000
        val failedSeeds = IntStream.range(0, numberOfRuns)
            .mapToObj { ThreadLocalRandom.current().nextLong() }
            .parallel()
            .filter { seed: Long -> testRebaseClone(seed, false) }
            .collect(Collectors.toList())
        println("failed: " + failedSeeds.size)
        assertEquals(emptyList<Any>(), failedSeeds)
    }

    @Test
    fun reproduceRebaseClone() {
        assertFalse(testRebaseClone(7238690851096249903L, true))
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
            assertEquals(
                buildSequences(original.VMutationsWithoutCDR3),
                buildSequences(result.VMutationsWithoutCDR3)
            )
            assertEquals(buildCDR3(original), buildCDR3(result))
            assertEquals(
                buildSequences(original.JMutationsWithoutCDR3),
                buildSequences(result.JMutationsWithoutCDR3)
            )
            assertEquals(rebaseToRootInfo.VRangeInCDR3, result.VMutationsInCDR3WithoutNDN.rangeInfo.range)
            assertEquals(rebaseToRootInfo.reconstructedNDN, result.knownNDN.sequence1)
            assertEquals(rebaseToRootInfo.JRangeInCDR3, result.JMutationsInCDR3WithoutNDN.rangeInfo.range)
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

    private fun testRebaseClone(seed: Long, print: Boolean): Boolean = try {
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
            VGeneMutations(
                VSequence,
                RangeMap<Mutations<NucleotideSequence>>().also {
                    it.put(
                        VRangeBeforeCDR3Begin,
                        VMutations.extractAbsoluteMutations(VRangeBeforeCDR3Begin, true)
                    )
                },
                PartInCDR3(
                    commonVRangeInCDR3,
                    VMutations.extractAbsoluteMutations(commonVRangeInCDR3, false)
                )
            ),
            JGeneMutations(
                JSequence,
                PartInCDR3(
                    commonJRangeInCDR3,
                    JMutations.extractAbsoluteMutations(commonJRangeInCDR3, true)
                ),
                RangeMap<Mutations<NucleotideSequence>>().also {
                    it.put(
                        JRangeAfterCDR3End,
                        JMutations.extractAbsoluteMutations(JRangeAfterCDR3End, false)
                    )
                }
            ),
            Lists.newArrayList(
                MutationsWithRange(
                    VSequence,
                    VMutations.extractAbsoluteMutations(VRangeBeforeCDR3Begin, true),
                    RangeInfo(VRangeBeforeCDR3Begin, true)
                )
            ),
            MutationsWithRange(
                VSequence,
                VMutations.extractAbsoluteMutations(commonVRangeInCDR3, false),
                RangeInfo(commonVRangeInCDR3, false)
            ),
            Range(commonVRangeInCDR3.upper, VRangeAfterCDR3Begin.upper).let { range ->
                VMutations.extractAbsoluteMutations(range, false) to range
            },
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
            Range(JRangeBeforeCDR3End.lower, commonJRangeInCDR3.lower).let { range ->
                JMutations.extractAbsoluteMutations(range, true) to range
            },
            MutationsWithRange(
                JSequence,
                JMutations.extractAbsoluteMutations(commonJRangeInCDR3, false),
                RangeInfo(commonJRangeInCDR3, false)
            ),
            Lists.newArrayList(
                MutationsWithRange(
                    JSequence,
                    JMutations.extractAbsoluteMutations(JRangeAfterCDR3End, false),
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
        ).rebaseClone(
            rootInfo,
            mutationsFromVJGermline,
            CloneWrapper(
                Clone(emptyArray(), Maps.newEnumMap(GeneType::class.java), null, 0.0, 0, 0),
                0,
                VJBase("BGeneName", "JGeneName", 20)
            )
        )
        assertEquals(
            rebasedClone.mutationsFromRoot.VMutationsWithoutCDR3.map { it.buildSequence() },
            rebasedClone.mutationsSet.VMutations.mutations.entrySet()
                .map { (range, mutations) ->
                    buildSequence(
                        rebasedClone.mutationsSet.VMutations.sequence1,
                        mutations,
                        range
                    )
                }
        )
        assertEquals(
            rebasedClone.mutationsFromRoot.VMutationsInCDR3WithoutNDN.buildSequence(),
            rebasedClone.mutationsSet.VMutations.partInCDR3.let { (range, mutations) ->
                buildSequence(rebasedClone.mutationsSet.VMutations.sequence1, mutations, range)
            }
        )

        val resultedNDN = rebasedClone.mutationsSet.NDNMutations.mutations.mutate(
            rebasedClone.mutationsSet.NDNMutations.base
        )
        assertEquals(
            rebasedClone.mutationsFromRoot.knownNDN.buildSequence(),
            resultedNDN
        )

        assertEquals(
            rebasedClone.mutationsFromRoot.JMutationsInCDR3WithoutNDN.buildSequence(),
            rebasedClone.mutationsSet.JMutations.partInCDR3.let { (range, mutations) ->
                buildSequence(rebasedClone.mutationsSet.JMutations.sequence1, mutations, range)
            }
        )
        assertEquals(
            rebasedClone.mutationsFromRoot.JMutationsWithoutCDR3.map { it.buildSequence() },
            rebasedClone.mutationsSet.JMutations.mutations.entrySet()
                .map { (range, mutations) ->
                    buildSequence(
                        rebasedClone.mutationsSet.JMutations.sequence1,
                        mutations,
                        range
                    )
                }
        )

        val resultSequenceBuilder = NucleotideSequence.ALPHABET.createBuilder()
        rebasedClone.mutationsSet.VMutations.mutations.entrySet()
            .map { (range, mutations) ->
                buildSequence(
                    rebasedClone.mutationsSet.VMutations.sequence1,
                    mutations,
                    range
                )
            }
            .forEach { resultSequenceBuilder.append(it) }
        val CDR3Begin = resultSequenceBuilder.size()
        rebasedClone.mutationsSet.VMutations.partInCDR3.let { (range, mutations) ->
            val sequence = buildSequence(rebasedClone.mutationsSet.VMutations.sequence1, mutations, range)
            resultSequenceBuilder.append(sequence)
        }
        resultSequenceBuilder.append(
            resultedNDN
        )
        rebasedClone.mutationsSet.JMutations.partInCDR3.let { (range, mutations) ->
            val sequence = buildSequence(rebasedClone.mutationsSet.JMutations.sequence1, mutations, range)
            resultSequenceBuilder.append(sequence)
        }
        val CDR3End = resultSequenceBuilder.size()
        rebasedClone.mutationsSet.JMutations.mutations.entrySet()
            .map { (range, mutations) ->
                buildSequence(
                    rebasedClone.mutationsSet.JMutations.sequence1,
                    mutations,
                    range
                )
            }
            .forEach { resultSequenceBuilder.append(it) }
        val resultSequence = resultSequenceBuilder.createAndDestroy()
        var VPartLeftInRootRange = VRangeAfterCDR3Begin.intersection(rootInfo.VRangeInCDR3)
        VPartLeftInRootRange = VPartLeftInRootRange ?: Range(VRangeAfterCDR3Begin.lower, VRangeAfterCDR3Begin.lower)
        val VPartLeftInRoot =
            buildSequence(VSequence, VMutations, RangeInfo(VPartLeftInRootRange, false))
        var JPartLeftInRootRange = JRangeBeforeCDR3End.intersection(rootInfo.JRangeInCDR3)
        JPartLeftInRootRange = JPartLeftInRootRange ?: Range(JRangeBeforeCDR3End.upper, JRangeBeforeCDR3End.upper)
        val JPartLeftInRoot =
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
            println("  result: $resultSequence")
            println()
            println(
                "   original with marking: "
                    + buildSequence(VSequence, VMutations, RangeInfo(VRangeBeforeCDR3Begin, true))
                    + " "
                    + VSequenceInCDR3
                    + " "
                    + NDN
                    + " "
                    + JSequenceInCDR3
                    + " "
                    + buildSequence(JSequence, JMutations, RangeInfo(JRangeAfterCDR3End, false))
            )
            println("     result with marking: "
                + rebasedClone.mutationsSet.VMutations.mutations.entrySet()
                .joinToString(" ") { (range, mutations) ->
                    buildSequence(
                        rebasedClone.mutationsSet.VMutations.sequence1,
                        mutations,
                        range
                    ).toString()
                }
                + " "
                + rebasedClone.mutationsSet.VMutations.partInCDR3
                .let { (range, mutations) ->
                    buildSequence(rebasedClone.mutationsSet.VMutations.sequence1, mutations, range)
                }
                + " "
                + resultedNDN
                + " "
                + rebasedClone.mutationsSet.JMutations.partInCDR3
                .let { (range, mutations) ->
                    buildSequence(rebasedClone.mutationsSet.JMutations.sequence1, mutations, range)
                }
                + " "
                + rebasedClone.mutationsSet.JMutations.mutations.entrySet()
                .joinToString(" ") { (range, mutations) ->
                    buildSequence(
                        rebasedClone.mutationsSet.JMutations.sequence1,
                        mutations,
                        range
                    ).toString()
                }
            )
            println(
                "root mutated in germline: "
                    + buildSequence(VSequence, VMutations, RangeInfo(VRangeBeforeCDR3Begin, true))
                    + " "
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
                "          rebase on root: "
                    + buildSequence(
                    VSequence,
                    EMPTY_NUCLEOTIDE_MUTATIONS,
                    RangeInfo(VRangeBeforeCDR3Begin, true)
                )
                    + " "
                    + buildSequence(
                    VSequence,
                    EMPTY_NUCLEOTIDE_MUTATIONS,
                    RangeInfo(rootInfo.VRangeInCDR3, false)
                )
                    + " "
                    + rootInfo.reconstructedNDN
                    + " "
                    + buildSequence(
                    JSequence,
                    EMPTY_NUCLEOTIDE_MUTATIONS,
                    RangeInfo(rootInfo.JRangeInCDR3, true)
                )
                    + " "
                    + buildSequence(
                    JSequence,
                    EMPTY_NUCLEOTIDE_MUTATIONS,
                    RangeInfo(JRangeAfterCDR3End, false)
                )
            )
            println()
            println("original CDR3: $CDR3")
            println(
                "  result CDR3: " + (resultSequence as Seq<NucleotideSequence>).getRange(CDR3Begin, CDR3End)
            )
            println()
            println("     original NDN: $NDN")
            println("      mutated NDN: ${rootInfo.reconstructedNDN}")
            println("    subset of NDN: $NDNSubsetBeforeMutation")
            println("mutated from root: $resultedNDN")
            println()
        }
        assertEquals(
            rootInfo.VRangeInCDR3.lower,
            rebasedClone.mutationsSet.VMutations.mutations.entrySet().maxOf { it.key.upper })
        assertEquals(rootInfo.VRangeInCDR3, rebasedClone.mutationsSet.VMutations.partInCDR3.range)
        assertEquals(
            rootInfo.JRangeInCDR3.upper,
            rebasedClone.mutationsSet.JMutations.mutations.entrySet().minOf { it.key.lower })
        assertEquals(rootInfo.JRangeInCDR3, rebasedClone.mutationsSet.JMutations.partInCDR3.range)
        assertEquals(CDR3, resultSequence.getRange(CDR3Begin, CDR3End))
        assertEquals(builtClone, resultSequence)
        assertEquals(
            VPartLeftInRoot.concatenate(VPartGotFromNDN),
            rebasedClone.mutationsSet.VMutations.partInCDR3.let { (range, mutations) ->
                buildSequence(rebasedClone.mutationsSet.VMutations.sequence1, mutations, range)
            }
        )
        assertEquals(NDNSubsetBeforeMutation, resultedNDN)
        assertEquals(
            JPartGotFromNDN.concatenate(JPartLeftInRoot),
            rebasedClone.mutationsSet.JMutations.partInCDR3.let { (range, mutations) ->
                buildSequence(rebasedClone.mutationsSet.JMutations.sequence1, mutations, range)
            }
        )
        false
    } catch (e: Throwable) {
        if (print) {
            e.printStackTrace()
        }
        true
    }

    private fun generate(random: Random, length: Int): NucleotideSequence {
        val chars = IntStream.range(0, length)
            .mapToObj { random.nextInt(4).toByte() }
            .collect(Collectors.toList())
        return NucleotideSequence(Bytes.toArray(chars))
    }
}
