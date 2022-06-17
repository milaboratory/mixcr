package com.milaboratory.mixcr.util

import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Collectors
import java.util.stream.IntStream
import kotlin.random.Random

object RandomizedTest {
    fun randomized(test: (random: Random, print: Boolean) -> Unit, numberOfRuns: Int) {
        val begin = Instant.now()
        val count = AtomicInteger(0)
        val failedSeeds = IntStream.range(0, numberOfRuns)
            .mapToObj { ThreadLocalRandom.current().nextLong() }
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
            .collect(Collectors.toList())
        println("failed: " + failedSeeds.size)
        failedSeeds shouldBe emptyList()
    }

    fun reproduce(test: (random: Random, print: Boolean) -> Unit, vararg seeds: Long) {
        seeds.forEach { seed ->
            test(Random(seed), true)
        }
    }
}
