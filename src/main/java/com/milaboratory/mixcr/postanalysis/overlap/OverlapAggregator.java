package com.milaboratory.mixcr.postanalysis.overlap;

import com.milaboratory.mixcr.postanalysis.Aggregator;
import com.milaboratory.mixcr.postanalysis.MetricValue;
import com.milaboratory.mixcr.postanalysis.WeightFunction;
import org.apache.commons.math3.stat.regression.SimpleRegression;

import java.util.List;

/**
 *
 */
public class OverlapAggregator<T> implements Aggregator<OverlapKey<OverlapType>, OverlapGroup<T>> {
    private final WeightFunction<T> weight;
    private final int i1, i2;
    private final String id1, id2;
    final SimpleRegression
            regressionAll = new SimpleRegression(),
            regressionIntersection = new SimpleRegression();
    double sumS1, sumS2,
            sumS1Intersection, sumS2Intersection,
            sumSqProduct;
    long diversity1, diversity2, diversityIntersection;

    public OverlapAggregator(WeightFunction<T> weight, int i1, int i2, String id1, String id2) {
        this.weight = weight;
        this.i1 = i1;
        this.i2 = i2;
        this.id1 = id1;
        this.id2 = id2;
    }

    @Override
    public void consume(OverlapGroup<T> obj) {
        List<T> s1 = obj.getBySample(i1);
        List<T> s2 = obj.getBySample(i2);
        double ss1 = s1.stream().mapToDouble(weight::weight).sum();
        double ss2 = s2.stream().mapToDouble(weight::weight).sum();
        regressionAll.addData(ss1, ss2);
        sumS1 += ss1;
        sumS2 += ss2;
        if (!s1.isEmpty())
            diversity1++;
        if (!s2.isEmpty())
            diversity2++;
        if (s1.isEmpty() || s2.isEmpty())
            return;
        regressionIntersection.addData(ss1, ss2);
        diversityIntersection++;
        sumS1Intersection += ss1;
        sumS2Intersection += ss2;
        sumSqProduct += Math.sqrt(ss1 * ss2);
    }

    @Override
    public MetricValue<OverlapKey<OverlapType>>[] result() {
        return new MetricValue[]{
                new MetricValue<>(key(OverlapType.D, id1, id2), 1.0 * diversityIntersection / diversity1 / diversity2),
                new MetricValue<>(key(OverlapType.SharedClonotypes, id1, id2), 1.0 * diversityIntersection),
                new MetricValue<>(key(OverlapType.F1, id1, id2), Math.sqrt(sumS1Intersection * sumS2Intersection / sumS1 / sumS2)),
                new MetricValue<>(key(OverlapType.F2, id1, id2), sumSqProduct / Math.sqrt(sumS1 * sumS2)),
                new MetricValue<>(key(OverlapType.Jaccard, id1, id2), 1.0 * diversityIntersection / (diversity1 + diversity2 - diversityIntersection)),
                new MetricValue<>(key(OverlapType.R_Intersection, id1, id2), regressionIntersection.getR()),
                new MetricValue<>(key(OverlapType.R_All, id1, id2), regressionAll.getR()),
        };
    }

    private static OverlapKey<OverlapType> key(OverlapType type, String i, String j) {
        return new OverlapKey<>(type, i, j);
    }
}
