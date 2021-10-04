package com.milaboratory.mixcr.postanalysis.plots

import com.milaboratory.mixcr.postanalysis.PostanalysisResult
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroup
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroupResult

typealias MutableDataFrame = MutableMap<String, MutableList<Any>>
typealias DataFrame = Map<String, List<Any>>

fun emptyDataFrame() = mutableMapOf<String, MutableList<Any>>()
fun emptyDataFrame(colNames: List<String>) = run {
    val df = emptyDataFrame()
    for (colName in colNames) {
        df[colName] = mutableListOf()
    }
    df
}

//fun MutableDataFrame.toDataFrame(): DataFrame = this.mapValues { (_, v) -> v.toList() }.toMap()

fun DataFrame.nRows() = if (this.isEmpty()) 0 else this.values.first().size
fun DataFrame.nCols() = this.size

fun DataFrame.colNames() = this.map { (k, _) -> k }.toList()

fun DataFrame.rows() = run {
    val rows = mutableListOf<List<Any>>()
    for (i in 0 until nRows()) {
        rows += this.map { (_, v) -> v[i] }.toList()
    }
    rows
}

fun fromRows(colNames: List<String>, rows: List<List<Any>>) = run {
    val df = emptyDataFrame(colNames)
    for (row in rows) {
        for (i in row.indices) {
            df[colNames[i]]!! += row[i]
        }
    }
    df
}

fun DataFrame.sort(col: String, descending: Boolean = false) = run {
    val rows = rows()
    val colNames = colNames()
    val iCol = colNames.indexOf(col)
    if (descending)
        rows.sortByDescending { it[iCol] as Comparable<Any> }
    else
        rows.sortBy { it[iCol] as Comparable<Any> }
    fromRows(colNames, rows)
}


fun DataFrame.sort(col: String, comparator: Comparator<Any>) = run {
    val rows: MutableList<List<Any>> = rows()
    val colNames = colNames()
    val iCol = colNames.indexOf(col)
    rows.sortWith { a, b ->
        comparator.compare(a[iCol], b[iCol])
    }
    fromRows(colNames, rows)
}


