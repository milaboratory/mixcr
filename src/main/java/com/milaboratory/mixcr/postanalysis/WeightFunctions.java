/*
 * Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
package com.milaboratory.mixcr.postanalysis;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.milaboratory.mixcr.basictypes.Clone;

import java.util.Objects;

/**
 *
 */
public final class WeightFunctions {
    private WeightFunctions() {}

    public static final Count Count = new Count();

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

    public static final class TagCount implements WeightFunction<Clone> {
        final int tagIndex;
        @JsonIgnore
        final int[] indices;

        @JsonCreator
        public TagCount(int tagIndex) {
            this.tagIndex = tagIndex;
            this.indices = new int[tagIndex + 1];
            for (int i = 0; i < tagIndex + 1; i++)
                indices[i] = i;
        }

        @Override
        public double weight(Clone clone) {
            return 1.0 * clone.getTagCount().projectionSize(indices);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TagCount tagCount = (TagCount) o;
            return tagIndex == tagCount.tagIndex;
        }

        @Override
        public int hashCode() {
            return Objects.hash(tagIndex);
        }
    }

    @SuppressWarnings("rawtypes")
    public static final NoWeight NoWeight = new NoWeight();

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

    public static <T> WeightFunction<T> Default() {return new DefaultWtFunction<T>();}

    public static class DefaultWtFunction<T> implements WeightFunction<T> {
        @Override
        public double weight(T o) {
            if (o instanceof Clone)
                return ((Clone) o).getCount();
            else
                throw new RuntimeException("Unsupported for class: " + o.getClass());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            return true;
        }

        @Override
        public int hashCode() {
            return 12;
        }
    }
}
