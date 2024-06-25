/*
 * Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
package com.milaboratory.mixcr.assembler.fullseq

import cc.redberry.pipe.CUtils
import cc.redberry.pipe.util.asOutputPort
import com.milaboratory.core.io.sequence.PairedRead
import com.milaboratory.core.io.sequence.SequenceRead
import com.milaboratory.core.io.sequence.SequenceReader
import com.milaboratory.core.io.sequence.SingleReadImpl
import com.milaboratory.core.sequence.NSequenceWithQuality
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.core.sequence.SequenceQuality
import com.milaboratory.core.sequence.quality.QualityTrimmerParameters
import com.milaboratory.mixcr.assembler.CloneFactory
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.MultiAlignmentHelper
import com.milaboratory.mixcr.basictypes.VDJCAlignments
import com.milaboratory.mixcr.basictypes.tag.TagsInfo
import com.milaboratory.mixcr.cli.CommandExportClonesPretty.Companion.outputCompact
import com.milaboratory.mixcr.util.RunMiXCR
import com.milaboratory.mixcr.util.RunMiXCR.RunMiXCRAnalysis
import com.milaboratory.mixcr.vdjaligners.VDJCParametersPresets
import gnu.trove.set.hash.TIntHashSet
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.repseq.core.GeneFeature
import io.repseq.core.GeneFeature.CDR3
import io.repseq.core.GeneFeatures
import io.repseq.core.GeneType
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import org.apache.commons.math3.random.RandomDataGenerator
import org.apache.commons.math3.random.Well19937c
import org.apache.commons.math3.random.Well44497b
import org.junit.Assert
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.*
import java.util.stream.Collectors
import java.util.stream.StreamSupport

/**
 *
 */
class FullSeqAssemblerTest {
    class MasterSequence(
        vPart: NucleotideSequence,
        cdr3Part: NucleotideSequence,
        jPart: NucleotideSequence,
        cPart: NucleotideSequence
    ) {
        val vPart: Int
        val cdr3Part: Int
        val jPart: Int
        val cPart: Int
        val masterSequence: NucleotideSequence

        init {
            this.vPart = vPart.size()
            this.cdr3Part = cdr3Part.size()
            this.jPart = jPart.size()
            this.cPart = cPart.size()
            masterSequence = vPart.concatenate(cdr3Part).concatenate(jPart).concatenate(cPart)
        }

        constructor(vPart: String, cdr3Part: String, jPart: String, cPart: String) : this(
            NucleotideSequence(vPart.replace(" ", "")), NucleotideSequence(cdr3Part.replace(" ", "")),
            NucleotideSequence(jPart.replace(" ", "")), NucleotideSequence(cPart.replace(" ", ""))
        ) {
        }

        fun getRange(vPadd: Int, jPadd: Int): NucleotideSequence {
            return masterSequence.getRange(vPart + vPadd, vPart + cdr3Part + jPadd)
        }

        fun getRangeFromCDR3Begin(vPadd: Int, len: Int): NucleotideSequence {
            return masterSequence.getRange(vPart + vPadd, vPart + vPadd + len)
        }

        fun getRangeFromCDR3End(jPadd: Int, len: Int): NucleotideSequence {
            return masterSequence.getRange(vPart + cdr3Part + jPadd, vPart + cdr3Part + jPadd + len)
        }
    }

