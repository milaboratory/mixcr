package com.milaboratory.mixcr.trees;

class IdGenerator {
    private int counter = 0;

    public TreeWithMetaBuilder.TreeId next(VJBase VJBase) {
        return new TreeWithMetaBuilder.TreeId(counter++, VJBase);
    }
}
