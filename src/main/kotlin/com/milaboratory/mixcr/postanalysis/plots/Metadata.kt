/*
 * Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
package com.milaboratory.mixcr.postanalysis.plots

import com.milaboratory.util.StringUtil
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.RowFilter
import org.jetbrains.kotlinx.dataframe.api.*
import org.jetbrains.kotlinx.dataframe.io.read
import org.jetbrains.kotlinx.dataframe.io.readTSV

fun AnyFrame.isNumeric(col: String) = this[col].all { it == null || it is Number }
fun AnyFrame.isCategorical(col: String) = !isNumeric(col)

typealias Metadata = AnyFrame

@JvmName("readMetadataNullable")
fun readMetadata(path: String?): Metadata? = if (path == null) null else readMetadata(path)

/**
 * Read metadata from file
 */
fun readMetadata(path: String): Metadata =
    if (path.endsWith(".tsv"))
        DataFrame.readTSV(path)
    else
        DataFrame.read(path)

/**
 * Attaches metadata to statistics
 **/
fun <T> DataFrame<T>.withMetadata(metadataPath: String) = withMetadata(readMetadata(metadataPath))

/**
 * Attaches metadata to statistics
 **/
fun <T> DataFrame<T>.withMetadata(metadata: Metadata) = run {
    attachMetadata(this, "sample", metadata, "sample").cast<T>()
}

/**
 * Attaches metadata to statistics
 **/
fun <T> attachMetadata(
    data: DataFrame<T>,
    dataCol: String,
    meta: Metadata,
    metaCol: String
) = run {
    val m = StringUtil.matchLists(
        data[dataCol].distinct().cast<String>().toList(),
        meta[metaCol].distinct().cast<String>().toList(),
    )

    m.filter { it.value == null }.apply {
        if (!isEmpty())
            throw IllegalArgumentException("can't unambiguously match metadata for the following rows: $keys")
    }

    data.add("_meta_join_") { m[it[dataCol]] }
        .leftJoin(meta) { "_meta_join_" match metaCol }
        .remove("_meta_join_")
}

fun Metadata.parseFilter(expr: String) =
    // equality
    if (expr.contains("=")) {
        val (column, value) = expr.split("=")
        if (isNumeric(column))
            Eq(column, value.toDouble())
        else
            Eq(column, value)
    } else if (expr.contains(">=")) {
        val (column, value) = expr.split(">=")
        Geq(column, value.toDouble())
    } else if (expr.contains("<=")) {
        val (column, value) = expr.split("<=")
        Leq(column, value.toDouble())
    } else if (expr.contains(">")) {
        val (column, value) = expr.split(">=")
        Ge(column, value.toDouble())
    } else if (expr.contains("<")) {
        val (column, value) = expr.split("<=")
        Le(column, value.toDouble())
    } else throw IllegalArgumentException("incorrect filter string")


sealed interface Filter {
    val column: String

    fun <T> apply(df: DataFrame<T>): DataFrame<T> = df.filter(predicate())

    fun <T> predicate(): RowFilter<T>

    fun rename(newColumn: String): Filter

    fun rename(mapping: (String) -> String): Filter = rename(mapping(column))
}

/** = */
data class Eq(override val column: String, val value: Any) : Filter {
    override fun <T> predicate(): RowFilter<T> = { it[column] == value }
    override fun rename(newColumn: String) = copy(column = newColumn)
}

/** <= */
data class Leq(override val column: String, val value: Double) : Filter {
    override fun <T> predicate(): RowFilter<T> = { column<Double>() <= value }
    override fun rename(newColumn: String) = copy(column = newColumn)
}

/** >= */
data class Geq(override val column: String, val value: Double) : Filter {
    override fun <T> predicate(): RowFilter<T> = { column<Double>() >= value }
    override fun rename(newColumn: String) = copy(column = newColumn)
}

/** < */
data class Le(override val column: String, val value: Double) : Filter {
    override fun <T> predicate(): RowFilter<T> = { column<Double>() < value }
    override fun rename(newColumn: String) = copy(column = newColumn)
}

/** > */
data class Ge(override val column: String, val value: Double) : Filter {
    override fun <T> predicate(): RowFilter<T> = { column<Double>() > value }
    override fun rename(newColumn: String) = copy(column = newColumn)
}
