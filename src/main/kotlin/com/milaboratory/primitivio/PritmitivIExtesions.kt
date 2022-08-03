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
import cc.redberry.pipe.blocks.Buffer
import cc.redberry.pipe.blocks.FilteringPort
import cc.redberry.pipe.util.FlatteningOutputPort
import com.milaboratory.mixcr.util.OutputPortWithProgress

inline fun <reified T : Any> PrimitivI.readObjectOptional(): T? = readObject(T::class.java)
inline fun <reified T : Any> PrimitivI.readObjectRequired(): T = readObject(T::class.java)

inline fun <reified K : Any, reified V : Any> PrimitivI.readMap(): Map<K, V> =
    Util.readMap(this, K::class.java, V::class.java)

inline fun <reified T : Any> PrimitivI.readList(): List<T> = Util.readList(T::class.java, this)

fun PrimitivO.writeList(array: List<*>) = Util.writeList(array, this)

fun PrimitivO.writeMap(array: Map<*, *>) = Util.writeMap(array, this)

fun <T : Any> PrimitivO.writeArray(array: Array<T>) {
    this.writeInt(array.size)
    for (o in array) this.writeObject(o)
}

inline fun <reified T : Any> PrimitivI.readArray(): Array<T> = Array(readInt()) {
    readObject(T::class.java)
}

inline fun <reified T : Any, R> OutputPortCloseable<T>.withProgress(
    expectedSize: Long,
    block: (OutputPortWithProgress<T>) -> R
): R = OutputPortWithProgress.wrap(expectedSize, this).use(block)

fun <T, R> OutputPort<T>.map(function: (T) -> R): OutputPort<R> = CUtils.wrap(this, function)

fun <T, R> OutputPort<T>.mapInParallel(
    threads: Int,
    buffer: Int = Buffer.DEFAULT_SIZE,
    function: (T) -> R
): OutputPort<R> = CUtils.orderedParallelProcessor(this, function, buffer, threads)

fun <T, R> OutputPort<T>.mapNotNull(function: (T) -> R?): OutputPortCloseable<R> = flatMap {
    listOfNotNull(function(it))
}

fun <T> List<OutputPort<T>>.flatten(): OutputPortCloseable<T> =
    FlatteningOutputPort(CUtils.asOutputPort(this))

fun <T> OutputPort<List<T>>.flatten(): OutputPortCloseable<T> = flatMap { it }

fun <T, R> OutputPort<T>.flatMap(function: (element: T) -> Iterable<R>): OutputPortCloseable<R> =
    FlatteningOutputPort(CUtils.wrap(this) {
        CUtils.asOutputPort(function(it))
    })

fun <T> OutputPort<T>.filter(test: (element: T) -> Boolean): OutputPortCloseable<T> =
    FilteringPort(this, test)

fun <T> OutputPort<T>.forEach(action: (element: T) -> Unit): Unit =
    CUtils.it(this).forEach(action)

fun <T> OutputPort<T>.forEachInParallel(threads: Int, action: (element: T) -> Unit): Unit =
    CUtils.processAllInParallel(this, action, threads)

fun <T> OutputPort<T>.toList(): List<T> =
    CUtils.it(this).toList()

fun <T> OutputPort<T>.asSequence(): Sequence<T> =
    CUtils.it(this).asSequence()

fun <T> OutputPort<T>.count(): Int =
    CUtils.it(this).count()

