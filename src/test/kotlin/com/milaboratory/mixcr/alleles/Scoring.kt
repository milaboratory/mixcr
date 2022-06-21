package com.milaboratory.mixcr.alleles

import cc.redberry.pipe.CUtils
import com.milaboratory.core.alignment.Aligner
import com.milaboratory.core.alignment.LinearGapAlignmentScoring
import com.milaboratory.core.mutations.Mutation
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence.ALPHABET
import com.milaboratory.mixcr.basictypes.CloneReader
import com.milaboratory.mixcr.basictypes.CloneSetIO
import com.milaboratory.mixcr.util.asMutations
import com.milaboratory.mixcr.util.asSequence
import com.milaboratory.util.TempFileDest
import com.milaboratory.util.TempFileManager
import io.repseq.core.GeneFeature.CDR3
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import io.repseq.core.VDJCLibraryRegistry
import org.junit.Test
import java.io.File
import java.nio.file.Paths
import kotlin.random.Random
import kotlin.system.exitProcess

class Scoring {
    @Test
    fun `just run 2`() {

    }

    @Test
    fun `just run`() {
        val libraryRegistry = VDJCLibraryRegistry.getDefault()
        fun mkReader(name: String): CloneReader = CloneSetIO.mkReader(
            Paths.get("/home/gnefedev/repos/mixcr/src/test/resources/sequences/big/alleles/mixcr/$name.clna"),
            libraryRegistry
        )

        val allDatasets = listOf(
            mkReader("D01_p01_Btot_1"),
            mkReader("D01_p01_Btot_2"),
            mkReader("D01_p02_PBL_1"),
            mkReader("D01_p02_PBL_2"),
            mkReader("D01_p02_PL_1"),
            mkReader("D01_p02_PL_2"),
            mkReader("D01_p02_Bmem_1"),
            mkReader("D01_p02_Bmem_3"),
            mkReader("D01_p03_Bmem_1"),
            mkReader("D01_p03_Bmem_2"),
            mkReader("D01_p03_PBL_1"),
            mkReader("D01_p03_PBL_3"),
            mkReader("D01_p03_pbmc_1")
        )

        val datasetName = "D01_p02_PBL_1__D01_p02_PBL_2"
        val output =
            File("/home/gnefedev/repos/mixcr/src/test/resources/sequences/big/alleles/stats/$datasetName.csv")
        output.delete()
        output.appendText("VGene;mutation;mutationDiversity;withMutationCount;withoutMutationCount;connectionsCount;similarCDR3Count;sumOfCDR3Score;verySimilarCDR3Count;sumOfVerySimilarCDR3Scores;similarCDR3AndHaveCommonMutation\n")

        val datasets = allDatasets
//        val datasets = listOf(
//            mkReader("D01_p01_Btot_1")
//        )
        val tempDest: TempFileDest = TempFileManager.systemTempFolderDestination("for_tests")
        val allelesBuilder = AllelesBuilder(
            FindAllelesParameters.presets.getByName("default")!!,
            datasets,
            tempDest
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

            if (false) {
                val clones = cluster.cluster
//                    .filter { clone ->
//                        val CName = clone.getBestHit(Constant)?.gene?.name
//                        CName != null && (CName.startsWith("IGHG") || CName.startsWith("IGHA"))
//                    }
                    .map { clone ->
                        clone to CloneDescription(
                            clone.getBestHit(geneType).alignments.asSequence()
                                .flatMap { it.absoluteMutations.asSequence() }
                                .asMutations(ALPHABET),
                            clone.getNFeature(CDR3).size(),
                            clone.getBestHit(Joining).gene.geneName
                        )
                    }

                val mutationsWithDiversity = clones
                    .map { it.second }
                    .flatMap { it.mutations.asSequence() }
                    .distinct()
                    .map { mutation ->
                        mutation to clones.asSequence()
                            .map { it.second }
                            .filter { clone -> clone.mutations.asSequence().any { it == mutation } }
                            .map { it.clusterIdentity }
                            .distinct()
                            .count()
                    }
                    .sortedByDescending { it.second }
                    .take(30)

                val CDR3ScoresCache = mutableMapOf<Pair<Int, Int>, Float>()
                val threshold = 4.0

                mutationsWithDiversity.forEach { (mutation, diversity) ->
                    val clonesWithMutation =
                        clones.filter { clone -> clone.second.mutations.asSequence().any { it == mutation } }
                    val clonesWithoutMutation =
                        clones.filter { clone -> clone.second.mutations.asSequence().none { it == mutation } }

                    var connectionsCount = 0
                    var similarCDR3AndHaveCommonMutation = 0
                    val CDR3Scores = mutableListOf<Float>()
                    clonesWithMutation.forEach { (leftClone, left) ->
                        clonesWithoutMutation.forEach { (rightClone, right) ->
                            val commonMutationsCount = left.mutations.asSequence().toSet()
                                .intersect(right.mutations.asSequence().toSet())
                                .size
                            connectionsCount += commonMutationsCount
                            when {
                                leftClone.getBestHit(Joining).gene.name != rightClone.getBestHit(Joining).gene.name -> {}
                                else -> {
                                    val leftCDR3 = leftClone.getNFeature(CDR3)
                                    val rightCDR3 = rightClone.getNFeature(CDR3)
                                    when {
                                        leftCDR3.size() != rightCDR3.size() -> {}
                                        else -> {
                                            val score =
                                                CDR3ScoresCache.computeIfAbsent(leftClone.id to rightClone.id) {
                                                    Aligner.alignGlobal(
                                                        LinearGapAlignmentScoring.getNucleotideBLASTScoring(),
                                                        leftCDR3,
                                                        rightCDR3
                                                    ).score / leftCDR3.size()
                                                }
                                            if (score > threshold && commonMutationsCount > 0) {
                                                similarCDR3AndHaveCommonMutation++
                                            }
                                            CDR3Scores += score
                                        }
                                    }
                                }
                            }
                        }
                    }

                    output.appendText(
                        "$geneId;${Mutation.encode(mutation, ALPHABET)};$diversity;${clonesWithMutation.size};" +
                            "${clonesWithoutMutation.size};$connectionsCount;" +
                            "${CDR3Scores.size};${CDR3Scores.sum()};" +
                            "${CDR3Scores.filter { it > threshold }.size};" +
                            "${CDR3Scores.filter { it > threshold }.sum()};" +
                            "$similarCDR3AndHaveCommonMutation\n"
                    )
                }
                return@forEach
            }

            if (false) {
                if (geneId == "IGHV4-59*00") {
                    val clones = cluster.cluster
                        .map { clone ->
                            clone to CloneDescription(
                                clone.getBestHit(geneType).alignments.asSequence()
                                    .flatMap { it.absoluteMutations.asSequence() }
                                    .asMutations(ALPHABET),
                                clone.getNFeature(CDR3).size(),
                                clone.getBestHit(Joining).gene.geneName
                            )
                        }

                    val mutations = clones.map { it.second }.flatMap { it.mutations.asSequence() }.distinct()
                        .sortedByDescending { mutation ->
                            clones.asSequence()
                                .map { it.second }
                                .filter { clone -> clone.mutations.asSequence().any { it == mutation } }
                                .map { it.clusterIdentity }
                                .distinct()
                                .count()
                        }
                        .take(8)

                    val colours = listOf(
                        "red",
                        "blue",
                        "yellow",
                        "grey",
                        "green",
                        "brown",
                        "gold",
                        "silver",
                        "orange"
                    )

                    val coloursByMutations = mutations.withIndex()
                        .associateBy({ it.value }, { colours[it.index] })

                    val alleleMutations = Mutations(ALPHABET, "ST417C,SG420A").asSequence().toSet()
//                val alleleMutations = Mutations(ALPHABET, "SG303A").asSequence().toSet()

                    val subset = clones
//                    .filter { it.second.mutations.size() >= 5 }
                        .shuffled(Random(0)).take(20)

                    println("""rectangle "With allele" {""")
                    subset
                        .filter { clonePair -> clonePair.second.mutations.asSequence().any { it in alleleMutations } }
//                        .filter { clonePair -> clonePair.second.mutations.asSequence().any { it in mutations && it !in alleleMutations } }
                        .forEach { println("(${it.first.id})") }
                    println("}")
                    println("""rectangle "Without allele" {""")
                    subset
                        .filter { clonePair -> clonePair.second.mutations.asSequence().all { it !in alleleMutations } }
                        .filter { clonePair -> clonePair.second.mutations.asSequence().any { it in mutations } }
                        .forEach { println("(${it.first.id})") }
                    println("}")

                    subset.indices.forEach { i ->
                        val (leftClone, left) = subset[i]
                        for (j in 0 until i) {
                            val (rightClone, right) = subset[j]
                            val score = when {
                                leftClone.getBestHit(Joining).gene.name != rightClone.getBestHit(Joining).gene.name -> 0
                                else -> {
                                    val leftCDR3 = leftClone.getNFeature(CDR3)
                                    val rightCDR3 = rightClone.getNFeature(CDR3)
                                    when {
                                        leftCDR3.size() != rightCDR3.size() -> 0
                                        else -> Aligner.alignGlobal(
                                            LinearGapAlignmentScoring.getNucleotideBLASTScoring(),
                                            leftCDR3,
                                            rightCDR3
                                        ).score / leftCDR3.size()
                                    }
                                }
                            }

                            mutations
//                            .filter { it !in alleleMutations }
                                .forEach { mutation ->
                                    if (left.mutations.asSequence().any { it == mutation } &&
                                        right.mutations.asSequence().any { it == mutation }) {
                                        val colour = coloursByMutations[mutation]!!
                                        println("(${leftClone.id}) --- (${rightClone.id}) #$colour : $score")
                                    }
                                }
                        }
                    }

                    println(coloursByMutations.mapKeys { Mutation.encode(it.key, ALPHABET) })
                }
                return@forEach
            }
            val expectations = mapOf(
                "IGHV3-11*00" to "00, 00_C1G_C36A_A37C_G106A_A107G_C108T_C111T_T112G_C114A_G119A_A124G_G237A_C300T_G303T",
                "IGHV1-2*00" to "00, 00_A220T (wrong?)",
                "IGHV3-7*00" to "00, 00_T300C, 00_C36A_G48A_T90C_C108T_T112G_G113A_G114A_G119A_G159T_G160T_C162A_A163T_A168T_A170G_G171T_C172A_A173G_A174T_G175A_A176G_A186T_G190A_A191C_G192C_A194T_T201C_T203C_G204A_G303T",
                "IGHV3-33*00" to "00, 00_G75C_G113C_C114T_G150A_G170C_G171A_T201C_C288T, 00_G75C_G113C_C114T_G170C_G171A_T189C_T201C_C288T",
                "IGHV3-15*00" to "00, 00_A81T_G119A_T159C",
                "IGHV4-59*00" to "00, ST417CSG420A",
                "IGHV3-48*00" to "00_G48A_C108T_A112G_G113A_C114A_A184G_T243C_A287C_G303T",
                "IGHV4-28*00" to "00_A50G_C51G_T82G_A83G_G118A_C120T_A124G_G129C_A147G_T163G_C165A_T172C_T196A_G231A_C242A_T290C, 00_C51G_A103G_A109T_G113A_G114C_A147G_T163A_A164G_C165T_T172C_G231A_T290C_G291A",
                "IGHV4-39*00" to "00_C4G_A70G_G82T_G83A_A103G_C129G_T172C_C234A_T300C",
                "IGHV4-55*00" to "00_A50G_T65C_A83G_G106A_A117G_T119G_C120T_C169T_T196A_A223G_G231A_C234A_C242A_A263C",
            )
            if (true || geneId in expectations.keys) {
                val clones = cluster.cluster
//                    .filter { it.getBestHit(Constant) != null }
//                    .filterNot { it.getBestHit(Constant).gene.geneName.startsWith("IGHM") }
                    .map { clone ->
                        CloneDescription(
                            clone.getBestHit(geneType).alignments.asSequence()
                                .flatMap { it.absoluteMutations.asSequence() }
                                .asMutations(ALPHABET),
                            clone.getNFeature(CDR3).size(),
                            clone.getBestHit(Joining).gene.geneName
                        )
                    }
                println(geneId)
                println("expect: " + (expectations[geneId] ?: "?"))
                val allelesSearcher = TIgGERAllelesSearcher(
                    datasets[0].assemblerParameters.cloneFactoryParameters.vParameters.scoring,
                    cluster.cluster.first().getBestHit(geneType).alignments[0].sequence1,
                    FindAllelesParameters.presets.getByName("default")!!
                )
                println("actual: " + allelesSearcher.search(clones).map { "'${it.allele.encode(",")}'" })
                println()
                if (false && geneId.startsWith("IGHV4-28")) {
                    clones
                        .groupBy { it.mutations }
                        .mapValues { (_, value) -> value.size to value.map { it.clusterIdentity }.distinct().count() }
                        .entries
                        .sortedBy { it.key.size() }
                        .forEach { (key, value) ->
                            println(
                                "c: ${
                                    String.format(
                                        "%3d",
                                        value.first
                                    )
                                } d: ${String.format("%2d", value.second)} ${key.encode(",")}"
                            )
                        }
                    exitProcess(0)
                }
                return@forEach

                if (geneId == "IGHV4-59*00") {
                    val allelesScoring = AllelesScoring(
                        "IGHV4-59*00",
                        cluster.cluster.first().getBestHit(geneType).alignments[0].sequence1,
                        allDatasets[0].assemblerParameters.cloneFactoryParameters.vParameters.scoring
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
                        allDatasets[0].assemblerParameters.cloneFactoryParameters.vParameters.scoring
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
                        allDatasets[0].assemblerParameters.cloneFactoryParameters.vParameters.scoring
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
