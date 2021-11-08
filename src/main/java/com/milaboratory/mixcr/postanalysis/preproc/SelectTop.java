package com.milaboratory.mixcr.postanalysis.preproc;

import cc.redberry.pipe.InputPort;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.postanalysis.*;
import gnu.trove.impl.Constants;
import gnu.trove.map.hash.TLongLongHashMap;

import java.util.Arrays;
import java.util.Objects;

import static java.lang.Math.round;

/**
 *
 */
public class SelectTop<T> implements SetPreprocessor<T> {
    final WeightFunction<T> weight;
    final double abundanceFraction;
    final int numberOfTop;

    SelectTop(WeightFunction<T> weight,
              double abundanceFraction,
              int numberOfTop) {
        if (!Double.isNaN(abundanceFraction) && (abundanceFraction <= 0 || abundanceFraction > 1.0))
            throw new IllegalArgumentException();
        if (numberOfTop != -1 && numberOfTop <= 0)
            throw new IllegalArgumentException();

        this.weight = weight;
        this.abundanceFraction = abundanceFraction;
        this.numberOfTop = numberOfTop;
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
        return t -> {
            if (hist.isEmpty())
                return null;

            long wt = Math.round(this.weight.weight(t));
            long n = hist.get(wt);
            if (n == -1)
                return null;
            assert n != 0;
            if (n == 1)
                hist.remove(wt);
            else
                hist.adjustValue(wt, -1);

            return t;
        };
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
        public SetPreprocessor<T> getInstance() {
            return new SelectTop<>(weight, abundanceFraction, numberOfTop);
        }

        @Override
        public String[] description() {
            String s1 = Double.isNaN(abundanceFraction) ? null : "Select top clonotypes constituting " + (abundanceFraction * 100) + "% of reads";
            String s2 = numberOfTop == -1 ? null : "Select top " + numberOfTop + " clonotypes";
            if (s1 == null)
                return new String[]{s2};
            else if (s2 == null)
                return new String[]{s1};
            return new String[]{s1, s2,};
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
