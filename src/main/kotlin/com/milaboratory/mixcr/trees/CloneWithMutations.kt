package com.milaboratory.mixcr.trees

class CloneWithMutationsFromReconstructedRoot(
    val mutationsSet: MutationsSet,
    val mutationsFromVJGermline: MutationsFromVJGermline,
    val clone: CloneWrapper
)

class CloneWithMutationsFromVJGermline(
    val mutations: MutationsFromVJGermline,
    val cloneWrapper: CloneWrapper
)
