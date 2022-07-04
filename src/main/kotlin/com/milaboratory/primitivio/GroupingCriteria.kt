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
package com.milaboratory.primitivio

import cc.redberry.pipe.CUtils
import cc.redberry.pipe.OutputPort
import cc.redberry.pipe.OutputPortCloseable
import com.milaboratory.mixcr.util.OutputPortWithProgress
import com.milaboratory.util.CanReportProgress
import com.milaboratory.util.TempFileDest
import com.milaboratory.util.sorting.HashSorter

interface GroupingCriteria<T> {
    /**
     * Returns the hash code of the feature which is used to group entities
     */
    fun hashCodeForGroup(entity: T): Int

    /**
     * Comparator for entities with the same hash code but from different clusters
     */
    val comparator: Comparator<T>

    companion object {
        fun <T : Any, R> sortBy(property: (T) -> R): GroupingCriteria<T> where R : Comparable<R>, R : Any =
            object : GroupingCriteria<T> {
                override fun hashCodeForGroup(entity: T): Int = property(entity).hashCode()

                override val comparator: Comparator<T> = Comparator.comparing(property)
            }
    }
}

inline fun <reified T : Any> OutputPort<T>.sort(
    groupingCriteria: GroupingCriteria<T>,
    stateBuilder: PrimitivIOStateBuilder,
    tempFileDest: TempFileDest
): OutputPortCloseable<T> {
    // todo check memory budget
    val memoryBudget = if (Runtime.getRuntime().maxMemory() > 10000000000L /* -Xmx10g */) Runtime.getRuntime()
        .maxMemory() / 4L /* 1 Gb */ else 1 shl 28
    // todo move constants to parameters
    return HashSorter(
        T::class.java,
        groupingCriteria::hashCodeForGroup,
        groupingCriteria.comparator,
        5,
        tempFileDest,
        8,
        8,
        stateBuilder.oState,
        stateBuilder.iState,
        memoryBudget,
        (1 shl 18 /* 256 Kb */).toLong()
    ).port(this)
}

inline fun <reified T : Any> OutputPort<T>.sortAndGroup(
    groupingCriteria: GroupingCriteria<T>,
    stateBuilder: PrimitivIOStateBuilder,
    tempFileDest: TempFileDest
): OutputPort<List<T>> =
    sort(groupingCriteria, stateBuilder, tempFileDest)
        .groupBySortedData(groupingCriteria)

inline fun <reified T : Any> OutputPort<T>.sortAndGroupWithProgress(
    groupingCriteria: GroupingCriteria<T>,
    stateBuilder: PrimitivIOStateBuilder,
    tempFileDest: TempFileDest,
    expectedSize: Long
): Pair<OutputPort<List<T>>, CanReportProgress> {
    val sorted = sort(groupingCriteria, stateBuilder, tempFileDest)
    val withProgress = OutputPortWithProgress.wrap(expectedSize, sorted)
    return withProgress
        .groupBySortedData(groupingCriteria) to withProgress
}

/**
 * Call only after `sort`
 */
fun <T : Any> OutputPort<T>.groupBySortedData(groupingCriteria: GroupingCriteria<T>): OutputPort<List<T>> {

    var cluster: MutableList<T> = mutableListOf()

    return CUtils.makeSynchronized(object : OutputPortCloseable<List<T>> {
        override fun close() {
            (this@groupBySortedData as? OutputPortCloseable)?.close()
        }

        override fun take(): List<T>? {
            while (true) {
                val element = this@groupBySortedData.take()
                if (element == null) {
                    if (cluster.isNotEmpty()) {
                        val result = cluster
                        cluster = mutableListOf()
                        return result
                    }
                    return null
                }
                if (cluster.isEmpty()) {
                    //first element from underling stream. Start first cluster
                    cluster += element
                    continue
                }
                val lastAdded = cluster[cluster.size - 1]
                if (groupingCriteria.comparator.compare(lastAdded, element) == 0) {
                    //the same cluster
                    cluster += element
                } else {
                    //replace cluster
                    val result = cluster
                    cluster = mutableListOf(element)
                    return result
                }
            }
        }
    })
}
