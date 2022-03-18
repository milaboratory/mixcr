package com.milaboratory.mixcr.trees;

class IdGenerator {
    private int counter = 0;

    public TreeWithMetaBuilder.TreeId next() {
        return new TreeWithMetaBuilder.TreeId(counter++);
    }
}
