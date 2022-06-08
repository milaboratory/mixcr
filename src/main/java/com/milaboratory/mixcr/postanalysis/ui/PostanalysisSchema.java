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
import com.milaboratory.mixcr.postanalysis.Dataset;
import com.milaboratory.mixcr.postanalysis.PostanalysisResult;
import com.milaboratory.mixcr.postanalysis.PostanalysisRunner;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 */
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class PostanalysisSchema<T> {
    @JsonProperty("isOverlap")
    public final boolean isOverlap;
    @JsonProperty("tables")
    public final List<CharacteristicGroup<?, T>> tables;

    @JsonCreator
    public PostanalysisSchema(@JsonProperty("isOverlap") boolean isOverlap,
                              @JsonProperty("tables") List<CharacteristicGroup<?, T>> tables) {
        this.isOverlap = isOverlap;
        this.tables = tables;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public PostanalysisSchema<T> transform(Function<Characteristic<?, T>, Characteristic<?, T>> mapper) {
        Stream<? extends CharacteristicGroup<?, T>> characteristicGroupStream = tables.stream()
                .map(gr -> (CharacteristicGroup<?, T>) gr.transform(c -> (Characteristic) mapper.apply(c)));
        List<CharacteristicGroup<?, T>> collect = characteristicGroupStream
                .collect(Collectors.<CharacteristicGroup<?, T>>toList());
        return new PostanalysisSchema<>(isOverlap, collect);
    }

    public List<Characteristic<?, T>> getAllCharacterisitcs() {
        return tables.stream().flatMap(t -> t.characteristics.stream()).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    public <K> CharacteristicGroup<K, T> getGroup(String name) {
        return (CharacteristicGroup<K, T>) tables.stream().filter(g -> g.name.equals(name)).findAny().orElse(null);
    }

    @Override
    public String toString() {
        return tables.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PostanalysisSchema<?> that = (PostanalysisSchema<?>) o;
        return isOverlap == that.isOverlap && Objects.equals(tables, that.tables);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isOverlap, tables);
    }

    public PostanalysisResult run(List<Dataset<T>> datasets) {
        PostanalysisRunner<T> runner = new PostanalysisRunner<>();
        runner.addCharacteristics(getAllCharacterisitcs());
        return runner.run(datasets);
    }
}
