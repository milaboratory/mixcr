package com.milaboratory.mixcr.cli;

public enum BuildSHMTreeStep {
    BuildingInitialTrees("Building initial trees"),
    AttachClonesByDistanceChange("Attaching clones by distance change"),
    CombineTrees("Combining trees"),
    AttachClonesByNDN("Attaching clones by NDN");

    public final String forPrint;

    BuildSHMTreeStep(String forPrint) {
        this.forPrint = forPrint;
    }
}
