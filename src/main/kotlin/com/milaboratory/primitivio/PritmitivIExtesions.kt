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
package com.milaboratory.primitivio

inline fun <reified T : Any> PrimitivI.readObjectOptional(): T? = readObject(T::class.java)
inline fun <reified T : Any> PrimitivI.readObjectRequired(): T = readObject(T::class.java)

inline fun <reified K : Any, reified V : Any> PrimitivI.readMap(): Map<K, V> =
    Util.readMap(this, K::class.java, V::class.java)

inline fun <reified T : Any> PrimitivI.readList(): List<T> = Util.readList(T::class.java, this)
