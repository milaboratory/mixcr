package com.milaboratory.mixcr.trees

import com.milaboratory.mixcr.trees.TreeWithMetaBuilder.TreeId
import java.util.concurrent.atomic.AtomicInteger

internal class IdGenerator {
    private val counter = AtomicInteger(0)
    fun next(VJBase: VJBase): TreeId = TreeId(counter.incrementAndGet(), VJBase)
}
