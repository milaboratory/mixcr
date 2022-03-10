package com.milaboratory.mixcr.trees;

class CloneWithMutationsFromVJGermline {
    private final MutationsFromVJGermline mutations;
    private final CloneWrapper cloneWrapper;

    CloneWithMutationsFromVJGermline(MutationsFromVJGermline mutations, CloneWrapper cloneWrapper) {
        this.mutations = mutations;
        this.cloneWrapper = cloneWrapper;
    }

    MutationsFromVJGermline getMutations() {
        return mutations;
    }

    CloneWrapper getCloneWrapper() {
        return cloneWrapper;
    }
}
