/*
 * Copyright (c) 2014-2023, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
package com.milaboratory.mixcr.partialassembler

import cc.redberry.pipe.InputPort
import com.milaboratory.core.io.sequence.PairedRead
import com.milaboratory.core.io.sequence.SingleReadImpl
import com.milaboratory.core.sequence.NSequenceWithQuality
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.core.sequence.SequenceQuality
import com.milaboratory.mixcr.basictypes.VDJCAlignments
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter
import com.milaboratory.mixcr.tests.MiXCRTestUtils
import com.milaboratory.mixcr.util.RunMiXCR
import com.milaboratory.mixcr.util.RunMiXCR.AlignResult
import com.milaboratory.mixcr.util.RunMiXCR.RunMiXCRAnalysis
import com.milaboratory.mixcr.vdjaligners.VDJCParametersPresets
import com.milaboratory.test.TestUtil
import com.milaboratory.util.RandomUtil
import com.milaboratory.util.TempFileManager.newTempFile
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import io.repseq.core.GeneVariantName
import io.repseq.core.VDJCGene
import io.repseq.core.VDJCLibraryRegistry
import org.apache.commons.math3.random.RandomGenerator
import org.junit.Assert
import org.junit.Test
import java.util.*

class PartialAlignmentsAssemblerTest {
    //@Test
    // public void testMaxAllele() throws Exception {
    //    final LociLibrary ll = LociLibraryManager.getDefault().getLibrary("mi");
    //    final Locus locus = Locus.TRB;
    //
    //    for (GeneFeature feature : new GeneFeature[]{VRegionWithP, DRegion, JRegionWithP, CRegion}) {
    //        Allele maxAllele = null;
    //        for (Allele allele : ll.getAllAlleles()) {
    //            if (allele.getLocusContainer().getSpeciesAndLocus().taxonId != Species.HomoSapiens)
    //                continue;
    //            if (!allele.isFunctional())
    //                continue;
    //            if (allele.getLocus() != locus)
    //                continue;
    //            if (maxAllele == null && allele.getFeature(feature) != null)
    //                maxAllele = allele;
    //            if (allele.getFeature(feature) != null && allele.getFeature(feature).size() > maxAllele.getFeature(feature).size())
    //                maxAllele = allele;
    //        }
    //
    //        System.out.println(maxAllele.getName() + "    Size: " + maxAllele.getFeature(feature).size());
    //    }
    //}
    @Test
    fun test1() {
        val input = createTestData()
        val reference = input.reference
        val refPositions = input.refPositions
        val data = arrayOf(
            createPair(
                0, reference.getRange(
                    refPositions[GeneType.Variable]!![1] - 85, refPositions[GeneType.Variable]!![1] + 15
                ), reference.getRange(
                    refPositions[GeneType.Joining]!![1] - 20, refPositions[GeneType.Joining]!![1] + 80
                ).reverseComplement
            ),
            createPair(
                1, reference.getRange(
                    refPositions[GeneType.Variable]!![1] - 186, refPositions[GeneType.Variable]!![1] - 86
                ), reference.getRange(
                    refPositions[GeneType.Variable]!![1] - 10, refPositions[GeneType.Variable]!![1] + 102
                ).reverseComplement
            )
        )
        val testResult = processData(data, input)
        for (al in testResult.assembled) {
            MiXCRTestUtils.printAlignment(al)
        }
    }

    @Test
    fun test2() {
        RandomUtil.reseedThreadLocal(47)
        val input = createTestData(47)
        val reference = input.reference
        val refPositions = input.refPositions
        val data = arrayOf(
            createPair(
                0, reference.getRange(
                    refPositions[GeneType.Diversity]!![0] - 85, refPositions[GeneType.Diversity]!![0] + 10
                ), reference.getRange(
                    refPositions[GeneType.Diversity]!![1], refPositions[GeneType.Diversity]!![1] + 85
                ).reverseComplement
            ),
            createPair(
                1, reference.getRange(
                    refPositions[GeneType.Diversity]!![0] - 135, refPositions[GeneType.Diversity]!![0] - 70
                ), reference.getRange(
                    refPositions[GeneType.Diversity]!![0] - 8, refPositions[GeneType.Diversity]!![0] + 85
                ).reverseComplement
            )
        )
        val testResult = processData(data, input)
        for (al in testResult.assembled) {
            MiXCRTestUtils.printAlignment(al)
            //            System.out.println(input.VJJunction);
            //            System.out.println(al.getFeature(GeneFeature.VJJunction).getSequence());
            Assert.assertTrue(
                input.VJJunction.toString().contains(al.getFeature(GeneFeature.VJJunction)!!.sequence.toString())
            )
        }
    }

    @Test
    fun test2a() {
        RandomUtil.reseedThreadLocal(47)
        val input = createTestData(47)
        val reference = input.reference
        val refPositions = input.refPositions
        var left1 = reference.getRange(
            refPositions[GeneType.Diversity]!![0] - 85, refPositions[GeneType.Diversity]!![0] + 10
        )
        val chars = left1.toString().toCharArray()
        chars[3] = 'a'
        left1 = NucleotideSequence(String(chars))
        val data = arrayOf(
            createPair(
                0, left1, reference.getRange(
                    refPositions[GeneType.Diversity]!![1], refPositions[GeneType.Diversity]!![1] + 85
                ).reverseComplement
            ),
            createPair(
                1, reference.getRange(
                    refPositions[GeneType.Diversity]!![0] - 135, refPositions[GeneType.Diversity]!![0] - 30
                ), reference.getRange(
                    refPositions[GeneType.Diversity]!![0] - 8, refPositions[GeneType.Diversity]!![0] + 85
                ).reverseComplement
            )
        )
        val testResult = processData(data, input)
        for (al in testResult.assembled) {
            MiXCRTestUtils.printAlignment(al)
            //            System.out.println(input.VJJunction);
            //            System.out.println(al.getFeature(GeneFeature.VJJunction).getSequence());
            //            Assert.assertTrue(input.VJJunction.toString().contains(al.getFeature(GeneFeature.VJJunction).getSequence().toString()));
        }
    }

    @Test
    fun test3() {
        for (i in 0..99) {
            RandomUtil.reseedThreadLocal(i.toLong())
            //            System.out.println(i);
            val input = createTestData(i.toLong())
            val reference = input.reference
            val refPositions = input.refPositions
            val data = arrayOf(
                createPair(
                    0, reference.getRange(
                        refPositions[GeneType.Diversity]!![0] - 85, refPositions[GeneType.Diversity]!![0] + 10
                    ), reference.getRange(
                        refPositions[GeneType.Diversity]!![1], refPositions[GeneType.Diversity]!![1] + 85
                    ).reverseComplement
                ),
                createPair(
                    1, reference.getRange(
                        refPositions[GeneType.Diversity]!![0] - 135, refPositions[GeneType.Diversity]!![0] - 70
                    ), reference.getRange(
                        refPositions[GeneType.Diversity]!![0] - 8, refPositions[GeneType.Diversity]!![0] + 85
                    ).reverseComplement
                )
            )
            val testResult = processData(data, input)
            for (al in testResult.assembled) {
                //                printAlignment(al);
                if (al.numberOfTargets() == 1) {
                    //                    System.out.println(input.VJJunction);
                    //                    System.out.println(al.getFeature(GeneFeature.VJJunction).getSequence());
                    Assert.assertTrue(
                        input.VJJunction.toString().contains(
                            al.getFeature(GeneFeature.VJJunction)!!.sequence.toString()
                        )
                    )
                }
            }
        }
    }

    class InputTestData(
        val genes: EnumMap<GeneType, VDJCGene>,
        val germlineRegions: EnumMap<GeneType, NucleotideSequence>,
        val germlineCuts: EnumMap<GeneType, IntArray?>,
        val refPositions: EnumMap<GeneType, IntArray>,
        val VDJunction: NucleotideSequence,
        val DJJunction: NucleotideSequence,
        val reference: NucleotideSequence,
        val VJJunction: NucleotideSequence
    )

    class TestResult(
        val inputReads: Array<PairedRead>,
        val inputAlignments: AlignResult,
        val assembled: ArrayList<VDJCAlignments>
    )

    companion object {
        fun createPair(id: Long, R1: String?, R2: String?): PairedRead {
            return createPair(id, NucleotideSequence(R1), NucleotideSequence(R2))
        }

        fun createPair(id: Long, R1: NucleotideSequence, R2: NucleotideSequence): PairedRead {
            return PairedRead(
                SingleReadImpl(
                    id,
                    NSequenceWithQuality(R1, SequenceQuality.getUniformQuality(25.toByte(), R1.size())),
                    "" + id + "R1"
                ),
                SingleReadImpl(
                    id,
                    NSequenceWithQuality(R2, SequenceQuality.getUniformQuality(25.toByte(), R2.size())),
                    "" + id + "R2"
                )
            )
        }

        fun <V> gtMap(): EnumMap<GeneType, V> {
            return EnumMap(GeneType::class.java)
        }

        fun processData(data: Array<PairedRead>, input: InputTestData): TestResult {
            val params = RunMiXCRAnalysis(*data)
            params.alignerParameters.setAllowPartialAlignments(true)
            val inputAlignments = RunMiXCR.align(params)
            // inputAlignments.report.writeReport(new ReportHelper(System.out));
            // System.out.println("\n");
            for (al in inputAlignments.alignments) {
                for (gt in GeneType.VJC_REFERENCE) {
                    val hits = al.getHits(gt) ?: continue
                    var yes = false
                    for (hit in hits) {
                        if (input.genes[gt]!!.equals(hit.gene)) {
                            yes = true
                            break
                        }
                    }
                    // Assert.assertTrue(yes);
                }

                // if (al.getFeature(GeneFeature.VJJunction) != null)
                //     Assert.assertTrue(input.VJJunction.toString().contains(al.getFeature(GeneFeature.VJJunction).getSequence().toString()));
            }
            val overlappedAlignments = newTempFile()
            VDJCAlignmentsWriter(overlappedAlignments).use { writer ->
                val pParameters = PartialAlignmentsAssemblerParameters.default.let {
                    it.copy(mergerParameters = it.mergerParameters.overrideMinimalIdentity(0.0))
                }
                val assembler = PartialAlignmentsAssembler(
                    pParameters,
                    inputAlignments.parameters.alignerParameters,
                    inputAlignments.usedGenes,
                    true,
                    false,
                    0,
                    InputPort { alignment -> writer.write(alignment!!) }
                )
                inputAlignments.resultReader().use { reader ->
                    writer.inheritHeaderAndFooterFrom(reader)
                    assembler.buildLeftPartsIndex(reader)
                }
                inputAlignments.resultReader().use { reader -> assembler.searchOverlaps(reader) }
                // assembler.writeReport(new ReportHelper(System.out));
                // System.out.println("\n");
                writer.setFooter(MiXCRTestUtils.emptyFooter())
            }
            val readResult = VDJCAlignmentsReader(overlappedAlignments)
            val overlapped = ArrayList<VDJCAlignments>()
            var al: VDJCAlignments?
            while (readResult.take().also { al = it } != null) {
                al?.let(overlapped::add)
            }
            overlappedAlignments.delete()
            return TestResult(data, inputAlignments, overlapped)
        }

        fun createTestData(seed: Long = System.currentTimeMillis()): InputTestData {
            val geneNames = object : EnumMap<GeneType, GeneVariantName>(GeneType::class.java) {
                init {
                    put(GeneType.Variable, GeneVariantName("TRBV20-1*00"))
                    put(GeneType.Diversity, GeneVariantName("TRBD2*00"))
                    put(GeneType.Joining, GeneVariantName("TRBJ2-6*00"))
                    put(GeneType.Constant, GeneVariantName("TRBC2*00"))
                }
            }

            // config
            val rnd: RandomGenerator = RandomUtil.getThreadLocalRandom()
            rnd.setSeed(seed)
            val defaultFeatures = VDJCParametersPresets.getByName("default")
            defaultFeatures.vAlignerParameters.geneFeatureToAlign = GeneFeature.VRegion
            defaultFeatures.dAlignerParameters.setGeneFeatureToAlign(GeneFeature.DRegion)
            defaultFeatures.jAlignerParameters.geneFeatureToAlign = GeneFeature.JRegion

            // used alleles
            val genes = EnumMap<GeneType, VDJCGene>(GeneType::class.java)
            // germline parts of sequences
            val germlineRegions = gtMap<NucleotideSequence>()
            // left, right cut of germline
            val germlineCuts = gtMap<IntArray?>()
            // begin, end positions in assembled sequence
            val refPositions = gtMap<IntArray>()
            // single assembled sequence
            val referenceBuilder = NucleotideSequence.ALPHABET.createBuilder()
            val VDJunction = TestUtil.randomSequence(NucleotideSequence.ALPHABET, 3, 10)
            val DJJunction = TestUtil.randomSequence(NucleotideSequence.ALPHABET, 3, 10)
            for (gt in GeneType.VDJC_REFERENCE) {
                val gene = VDJCLibraryRegistry.getDefault().getLibrary("default", "hs")[geneNames[gt]!!]
                val seq = gene.getFeature(defaultFeatures.getFeatureToAlign(gt)!!)
                var cuts: IntArray? = null
                cuts = when (gt) {
                    GeneType.Variable -> intArrayOf(
                        0, rnd.nextInt(
                            gene.getFeature(GeneFeature.GermlineVCDR3Part)!!.size() - 5
                        )
                    )

                    GeneType.Diversity -> intArrayOf(rnd.nextInt(seq!!.size() / 3), rnd.nextInt(seq.size() / 3))
                    GeneType.Joining -> intArrayOf(
                        rnd.nextInt(
                            gene.getFeature(GeneFeature.GermlineJCDR3Part)!!.size() - 5
                        ), 0
                    )

                    GeneType.Constant -> intArrayOf(0, rnd.nextInt(seq!!.size() / 2))
                }
                val gSeq = seq!!.getRange(cuts[0], seq.size() - cuts[1])
                val positions = IntArray(2)
                positions[0] = referenceBuilder.size()
                referenceBuilder.append(gSeq)
                positions[1] = referenceBuilder.size()
                if (gt == GeneType.Variable) referenceBuilder.append(VDJunction)
                if (gt == GeneType.Diversity) referenceBuilder.append(DJJunction)
                genes[gt] = gene
                germlineCuts[gt] = cuts
                germlineRegions[gt] = gSeq
                refPositions[gt] = positions
            }
            val VJJunction = NucleotideSequence.ALPHABET.createBuilder()
                .append(VDJunction)
                .append(germlineRegions[GeneType.Diversity])
                .append(DJJunction)
                .createAndDestroy()
            val reference = referenceBuilder.createAndDestroy()
            return InputTestData(
                genes,
                germlineRegions,
                germlineCuts,
                refPositions,
                VDJunction,
                DJJunction,
                reference,
                VJJunction
            )
        }
    }
}
