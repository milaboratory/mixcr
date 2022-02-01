package com.milaboratory.mixcr.util;

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class Cluster<T> {
    public final List<T> cluster;

    public Cluster(List<T> cluster) {
        this.cluster = cluster;
    }

    public static class Builder<T> {
        private final List<T> cluster = new ArrayList<>();

        public Builder<T> add(T element) {
            cluster.add(element);
            return this;
        }

        public List<T> getCurrentCluster() {
            return cluster;
        }

        public Cluster<T> build() {
            return new Cluster<>(cluster);
        }
    }
}
