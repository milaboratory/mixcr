package com.milaboratory.mixcr.alleles

import cc.redberry.pipe.CUtils
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence.ALPHABET
import com.milaboratory.mixcr.basictypes.CloneSetIO
import com.milaboratory.mixcr.util.asMutations
import com.milaboratory.mixcr.util.asSequence
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import io.repseq.core.VDJCLibraryRegistry
import org.junit.Test
import java.nio.file.Paths

class Scoring {
    @Test
    fun `just run 2`() {

    }

    @Test
    fun `just run`() {
        val libraryRegistry = VDJCLibraryRegistry.getDefault()
        val datasets = listOf(
            CloneSetIO.mkReader(
                Paths.get("/home/gnefedev/repos/mixcr/src/test/resources/sequences/big/alleles/mixcr/D01_p01_Btot_1.clna"),
                libraryRegistry
            ),
            CloneSetIO.mkReader(
                Paths.get("/home/gnefedev/repos/mixcr/src/test/resources/sequences/big/alleles/mixcr/D01_p01_Btot_2.clna"),
                libraryRegistry
            ),
            CloneSetIO.mkReader(
                Paths.get("/home/gnefedev/repos/mixcr/src/test/resources/sequences/big/alleles/mixcr/D01_p02_PBL_1.clna"),
                libraryRegistry
            ),
            CloneSetIO.mkReader(
                Paths.get("/home/gnefedev/repos/mixcr/src/test/resources/sequences/big/alleles/mixcr/D01_p02_PBL_2.clna"),
                libraryRegistry
            ),
            CloneSetIO.mkReader(
                Paths.get("/home/gnefedev/repos/mixcr/src/test/resources/sequences/big/alleles/mixcr/D01_p02_PL_1.clna"),
                libraryRegistry
            ),
            CloneSetIO.mkReader(
                Paths.get("/home/gnefedev/repos/mixcr/src/test/resources/sequences/big/alleles/mixcr/D01_p02_PL_2.clna"),
                libraryRegistry
            ),
            CloneSetIO.mkReader(
                Paths.get("/home/gnefedev/repos/mixcr/src/test/resources/sequences/big/alleles/mixcr/D01_p03_Bmem_1.clna"),
                libraryRegistry
            ),
            CloneSetIO.mkReader(
                Paths.get("/home/gnefedev/repos/mixcr/src/test/resources/sequences/big/alleles/mixcr/D01_p03_Bmem_2.clna"),
                libraryRegistry
            ),
            CloneSetIO.mkReader(
                Paths.get("/home/gnefedev/repos/mixcr/src/test/resources/sequences/big/alleles/mixcr/D01_p03_PBL_1.clna"),
                libraryRegistry
            ),
            CloneSetIO.mkReader(
                Paths.get("/home/gnefedev/repos/mixcr/src/test/resources/sequences/big/alleles/mixcr/D01_p03_PBL_3.clna"),
                libraryRegistry
            ),
            CloneSetIO.mkReader(
                Paths.get("/home/gnefedev/repos/mixcr/src/test/resources/sequences/big/alleles/mixcr/D01_p03_pbmc_1.clna"),
                libraryRegistry
            )
        )
        val allelesBuilder = AllelesBuilder(
            FindAllelesParametersPresets.getByName("default")!!,
            datasets
        )
        val geneType = Variable
        val sortedClones = allelesBuilder.sortClonotypes().getSortedBy(geneType)
        val clusters = allelesBuilder.buildClusters(sortedClones, geneType)
        CUtils.it(clusters).forEach { cluster ->
            val geneId = cluster.cluster[0].getBestHit(geneType).gene.name
//            if (geneId == "IGHV1-69*00") {
//                //tree alleles, count 3430
//                //01, 04, 07
//                //[], SA376G, SG162C,SG304A,ST319C,SC326T
//                cluster.cluster
//                    .map { it.getBestHit(Variable).alignments[0].absoluteMutations }
//                    .groupBy { it }
//                    .mapValues { it.value.size }
//                    .entries
//                    .sortedBy { it.key.size() }
//                    .forEach { println("${it.value} ${it.key.encode(",")}") }
//            }
//            if (geneId == "IGHV1-2*00") {
//                //two alleles, count 1450
//                //02, 04
//                //[], [S355:A->T]
//                cluster.cluster
//                    .map { it.getBestHit(Variable).alignments[0].absoluteMutations }
//                    .groupBy { it }
//                    .mapValues { it.value.size }
//                    .entries
//                    .sortedBy { it.key.size() }
//                    .forEach { println("${it.value} ${it.key.encode(",")}") }
//            }
//            if (geneId == "IGHV4-59*00") {
//                //two alleles, count 1764
//                //01, 08
//                //[], ST417C,SG420A
//                cluster.cluster
//                    .map { it.getBestHit(Variable).alignments[0].absoluteMutations }
//                    .groupBy { it }
//                    .mapValues { it.value.size }
//                    .entries
//                    .sortedBy { it.key.size() }
//                    .forEach { println("${it.value} ${it.key.encode(",")}") }
//            }
            if (geneId == "IGHV1-2*00" || geneId == "IGHV1-69*00" || geneId == "IGHV4-59*00") {
                val clones = cluster.cluster
//                    .filter { it.getBestHit(Constant) != null }
//                    .filterNot { it.getBestHit(Constant).gene.geneName.startsWith("IGHM") }
                    .map { clone ->
                        CloneDescription(
                            clone.getBestHit(geneType).alignments.asSequence()
                                .flatMap { it.absoluteMutations.asSequence() }
                                .asMutations(ALPHABET),
                            clone.getNFeature(GeneFeature.CDR3).size(),
                            clone.getBestHit(Joining).gene.geneName
                        )
                    }
                println(geneId)
                println(TIgGERAllelesSearcher().search(clones))
                println()
                return@forEach

                if (geneId == "IGHV4-59*00") {
                    val allelesScoring = AllelesScoring(
                        "IGHV4-59*00",
                        cluster.cluster.first().getBestHit(geneType).alignments[0].sequence1,
                        datasets[0].assemblerParameters.cloneFactoryParameters.vParameters.scoring
                    )
                    val expectedAlleles = listOf(
                        Mutations(ALPHABET, ""),
                        Mutations(ALPHABET, "ST417C")
                    )
                    println(
                        allelesScoring.score(
                            clones,
                            expectedAlleles,
                            expectedAlleles
                        )
                    )
                }
                if (geneId == "IGHV1-69*00") {
                    val allelesScoring = AllelesScoring(
                        "IGHV1-69*00",
                        cluster.cluster.first().getBestHit(geneType).alignments[0].sequence1,
                        datasets[0].assemblerParameters.cloneFactoryParameters.vParameters.scoring
                    )
                    val expectedAlleles = listOf(
                        Mutations(ALPHABET, ""),
                        Mutations(ALPHABET, "SA376G"),
                        Mutations(ALPHABET, "SG162C,SG304A,ST319C,SC326T")
                    )
                    println(
                        allelesScoring.score(
                            clones,
                            expectedAlleles,
                            expectedAlleles
                        )
                    )
                    println(
                        allelesScoring.score(
                            clones,
                            listOf(
                                Mutations(ALPHABET, ""),
                                Mutations(ALPHABET, "SA376G"),
                            ),
                            expectedAlleles
                        )
                    )
                    println(
                        allelesScoring.score(
                            clones,
                            listOf(
                                Mutations(ALPHABET, ""),
                                Mutations(ALPHABET, "SG162C,SG304A,ST319C,SC326T")
                            ),
                            expectedAlleles
                        )
                    )
                    println(
                        allelesScoring.score(
                            clones,
                            listOf(
                                Mutations(ALPHABET, ""),
                                Mutations(ALPHABET, "SA376G"),
                                Mutations(ALPHABET, "SG162C,SG304A,ST319C")
                            ),
                            expectedAlleles
                        )
                    )
                }
                if (geneId == "IGHV1-2*00") {
                    val allelesScoring = AllelesScoring(
                        "IGHV1-2*00",
                        cluster.cluster.first().getBestHit(geneType).alignments[0].sequence1,
                        datasets[0].assemblerParameters.cloneFactoryParameters.vParameters.scoring
                    )
                    val expectedAlleles = listOf(
                        Mutations(ALPHABET, "SA355T"),
                        Mutations(ALPHABET, "")
                    )
                    println(
                        allelesScoring.score(
                            clones,
                            expectedAlleles,
                            expectedAlleles
                        )
                    )
                    println(
                        allelesScoring.score(
                            clones,
                            listOf(
                                Mutations(ALPHABET, "SA355T")
                            ),
                            expectedAlleles
                        )
                    )
                    println(
                        allelesScoring.score(
                            clones,
                            listOf(
                                Mutations(ALPHABET, "SA355T,SA450G"),
                                Mutations(ALPHABET, "")
                            ),
                            expectedAlleles
                        )
                    )
                }
            }
        }
    }
}
