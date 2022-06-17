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
package com.milaboratory.mixcr.assembler;

public final class AssemblerEvent implements Comparable<AssemblerEvent> {
    // auxiliary status codes used in place of cloneIndex
    public static final int DROPPED = -2, DEFERRED = -3, EOF = -1;
    public final long preCloneIndex;
    public final int cloneIndex;

    public AssemblerEvent(long preCloneIndex, int cloneIndex) {
        if (cloneIndex == EOF)
            throw new IllegalArgumentException();
        this.preCloneIndex = preCloneIndex;
        this.cloneIndex = cloneIndex;
    }

    @Override
    public int compareTo(AssemblerEvent o) {
        return Long.compare(preCloneIndex, o.preCloneIndex);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AssemblerEvent)) return false;

        AssemblerEvent that = (AssemblerEvent) o;

        if (preCloneIndex != that.preCloneIndex) return false;
        return cloneIndex == that.cloneIndex;
    }

    @Override
    public int hashCode() {
        int result = (int) (preCloneIndex ^ (preCloneIndex >>> 32));
        result = 31 * result + cloneIndex;
        return result;
    }
}
