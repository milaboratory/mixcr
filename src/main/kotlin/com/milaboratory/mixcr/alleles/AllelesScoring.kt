package com.milaboratory.mixcr.alleles

import com.milaboratory.core.alignment.AlignmentScoring
import com.milaboratory.core.alignment.AlignmentUtils
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.miplots.writePDF
import com.milaboratory.mixcr.util.asSequence
import jetbrains.letsPlot.geom.geomLine
import jetbrains.letsPlot.geom.geomPoint
import jetbrains.letsPlot.ggsize
import jetbrains.letsPlot.intern.Plot
import jetbrains.letsPlot.letsPlot
import kotlin.io.path.Path
import kotlin.math.log10
import kotlin.math.pow

private const val minDiversity = 10
private const val window = 3

class AllelesScoring(
    private val geneId: String,
    private val sequence1: NucleotideSequence,
    private val scoring: AlignmentScoring<NucleotideSequence>,
) {
    private var counter = 1

    fun score(
        clones: List<CloneDescription>,
        alleles: List<Mutations<NucleotideSequence>>,
        expectedAlleles: List<Mutations<NucleotideSequence>>
    ): Double {
        val mutationsInRightAlleles = expectedAlleles.flatMap { it.asSequence() }.toSet()
        val distributedClones = alleles.associateWith { mutableListOf<CloneDescription>() }
        val alleleBases = alleles.associateWith { allele -> allele.mutate(sequence1) }

        clones.forEach { clone ->
            val nearestAllele = alleles.maxByOrNull { allele ->
                AlignmentUtils.calculateScore(
                    alleleBases.getValue(allele),
                    allele.invert().combineWith(clone.mutations),
                    scoring
                )
            }!!
            distributedClones.getValue(nearestAllele) += clone.copy(
                mutations = nearestAllele.invert().combineWith(clone.mutations)
            )
        }

        val plots = mutableListOf<Plot>()

        plots += drawPlot(clones, "Full", mutationsInRightAlleles)
//        alleles.forEach { allele ->
//            plots += drawPlot(
//                distributedClones.getValue(allele),
//                allele.toString(),
//                mutationsInRightAlleles
//            )
//        }
        val rebasedClones = alleles.flatMap { allele -> distributedClones.getValue(allele) }
//        plots += drawPlot(rebasedClones, alleles.joinToString { it.toString() }, mutationsInRightAlleles)
        plots += drawPlot(rebasedClones, "filtered", mutationsInRightAlleles)

        val number = counter++
        writePDF(
            Path("/home/gnefedev/repos/mixcr/src/test/resources/sequences/big/report/${geneId}_$number.pdf"),
            plots
        )

        plots.clear()

        plots += drawTigerStandard(clones, "Full", mutationsInRightAlleles)
        alleles.forEach { allele ->
            plots += drawTigerStandard(
                distributedClones.getValue(allele),
                allele.toString(),
                mutationsInRightAlleles
            )
        }

        writePDF(
            Path("/home/gnefedev/repos/mixcr/src/test/resources/sequences/big/report/${geneId}_tiger_standard_$number.pdf"),
            plots
        )

        plots.clear()

        plots += drawTiger(clones, "Full", mutationsInRightAlleles)
        alleles.forEach { allele ->
            plots += drawTiger(
                distributedClones.getValue(allele),
                allele.toString(),
                mutationsInRightAlleles
            )
        }

        writePDF(
            Path("/home/gnefedev/repos/mixcr/src/test/resources/sequences/big/report/${geneId}_tiger_$number.pdf"),
            plots
        )
        return 0.0
    }

    private fun drawPlot(
        clones: List<CloneDescription>,
        name: String,
        mutationsInAlleles: Set<Int>
    ): Plot {
        val mutations = clones.flatMap { it.mutations.asSequence() }.distinct()
            .filter { mutation ->
                clones.filter { clone -> clone.mutations.asSequence().any { it == mutation } }
                    .map { it.clusterIdentity }
                    .distinct()
                    .count() > minDiversity
            }

//        val diversityByMutationsCount = clones
//            .groupBy { it.mutations.size() }
//            .mapValues { (_, value) -> value.map { it.clusterIdentity }.distinct().count() }
//        val allPoints = mutations
//            .associateWith { mutation ->
//                val clonesWithMutation = clones.filter { clone -> clone.mutations.asSequence().any { it == mutation } }
//                clonesWithMutation
//                    .groupBy { it.mutations.size() }
//                    .mapValues { (_, value) -> value.map { it.clusterIdentity }.distinct().count() }
//            }

        val diversityByMutationsCount = diversityByMutationsCountWithWindow(clones)
        val allPoints = mutations
            .associateWith { mutation ->
                val clonesWithMutation = clones.filter { clone -> clone.mutations.asSequence().any { it == mutation } }
                diversityByMutationsCountWithWindow(clonesWithMutation)
//                    .mapValues {
//                        if (it.key <= 5) 0 else it.value
//                    }
//                    .mapValues { if (it.value == 0) 0.0 else ln(it.value.toDouble()) }
            }

        val reformattedPoints = allPoints.flatMap { (mutation, diversityByCount) ->
            diversityByCount.entries.map { (count, diversity) ->
                Triple(mutation, diversity, count)
            }
        }
            .groupBy({ (_, _, count) -> count }, { (mutation, diversity, _) -> mutation to diversity })

        val meanValues = reformattedPoints.mapValues { (count, mutationToDiversity) ->
            mutationToDiversity.map { it.second }.average() /*/ diversityByMutationsCount[count]!!.toDouble()*/
        }
            .filterKeys { it <= 20 }

        val medians = reformattedPoints.mapValues { (count, mutationToDiversity) ->
            median(mutationToDiversity.map { it.second }) /*/ diversityByMutationsCount[count]!!.toDouble()*/
        }
            .filterKeys { it <= 20 }

        val lines = allPoints.map { (mutation, points) ->
            mutation to points.map { (count, diversity) ->
                count to diversity /*/ diversityByMutationsCount[count]!!.toDouble()*/
            }
                .toMap()
                .filterKeys { it <= 20 }
        }.toMap()

        val dispersion = lines.values
            .flatMap { it.entries }
            .groupBy({ it.key }, { it.value })
            .mapValues { (count, points) ->
                val meanValue = meanValues[count]!!
                points.map { (it - meanValue).pow(2) }.average()
            }

        val deltas = lines.mapValues { (_, line) ->
            line.map { (count, diversity) ->
                (diversity - meanValues[count]!!).pow(2)
            }.average().pow(0.5)
        }

        val probabilities = lines.mapValues { (_, line) ->
            line.map { (count, diversity) ->
                val standardDeviation = dispersion[count]!!.pow(0.5)
                val result = gauseFunction(meanValues[count]!!, standardDeviation, diversity.toDouble())
                if (result.isNaN()) 1.0 else result
            }.fold(1.0) { a, b -> a * b }
        }


        val mutationsWithTopDeltas = deltas.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(10)
            .toSet()

        val label =
            "$name ${(deltas.maxOf { it.value } * 100).toInt()} ${log10(probabilities.minOf { it.value }).toInt()}"
        var plot = letsPlot {
            x = label
            y = "part"
        } + ggsize(500, 250)

        plot += geomLine(
            data = mapOf(
                label to meanValues.keys,
                "part" to meanValues.values
            ),
            color = 2
        )

//        plot += geomLine(
//            data = mapOf(
//                label to medians.keys,
//                "part" to medians.values
//            ),
//            color = 2
//        )

//        plot += geomLine(
//            data = mapOf(
//                label to dispersion.keys,
//                "part" to dispersion.values
//            ),
//            color = 3
//        )

        lines
//            .filterKeys { it in mutationsWithTopDeltas }
            .forEach { (mutation, result) ->
                val lineType = if (mutation in mutationsInAlleles) 1 else 3
                plot += geomLine(
                    data = mapOf(
                        label to result.keys,
                        "part" to result.values
                    ),
                    linetype = lineType
                )
            }
        return plot
    }

    private fun gauseFunction(meanValue: Double, standardDeviation: Double, x: Double): Double =
        (1 / standardDeviation * (2 * Math.PI).pow(0.5)) *
            Math.E.pow(-0.5 * ((x - meanValue) / standardDeviation).pow(2))

    private fun median(data: List<Number>, portion: Double = 0.5): Double {
        if (data.size == 1) return data.first().toDouble()
        val sorted = data.map { it.toDouble() }.sorted()
        return (sorted[(data.size * portion).toInt() - 1] + sorted[(data.size * portion).toInt()]) / 2.0
    }

    private fun drawPlotOld3(
        clones: List<CloneDescription>,
        label: String,
        alleles: List<Mutations<NucleotideSequence>>
    ): Plot {
        val mutations = clones.flatMap { it.mutations.asSequence() }.distinct()
            .filter { mutation ->
                clones.filter { clone -> clone.mutations.asSequence().any { it == mutation } }
                    .map { it.clusterIdentity }
                    .distinct()
                    .count() > minDiversity
            }
        var plot = letsPlot {
            x = label
            y = "part"
        } + ggsize(500, 250)
        val diversityByMutationsCount = diversityByMutationsCountWithWindow(clones)
        val mutationsInAlleles = alleles.flatMap { it.asSequence() }.toSet()
        val allPoints = mutations
            .associateWith { mutation ->
                val clonesWithMutation = clones.filter { clone -> clone.mutations.asSequence().any { it == mutation } }
                diversityByMutationsCountWithWindow(clonesWithMutation)
                    .filter { it.key < 20 }
            }

        val reformattedPoints = allPoints.flatMap { (mutation, diversityByCount) ->
            diversityByCount.entries.map { (count, diversity) ->
                Triple(mutation, diversity, count)
            }
        }
            .groupBy({ (_, _, count) -> count }, { (mutation, diversity, _) -> mutation to diversity })

        allPoints.forEach { (mutation, points) ->
            val result = points.mapNotNull { (count, diversity) ->
                if (reformattedPoints[count]!!.size < 3) return@mapNotNull null
                val medianDiversity = median(reformattedPoints[count]!!
                    .map { it.second })
                count to (diversity - medianDiversity) / diversityByMutationsCount[count]!!.toDouble()
            }
                .toMap()
            plot += geomLine(
                data = mapOf(
                    label to result.keys,
                    "part" to result.values
                ),
                linetype = if (mutation in mutationsInAlleles) 1 else 3
            )
        }
        return plot
    }

    private fun diversityByMutationsCountWithWindow(clones: List<CloneDescription>): Map<Int, Int> {
        val clonesByMutationsCount = clones.groupBy { it.mutations.size() }
        val diversityByMutationsCount = (0..clones.maxOf { it.mutations.size() })
            .associateWith { count ->
                (count..(count + window))
                    .flatMap { clonesByMutationsCount[it] ?: emptyList() }
                    .map { it.clusterIdentity }
                    .distinct().count()
            }
        return diversityByMutationsCount
    }

    private fun countByMutationsCountWithWindow(clones: List<CloneDescription>): Map<Int, Int> {
        val clonesByMutationsCount = clones.groupBy { it.mutations.size() }
        val countsByMutationsCount = (0..clones.maxOf { it.mutations.size() })
            .associateWith { count ->
                (count..(count + window))
                    .flatMap { clonesByMutationsCount[it] ?: emptyList() }
                    .count()
            }
        return countsByMutationsCount
    }

    private fun drawPlotOld2(
        clones: List<CloneDescription>,
        label: String,
        alleles: List<Mutations<NucleotideSequence>>
    ): Plot {
        val mutations = clones.flatMap { it.mutations.asSequence() }.distinct()
            .filter { mutation ->
                clones.filter { clone -> clone.mutations.asSequence().any { it == mutation } }
                    .map { it.clusterIdentity }
                    .distinct()
                    .count() > minDiversity
            }
        var plot = letsPlot {
            x = label
            y = "part"
        } + ggsize(500, 250)
        val clonesByMutationsCount = clones.groupBy { it.mutations.size() }
        val diversityByMutations = clonesByMutationsCount
            .mapValues { (_, value) -> value.map { it.clusterIdentity }.distinct().count() }
        val mutationsInAlleles = alleles.flatMap { it.asSequence() }.toSet()
        val allPoints = mutations
            .associateWith { mutation ->
                clones.filter { clone -> clone.mutations.asSequence().any { it == mutation } }
                    .groupBy { it.mutations.size() }
                    .mapValues { (_, value) ->
                        value.map { it.clusterIdentity }.distinct().count()
                    }
                    .filter { it.key < 20 }
            }

        val reformattedPoints = allPoints.flatMap { (mutation, diversityByCount) ->
            diversityByCount.entries.map { (count, diversity) ->
                Triple(mutation, diversity, count)
            }
        }
            .groupBy({ (_, _, count) -> count }, { (mutation, diversity, _) -> mutation to diversity })

        allPoints.forEach { (mutation, points) ->
            val result = points.mapNotNull { (count, diversity) ->
                val nextByDiversity = reformattedPoints[count]!!
                    .filter { it.first != mutation }
                    .maxOfOrNull { it.second }
                when (nextByDiversity) {
                    null -> null
                    else -> count to (diversity - nextByDiversity) / diversityByMutations[count]!!.toDouble()
                }
            }
                .toMap()
            plot += geomLine(
                data = mapOf(
                    label to result.keys,
                    "part" to result.values
                ),
                linetype = if (mutation in mutationsInAlleles) 1 else 3
            )
        }
        return plot
    }

    private fun drawPlotOld(clones: List<CloneDescription>, label: String): Plot {
        val data = clones
            .groupBy { it.mutations.size() }
            .flatMap { (mutationsCount, clones) ->
                clones
                    .groupBy { it.mutations }
                    .map { (mutations, value) ->
                        mutationsCount to
                            value.map { it.clusterIdentity }.distinct().count()
                    }
            }
//            .map { (key, value) -> key to value
//                .groupBy { it.mutations }
//                .maxOf { it.value.map { it.clusterIdentity }.distinct().count() }
//            }
//            .map { (key, value) -> key to value.asSequence().map { it.clusterIdentity }.distinct().count() }
//            .mapValues { (_, value) -> value.count() }
            .filter { it.first <= 20 }
        val plotData = mapOf(
            "count" to data.map { it.first },
            label to data.map { it.second }
        )
        val plot = letsPlot(plotData) {
            x = "count"
            y = label
        }
        return plot + ggsize(500, 250) + geomPoint()/* + geomLine()*/
    }

    private fun drawTiger(
        clones: List<CloneDescription>,
        label: String,
        mutationsInAlleles: Set<Int>
    ): Plot {
        val mutations = clones.flatMap { it.mutations.asSequence() }.distinct()
            .filter { mutation ->
                clones.filter { clone -> clone.mutations.asSequence().any { it == mutation } }
                    .map { it.clusterIdentity }
                    .distinct()
                    .count() > minDiversity
            }
        var plot = letsPlot {
            x = label
            y = "part"
        } + ggsize(500, 250)
        val diversityByMutations = clones.groupBy { it.mutations.size() }
            .mapValues { (_, value) -> value.map { it.clusterIdentity }.distinct().count() }
        for (mutation in mutations) {
            val points = clones.filter { clone -> clone.mutations.asSequence().any { it == mutation } }
                .groupBy { it.mutations.size() }
                .mapValues { (key, value) ->
                    value.map { it.clusterIdentity }.distinct().count() / diversityByMutations[key]!!.toDouble()
                }
                .filter { it.key < 20 }
            plot += geomLine(
                data = mapOf(
                    label to points.keys,
                    "part" to points.values
                ),
                linetype = if (mutation in mutationsInAlleles) 1 else 3
            )
        }
        return plot
    }

    private fun drawTigerStandard(
        clones: List<CloneDescription>,
        label: String,
        mutationsInAlleles: Set<Int>
    ): Plot {
        val mutations = clones.flatMap { it.mutations.asSequence() }.distinct()
            .filter { mutation ->
                clones.filter { clone -> clone.mutations.asSequence().any { it == mutation } }
                    .map { it.clusterIdentity }
                    .distinct()
                    .count() > minDiversity
            }
        var plot = letsPlot {
            x = label
            y = "part"
        } + ggsize(500, 250)
        val countByMutations = clones.groupBy { it.mutations.size() }
            .mapValues { it.value.size }
        for (mutation in mutations) {
            val points = clones.filter { clone -> clone.mutations.asSequence().any { it == mutation } }
                .groupBy { it.mutations.size() }
                .mapValues { it.value.size / countByMutations[it.key]!!.toDouble() }
                .filter { it.key < 20 }
            plot += geomLine(
                data = mapOf(
                    label to points.keys,
                    "part" to points.values
                ),
                linetype = if (mutation in mutationsInAlleles) 1 else 3
            )
        }
        return plot
    }
}
