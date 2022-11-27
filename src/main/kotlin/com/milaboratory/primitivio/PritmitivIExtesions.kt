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
import cc.redberry.pipe.Processor
import cc.redberry.pipe.blocks.Buffer
import cc.redberry.pipe.blocks.FilteringPort
import cc.redberry.pipe.blocks.Merger
import cc.redberry.pipe.blocks.ParallelProcessor
import cc.redberry.pipe.util.Chunk
import cc.redberry.pipe.util.FlatteningOutputPort
import cc.redberry.pipe.util.Indexer
import cc.redberry.pipe.util.OrderedOutputPort
import cc.redberry.pipe.util.TBranchOutputPort
import cc.redberry.primitives.Filter
import com.milaboratory.primitivio.blocks.PrimitivIBlocks
import com.milaboratory.primitivio.blocks.PrimitivIOBlocksUtil
import com.milaboratory.primitivio.blocks.PrimitivOBlocks
import com.milaboratory.util.TempFileDest
import net.jpountz.lz4.LZ4Compressor
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.function.Predicate

inline fun <reified T : Any> PrimitivI.readObjectOptional(): T? = readObject(T::class.java)

inline fun <reified T : Any> PrimitivI.readObjectRequired(): T {
    val result = readObject(T::class.java)
    if (result != null) {
        return result
    } else {
        throw IllegalStateException("Error on read ${T::class}, expected not null, but was null")
    }
}

inline fun <reified K : Any, reified V : Any> PrimitivI.readMap(): Map<K, V> =
    Util.readMap(this, K::class.java, V::class.java)

fun <T : Any> PrimitivI.readList(reader: PrimitivI.() -> T): List<T> = readCollection(::ArrayList, reader)

fun <T : Any> PrimitivI.readSet(reader: PrimitivI.() -> T): Set<T> = readCollection(::HashSet, reader)

fun <T> PrimitivO.writeCollection(collection: Collection<T>, writer: PrimitivO.(T) -> Unit) {
    this.writeInt(collection.size)
    collection.forEach {
        writer(it)
    }
}

private fun <T : Any, C : MutableCollection<T>> PrimitivI.readCollection(
    supplier: (size: Int) -> C,
    reader: PrimitivI.() -> T
): C {
    val size = this.readInt()
    val collection = supplier(size)
    repeat(size) {
        collection.add(reader())
    }
    return collection
}

fun PrimitivO.writeMap(map: SortedMap<*, *>) = Util.writeMap(map, this)

fun <T : Any> PrimitivO.writeArray(array: Array<T>) {
    this.writeInt(array.size)
    for (o in array) this.writeObject(o)
}

fun PrimitivO.writeIntArray(array: IntArray) {
    this.writeInt(array.size)
    for (o in array) this.writeInt(o)
}

inline fun <reified T : Any> PrimitivI.readArray(): Array<T> = Array(readInt()) {
    readObject(T::class.java)
}

fun PrimitivI.readIntArray(): IntArray = IntArray(readInt()) {
    readInt()
}

operator fun <T : Any, E : T, R : Any> Processor<T, R>.invoke(input: E): R = process(input)

inline operator fun <T : Any, reified R : Any> Processor<T, R>.invoke(chunk: Chunk<out T>): Chunk<out R> =
    Chunk(Array(chunk.size()) { i ->
        process(chunk[i])
    })

val <T : Any> Iterable<T>.port: OutputPort<T>
    get() = CUtils.asOutputPort(this)

fun <T : Any, R : Any> OutputPort<T>.map(function: (T) -> R): OutputPortCloseable<R> = CUtils.wrap(this, function)

fun <T : Any, R : Any> OutputPort<T>.mapInParallelOrdered(
    threads: Int,
    bufferSize: Int = Buffer.DEFAULT_SIZE,
    function: (T) -> R
): OutputPort<R> = CUtils.orderedParallelProcessor(this, function, bufferSize, threads)

fun <T : Any, R : Any> OutputPort<T>.mapInParallel(
    threads: Int,
    bufferSize: Int = Buffer.DEFAULT_SIZE,
    function: (T) -> R
): ParallelProcessor<T, R> = ParallelProcessor(this, function, bufferSize, threads)

