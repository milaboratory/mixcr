package com.milaboratory.mixcr.util

/**
 *
 */
class Cluster<T>(val cluster: List<T>) {
    class Builder<T> {
        private val cluster: MutableList<T> = ArrayList()
        fun add(element: T): Builder<T> {
            cluster.add(element)
            return this
        }

        val currentCluster: List<T>
            get() = cluster

        fun build(): Cluster<T> {
            return Cluster(cluster)
        }
    }
}
