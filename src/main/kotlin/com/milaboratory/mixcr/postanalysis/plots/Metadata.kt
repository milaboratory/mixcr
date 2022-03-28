package com.milaboratory.mixcr.postanalysis.plots

import com.milaboratory.util.StringUtil
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.RowFilter
import org.jetbrains.kotlinx.dataframe.api.*
import org.jetbrains.kotlinx.dataframe.io.read
import org.jetbrains.kotlinx.dataframe.io.readTSV
import java.util.*
import javax.xml.crypto.Data

fun AnyFrame.isNumeric(col: String) = this[col].all { it == null || it is Number }
fun AnyFrame.isCategorial(col: String) = !isNumeric(col)

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
    val m = matchLists(
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

fun matchLists(target: List<String>, query: List<String>): Map<String, String?> {
    val matched: MutableMap<String, PriorityQueue<Pair<String, Double>>> = HashMap()
    for (t in target) {
        val matchedForKey = PriorityQueue<Pair<String, Double>>(
            Comparator.comparing { -it.second }
        )

        for (q in query) {
            val a = t.lowercase(Locale.getDefault())
            val b = q.lowercase(Locale.getDefault())
            val match = StringUtil.longestCommonSubstring(a, b)
            val score = 2.0 * (0.5 + match) / (1 + a.length + b.length)
            matchedForKey.add(q to score)
        }

        matched[t] = matchedForKey
    }

    val unmatchedQ = query.toMutableSet()
    val r = mutableMapOf<String, String?>()

    for ((t, q) in matched.toList().sortedBy { kv -> -kv.second.maxOf { it.second } }) {
        if (q.isEmpty()) {
            r[t] = null
            continue
        }
        var m: String? = null
        while (!q.isEmpty()) {
            val candidate = q.poll()
            val wasUnmatched = unmatchedQ.remove(candidate.first)
            if (wasUnmatched) {
                m = candidate.first
                break
            }
        }
        r[t] = m
    }

    return r
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
