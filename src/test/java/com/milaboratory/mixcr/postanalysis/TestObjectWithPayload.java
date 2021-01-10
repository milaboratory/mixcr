package com.milaboratory.mixcr.postanalysis;

import java.util.Objects;

/**
 *
 */
public class TestObjectWithPayload<Payload> extends TestObject {
    public final Payload payload;

    public TestObjectWithPayload(double value, double weight, Payload payload) {
        super(value, weight);
        this.payload = payload;
    }

    @Override
    public String toString() {
        return "val:" + value + " wt:" + weight + " pa:" + payload;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        TestObjectWithPayload<?> that = (TestObjectWithPayload<?>) o;
        return Objects.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), payload);
    }
}
