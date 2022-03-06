package com.milaboratory.mixcr.postanalysis.plots

import com.milaboratory.util.StringUtil
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.add
import org.jetbrains.kotlinx.dataframe.api.all
import org.jetbrains.kotlinx.dataframe.api.cast
import org.jetbrains.kotlinx.dataframe.api.leftJoin
import java.util.*


fun AnyFrame.isNumeric(col: String) = this[col].all { it == null || it is Number }
fun AnyFrame.isCategorial(col: String) = !isNumeric(col)

/**
 * Attaches metadata to statistics
 **/
fun <T> DataFrame<T>.withMetadata(metadata: AnyFrame) = run {
    attachMetadata(this, "sample", metadata, "sample").cast<T>()
}

/**
 * Attaches metadata to statistics
 **/
fun attachMetadata(
    data: AnyFrame,
    dataCol: String,
    meta: AnyFrame,
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

    data
        .add("_meta_join_") { m[it[dataCol]] }
        .leftJoin(meta) { "_meta_join_" match metaCol }
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

data class Filter(val column: String, val value: Any)