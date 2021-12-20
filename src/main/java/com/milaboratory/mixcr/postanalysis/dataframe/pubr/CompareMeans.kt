package com.milaboratory.mixcr.postanalysis.dataframe.pubr

import com.milaboratory.mixcr.postanalysis.dataframe.pubr.RefGroup.Companion.all
import org.apache.commons.math3.stat.inference.MannWhitneyUTest
import org.apache.commons.math3.stat.inference.OneWayAnova
import org.apache.commons.math3.stat.inference.TTest
import org.apache.commons.math3.stat.inference.WilcoxonSignedRankTest
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.annotations.DataSchema
import org.jetbrains.kotlinx.dataframe.api.*

/**
 * A formula containing
 *   y - a numeric variable
 *   factor -  a factor with one or multiple levels
 */
data class Formula(
    /** A numeric variable */
    val y: String,
    /** A factor */
    val factor: Factor
)

/**
 * A group of columns representing a factor for grouping
 */
data class Factor(val columnNames: List<String>) {
    internal val arr = columnNames.toTypedArray()
}

fun Factor(vararg columnNames: String) = Factor(columnNames.toList())

/**
 * Reference group
 **/
sealed interface RefGroup {
    companion object {
        internal object All : RefGroup {
            override fun toString() = "all"
        }

        /** */
        val all: RefGroup = All

        data class RefGroupImpl(val colValues: List<Any?>) : RefGroup {
            override fun toString() = colValues.joinToString("+")
        }

        fun of(vararg columnValues: Any) = RefGroupImpl(columnValues.toList())

        fun of(columnValues: List<Any>) = RefGroupImpl(columnValues)
    }
}

/**
 *
 */
class CompareMeans(
    val formula: Formula,
    val data: AnyFrame,
    val groupBy: Factor? = null,
    val method: TestMethod = TestMethod.Wilcox,
    val pAdjustMethod: String? = null,
    val refGroup: RefGroup? = null
) {

    /** dataframe for each group*/
    private val groups: List<Pair<RefGroup, AnyFrame>> =
        data.groupBy(*formula.factor.arr).groups.toList().map {
            getGroupName(it, formula.factor.arr) to it
        }


    val stat = run {
        if (refGroup != null) {
            val groups = this.groups.toMap()

            //  get reference data
            val refData = (
                    if (refGroup == all)
                        data
                    else
                        groups[refGroup] ?: throw IllegalArgumentException("reference group not found")
                    )[formula.y].cast<Double>().toDoubleArray()

            groups.map { (gr, df) ->
                if (gr == refGroup)
                    return@map null

                val data = df[formula.y].cast<Double>().toDoubleArray()
                val pValue = calc(method, refData, data)

                CompareMeansRow(formula.y, method, refGroup, gr, pValue, -1.0, significance(pValue));
            }.filterNotNull()
                .toDataFrame()
        } else {
            val comparisons = mutableListOf<CompareMeansRow>()

            for (i in groupsList.indices) {
                for (j in 0 until i) {
                    val iGroup = groupsList[i]
                    val jGroup = groupsList[j]
                    val pValue = calc(
                        method,
                        iGroup.second[formula.y].cast<Double>().toDoubleArray(),
                        jGroup.second[formula.y].cast<Double>().toDoubleArray()
                    )

                    comparisons += CompareMeansRow(
                        formula.y, method,
                        iGroup.first, jGroup.first,
                        pValue, -1.0, significance(pValue)
                    )
                }
            }

            comparisons.toDataFrame()
        }
    }

    private fun getGroupName(df: AnyFrame, group: Array<String>) = run {
        val f = df.first()
        RefGroup.Companion.RefGroupImpl(group.map { f[it] })
    }


    private fun calc(method: TestMethod, a: DoubleArray, b: DoubleArray) = when (method) {
        TestMethod.Wilcox ->
            if (a.size != b.size)
                MannWhitneyUTest().mannWhitneyUTest(a, b)
            else
                WilcoxonSignedRankTest().wilcoxonSignedRankTest(a, b, false)
        TestMethod.TTest ->
            if (a.size != b.size)
                TTest().tTest(a, b)
            else
                TTest().pairedTTest(a, b)
        TestMethod.ANOVA -> OneWayAnova().anovaPValue(listOf(a, b))
        TestMethod.Kruskal -> throw RuntimeException("not supported yet")
    }
//
//    private fun byGroups() = run {
//        groupBy!!
//
//    }

    companion object {
        private fun significance(pValue: Double) =
            if (pValue >= 0.05) "ns"
            else if (pValue < 0.0001) "***"
            else if (pValue < 0.001) "**"
            else "*"
    }
}

enum class TestMethod {
    TTest,
    Wilcox,
    ANOVA,
    Kruskal
}

/**
 * DataFrame row for CompareMeans result
 */
@DataSchema
@Suppress("UNCHECKED_CAST")
data class CompareMeansRow(
    /** The variable used in test */
    val y: String,

    /** Method used */
    val method: TestMethod,

    /** First group */
    val group1: RefGroup,

    /** Second group */
    val group2: RefGroup,

    /** The p-value */
    val pValue: Double,

    /** The adjusted p-value */
    val pValueAdj: Double,

    /** The significance level */
    val pSignif: String
)
