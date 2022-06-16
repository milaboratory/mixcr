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
