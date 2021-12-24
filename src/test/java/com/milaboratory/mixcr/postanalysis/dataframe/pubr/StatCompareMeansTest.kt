package com.milaboratory.mixcr.postanalysis.dataframe.pubr

import com.milaboratory.mixcr.postanalysis.dataframe.toPDF
import com.milaboratory.mixcr.postanalysis.dataframe.writePDF
import jetbrains.datalore.plot.base.stat.math3.mean
import jetbrains.letsPlot.Pos
import jetbrains.letsPlot.elementRect
import jetbrains.letsPlot.geom.geomBoxplot
import jetbrains.letsPlot.geom.geomPoint
import jetbrains.letsPlot.geom.geomText
import jetbrains.letsPlot.letsPlot
import jetbrains.letsPlot.theme
import org.apache.commons.math3.distribution.BinomialDistribution
import org.apache.commons.math3.stat.StatUtils
import org.apache.commons.math3.stat.inference.TTest
import org.jetbrains.kotlinx.dataframe.api.toMap
import org.junit.Test
import java.nio.file.Paths
import kotlin.random.Random
import kotlin.random.asJavaRandom

/**
 *
 */
class StatCompareMeansTest {
    @Test
    fun name() {
        val rnd = Random(100).asJavaRandom()
        val d = BinomialDistribution(100, 0.1)
//        val a = (0 until 111000).map { 1.0 * d.sample() }.toDoubleArray()
//        val b = (0 until 111000).map { 1.0 * d.sample() }.toDoubleArray()
        val a = (0 until 111000).map { it * 1.0 }.toDoubleArray()
        val b = (0 until 111000).map { d.sample() * 1.0 }.toDoubleArray()
        println(mean(a))
        println(mean(b))
        println(StatUtils.variance(a))
        println(StatUtils.variance(b))
        println(a.minOrNull())
        println(a.maxOrNull())
        println(b.minOrNull())
        println(b.maxOrNull())
        println(TTest().tTest(a, b))
    }


    @Test
    fun test1() {

        val Y = "Y"
        val X = "X"
        val G = "G"

        val data = CompareMeansTest.rndData(
            "Y" to Rnd,
            "X" to Category(10),
            "G" to Category(2),
            len = 100
        )

        var plt = letsPlot(data.toMap()) {
            x = X
            y = Y
        }

        plt += geomBoxplot {
            fill = X
        }

        plt += geomPoint(
            position = Pos.jitterdodge,
            shape = 21,
            color = "black"
        ) {
            color = X
            fill = X
        }


        plt += theme(panelGrid = "blank", panelBackground = elementRect(color = "black"))
            .legendPositionTop()

        val overall = plt + statCompareMeans()
        val refGroupAll = plt + statCompareMeans(method = TestMethod.TTest, refGroup = RefGroup.all)
        val refGroupA = plt + statCompareMeans(method = TestMethod.TTest, refGroup = RefGroup.of("A"))


        writePDF(
            Paths.get("scratch/bp.pdf"),
            overall.toPDF(),
            refGroupAll.toPDF(),
            refGroupA.toPDF()
        )
    }

    @Test
    fun test2() {
        val data3 = mapOf(
            "category" to listOf("1", "2", "3", "1", "2", "3", "1", "2", "3"),
            "category2" to listOf("1", "2", "3", "1", "2", "3", "1", "2", "3"),
            "value" to listOf(1, 2, 3, 4, 7, 8, 11, 0, 1)
        )

        var p = letsPlot(data3) {
            x = "category"
            y = "value"
        }

        p += geomBoxplot {
            fill = "category"
        }


        p += geomPoint(
            position = Pos.jitterdodge,
            shape = 21,
            color = "black"
        ) {
            color = "category"
            fill = "category"
        }


        val sign = mapOf(
            "category" to listOf("2", "1", "3"),
            "z" to listOf("*", "**", "***")
        )

        p += geomText(sign, y = 15) {
            x = "category"
            label = "z"
        }

        p += theme(panelGrid = "blank", panelBackground = elementRect(color = "black"))
            .legendPositionTop()

        writePDF(
            Paths.get("scratch/bp.pdf"),
            p.toPDF()
        )
    }
}
