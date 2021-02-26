package com.milaboratory.mixcr.postanalysis.diversity;

import com.milaboratory.mixcr.postanalysis.Aggregator;
import com.milaboratory.mixcr.postanalysis.MetricValue;
import gnu.trove.impl.Constants;
import gnu.trove.iterator.TLongIntIterator;
import gnu.trove.map.hash.TLongIntHashMap;
import org.apache.commons.math3.util.CombinatoricsUtils;

import java.util.*;
import java.util.function.ToLongFunction;
import java.util.stream.IntStream;

import static com.milaboratory.mixcr.postanalysis.diversity.DiversityMeasure.*;

/**
 *
 */
public class DiversityAggregator<T> implements Aggregator<DiversityMeasure, T> {
    private final Set<DiversityMeasure> measures;
    /** number of clonotypes */
    private int diversity = 0;
    /** total count across all clonotypes */
    private long countSum = 0;
    /** clone count -> number of clones */
    final TLongIntHashMap freqTable = new TLongIntHashMap(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, -1, 0);
    final ToLongFunction<T> count;

    /** @param count returns count of element */
    public DiversityAggregator(ToLongFunction<T> count, DiversityMeasure[] measures) {
        this.count = count;
        this.measures = EnumSet.noneOf(DiversityMeasure.class);
        this.measures.addAll(Arrays.asList(measures));
    }

    @Override
    public void consume(T obj) {
        long count = this.count.applyAsLong(obj);
        freqTable.adjustOrPutValue(count, 1, 1);
        diversity += 1;
        countSum += count;
    }

    /** Chao1 */
    private List<MetricValue<DiversityMeasure>> computeChao1() {
        if (!measures.contains(Chao1) && !measures.contains(Chao1Std))
            return Collections.emptyList();
        int sobs = diversity;
        double f1 = freqTable.get(1); // singletons
        double f2 = freqTable.get(2); // doubletons
        double f0 = f1 * (f1 - 1) / 2 / (f2 + 1);
        double chao1 = sobs + f0;
        double chao1std = Math.sqrt(
                f0 + f1 * (2 * f1 - 1) * (2 * f1 - 1) / 4 / (f2 + 1) / (f2 + 1) +
                        f1 * f1 * f2 * (f1 - 1) * (f1 - 1) / 4 / (f2 + 1) / (f2 + 1) / (f2 + 1) / (f2 + 1));

        return Arrays.asList(new MetricValue<>(Chao1, chao1), new MetricValue<>(Chao1Std, chao1std));
    }

    /** Clonality, Gini and Shannon-Weiner */
    private List<MetricValue<DiversityMeasure>> computeClonality() {
        if (!(measures.contains(ShannonWeiner) || measures.contains(Clonality) || measures.contains(InverseSimpson) || measures.contains(Gini)))
            return Collections.emptyList();
        double shannonWeiner = 0;
        double gini = 0;

        TLongIntIterator it = freqTable.iterator();
        while (it.hasNext()) {
            it.advance();

            double cloneCount = it.key();
            double nClones = it.value();
            double cloneFreq = cloneCount / countSum;

            gini += -nClones * cloneFreq * cloneFreq;
            shannonWeiner += -nClones * cloneFreq * Math.log(cloneFreq);
        }

        List<MetricValue<DiversityMeasure>> result = new ArrayList<>();
        if (measures.contains(ShannonWeiner))
            result.add(new MetricValue<>(ShannonWeiner, shannonWeiner));
        if (measures.contains(Clonality))
            result.add(new MetricValue<>(Clonality, 1 - shannonWeiner / Math.log(diversity)));
        if (measures.contains(InverseSimpson))
            result.add(new MetricValue<>(InverseSimpson, -1 / gini));
        if (measures.contains(Gini))
            result.add(new MetricValue<>(Gini, 1 - gini));
        return result;
    }

    public static final int DEFAULT_EFRON_THISTED_MAX_DEPTH = 20;
    public static final double DEFAULT_EFRON_THISTED_CV_THRESHOLD = 0.05;

    private List<MetricValue<DiversityMeasure>> computeEfronThisted() {
        return computeEfronThisted(DEFAULT_EFRON_THISTED_MAX_DEPTH, DEFAULT_EFRON_THISTED_CV_THRESHOLD);
    }

    /** Efron-Thisted */
    private List<MetricValue<DiversityMeasure>> computeEfronThisted(int maxDepth, double cvThreshold) {
        if (!measures.contains(EfronThisted) && !measures.contains(EfronThistedStd))
            return Collections.emptyList();

        double S = -1, D = -1, CV;
        for (int depth = 1; depth <= maxDepth; depth++) {
            final double[] h = new double[depth], nx = new double[depth];
            for (int y = 1; y <= depth; y++) {
                nx[y - 1] = freqTable.get(y);

                // Calculate Euler coefficients
                for (int x = 1; x <= y; x++) {
                    double coef = CombinatoricsUtils.binomialCoefficientDouble((y - 1), (x - 1));
                    if (x % 2 == 1)
                        h[x - 1] += coef;
                    else
                        h[x - 1] -= coef;
                }
            }

            // Extrapolate count
            S = diversity + IntStream.range(0, depth).mapToDouble(i -> h[i] * nx[i]).sum();
            D = Math.sqrt(IntStream.range(0, depth).mapToDouble(i -> h[i] * h[i] * nx[i]).sum());
            CV = D / S;

            // Go to maximum count depth, but balance that STD doesn't get too high
            if (CV >= cvThreshold)
                break;
        }

        List<MetricValue<DiversityMeasure>> result = new ArrayList<>();
        if (measures.contains(EfronThisted))
            result.add(new MetricValue<>(EfronThisted, S));
        if (measures.contains(EfronThistedStd))
            result.add(new MetricValue<>(EfronThistedStd, D));
        return result;
    }

    private List<MetricValue<DiversityMeasure>> computeObserved() {
        if (!measures.contains(Observed))
            return Collections.emptyList();
        return Collections.singletonList(new MetricValue<>(Observed, diversity));
    }

    @Override
    public MetricValue<DiversityMeasure>[] result() {
        List<MetricValue<DiversityMeasure>> result = new ArrayList<>();
        result.addAll(computeObserved());
        result.addAll(computeClonality());
        result.addAll(computeChao1());
        result.addAll(computeEfronThisted());
        return result.toArray(new MetricValue[0]);
    }
}
