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
package com.milaboratory.mixcr.util;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Objects;
import java.util.function.Function;

/**
 *
 */
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE
)
public class Tuple2<K1, K2> {
    @JsonProperty("_1")
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    public final K1 _1;
    @JsonProperty("_2")
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    public final K2 _2;

    @JsonCreator
    public Tuple2(@JsonProperty("_1") K1 _1,
                  @JsonProperty("_2") K2 _2) {
        this._1 = _1;
        this._2 = _2;
    }

    public <_K1, _K2> Tuple2<_K1, _K2> map(Function<K1, _K1> f1, Function<K2, _K2> f2) {
        return new Tuple2<>(f1.apply(_1), f2.apply(_2));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tuple2<?, ?> tuple2 = (Tuple2<?, ?>) o;
        return Objects.equals(_1, tuple2._1) &&
                Objects.equals(_2, tuple2._2);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_1, _2);
    }
}
