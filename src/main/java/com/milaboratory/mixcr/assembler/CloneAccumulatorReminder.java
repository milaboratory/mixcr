/*
 * Copyright (c) 2014-2023, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
package com.milaboratory.mixcr.assembler;

import java.util.Objects;

public final class CloneAccumulatorReminder {
    private final CloneAccumulator parent;
    private final boolean complete;
    private final long coreCount, mappedCount;

    public CloneAccumulatorReminder(CloneAccumulator parent) {
        this.parent = parent;
        this.complete = true;
        this.coreCount = parent.getCoreCount();
        this.mappedCount = parent.getMappedCount();
    }

    public CloneAccumulatorReminder(CloneAccumulator parent, long coreCount, long mappedCount) {
        this.parent = parent;
        this.complete = false;
        this.coreCount = parent.getCoreCount();
        this.mappedCount = parent.getMappedCount();
    }

    public CloneAccumulator getParent() {
        return parent;
    }

    public boolean isComplete() {
        return complete;
    }

    public long getCoreCount() {
        return coreCount;
    }

    public long getMappedCount() {
        return mappedCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CloneAccumulatorReminder that = (CloneAccumulatorReminder) o;
        return complete == that.complete && coreCount == that.coreCount && mappedCount == that.mappedCount && parent.equals(that.parent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parent, complete, coreCount, mappedCount);
    }
}
