package com.milaboratory.mixcr.trees;

public class FitnessFunctionParams {
    public final double distanceBetweenClonesInCDR3;
    public final double distanceBetweenClones;
    public final double distanceBetweenClonesWithoutCDR3;
    public final double minDistanceToGermline;

    public FitnessFunctionParams(
            double distanceBetweenClonesInCDR3,
            double distanceBetweenClones,
            double distanceBetweenClonesWithoutCDR3,
            double minDistanceToGermline
    ) {
        this.distanceBetweenClonesInCDR3 = distanceBetweenClonesInCDR3;
        this.distanceBetweenClones = distanceBetweenClones;
        this.distanceBetweenClonesWithoutCDR3 = distanceBetweenClonesWithoutCDR3;
        this.minDistanceToGermline = minDistanceToGermline;
    }
}
