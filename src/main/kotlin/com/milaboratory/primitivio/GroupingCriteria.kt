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
import com.milaboratory.mixcr.util.OutputPortWithProgress
import com.milaboratory.util.ObjectSerializer
import com.milaboratory.util.ProgressAndStage
import com.milaboratory.util.TempFileDest
import com.milaboratory.util.TempFileManager
import com.milaboratory.util.sorting.HashSorter
import com.milaboratory.util.sorting.Sorter
import org.apache.commons.io.FileUtils
import java.io.File
import java.util.function.ToLongFunction

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
        fun <T : Any, R> groupBy(property: (T) -> R): GroupingCriteria<T> where R : Comparable<R>, R : Any =
            object : GroupingCriteria<T> {
                override fun hashCodeForGroup(entity: T): Int = property(entity).hashCode()

                override val comparator: Comparator<T> = Comparator.comparing(property)
            }

        fun <T : Any, R> groupBy(comparator: Comparator<R>, property: (T) -> R): GroupingCriteria<T> where R : Any =
            object : GroupingCriteria<T> {
                override fun hashCodeForGroup(entity: T): Int = property(entity).hashCode()

                override val comparator: Comparator<T> = Comparator.comparing(property, comparator)
            }
    }
}

inline fun <reified T : Any> OutputPort<T>.hashGrouping(
    groupingCriteria: GroupingCriteria<T>,
    stateBuilder: PrimitivIOStateBuilder,
    tempFileDest: TempFileDest,
    bitsPerStep: Int = 5,
    readerConcurrency: Int = 8,
    writerConcurrency: Int = 8,
    objectSizeInitialGuess: Long = 256 * FileUtils.ONE_KB,
    memoryBudget: Long = when {
        Runtime.getRuntime().maxMemory() > 10 * FileUtils.ONE_GB -> Runtime.getRuntime().maxMemory() / 8L
        else -> 256 * FileUtils.ONE_MB
    }
): OutputPort<T> {
    // todo check memory budget
    return HashSorter(
        T::class.java,
        groupingCriteria::hashCodeForGroup,
        groupingCriteria.comparator,
        bitsPerStep,
        tempFileDest,
        readerConcurrency,
        writerConcurrency,
        stateBuilder.oState,
        stateBuilder.iState,
        memoryBudget,
        objectSizeInitialGuess
    ).port(this)
}

inline fun <reified T : Any, R> OutputPort<T>.sort(
    comparator: Comparator<T>,
    tempFile: File = TempFileManager.getTempFile(),
    chunkSize: Int = 512 * 1024,
    block: (OutputPort<T>) -> R
): R = Sorter.sort(
    this,
    comparator,
    chunkSize,
    T::class.java,
    tempFile
).use(block)

fun <T : Any, R> OutputPort<T>.sort(
    comparator: Comparator<T>,
    serializer: ObjectSerializer<T>,
    tempFile: File = TempFileManager.getTempFile(),
    chunkSize: Int = 512 * 1024,
    block: (OutputPort<T>) -> R
): R = Sorter.sort(
    this,
    comparator,
    chunkSize,
    serializer,
    tempFile
).use(block)

inline fun <reified T : Any> OutputPort<T>.groupBy(
    stateBuilder: PrimitivIOStateBuilder,
    tempFileDest: TempFileDest,
    groupingCriteria: GroupingCriteria<T>,
    readerConcurrency: Int = 8,
    writerConcurrency: Int = 8
): OutputPort<List<T>> =
    hashGrouping(groupingCriteria, stateBuilder, tempFileDest, readerConcurrency, writerConcurrency)
        .groupBySortedData(groupingCriteria)

fun <T : Any, R> OutputPort<T>.withProgress(
    expectedSize: Long,
    progressAndStage: ProgressAndStage,
    stage: String,
    function: (OutputPort<T>) -> R
): R {
    val withProgress = OutputPortWithProgress.wrap(expectedSize, this)
    progressAndStage.delegate(stage, withProgress)
    val result = function(withProgress)
    withProgress.finish()
    return result
}

fun <T : Any, R> OutputPort<T>.withProgress(
    expectedSize: Long,
    progressAndStage: ProgressAndStage,
    stage: String,
    countPerElement: ToLongFunction<T>,
    function: (OutputPort<T>) -> R
): R {
    val withProgress = OutputPortWithProgress.wrap(expectedSize, this, countPerElement)
    progressAndStage.delegate(stage, withProgress)
    val result = function(withProgress)
    withProgress.finish()
    return result
}

/**
 * Call only after `sort`
 */
fun <T : Any> OutputPort<T>.groupBySortedData(groupingCriteria: GroupingCriteria<T>): OutputPort<List<T>> {

    var cluster: MutableList<T> = mutableListOf()

    return CUtils.makeSynchronized(object : OutputPort<List<T>> {
        override fun close() {
            this@groupBySortedData.close()
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
