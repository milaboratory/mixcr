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

import cc.redberry.pipe.CUtils
import cc.redberry.pipe.OutputPort
import cc.redberry.pipe.OutputPortCloseable

/**
 *
 */
class Cluster<T>(val cluster: List<T>)

fun <T> OutputPortCloseable<T>.buildClusters(comparator: Comparator<T>): OutputPort<Cluster<T>> {

    // todo do not copy cluster
    val cluster = mutableListOf<T>()

    return CUtils.makeSynchronized(object : OutputPortCloseable<Cluster<T>> {
        override fun close() {
            this@buildClusters.close()
        }

        override fun take(): Cluster<T>? {
            while (true) {
                val element = this@buildClusters.take()
                if (element == null) {
                    if (cluster.isNotEmpty()) {
                        val copy = ArrayList(cluster)

                        // new cluster
                        cluster.clear()
                        return Cluster(copy)
                    }
                    return null
                }
                if (cluster.isEmpty()) {
                    cluster.add(element)
                    continue
                }
                val lastAdded = cluster[cluster.size - 1]
                if (comparator.compare(lastAdded, element) == 0) {
                    // new cluster
                    cluster.add(element)
                } else {
                    val copy = ArrayList(cluster)

                    // new cluster
                    cluster.clear()
                    cluster.add(element)
                    return Cluster(copy)
                }
            }
        }
    })
}
