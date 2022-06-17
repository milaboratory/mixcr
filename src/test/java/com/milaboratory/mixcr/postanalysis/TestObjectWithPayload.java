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
