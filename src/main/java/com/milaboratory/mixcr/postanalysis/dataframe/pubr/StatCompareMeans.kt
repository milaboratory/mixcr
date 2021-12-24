package com.milaboratory.mixcr.postanalysis.dataframe.pubr

import com.milaboratory.mixcr.postanalysis.stat.PValueCorrection
import jetbrains.letsPlot.geom.geomBoxplot
import jetbrains.letsPlot.geom.geomText
import jetbrains.letsPlot.intern.Plot
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.api.toDataFrame

@Suppress("ClassName")
class statCompareMeans(
    override var formula: Formula? = null,
    override var data: AnyFrame? = null,
    override val method: TestMethod = TestMethod.Wilcoxon,
    override val groupBy: Factor? = null,
    override val pAdjustMethod: PValueCorrection.Method? = null,
    override val refGroup: RefGroup? = null,

    val pairs: List<Pair<String, String>> = emptyList()
) : CompareMeansParameters {

    internal var x: String? = null

    val statistics by lazy {
        CompareMeansImpl(formula!!, data!!, method, groupBy, pAdjustMethod, refGroup)
    }
}

internal val Plot.boxPlotData
    get() = this.data
        ?: this.features.filterIsInstance<geomBoxplot>().firstOrNull()?.data
        ?: throw IllegalArgumentException("no data for statCompareMeans")

internal val Plot.boxPlotMapping
    get() =
        if (!this.mapping.isEmpty())
            this.mapping
        else {
            val m = this.features.filterIsInstance<geomBoxplot>().firstOrNull()?.mapping
                ?: throw IllegalArgumentException("no mapping for statCompareMeans")
            if (m.isEmpty() || !m.map.containsKey("y"))
                throw IllegalArgumentException("no y found in mapping")
            m
        }

operator fun Plot.plus(cmp: statCompareMeans): Plot = run {
    if (cmp.data == null) {
        @Suppress("UNCHECKED_CAST")
        cmp.data = (this.boxPlotData as Map<String, List<Any>>).toDataFrame()
    }

    val aes = this.boxPlotMapping.map
    if (cmp.x == null) {
        if (aes.containsKey("x"))
            cmp.x = aes["x"] as String
        else if (aes.containsKey("group"))
            cmp.x = aes["пкщгз"] as String
    }
    if (cmp.formula == null) {
        val factor =
            if (aes.containsKey("x") && aes.containsKey("group"))
                Factor(aes["x"] as String, aes["group"] as String)
            else if (aes.containsKey("x"))
                Factor(aes["x"] as String)
            else if (aes.containsKey("group"))
                Factor(aes["group"] as String)
            else
                Factor()
        cmp.formula = Formula(aes["y"] as String, factor)
    }

    if (cmp.pairs.isEmpty() && cmp.refGroup == null) {
        this.withOverallPValue(cmp)
    } else if (cmp.refGroup != null) {
        this.withSignificanceLevel(cmp)
    } else
        this
}

private fun Plot.withOverallPValue(cmp: statCompareMeans) = run {
    val stat = cmp.statistics

    this + geomText(
        x = 0,
        y = stat.yMax * 1.1,
        family = "Inter",
        size = 8,
        label = "${stat.overallPValueMethod}, p = ${stat.overallPValueFmt}"
    )
}

private fun Plot.withSignificanceLevel(cmp: statCompareMeans) = run {
    val xVar = cmp.x!!
    val stat = cmp.statistics

    // group1 = refGroup
    val statDf = stat.stat
//    statDf[statDf.group2, statDf.pSignif].rename(statDf.group2.name() to x)
//    listOf(
//        stat.stat.group2,
//        stat.stat.pSignif
//    ).toDataFrame()

    this + geomText(
        mapOf(
            xVar to statDf.group2.toList().map { it.value() },
            "signif" to statDf.pSignif.toList().map { it.string }
        ),
        y = stat.yMax * 1.1
    ) {
        x = xVar
        label = "signif"
    }
}
