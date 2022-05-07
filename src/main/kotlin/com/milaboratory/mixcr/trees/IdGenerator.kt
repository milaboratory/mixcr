package com.milaboratory.mixcr.trees;

import java.util.concurrent.atomic.AtomicInteger;

class IdGenerator {
    private final AtomicInteger counter = new AtomicInteger(0);

    public TreeWithMetaBuilder.TreeId next(VJBase VJBase) {
        return new TreeWithMetaBuilder.TreeId(counter.incrementAndGet(), VJBase);
    }
}
