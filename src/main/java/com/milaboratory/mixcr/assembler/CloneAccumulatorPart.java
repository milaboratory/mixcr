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

import com.milaboratory.mixcr.basictypes.tag.TagCountAggregator;

import java.util.Objects;

public final class CloneAccumulatorPart {
    private final CloneAccumulator parent;
    private final boolean complete;
    private final TagCountAggregator tagAggregator;
    private final long coreCount, mappedCount;

    public CloneAccumulatorPart(CloneAccumulator parent) {
        this.parent = parent;
        this.complete = true;
        this.tagAggregator = parent.getTagAggregator();
        this.coreCount = parent.getCoreCount();
        this.mappedCount = parent.getMappedCount();
    }

    public CloneAccumulatorPart(CloneAccumulator parent, TagCountAggregator tagAggregator,
                                long coreCount, long mappedCount) {
        this.parent = parent;
        this.complete = false;
        this.tagAggregator = tagAggregator;
        this.coreCount = parent.getCoreCount();
        this.mappedCount = parent.getMappedCount();
    }

    public CloneAccumulator getParent() {
        return parent;
    }

    /** True if part represents the whole parent clonotype accumulator */
    public boolean isComplete() {
        return complete;
    }

    public long getCoreCount() {
        return coreCount;
    }

    public long getMappedCount() {
        return mappedCount;
    }

    public long getCount() {
        return coreCount + mappedCount;
    }

    public TagCountAggregator getTagAggregator() {
        return tagAggregator;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CloneAccumulatorPart that = (CloneAccumulatorPart) o;
        return complete == that.complete && coreCount == that.coreCount && mappedCount == that.mappedCount && parent.equals(that.parent) && tagAggregator.equals(that.tagAggregator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parent, complete, tagAggregator, coreCount, mappedCount);
    }
}