    @Test
    fun testRandom1() {
        val clones = arrayOf(
            CloneFraction(750, masterSeq1WT),  // V: S346:G->T
            CloneFraction(1000, masterSeq1VSub1),  // V: D373:G
            // J: D55:A
            CloneFraction(1000, masterSeq1VDel1JDel1),  // V: S319:G->T,S357:A->T,D391:C
            // J: D62:C
            CloneFraction(500, masterSeq1VDel1JDelVSub2)
        )
        val rand = Well19937c()
        rand.setSeed(12345)
        val rdg = RandomDataGenerator(rand)
        val readsOrig: MutableList<SequenceRead> = ArrayList()
        val readLength = 100
        var id = -1
        for (clone in clones) {
            for (i in 0 until clone.count) {
                // Left read with CDR3
                ++id
                readsOrig.add(
                    PairedRead(
                        SingleReadImpl(
                            id.toLong(),
                            NSequenceWithQuality(
                                clone.seq.getRangeFromCDR3Begin(
                                    -rand.nextInt(readLength - clone.seq.cdr3Part),
                                    readLength
                                )
                            ),
                            "R1_$id"
                        ),
                        SingleReadImpl(
                            id.toLong(), NSequenceWithQuality(
                                clone.seq.getRangeFromCDR3End(
                                    rdg.nextInt(-clone.seq.cdr3Part / 2, clone.seq.jPart),
                                    readLength
                                ).reverseComplement
                            ), "R2_$id"
                        )
                    )
                )
                ++id
                readsOrig.add(
                    PairedRead(
                        SingleReadImpl(
                            id.toLong(),
                            NSequenceWithQuality(
                                clone.seq.getRangeFromCDR3Begin(
                                    rdg.nextInt(
                                        -clone.seq.vPart,
                                        clone.seq.cdr3Part / 2 - readLength
                                    ), readLength
                                )
                            ),
                            "R1_$id"
                        ),
                        SingleReadImpl(
                            id.toLong(), NSequenceWithQuality(
                                clone.seq.getRangeFromCDR3Begin(
                                    -rand.nextInt(readLength - clone.seq.cdr3Part),
                                    readLength
                                )
                            ).reverseComplement, "R2_$id"
                        )
                    )
                )
            }
        }

        //        readsOrig = Arrays.asList(setReadId(0, readsOrig.get(12)), setReadId(1, readsOrig.get(13)));
        val perm = rdg.nextPermutation(readsOrig.size, readsOrig.size)
        val reads: MutableList<SequenceRead> = ArrayList()
        for (i in readsOrig.indices) reads.add(readsOrig[perm[i]])
        val params = RunMiXCRAnalysis(
            object : SequenceReader<SequenceRead> {
                var counter = 0
                override fun getNumberOfReads(): Long {
                    return counter.toLong()
                }

                @Synchronized
                override fun take(): SequenceRead? {
                    return if (counter == reads.size) null else reads[counter++]
                }
            }, true
        )
        params.alignerParameters = VDJCParametersPresets.getByName("rna-seq")
        params.alignerParameters.isSaveOriginalReads = true
        params.alignerParameters.setVAlignmentParameters(
            params.alignerParameters.vAlignerParameters.copy(geneFeatureToAlign = GeneFeature.VTranscriptWithP)
        )
        val align = RunMiXCR.align(params)

        //        // TODO exception for translation
        //        for (VDJCAlignments al : align.alignments) {
        //            for (int i = 0; i < al.numberOfTargets(); i++) {
        //                System.out.println(VDJCAlignmentsFormatter.getTargetAsMultiAlignment(al, i));
        //                System.out.println();
        //            }
        //            System.out.println();
        //            System.out.println(" ================================================ ");
        //            System.out.println();
        //        }
        val assemble = RunMiXCR.assemble(align)
        Assert.assertEquals(1, assemble.cloneSet.size().toLong())
        val cloneFactory = CloneFactory(
            align.parameters.cloneAssemblerParameters.cloneFactoryParameters,
            align.parameters.cloneAssemblerParameters.assemblingFeatures,
            align.usedGenes, align.parameters.alignerParameters.featuresToAlignMap
        )
        val agg = FullSeqAssembler(
            cloneFactory,
            DEFAULT_PARAMETERS,
            align.parameters.cloneAssemblerParameters.assemblingFeatures,
            assemble.cloneSet[0],
            align.parameters.alignerParameters,
            assemble.cloneSet[0].getBestHit(Variable),
            assemble.cloneSet[0].getBestHit(Joining),
            1 shl 14
        )
        val prep = agg.calculateRawData {
            CUtils.asOutputPort(
                align.alignments.stream().filter { a: VDJCAlignments -> a.getFeature(CDR3) != null }
                    .collect(
                        Collectors.toList()
                    )
            )
        }
        val clns = listOf(*agg.callVariants(prep))
            .sortedWith(Comparator.comparingDouble { obj: Clone -> obj.count }.reversed())
        println("# Clones: " + clns.size)
        clns
            .mapIndexed { i, clone -> clone.withId(i) }
            .forEach { clone ->
                println(clone.numberOfTargets())
                println(clone.count)
                println(clone.fraction)
                println(clone.getBestHit(Variable)!!.getAlignment(0)!!.absoluteMutations)
                println(clone.getBestHit(GeneType.Joining)!!.getAlignment(0)!!.absoluteMutations)
                println()
                //            ActionExportClonesPretty.outputCompact(System.out, clone);
            }
    }

    class CloneFraction(val count: Int, val seq: MasterSequence)

