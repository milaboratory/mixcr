package com.milaboratory.mixcr.postanalysis;

import com.milaboratory.mixcr.basictypes.Clone;

/**
 *
 */
public final class WeightFunctions {
    private WeightFunctions() {}

    public static final class Count implements WeightFunction<Clone> {
        @Override
        public double weight(Clone clone) {
            return clone.getCount();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            return true;
        }

        @Override
        public int hashCode() {
            return 17;
        }
    }

    public static final class NoWeight<T> implements WeightFunction<T> {
        @Override
        public double weight(T clone) {
            return 1.0;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            return true;
        }

        @Override
        public int hashCode() {
            return 11;
        }
    }
}
