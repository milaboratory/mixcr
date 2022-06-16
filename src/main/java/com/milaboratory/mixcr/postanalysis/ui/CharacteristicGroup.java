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
package com.milaboratory.mixcr.postanalysis.ui;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.postanalysis.Characteristic;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 *
 */
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
public class CharacteristicGroup<K, T> {
    @JsonProperty("name")
    public final String name;
    @JsonProperty("characteristics")
    public final List<? extends Characteristic<K, T>> characteristics;
    @JsonProperty("views")
    public final List<CharacteristicGroupOutputExtractor<K>> views;

    @JsonCreator
    public CharacteristicGroup(@JsonProperty("name") String name,
                               @JsonProperty("characteristics") List<? extends Characteristic<K, T>> characteristics,
                               @JsonProperty("views") List<CharacteristicGroupOutputExtractor<K>> views) {
        this.name = name;
        this.characteristics = characteristics;
        this.views = views;
    }

    public CharacteristicGroup<K, T> transform(Function<Characteristic<K, T>, Characteristic<K, T>> mapper) {
        return new CharacteristicGroup<>(name, characteristics.stream().map(mapper).collect(Collectors.toList()), views);
    }

    @Override
    public String toString() {
        return "CharacteristicGroup{" +
                "name='" + name + '\'' +
                ", characteristics=" + characteristics +
                ", views=" + views +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CharacteristicGroup<?, ?> that = (CharacteristicGroup<?, ?>) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(characteristics, that.characteristics) &&
                Objects.equals(views, that.views);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, characteristics, views);
    }
}