    @Test
    fun test1() {
        val len = 140
        val read1 = PairedRead(
            SingleReadImpl(0, NSequenceWithQuality(masterSeq1WT.getRangeFromCDR3Begin(-200, len)), "R1"),
            SingleReadImpl(
                0,
                NSequenceWithQuality(masterSeq1WT.getRangeFromCDR3Begin(-20, len).reverseComplement),
                "R2"
            )
        )
        val read2 = PairedRead(
            SingleReadImpl(1, NSequenceWithQuality(masterSeq1WT.getRangeFromCDR3Begin(-150, len)), "R1"),
            SingleReadImpl(
                1,
                NSequenceWithQuality(masterSeq1WT.getRangeFromCDR3Begin(-30, len).reverseComplement),
                "R2"
            )
        )
        val params = RunMiXCRAnalysis(read1, read2)

        // [-200, -60]  [-20, 120]
        //      [-150, 110]
        //
        // [-200, -150], [110, 120] = 60
        // [-60, -20] = 40
        params.alignerParameters = VDJCParametersPresets.getByName("rna-seq")
        params.alignerParameters.isSaveOriginalReads = true
        params.cloneAssemblerParameters.updateFrom(params.alignerParameters)
        val align = RunMiXCR.align(params)
        //        for (VDJCAlignments al : align.alignments) {
        //            for (int i = 0; i < al.numberOfTargets(); i++)
        //                System.out.println(VDJCAlignmentsFormatter.getTargetAsMultiAlignment(al, i));
        //            System.out.println();
        //        }
        val assemble = RunMiXCR.assemble(align)
        val cloneFactory = CloneFactory(
            align.parameters.cloneAssemblerParameters.cloneFactoryParameters,
            align.parameters.cloneAssemblerParameters.assemblingFeatures,
            align.usedGenes, align.parameters.alignerParameters.featuresToAlignMap
        )
        val agg = FullSeqAssembler(
            cloneFactory,
            DEFAULT_PARAMETERS,
            align.parameters.cloneAssemblerParameters.assemblingFeatures,
            assemble.cloneSet[0],
            align.parameters.alignerParameters,
            assemble.cloneSet[0].getBestHit(Variable),
            assemble.cloneSet[0].getBestHit(Joining),
            1 shl 14
        )
        val r2s = agg.toPointSequences(align.alignments[1])
        val p2 = TIntHashSet(Arrays.stream(r2s).mapToInt { s: PointSequence -> s.point }
            .toArray())
        Assert.assertEquals((261 - masterSeq1WT.cdr3Part).toLong(), p2.size().toLong())
        val r1s = agg.toPointSequences(align.alignments[0])
        val p1 = TIntHashSet(Arrays.stream(r1s).mapToInt { s: PointSequence -> s.point }
            .toArray())
        Assert.assertEquals((281 - masterSeq1WT.cdr3Part).toLong(), p1.size().toLong())
        val prep = agg.calculateRawData { CUtils.asOutputPort(align.alignments) }
        val uniq1 = StreamSupport.stream(CUtils.it(prep.createPort()).spliterator(), false)
            .mapToInt { l: IntArray -> l[0] }
            .filter { c: Int -> c == FullSeqAssembler.ABSENT_PACKED_VARIANT_INFO }.count()
        val uniq2 = StreamSupport.stream(CUtils.it(prep.createPort()).spliterator(), false)
            .mapToInt { l: IntArray -> l[1] }
            .filter { c: Int -> c == FullSeqAssembler.ABSENT_PACKED_VARIANT_INFO }.count()
        Assert.assertEquals(40, uniq1)
        Assert.assertEquals(60, uniq2)
        val clones = agg.callVariants(prep)
        clones shouldHaveSize 1
        val clone = clones.first()
        val outputStream = ByteArrayOutputStream()
        outputCompact(PrintStream(outputStream), clone, TagsInfo.NO_TAGS)
        val result = outputStream.toString()

        result.also { println(it) }.lines().map { it.trimEnd() } shouldContainInOrder """
|>>> Clone id: 0
|>>> Abundance, reads (fraction): 2.0 (NaN)
|
|                           FR2><CDR2              CDR2><FR3                                         
|                 V  W  V  S  R  I  N  S  D  G  S  S  T  S  Y  A  D  S  V  K  G  R  F  T  I  S  R    
|    Quality     99999999999999999999999999999999999999999999999999999999999999999999999999999999    
|    Target0   0 GTGTGGGTCTCACGTATTAATAGTGATGGGAGTAGCACAAGCTACGCGGACTCCGTGAAGGGCCGATTCACCATCTCCAG 79   Score (hit score)
|IGHV3-74*00 212 gtgtgggtctcacgtattaatagtgatgggagtagcacaagctacgcggactccgtgaagggccgattcaccatctccag 291  450 (825)
|
|                              
|                  D  N  A     
|    Quality     9999999999    
|    Target0  80 AGACAACGCC 89   Score (hit score)
|IGHV3-74*00 292 agacaacgcc 301  450 (825)
|
|                                                                         DP>                        
|                                FR3><CDR3   V>             <D            D><DP<J     CDR3><FR4      
|                _  D  T  A  V  Y  Y  C  A  R  G  P  Q  E  N  S  G  Y  Y  Y  G  F  D  Y  W  G  Q     
|    Quality     99999999999999999999999999999999999999999999999999999999999999999999999999999999    
|    Target1   0 AGGACACGGCTGTGTATTACTGTGCAAGAGGGCCCCAAGAAAATAGTGGTTATTACTACGGGTTTGACTACTGGGGCCAG 79   Score (hit score)
|IGHV3-74*00 342 aggacacggctgtgtattactgtgcaagag                                                   371  150 (825)
|IGHD3-22*00  46                                            tagtggttattactacg                     62   85 (85)
|   IGHJ4*00  25                                                               tttgactactggggccag 42   215 (215)
|
|                                 FR4>                             
|             G  T  L  V  T  V  S  S _                             
| Quality    99999999999999999999999999999999999999999999999999    
| Target1 80 GGAACCCTGGTCACCGTCTCCTCAGCCTCCACCAAGGGCCCATCGGTCTT 129  Score (hit score)
|IGHJ4*00 43 ggaaccctggtcaccgtctcctcag                          67   215 (215)
        """.trimMargin().lines().map { it.trimEnd() }
    }

