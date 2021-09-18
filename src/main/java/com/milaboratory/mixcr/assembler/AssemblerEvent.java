/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 * 
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 * 
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.assembler;

import java.util.Arrays;

public final class AssemblerEvent implements Comparable<AssemblerEvent> {
    //auxiliary status codes used instead of cloneIndex
    public static final int DROPPED = -2, DEFERRED = -3, EOF = -1;
    public final long alignmentsIndex;
    public final int cloneIndex;

    public AssemblerEvent(long alignmentsIndex, int cloneIndex) {
        if (cloneIndex == EOF)
            throw new IllegalArgumentException();
        this.alignmentsIndex = alignmentsIndex;
        this.cloneIndex = cloneIndex;
    }

    @Override
    public int compareTo(AssemblerEvent o) {
        return Long.compare(alignmentsIndex, o.alignmentsIndex);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AssemblerEvent)) return false;

        AssemblerEvent that = (AssemblerEvent) o;

        if (alignmentsIndex != that.alignmentsIndex) return false;
        return cloneIndex == that.cloneIndex;
    }

    @Override
    public int hashCode() {
        int result = (int) (alignmentsIndex ^ (alignmentsIndex >>> 32));
        result = 31 * result + cloneIndex;
        return result;
    }
}
