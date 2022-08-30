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
package com.milaboratory.mixcr.util

import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.LongStream
import kotlin.random.Random
import kotlin.streams.toList

object RandomizedTest {
    fun randomized(test: (random: Random, print: Boolean) -> Unit, numberOfRuns: Int) {
        val begin = Instant.now()
        val count = AtomicInteger(0)
        val failedSeeds = LongStream
            .generate { ThreadLocalRandom.current().nextLong() }
            .limit(numberOfRuns.toLong())
            .parallel()
            .filter { seed ->
                val current = count.incrementAndGet().toLong()
                if (current % 10000 == 0L) {
                    val runFor = Duration.between(begin, Instant.now())
                    print("\r current is " + current + " run for " + runFor + " ETC: " + runFor.multipliedBy((numberOfRuns - current) / current))
                    System.out.flush()
                }
                try {
                    test(Random(seed), false)
                    false
                } catch (e: Throwable) {
                    true
                }
            }
            .toList()
        println()
        println("failed: " + failedSeeds.size)
        failedSeeds shouldBe emptyList()
    }

    fun reproduce(test: (random: Random, print: Boolean) -> Unit, vararg seeds: Long) {
        seeds.forEach { seed ->
            try {
                test(Random(seed), true)
            } catch (e: Throwable) {
                println("failed seed: $seed")
                throw e
            }
        }
    }
}