    @Test
    fun testLargeCloneNoMismatches() {
        val master = masterSeq1WT
        val seq = NSequenceWithQuality(
            master.getRange(-master.vPart + 10, 80),
            SequenceQuality.GOOD_QUALITY_VALUE
        )
        val params0 = RunMiXCRAnalysis(SingleReadImpl(0, seq, ""))
        params0.cloneAssemblerParameters.assemblingFeatures = arrayOf(GeneFeature.VDJRegion)
        val largeClone = RunMiXCR.assemble(RunMiXCR.align(params0)).cloneSet[0]

        //        ActionExportClonesPretty.outputCompact(System.out, largeClone);
        //        System.exit(0);
        val rnd = Well44497b(1234567889L)
        val nReads = 100000
        val readLength = 75
        val readGap = 150

        // slice seq randomly
        val slicedReads = arrayOfNulls<PairedRead>(nReads)
        for (i in 0 until nReads) {
            val r1from = rnd.nextInt(seq.size() - readLength - 1)
            val r1to = r1from + readLength
            val r2from = r1from + 1 + rnd.nextInt(seq.size() - r1from - readLength - 1)
            val r2to = r2from + readLength
            assert(r2from > r1from)
            slicedReads[i] = PairedRead(
                SingleReadImpl(i.toLong(), seq.getRange(r1from, r1to), "" + i),
                SingleReadImpl(i.toLong(), seq.getRange(r2from, r2to).reverseComplement, "" + i)
            )
        }
        val params = RunMiXCRAnalysis(*slicedReads)
        // params.alignerParameters = VDJCParametersPresets.getByName("rna-seq");
        params.alignerParameters.isSaveOriginalReads = true
        val align = RunMiXCR.align(params)
        val assemble = RunMiXCR.assemble(align)
        for (al in align.alignments) {
            if (al.getFeature(CDR3) == null) continue
            if (NucleotideSequence("TACGGGTTTGACTACTGG") != al.getFeature(CDR3)!!.sequence) continue
            for (i in 0 until al.numberOfTargets()) {
                println(MultiAlignmentHelper.Builder.formatMultiAlignments(al, i).format())
                println()
            }
            println()
            println(" ================================================ ")
            println()
        }
        for (clone in assemble.cloneSet) {
            outputCompact(System.out, clone, TagsInfo.NO_TAGS)
        }

        // Assert.assertEquals(1, assemble.cloneSet.size());
        val initialClone = assemble.cloneSet[0]
        val cdr3 = initialClone.getFeature(CDR3)
        val alignments = align.alignments
            .filter { al -> cdr3 == al.getFeature(CDR3) }
        alignments
            .filter { al ->
                al.getBestHit(Variable)!!.getAlignments()
                    .filterNotNull()
                    .any { a -> !a.absoluteMutations.isEmpty }
            }
            .filter { al -> al.getBestHit(Variable)!!.gene.name.name.contains("3-74") }
            .forEach { al ->
                for (i in 0 until al.numberOfTargets()) {
                    println(MultiAlignmentHelper.Builder.formatMultiAlignments(al, i).format())
                    println()
                }
                println()
                println(" ================================================ ")
                println()
            }

        //        System.exit(0);
        println("=> Agg")
        val cloneFactory = CloneFactory(
            align.parameters.cloneAssemblerParameters.cloneFactoryParameters,
            align.parameters.cloneAssemblerParameters.assemblingFeatures,
            align.usedGenes, align.parameters.alignerParameters.featuresToAlignMap
        )
        val agg = FullSeqAssembler(
            cloneFactory,
            DEFAULT_PARAMETERS,
            align.parameters.cloneAssemblerParameters.assemblingFeatures,
            initialClone,
            align.parameters.alignerParameters,
            initialClone.getBestHit(Variable),
            initialClone.getBestHit(Joining),
            1 shl 14
        )
        val prep = agg.calculateRawData { alignments.asOutputPort() }
        val clones = listOf(*agg.callVariants(prep))
            .sortedWith(Comparator.comparingDouble { obj: Clone -> obj.count }
                .reversed())
        for (clone in clones) {
            outputCompact(System.out, clone!!, TagsInfo.NO_TAGS)
            println()
            println(" ================================================ ")
            println()
        }
    }

