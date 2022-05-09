package com.milaboratory.mixcr.postanalysis;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.util.concurrent.AtomicDouble;
import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class SetPreprocessorStat {
    @JsonProperty("preprocId")
    public final String preprocId;
    @JsonProperty("dropped")
    public final boolean dropped;
    @JsonProperty("nElementsBefore")
    public final long nElementsBefore;
    @JsonProperty("nElementsAfter")
    public final long nElementsAfter;
    @JsonProperty("sumWeightBefore")
    public final double sumWeightBefore;
    @JsonProperty("sumWeightAfter")
    public final double sumWeightAfter;

    @JsonCreator
    public SetPreprocessorStat(@JsonProperty("preprocId") String preprocId,
                               @JsonProperty("dropped") boolean dropped,
                               @JsonProperty("nElementsBefore") long nElementsBefore,
                               @JsonProperty("nElementsAfter") long nElementsAfter,
                               @JsonProperty("sumWeightBefore") double sumWeightBefore,
                               @JsonProperty("sumWeightAfter") double sumWeightAfter) {
        this.preprocId = preprocId;
        this.dropped = dropped;
        this.nElementsBefore = nElementsBefore;
        this.nElementsAfter = nElementsAfter;
        this.sumWeightBefore = sumWeightBefore;
        this.sumWeightAfter = sumWeightAfter;
    }

    public SetPreprocessorStat(String preprocId,
                               long nElementsBefore,
                               long nElementsAfter,
                               double sumWeightBefore,
                               double sumWeightAfter) {
        this.preprocId = preprocId;
        this.dropped = nElementsAfter == 0;
        this.nElementsBefore = nElementsBefore;
        this.nElementsAfter = nElementsAfter;
        this.sumWeightBefore = sumWeightBefore;
        this.sumWeightAfter = sumWeightAfter;
    }

    public SetPreprocessorStat(String preprocId) {
        this(preprocId, true, -1, -1, Double.NaN, Double.NaN);
    }

    public SetPreprocessorStat setPreprocId(String newId) {
        return new SetPreprocessorStat(newId, dropped, nElementsBefore, nElementsAfter, sumWeightBefore, sumWeightAfter);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SetPreprocessorStat that = (SetPreprocessorStat) o;
        return dropped == that.dropped && nElementsBefore == that.nElementsBefore && nElementsAfter == that.nElementsAfter && Double.compare(that.sumWeightBefore, sumWeightBefore) == 0 && Double.compare(that.sumWeightAfter, sumWeightAfter) == 0 && Objects.equals(preprocId, that.preprocId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(preprocId, dropped, nElementsBefore, nElementsAfter, sumWeightBefore, sumWeightAfter);
    }

    @Override
    public String toString() {
        return "SetPreprocessorStat{" +
                "preprocId='" + preprocId + '\'' +
                ", dropped=" + dropped +
                ", nElementsBefore=" + nElementsBefore +
                ", nElementsAfter=" + nElementsAfter +
                ", sumWeightBefore=" + sumWeightBefore +
                ", sumWeightAfter=" + sumWeightAfter +
                '}';
    }

    public static SetPreprocessorStat cumulative(List<SetPreprocessorStat> stats) {
        SetPreprocessorStat first = stats.get(0);
        SetPreprocessorStat last = stats.get(stats.size() - 1);
        return new SetPreprocessorStat(
                stats.stream().map(s -> s.preprocId).collect(Collectors.joining(" | ")),
                first.nElementsBefore,
                last.nElementsAfter,
                first.sumWeightBefore,
                last.sumWeightAfter);
    }

    private static final class BuilderForSample<T> {
        final String preprocId;
        final AtomicBoolean dropped = new AtomicBoolean(false);
        final AtomicLong nElementsBefore = new AtomicLong(0);
        final AtomicLong nElementsAfter = new AtomicLong(0);
        final AtomicDouble sumWeightBefore = new AtomicDouble(0);
        final AtomicDouble sumWeightAfter = new AtomicDouble(0);
        final WeightFunction<T> wtFunc;

        public BuilderForSample(String preprocId, WeightFunction<T> wtFunc) {
            this.preprocId = preprocId;
            this.wtFunc = wtFunc;
        }

        public void before(T t) {
            nElementsBefore.incrementAndGet();
            sumWeightBefore.addAndGet(wtFunc.weight(t));
        }

        public void after(T t) {
            nElementsAfter.incrementAndGet();
            sumWeightAfter.addAndGet(wtFunc.weight(t));
        }

        public void drop() {
            dropped.set(true);
        }

        public SetPreprocessorStat build() {
            return new SetPreprocessorStat(
                    preprocId,
                    dropped.get(),
                    nElementsBefore.get(),
                    nElementsAfter.get(),
                    sumWeightBefore.get(),
                    sumWeightAfter.get()
            );
        }
    }

    public static final class Builder<T> {
        final String preprocId;
        final TIntObjectHashMap<BuilderForSample<T>> map = new TIntObjectHashMap<>();
        final WeightFunction<T> wtFunc;

        public Builder(String preprocId,
                       WeightFunction<T> wtFunc) {
            this.preprocId = preprocId;
            this.wtFunc = wtFunc;
        }

        private BuilderForSample<T> builder(int iDataset) {
            BuilderForSample<T> b = map.get(iDataset);
            if (b == null)
                map.put(iDataset, b = new BuilderForSample<>(preprocId, wtFunc));
            return b;
        }

        public void drop(int iDataset) {
            builder(iDataset).drop();
        }

        public void before(int iDataset, T t) {
            builder(iDataset).before(t);
        }

        public void after(int iDataset, T t) {
            builder(iDataset).after(t);
        }

        public SetPreprocessorStat getStat(int iDataset) {
            BuilderForSample<T> b = map.get(iDataset);
            if (b == null)
                return new SetPreprocessorStat(preprocId);
            return b.build();
        }

        public TIntObjectHashMap<List<SetPreprocessorStat>> getStatMap() {
            TIntObjectHashMap<List<SetPreprocessorStat>> r = new TIntObjectHashMap<>();
            TIntObjectIterator<BuilderForSample<T>> it = map.iterator();
            while (it.hasNext()) {
                it.advance();
                r.put(it.key(), Collections.singletonList(it.value().build()));
            }
            return r;
        }
    }
}

