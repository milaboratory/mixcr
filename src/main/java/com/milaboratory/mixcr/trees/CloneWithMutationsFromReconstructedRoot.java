package com.milaboratory.mixcr.trees;

class CloneWithMutationsFromReconstructedRoot {
    private final MutationsDescription mutationsFromRoot;
    private final MutationsFromVJGermline mutationsFromVJGermline;
    private final CloneWrapper clone;

    CloneWithMutationsFromReconstructedRoot(
            MutationsDescription mutationsFromRoot,
            MutationsFromVJGermline mutationsFromVJGermline,
            CloneWrapper clone
    ) {
        this.mutationsFromRoot = mutationsFromRoot;
        this.mutationsFromVJGermline = mutationsFromVJGermline;
        this.clone = clone;
    }

    public MutationsFromVJGermline getMutationsFromVJGermline() {
        return mutationsFromVJGermline;
    }

    public CloneWrapper getClone() {
        return clone;
    }

    public MutationsDescription getMutationsFromRoot() {
        return mutationsFromRoot;
    }
}
