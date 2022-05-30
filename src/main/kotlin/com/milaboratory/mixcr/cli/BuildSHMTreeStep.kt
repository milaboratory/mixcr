package com.milaboratory.mixcr.cli

enum class BuildSHMTreeStep(val forPrint: String) {
    BuildingInitialTrees("Building initial trees"),
    AttachClonesByDistanceChange("Attaching clones by distance change"),
    CombineTrees("Combining trees"),
    AttachClonesByNDN("Attaching clones by NDN");
}
