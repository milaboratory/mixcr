package com.milaboratory.mixcr.postanalysis.spectratype;

import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.postanalysis.Aggregator;
import com.milaboratory.mixcr.postanalysis.MetricValue;
import com.milaboratory.mixcr.postanalysis.WeightFunction;
import gnu.trove.impl.Constants;
import gnu.trove.iterator.TObjectDoubleIterator;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TObjectDoubleHashMap;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 *
 */
public class SpectratypeAggregator<Payload> implements Aggregator<SpectratypeKey<Payload>, Clone> {
    final int nTopClones;
    final SpectratypeKeyFunction<Payload, Clone> keyFunction;
    final WeightFunction<Clone> weightFunction;
    /** payload -> weight */
    final TObjectDoubleHashMap<Payload> weights = new TObjectDoubleHashMap<>(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, Double.NaN);
    /** sorted set */
    final TreeSet<Count<Payload>> payloadCounts = new TreeSet<>();
    final TIntObjectHashMap<Bin<Payload>> bins = new TIntObjectHashMap<>();

    public SpectratypeAggregator(int nTopClones, SpectratypeKeyFunction<Payload, Clone> keyFunction, WeightFunction<Clone> weightFunction) {
        assert nTopClones > 0;
        this.nTopClones = nTopClones;
        this.keyFunction = keyFunction;
        this.weightFunction = weightFunction;
    }

    @Override
    public void consume(Clone obj) {
        SpectratypeKey<Payload> key = keyFunction.getKey(obj);
        Payload payload = key.payload;
        double weight = weightFunction.weight(obj);
        Count<Payload> count = new Count<>(payload, weight);

        Bin<Payload> bin = bins.get(key.length);
        if (bin == null) {
            bin = new Bin<>(key.length);
            bins.put(key.length, bin);
        }

        bin.add(count);
        if (!weights.containsKey(payload)) {
            weights.put(payload, weight);
            payloadCounts.add(count);

            if (weights.size() > nTopClones) {
                Count<Payload> markAsOther = payloadCounts.pollFirst();
                weights.remove(markAsOther.payload);
                for (Bin<Payload> b : bins.valueCollection())
                    b.markAsOther(markAsOther.payload);
            }
        }
    }

    @Override
    public MetricValue<SpectratypeKey<Payload>>[] result() {
        List<MetricValue<SpectratypeKey<Payload>>> result = new ArrayList<>();
        for (Bin<Payload> bin : bins.valueCollection()) {
            TObjectDoubleIterator<Payload> it = bin.weights.iterator();
            while (it.hasNext()) {
                it.advance();
                result.add(new MetricValue<>(new SpectratypeKey<>(bin.x, it.key()), it.value()));
            }
            result.add(new MetricValue<>(new SpectratypeKey<>(bin.x, null), bin.other));
        }
        return result.toArray(new MetricValue[0]);
    }

    private static final class Bin<Payload> {
        final int x;
        /** weight of all "other" payloads */
        double other = 0.0;
        /** payload -> weight */
        final TObjectDoubleHashMap<Payload> weights = new TObjectDoubleHashMap<>(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, Double.NaN);

        Bin(int x) { this.x = x; }

        void add(Count<Payload> p) {
            if (p.payload == null)
                other += p.weight;
            else
                weights.adjustOrPutValue(p.payload, p.weight, p.weight);
        }

        void markAsOther(Payload markAsOther) {
            double w = weights.remove(markAsOther);
            if (!Double.isNaN(w))
                other += w;
        }
    }

    private static final class Count<Payload> implements Comparable<Count<Payload>> {
        final Payload payload;
        final double weight;

        public Count(Payload payload, double weight) {
            this.payload = payload;
            this.weight = weight;
        }

        @Override
        public int compareTo(Count<Payload> o) {
            return Double.compare(weight, o.weight);
        }
    }
}
