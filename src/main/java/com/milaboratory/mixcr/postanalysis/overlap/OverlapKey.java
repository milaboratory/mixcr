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
public final class OverlapKey<K>  {
    @JsonProperty("key")
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "type")
    public final K key;
    @JsonProperty("id1")
    public final String id1;
    @JsonProperty("id2")
    public final String id2;

    @JsonCreator
    public OverlapKey(@JsonProperty("key") K key, @JsonProperty("id1") String id1, @JsonProperty("id2") String id2) {
        this.key = key;
        this.id1 = id1;
        this.id2 = id2;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OverlapKey<?> that = (OverlapKey<?>) o;
        return Objects.equals(key, that.key) && Objects.equals(id1, that.id1) && Objects.equals(id2, that.id2);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, id1, id2);
    }

    @Override
    public String toString() {
        return "OverlapKey{" +
                "key=" + key +
                ", id1='" + id1 + '\'' +
                ", id2='" + id2 + '\'' +
                '}';
    }
}
