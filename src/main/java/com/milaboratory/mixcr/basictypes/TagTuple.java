package com.milaboratory.mixcr.basictypes;

import java.util.Arrays;

/**
 *
 */
public final class TagTuple {
    public static final TagTuple EMPTY = new TagTuple(new String[0]);
    public final String[] tags;
    private final int hash;

    public TagTuple(String... tags) {
        this.tags = tags;
        this.hash = Arrays.hashCode(tags);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TagTuple tagTuple = (TagTuple) o;
        return hash == tagTuple.hash && Arrays.equals(tags, tagTuple.tags);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return String.join("+", tags);
    }
}
