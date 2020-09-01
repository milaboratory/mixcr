package com.milaboratory.mixcr.postanalysis.overlap;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Objects;

/**
 *
 */
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
public final class OverlapKey<K> {
    @JsonProperty("key")
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "type")
    public final K key;
    @JsonProperty("i1")
    public final int i1;
    @JsonProperty("i2")
    public final int i2;

    @JsonCreator
    public OverlapKey(@JsonProperty("key") K key, @JsonProperty("i1") int i1, @JsonProperty("i2") int i2) {
        this.key = key;
        this.i1 = i1;
        this.i2 = i2;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OverlapKey<?> that = (OverlapKey<?>) o;
        return i1 == that.i1 &&
                i2 == that.i2 &&
                Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, i1, i2);
    }

    @Override
    public String toString() {
        return "OverlapKey{" +
                "key=" + key +
                ", i1=" + i1 +
                ", i2=" + i2 +
                '}';
    }
}
