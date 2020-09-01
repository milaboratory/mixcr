package com.milaboratory.mixcr.postanalysis.spectratype;

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
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE)
public class SpectratypeKey<Payload> {
    @JsonProperty("length")
    public final int length;
    /** null payload for "other" */
    @JsonProperty("payload")
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "type")
    public final Payload payload;

    @JsonCreator
    public SpectratypeKey(@JsonProperty("length") int length, @JsonProperty("payload") Payload payload) {
        this.length = length;
        this.payload = payload;
    }

    public boolean isOther() {
        return payload == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SpectratypeKey<?> that = (SpectratypeKey<?>) o;
        return length == that.length &&
                Objects.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(length, payload);
    }

    @Override
    public String toString() {
        return length + " " + payload;
    }
}
