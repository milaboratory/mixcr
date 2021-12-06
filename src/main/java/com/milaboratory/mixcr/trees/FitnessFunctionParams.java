package com.milaboratory.mixcr.trees;

public class FitnessFunctionParams {
    public final double distanceBetweenClonesInCDR3;
    public final double distanceBetweenClones;
    public final double distanceBetweenClonesWithoutCDR3;
    public final double minDistanceToGermline;
    public final double averageDistanceToGermline;
    public final double commonAncestorDistanceFromGermline;
    public final double areaOfTriangle;
    public final double baseAngleOfTriangle;
    public final double baseHeightOfTriangle;
    public final double radiusOfCircumscribedCircle;

    public FitnessFunctionParams(
            double distanceBetweenClonesInCDR3,
            double distanceBetweenClones,
            double distanceBetweenClonesWithoutCDR3,
            double minDistanceToGermline,
            double averageDistanceToGermline,
            double commonAncestorDistanceFromGermline,
            double areaOfTriangle,
            double baseAngleOfTriangle,
            double baseHeightOfTriangle,
            double radiusOfCircumscribedCircle) {
        this.distanceBetweenClonesInCDR3 = distanceBetweenClonesInCDR3;
        this.distanceBetweenClones = distanceBetweenClones;
        this.distanceBetweenClonesWithoutCDR3 = distanceBetweenClonesWithoutCDR3;
        this.minDistanceToGermline = minDistanceToGermline;
        this.averageDistanceToGermline = averageDistanceToGermline;
        this.commonAncestorDistanceFromGermline = commonAncestorDistanceFromGermline;
        this.areaOfTriangle = areaOfTriangle;
        this.baseAngleOfTriangle = baseAngleOfTriangle;
        this.baseHeightOfTriangle = baseHeightOfTriangle;
        this.radiusOfCircumscribedCircle = radiusOfCircumscribedCircle;
    }
}
