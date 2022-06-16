package com.milaboratory.primitivio

inline fun <reified T : Any> PrimitivI.readObjectOptional(): T? = readObject(T::class.java)
inline fun <reified T : Any> PrimitivI.readObjectRequired(): T = readObject(T::class.java)

inline fun <reified K : Any, reified V : Any> PrimitivI.readMap(): Map<K, V> =
    Util.readMap(this, K::class.java, V::class.java)

inline fun <reified T : Any> PrimitivI.readList(): List<T> = Util.readList(T::class.java, this)
