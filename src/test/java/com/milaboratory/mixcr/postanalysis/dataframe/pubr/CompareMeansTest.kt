package com.milaboratory.mixcr.postanalysis.dataframe.pubr

import org.jetbrains.kotlinx.dataframe.api.print
import org.jetbrains.kotlinx.dataframe.api.toDataFrame
import org.junit.Test
import kotlin.random.Random
import kotlin.random.asJavaRandom

/**
 *
 */
internal class CompareMeansTest {


    @Test
    fun test1() {
        val data = rndData(
            "V" to Gaussian,
            "A" to Category(2),
            "C" to Category(2),
            len = 100000
        )

        val comp = CompareMeans(
            Formula("V", Factor("A", "C")),
            data,
            refGroup = RefGroup.all,
            method = TestMethod.TTest
        ).statistics.stat

        comp.print()
    }

    companion object {

        fun rndData(
            vararg cols: Pair<String, Distribution>,
            len: Int = 100,
            random: Random = Random.Default
        ) = run {
            val datum = cols.map {
                val d = it.second
                it.first to when (d) {
                    Normal -> (0 until len).map { 10 * random.nextDouble() }
                    Gaussian -> (0 until len).map { 10 * random.asJavaRandom().nextGaussian() }
                    is Category -> (0 until len).map { (65 + random.nextInt(d.n)).toChar().toString() }
                }
            }
            datum.toMap().toDataFrame()
        }
    }
}

sealed interface Distribution

data class Category(val n: Int) : Distribution
object Gaussian : Distribution
object Normal : Distribution
