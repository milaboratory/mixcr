package com.milaboratory.mixcr.postanalysis.dataframe.pubr

import com.milaboratory.mixcr.postanalysis.stat.PValueCorrection
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
    override val method: TestMethod = TestMethod.Wilcox,
    override val groupBy: Factor? = null,
    override val pAdjustMethod: PValueCorrection.Method? = null,
    override val refGroup: RefGroup? = null,

    val pairs: List<Pair<String, String>> = emptyList()
) : CompareMeansParameters {
    val statistics by lazy {
        CompareMeansImpl(formula!!, data!!, method, groupBy, pAdjustMethod, refGroup)
    }
}


operator fun Plot.plus(cmp: statCompareMeans) = run {
    if (cmp.data == null) {
        @Suppress("UNCHECKED_CAST")
        cmp.data = (this.data!! as Map<String, List<Any>>).toDataFrame()
    }

    if (cmp.formula == null) {
        val aes = this.mapping.map
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

        val data = cmp.data!!
        val y = cmp.formula!!.y
        val maxY = data[y].cast<Double>().max()

        this + geomText(x = 0, y = maxY, label = "p-value " + cmp.statistics.anovaPValue)
    } else {
        this
    }
}
