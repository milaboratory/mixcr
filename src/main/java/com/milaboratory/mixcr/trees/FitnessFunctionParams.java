package com.milaboratory.mixcr.trees;

public class FitnessFunctionParams {
    public final double distanceBetweenClonesInNDN;
    public final double distanceBetweenClones;
    public final double distanceBetweenClonesWithoutNDN;
    public final double firstDistanceToGermline;
    public final double secondDistanceToGermline;
    public final double minDistanceToGermline;

    public FitnessFunctionParams(
            double distanceBetweenClonesInNDN,
            double distanceBetweenClones,
            double distanceBetweenClonesWithoutNDN,
            double firstDistanceToGermline,
            double secondDistanceToGermline,
            double minDistanceToGermline
    ) {
        this.distanceBetweenClonesInNDN = distanceBetweenClonesInNDN;
        this.distanceBetweenClones = distanceBetweenClones;
        this.distanceBetweenClonesWithoutNDN = distanceBetweenClonesWithoutNDN;
        this.firstDistanceToGermline = firstDistanceToGermline;
        this.secondDistanceToGermline = secondDistanceToGermline;
        this.minDistanceToGermline = minDistanceToGermline;
    }
}
