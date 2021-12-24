package com.milaboratory.mixcr.postanalysis.dataframe.pubr

import com.milaboratory.mixcr.postanalysis.stat.PValueCorrection
import jetbrains.letsPlot.geom.geomBoxplot
import jetbrains.letsPlot.geom.geomText
import jetbrains.letsPlot.intern.Plot
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.api.cast
import org.jetbrains.kotlinx.dataframe.api.max
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

operator fun Plot.plus(cmp: statCompareMeans) = run {
    if (cmp.data == null) {
        @Suppress("UNCHECKED_CAST")
        cmp.data = (this.boxPlotData as Map<String, List<Any>>).toDataFrame()
    }

    if (cmp.formula == null) {
        val aes = this.boxPlotMapping.map
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

    if (cmp.pairs.isEmpty()) {

        val stat = cmp.statistics
        val data = cmp.data!!
        val y = cmp.formula!!.y
        val yMax = data[y].cast<Double>().max()


        this + geomText(
            x = 0,
            y = yMax * 1.1,
            label = "${stat.overallPValueMethod} p-value ${stat.overallPValue}"
        )
    } else {
        this
    }
}
