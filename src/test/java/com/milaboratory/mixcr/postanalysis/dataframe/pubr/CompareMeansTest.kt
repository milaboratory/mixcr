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

    fun rndData(
        vararg cols: Pair<String, Distribution>,
        len: Int = 100,
        random: Random = Random.Default
    ) = run {
        val datum = cols.map {
            val d = it.second
            it.first to when (d) {
                Normal -> (0 until len).map { random.nextDouble() }
                Gaussian -> (0 until len).map { random.asJavaRandom().nextGaussian() }
                is Category -> (0 until len).map { random.nextInt(d.n).toString() }
            }
        }
        datum.toMap().toDataFrame()
    }

    @Test
    fun test1() {
        val data = rndData(
            "V" to Gaussian,
            "A" to Category(2),
            "C" to Category(2),
            len = 100001
        )

        val comp = CompareMeans(
            Formula("V", Factor("A", "C")),
            data,
            refGroup = RefGroup.all
        ).stat

        comp.print()
    }
}

sealed interface Distribution

data class Category(val n: Int) : Distribution
object Gaussian : Distribution
object Normal : Distribution