    companion object {
        val DEFAULT_PARAMETERS = FullSeqAssemblerParameters(
            branchingMinimalQualityShare = 0.1,
            branchingMinimalSumQuality = 80,
            decisiveBranchingSumQualityThreshold = 120,
            alignedSequenceEdgeDelta = 3,
            alignmentEdgeRegionSize = 7,
            minimalNonEdgePointsFraction = 0.25,
            minimalMeanNormalizedQuality = 3.0,
            outputMinimalQualityShare = 0.5,
            outputMinimalSumQuality = 50,
            subCloningRegions = GeneFeatures(GeneFeature.VDJRegion),
            assemblingRegions = null,
            postFiltering = PostFiltering.NoFiltering,
            trimmingParameters = QualityTrimmerParameters(20.0f, 8),
            minimalContigLength = 20,
            isAlignedRegionsOnly = false,
            discardAmbiguousNucleotideCalls = false,
            useOnlyFullAlignmentsIfPossible = false
        )
        val masterSeq1WT = MasterSequence(
            "CTGAAGAAAACCAGCCCTGCAGCTCTGGGAGAGGAGCCCCAGCCCTGGGATTCCCAGCTGTTTCTGCTTGCTGATCAGGACTGCACACAGAGAACTCACC" +
                    "ATGGAGTTTGGGCTGAGCTGGGTTTTCCTTGTTGCTATTTTAAAAGGTGTCCAGTGTGAGGTGCAGCTGGTGGAGTCCGGGGGAGGCTTAGTTCAGCC" +
                    "TGGGGGGTCCCTGAGACTCTCCTGTGCAGCCTCTGGATTCACCTTCAGTAGCTACTGGATGCACTGGGTCCGCCAAGCTCCAGGGAAGGGGCTGGTGT" +
                    "GGGTCTCACGTATTAATAGTGATGGGAGTAGCACAAGCTACGCGGACTCCGTGAAGGGCCGATTCACCATCTCCAGAGACAACGCCAAGAACACGCTG" +
                    "TATCTGCAAATGAACAGTCTGAGAGCCGAGGACACGGCTGTGTATTAC",
            "TGTGCAAGAGGGCCCCAAGAAAATAGTGGTTATTACTACGGGTTTGACTACTGG",
            "GGCCAGGGAACCCTGGTCACCGTCTCCTCAG",
            "CCTCCACCAAGGGCCCATCGGTCTTCCCCCTGGCGCCCTGCTCCAGGAGCACCTCCGAGAGCACAGCGGCCCTGGGCTGCCTGGTCAAGGACTACTTCCC" +
                    "CGAACCGGTGACGGTGTCGTGGAACTCAGGCGCTCTGACCAGCGGCGTGCACACCTTCCCGGCTGTCCTACAGTCCTCAGGACTCTACTCCCTCAGCA" +
                    "GCGTGGTGACCGTGCCCTCCAGCAACTTCGGCACCCAGACCTACACCTGCAACGTAGATCACAAGCCCAGCAACACCAAGGTGGACAAGACAGTTGGT" +
                    "GAGAGGCCAGCTCAGGGAGGGAGGGTGTCTGCTGGAAGCCAGGCTCAGCCCTCCTGCCTGGACGCACCCCGGCTGTGCAGCCCCAGCCCAGGGCAGCA" +
                    "AGGCAGGCCCCATCTGTCTCCTCACCCGGAGGCCTCTGCCCGCCCCACTCATGCTCAGGGAGAGGGTCTTCTGGCTTTTTCCACCAGGCTCCAGGCAG" +
                    "GCACAGGCTGGGTGCCCCTACCCCAGGCCCTTCACACACAGGGGCAGGTGCTTGGCTCAGACCTGCCAAAAGCCATATCCGG"
        )
        val masterSeq1VDel1JDel1 = MasterSequence(
            "CTGAAGAAAACCAGCCCTGCAGCTCTGGGAGAGGAGCCCCAGCCCTGGGATTCCCAGCTGTTTCTGCTTGCTGATCAGGACTGCACACAGAGAACTCACC" +
                    "ATGGAGTTTGGGCTGAGCTGGGTTTTCCTTGTTGCTATTTTAAAAGGTGTCCAGTGTGAGGTGCAGCTGGTGGAGTCCGGGGGAGGCTTAGTTCAGCC" +
                    "TGGGGGGTCCCTGAGACTCTCCTGTGCAGCCTCTGGATTCACCTTCAGTAGCTACTGGATGCACTGGGTCCGCCAAGCTCCAGGGAAGGGGCTGGTGT" +
                    "GGGTCTCACGTATTAATAGTGATGGGAGTAGCACAAGCTACGCGGACTCCGTGAAGGGCCGATTCACCATCTCCAGAACAACGCCAAGAACACGCTG" +
                    "TATCTGCAAATGAACAGTCTGAGAGCCGAGGACACGGCTGTGTATTAC",
            "TGTGCAAGAGGGCCCCAAGAAAATAGTGGTTATTACTACGGGTTTGACTACTGG",
            "GGCCAGGGAACCCTGGTC CCGTCTCCTCAG",
            "CCTCCACCAAGGGCCCATCGGTCTTCCCCCTGGCGCCCTGCTCCAGGAGCACCTCCGAGAGCACAGCGGCCCTGGGCTGCCTGGTCAAGGACTACTTCCC" +
                    "CGAACCGGTGACGGTGTCGTGGAACTCAGGCGCTCTGACCAGCGGCGTGCACACCTTCCCGGCTGTCCTACAGTCCTCAGGACTCTACTCCCTCAGCA" +
                    "GCGTGGTGACCGTGCCCTCCAGCAACTTCGGCACCCAGACCTACACCTGCAACGTAGATCACAAGCCCAGCAACACCAAGGTGGACAAGACAGTTGGT" +
                    "GAGAGGCCAGCTCAGGGAGGGAGGGTGTCTGCTGGAAGCCAGGCTCAGCCCTCCTGCCTGGACGCACCCCGGCTGTGCAGCCCCAGCCCAGGGCAGCA" +
                    "AGGCAGGCCCCATCTGTCTCCTCACCCGGAGGCCTCTGCCCGCCCCACTCATGCTCAGGGAGAGGGTCTTCTGGCTTTTTCCACCAGGCTCCAGGCAG" +
                    "GCACAGGCTGGGTGCCCCTACCCCAGGCCCTTCACACACAGGGGCAGGTGCTTGGCTCAGACCTGCCAAAAGCCATATCCGG"
        )
        val masterSeq1VDel1JDelVSub2 = MasterSequence(
            "CTGAAGAAAACCAGCCCTGCAGCTCTGGGAGAGGAGCCCCAGCCCTGGGATTCCCAGCTGTTTCTGCTTGCTGATCAGGACTGCACACAGAGAACTCACC" +
                    "ATGGAGTTTGGGCTGAGCTGGGTTTTCCTTGTTGCTATTTTAAAAGGTGTCCAGTGTGAGGTGCAGCTGGTGGAGTCCGGGGGAGGCTTAGTTCAGCC" +
                    "TGGGGGGTCCCTGAGACTCTCCTGTGCAGCCTCTGGATTCACCTTCAGTAGCTACTGGATGCACTGGGTCCGCCAAGCTCCAGGGAAGGGGCTGGTGT" +
                    "GGGTCTCACGTATTAATAGTGATtGGAGTAGCACAAGCTACGCGGACTCCGTGAAGGGCCGtTTCACCATCTCCAGAGACAACGCCAAGAACACGTG" +
                    "TATCTGCAAATGAACAGTCTGAGAGCCGAGGACACGGCTGTGTATTAC",
            "TGTGCAAGAGGGCCCCAAGAAAATAGTGGTTATTACTACGGGTTTGACTACTGG",
            "GGCCAGGGAACCCTGGTCACCGTCTCTCAG",
            "CCTCCACCAAGGGCCCATCGGTCTTCCCCCTGGCGCCCTGCTCCAGGAGCACCTCCGAGAGCACAGCGGCCCTGGGCTGCCTGGTCAAGGACTACTTCCC" +
                    "CGAACCGGTGACGGTGTCGTGGAACTCAGGCGCTCTGACCAGCGGCGTGCACACCTTCCCGGCTGTCCTACAGTCCTCAGGACTCTACTCCCTCAGCA" +
                    "GCGTGGTGACCGTGCCCTCCAGCAACTTCGGCACCCAGACCTACACCTGCAACGTAGATCACAAGCCCAGCAACACCAAGGTGGACAAGACAGTTGGT" +
                    "GAGAGGCCAGCTCAGGGAGGGAGGGTGTCTGCTGGAAGCCAGGCTCAGCCCTCCTGCCTGGACGCACCCCGGCTGTGCAGCCCCAGCCCAGGGCAGCA" +
                    "AGGCAGGCCCCATCTGTCTCCTCACCCGGAGGCCTCTGCCCGCCCCACTCATGCTCAGGGAGAGGGTCTTCTGGCTTTTTCCACCAGGCTCCAGGCAG" +
                    "GCACAGGCTGGGTGCCCCTACCCCAGGCCCTTCACACACAGGGGCAGGTGCTTGGCTCAGACCTGCCAAAAGCCATATCCGG"
        )
        val masterSeq1VSub1 = MasterSequence(
            "CTGAAGAAAACCAGCCCTGCAGCTCTGGGAGAGGAGCCCCAGCCCTGGGATTCCCAGCTGTTTCTGCTTGCTGATCAGGACTGCACACAGAGAACTCACC" +
                    "ATGGAGTTTGGGCTGAGCTGGGTTTTCCTTGTTGCTATTTTAAAAGGTGTCCAGTGTGAGGTGCAGCTGGTGGAGTCCGGGGGAGGCTTAGTTCAGCC" +
                    "TGGGGGGTCCCTGAGACTCTCCTGTGCAGCCTCTGGATTCACCTTCAGTAGCTACTGGATGCACTGGGTCCGCCAAGCTCCAGGGAAGGGGCTGGTGT" +
                    "GGGTCTCACGTATTAATAGTGATGGGAGTAGCACAAGCTACGCGGACTCCtTGAAGGGCCGATTCACCATCTCCAGAGACAACGCCAAGAACACGCTG" +
                    "TATCTGCAAATGAACAGTCTGAGAGCCGAGGACACGGCTGTGTATTAC",
            "TGTGCAAGAGGGCCCCAAGAAAATAGTGGTTATTACTACGGGTTTGACTACTGG",
            "GGCCAGGGAACCCTGGTCACCGTCTCCTCAG",
            "CCTCCACCAAGGGCCCATCGGTCTTCCCCCTGGCGCCCTGCTCCAGGAGCACCTCCGAGAGCACAGCGGCCCTGGGCTGCCTGGTCAAGGACTACTTCCC" +
                    "CGAACCGGTGACGGTGTCGTGGAACTCAGGCGCTCTGACCAGCGGCGTGCACACCTTCCCGGCTGTCCTACAGTCCTCAGGACTCTACTCCCTCAGCA" +
                    "GCGTGGTGACCGTGCCCTCCAGCAACTTCGGCACCCAGACCTACACCTGCAACGTAGATCACAAGCCCAGCAACACCAAGGTGGACAAGACAGTTGGT" +
                    "GAGAGGCCAGCTCAGGGAGGGAGGGTGTCTGCTGGAAGCCAGGCTCAGCCCTCCTGCCTGGACGCACCCCGGCTGTGCAGCCCCAGCCCAGGGCAGCA" +
                    "AGGCAGGCCCCATCTGTCTCCTCACCCGGAGGCCTCTGCCCGCCCCACTCATGCTCAGGGAGAGGGTCTTCTGGCTTTTTCCACCAGGCTCCAGGCAG" +
                    "GCACAGGCTGGGTGCCCCTACCCCAGGCCCTTCACACACAGGGGCAGGTGCTTGGCTCAGACCTGCCAAAAGCCATATCCGG"
        )
        val masterSeq1VLargeIns1 = MasterSequence(
            "CTGAAGAAAACCAGCCCTGCAGCTCTGGGAGAGGAGCCCCAGCCCTGGGATTCCCAGCTGTTTCTGCTTGCTGATCAGGACTGCACACAGAGAACTCACC" +
                    "ATGGAGTTTGGGCTGAGCTGGGTTTTCCTTGTTGCTATTTTAAAAGGTGTCCAGTGTGAGGTGCAGCTGGTGGAGTCCGGGGGAGGCTTAGTTCAGCC" +
                    "TGGGGGGTCCCTGAGACTCTCCTGTGCAGCCTCTGGATTCACCTTCAGTAGCTACTGGATGCACTGGGTCCGCCAAGCTCCAGGGAAGGGGCTGGTGT" +
                    "GGGTCTCACGTATTAATAGTGATGGGAGTAGCACAAGCTtattACGCGGACTCCGTGAAGGGCCGATTCACCATCTCCAGAGACAACGCCAAGAACACGCTG" +
                    "TATCTGCAAATGAACAGTCTGAGAGCCGAGGACACGGCTGTGTATTAC",
            "TGTGCAAGAGGGCCCCAAGAAAATAGTGGTTATTACTACGGGTTTGACTACTGG",
            "GGCCAGGGAACCCTGGTCACCGTCTCCTCAG",
            "CCTCCACCAAGGGCCCATCGGTCTTCCCCCTGGCGCCCTGCTCCAGGAGCACCTCCGAGAGCACAGCGGCCCTGGGCTGCCTGGTCAAGGACTACTTCCC" +
                    "CGAACCGGTGACGGTGTCGTGGAACTCAGGCGCTCTGACCAGCGGCGTGCACACCTTCCCGGCTGTCCTACAGTCCTCAGGACTCTACTCCCTCAGCA" +
                    "GCGTGGTGACCGTGCCCTCCAGCAACTTCGGCACCCAGACCTACACCTGCAACGTAGATCACAAGCCCAGCAACACCAAGGTGGACAAGACAGTTGGT" +
                    "GAGAGGCCAGCTCAGGGAGGGAGGGTGTCTGCTGGAAGCCAGGCTCAGCCCTCCTGCCTGGACGCACCCCGGCTGTGCAGCCCCAGCCCAGGGCAGCA" +
                    "AGGCAGGCCCCATCTGTCTCCTCACCCGGAGGCCTCTGCCCGCCCCACTCATGCTCAGGGAGAGGGTCTTCTGGCTTTTTCCACCAGGCTCCAGGCAG" +
                    "GCACAGGCTGGGTGCCCCTACCCCAGGCCCTTCACACACAGGGGCAGGTGCTTGGCTCAGACCTGCCAAAAGCCATATCCGG"
        )
        val masterSeq1VLargeIns1Sub1 = MasterSequence(
            "CTGAAGAAAACCAGCCCTGCAGCTCTGGGAGAGGAGCCCCAGCCCTGGGATTCCCAGCTGTTTCTGCTTGCTGATCAGGACTGCACACAGAGAACTCACC" +
                    "ATGGAGTTTGGGCTGAGCTGGGTTTTCCTTGTTGCTATTTTAAAAGGTGTCCAGTGTGAGGTGCAGCTGGTGGAGTCCGGGGGAGGCTTAGTTCAGCC" +
                    "TGGGGGGTCCCTGAGACTCTCCTGTGCAGCCTCTGGATTCACCTTCAGTAGCTACTGGATGCACTGGGTCCGCCAAGCTCCAGGGAAGGGGCTGGTGT" +
                    "GGGTCTCACGTATTAATAGTGATGGGAGTAGCACAAGCTtattACGCGGACTCCGTGAAGGGCCGATTCACCATCTCCAGAGACAACGCCAAGAACACGCTG" +
                    "TATCTGCAAATGAACAGTCTcAGAGCCGAGGACACGGCTGTGTATTAC",
            "TGTGCAAGAGGGCCCCAAGAAAATAGTGGTTATTACTACGGGTTTGACTACTGG",
            "GGCCAGGGAACCCTGGTCACCGTCTCCTCAG",
            "CCTCCACCAAGGGCCCATCGGTCTTCCCCCTGGCGCCCTGCTCCAGGAGCACCTCCGAGAGCACAGCGGCCCTGGGCTGCCTGGTCAAGGACTACTTCCC" +
                    "CGAACCGGTGACGGTGTCGTGGAACTCAGGCGCTCTGACCAGCGGCGTGCACACCTTCCCGGCTGTCCTACAGTCCTCAGGACTCTACTCCCTCAGCA" +
                    "GCGTGGTGACCGTGCCCTCCAGCAACTTCGGCACCCAGACCTACACCTGCAACGTAGATCACAAGCCCAGCAACACCAAGGTGGACAAGACAGTTGGT" +
                    "GAGAGGCCAGCTCAGGGAGGGAGGGTGTCTGCTGGAAGCCAGGCTCAGCCCTCCTGCCTGGACGCACCCCGGCTGTGCAGCCCCAGCCCAGGGCAGCA" +
                    "AGGCAGGCCCCATCTGTCTCCTCACCCGGAGGCCTCTGCCCGCCCCACTCATGCTCAGGGAGAGGGTCTTCTGGCTTTTTCCACCAGGCTCCAGGCAG" +
                    "GCACAGGCTGGGTGCCCCTACCCCAGGCCCTTCACACACAGGGGCAGGTGCTTGGCTCAGACCTGCCAAAAGCCATATCCGG"
        )
    }
}
