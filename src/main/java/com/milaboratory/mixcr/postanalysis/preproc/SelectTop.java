package com.milaboratory.mixcr.postanalysis.preproc;

import cc.redberry.pipe.InputPort;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.postanalysis.*;
import gnu.trove.impl.Constants;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TLongLongHashMap;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static java.lang.Math.round;

/**
 *
 */
public class SelectTop<T> implements SetPreprocessor<T> {
    final WeightFunction<T> weight;
    final double abundanceFraction;
    final int numberOfTop;
    final String id;
    final SetPreprocessorStat.Builder<T> stats;

    SelectTop(WeightFunction<T> weight,
              double abundanceFraction,
              int numberOfTop,
              String id) {
        if (!Double.isNaN(abundanceFraction) && (abundanceFraction <= 0 || abundanceFraction > 1.0))
            throw new IllegalArgumentException();
        if (numberOfTop != -1 && numberOfTop <= 0)
            throw new IllegalArgumentException();

        this.weight = weight;
        this.abundanceFraction = abundanceFraction;
        this.numberOfTop = numberOfTop;
        this.id = id;
        this.stats = new SetPreprocessorStat.Builder<>(id, WeightFunctions.Default());
    }

    private boolean computeCumulativeTop() {
        return !Double.isNaN(abundanceFraction);
    }

    private boolean computeFixedTop() {
        return numberOfTop != -1;
    }

    private ComputeHists2 computeHists;

    @Override
    public SetPreprocessorSetup<T> nextSetupStep() {
        if (computeHists == null) {
            return computeHists = new ComputeHists2();
        }
        return null;
    }

    @Override
    public MappingFunction<T> getMapper(int iDataset) {
        TLongLongHashMap hist = computeHists.downsampledHist(iDataset);
        if (hist.isEmpty()) {
            stats.drop(iDataset);
            return t -> null;
        }

        return t -> {
            stats.before(iDataset, t);
            long wt = Math.round(this.weight.weight(t));
            long n = hist.get(wt);
            if (n == -1)
                return null;
            assert n != 0;
            if (n == 1)
                hist.remove(wt);
            else
                hist.adjustValue(wt, -1);

            stats.after(iDataset, t);
            return t;
        };
    }

    @Override
    public TIntObjectHashMap<List<SetPreprocessorStat>> getStat() {
        return stats.getStatMap();
    }

    @Override
    public String id() {
        return id;
    }

    private class ComputeHists2 implements SetPreprocessorSetup<T> {
        // weight -> n elements
        private TLongLongHashMap[] hists;
        private double[] totals;
        private boolean initialized = false;

        @Override
        public void initialize(int nDatasets) {
            if (initialized)
                throw new IllegalStateException();
            initialized = true;
            totals = new double[nDatasets];
            hists = new TLongLongHashMap[nDatasets];
            for (int i = 0; i < hists.length; i++) {
                hists[i] = new TLongLongHashMap();
            }
        }

        @Override
        public InputPort<T> consumer(int i) {
            if (!initialized)
                throw new IllegalStateException();
            return el -> {
                if (el == null) {
                    return;
                }
                double dwt = weight.weight(el);
                if (dwt == 0)
                    return;
                totals[i] += dwt;
                long lwt = round(dwt);
                if (lwt == 0)
                    lwt = 1;
                hists[i].adjustOrPutValue(lwt, 1, 1);
            };
        }

        TLongLongHashMap downsampledHist(int iDataset) {
            double total = computeHists.totals[iDataset];
            double sumThreshold = computeCumulativeTop() ? total * abundanceFraction : Double.MAX_VALUE;
            int nThreshold = computeFixedTop() ? numberOfTop : Integer.MAX_VALUE;

            TLongLongHashMap fullHist = computeHists.hists[iDataset];
            TLongLongHashMap downHist = new TLongLongHashMap(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, -1, -1);

            long[] weights = fullHist.keys();
            Arrays.sort(weights);

            double sumTotal = 0;
            int nTotal = 0;
            for (int i = weights.length - 1; i >= 0; --i) {
                long wt = weights[i];
                long n = fullHist.get(wt);

                if (nTotal + n > nThreshold) {
                    if (nThreshold - nTotal > 0)
                        downHist.put(wt, nThreshold - nTotal);
                    break;
                }

                if (n * wt + sumTotal > sumThreshold) {
                    long nToLeave = (long) Math.ceil((sumThreshold - sumTotal) / wt);
                    if (nToLeave <= 0)
                        break;
                    if (nToLeave < n) {
                        downHist.put(wt, nToLeave);
                        break;
                    }
                    assert nToLeave == n;
                }


                nTotal += n;
                sumTotal += n * wt;
                downHist.put(wt, n);
            }

            return downHist;
        }
    }

    public static final class Factory<T> implements SetPreprocessorFactory<T> {
        @JsonProperty("weight")
        private WeightFunction<T> weight;
        @JsonProperty("abundanceFraction")
        private double abundanceFraction = Double.NaN;
        @JsonProperty("numberOfTop")
        private int numberOfTop = -1;

        private Factory() {}

        public Factory(WeightFunction<T> weight, double abundanceFraction) {
            this.weight = weight;
            this.abundanceFraction = abundanceFraction;
        }

        public Factory(WeightFunction<T> weight, int numberOfTop) {
            this.weight = weight;
            this.numberOfTop = numberOfTop;
        }

        @Override
        public SetPreprocessor<T> newInstance() {
            return new SelectTop<>(weight, abundanceFraction, numberOfTop, id());
        }

        @Override
        public String id() {
            String topAbundance = Double.isNaN(abundanceFraction) ? null : "Cumulative top " + (abundanceFraction * 100);
            String topCount = numberOfTop == -1 ? null : "Top " + numberOfTop;
            if (topAbundance == null)
                return topCount;
            else if (topCount == null)
                return topAbundance;
            return topAbundance + " | " + topCount;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Factory<?> factory = (Factory<?>) o;
            return Double.compare(factory.abundanceFraction, abundanceFraction) == 0 && numberOfTop == factory.numberOfTop && Objects.equals(weight, factory.weight);
        }

        @Override
        public int hashCode() {
            return Objects.hash(weight, abundanceFraction, numberOfTop);
        }
    }
}