fun <T : Any, R : Any> OutputPort<Chunk<T>>.mapChunksInParallel(
    threads: Int,
    bufferSize: Int = Buffer.DEFAULT_SIZE,
    function: (T) -> R
): ParallelProcessor<Chunk<T>, Chunk<R>> = ParallelProcessor(this, CUtils.chunked(function), bufferSize, threads)

fun <T : Any, R : Any> OutputPort<T>.mapNotNull(function: (T) -> R?): OutputPortCloseable<R> = flatMap {
    listOfNotNull(function(it)).port
}

fun <T : Any> OutputPort<T>.chunked(chunkSize: Int): OutputPort<Chunk<T>> = CUtils.chunked(this, chunkSize)

// @Suppress("UNCHECKED_CAST")
fun <T : Any> OutputPort<Chunk<T>>.unchunked(): OutputPort<T> = CUtils.unchunked(this)

fun <T : Any> OutputPort<T>.buffered(bufferSize: Int): Merger<T> = CUtils.buffered(this, bufferSize)

fun <T : Any> OutputPort<T>.ordered(indexer: Indexer<T>): OutputPort<T> = OrderedOutputPort(this, indexer)

fun <T : Any> List<OutputPort<T>>.flatten(): OutputPortCloseable<T> =
    FlatteningOutputPort(this.port)

fun <T : Any> OutputPort<List<T>>.flatten(): OutputPortCloseable<T> = flatMap { it.port }

fun <T : Any, R : Any> OutputPort<T>.flatMap(function: (element: T) -> OutputPort<R>): OutputPortCloseable<R> =
    FlatteningOutputPort(CUtils.wrap(this) {
        function(it)
    })

fun <T : Any> OutputPort<T>.filter(test: Filter<T>): OutputPortCloseable<T> =
    FilteringPort(this, test)

fun <T : Any> Predicate<T>.asFilter(): Filter<T> = Filter { this.test(it) }
fun <T : Any> OutputPort<T>.limit(limit: Long): OutputPortCloseable<T> = object : OutputPortCloseable<T> {
    private var count = 0L

    override fun take(): T? = when {
        count < limit -> {
            val result = this@limit.take()
            count++
            result
        }

        else -> null
    }

    override fun close() {
        (this@limit as? OutputPortCloseable)?.close()
    }
}

fun <T : Any> OutputPort<T>.forEach(action: (element: T) -> Unit): Unit =
    CUtils.it(this).forEach(action)

fun <T : Any> OutputPort<T>.onEach(action: (element: T) -> Unit): OutputPortCloseable<T> =
    map {
        action(it)
        it
    }

fun <T : Any> OutputPort<T>.forEachInParallel(threads: Int, action: (element: T) -> Unit): Unit =
    CUtils.processAllInParallel(this, action, threads)

fun <T : Any> OutputPort<T>.toList(): List<T> =
    CUtils.it(this).toList()

fun <T : Any> OutputPort<T>.asSequence(): Sequence<T> =
    CUtils.it(this).asSequence()

fun <T : Any> OutputPort<T>.count(): Int =
    CUtils.it(this).count()

inline fun <reified T : Any, R> OutputPort<T>.cached(
    tempDest: TempFileDest,
    stateBuilder: PrimitivIOStateBuilder,
    blockSize: Int,
    concurrencyToRead: Int = 1,
    concurrencyToWrite: Int = 1,
    compressor: LZ4Compressor = PrimitivIOBlocksUtil.fastLZ4Compressor(),
    function: (() -> OutputPort<T>) -> R
): R {
    val primitivI = PrimitivIBlocks(T::class.java, concurrencyToRead, stateBuilder.iState)
    val primitivO = PrimitivOBlocks<T>(
        concurrencyToWrite,
        stateBuilder.oState,
        blockSize,
        compressor
    )

    val tempFile = tempDest.resolvePath("tempFile." + ThreadLocalRandom.current().nextInt())

    var wasCalled = false
    val portForFirstCall = TBranchOutputPort.wrap(primitivO.newWriter(tempFile), this)
    val result = function {
        if (!wasCalled) {
            wasCalled = true
            portForFirstCall
        } else {
            check(portForFirstCall.isClosed) {
                "First result from cache must be read entirely before calling the next time"
            }
            primitivI.newReader(tempFile, blockSize)
        }
    }
    tempFile.toFile().delete()
    (this as? OutputPortCloseable)?.close()
    return result
}
