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
public class TestObject {
    public final double value;
    public final double weight;

    public TestObject(double value, double weight) {
        this.value = value;
        this.weight = weight;
    }

    public TestObject setValue(double newValue) {
        return new TestObject(newValue, weight);
    }

    public TestObject setWeight(double newWeight) {
        return new TestObject(value, newWeight);
    }

    @Override
    public String toString() {
        return value + ": " + weight;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TestObject that = (TestObject) o;
        return Double.compare(that.value, value) == 0 && Double.compare(that.weight, weight) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, weight);
    }
}
