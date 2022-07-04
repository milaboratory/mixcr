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
package com.milaboratory.mixcr.basictypes.tag;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.milaboratory.primitivio.annotations.Serializable;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Iterator;

/**
 * Class represents a tuple of tags associated with a sequence, alignment, clone or other entity.
 *
 * Tag may be a sample name, cell marker or unique molecular identifier.
 */
@Serializable(by = IO.TagTupleSerializer.class)
public final class TagTuple implements Comparable<TagTuple>, Iterable<TagValue> {
    public static final TagTuple NO_TAGS = new TagTuple();

    final TagValue[] tags;
    private final int hash;

    @SuppressWarnings("UnstableApiUsage")
    public TagTuple(TagValue... tags) {
        this.tags = tags;
        Hasher hasher = Hashing.murmur3_32_fixed().newHasher();
        for (TagValue tag : tags)
            hasher.putInt(tag.hashCode());
        this.hash = hasher.hash().hashCode();
    }

    public TagTuple project(int... idxs) {
        TagValue[] t = new TagValue[idxs.length];
        for (int i = 0; i < idxs.length; i++)
            t[i] = tags[idxs[i]];
        return new TagTuple(t);
    }

    /** Returns whether the tag tuple contains only key tag values, so can be used as a grouping key. */
    public boolean isKey() {
        for (TagValue tag : tags)
            if (!tag.isKey())
                return false;
        return true;
    }

    /**
     * Strips all non-key information (i.e. quality scores) from tags inside the tuple,
     * and returns tag tuple intended to be used as a grouping key.
     */
    public TagTuple key() {
        boolean hasNonKey = false;
        for (TagValue tag : tags)
            if (!tag.isKey()) {
                hasNonKey = true;
                break;
            }
        if (!hasNonKey)
            return this;
        TagValue[] newTags = tags.clone();
        for (int i = 0; i < newTags.length; i++)
            newTags[i] = newTags[i].extractKey();
        return new TagTuple(newTags);
    }

    /**
     * Strips all non-key information (i.e. quality scores) from tags inside the tuple,
     * and returns tag tuple prefix of a specified depth, intended to be used as a grouping key.
     */
    public TagTuple keyPrefix(int depth) {
        if (depth == tags.length)
            return key();
        if (depth == 0)
            return NO_TAGS;
        TagValue[] newTags = Arrays.copyOf(tags, depth);
        for (int i = 0; i < newTags.length; i++)
            newTags[i] = newTags[i].extractKey();
        return new TagTuple(newTags);
    }

    /**
     * Strips all non-key information from tags inside the tuple,
     * and returns tag tuple suffix, by discarding a specified number of tuple elements from the left.
     */
    public TagTuple keySuffix(int depth) {
        if (depth == 0)
            return key();
        if (depth == tags.length)
            return NO_TAGS;
        TagValue[] newTags = Arrays.copyOfRange(tags, depth, tags.length);
        for (int i = 0; i < newTags.length; i++)
            newTags[i] = newTags[i].extractKey();
        return new TagTuple(newTags);
    }

    public TagValue get(int idx) {
        return tags[idx];
    }

    public int size() {
        return tags.length;
    }

    /** Returns clone of internal array */
    public TagValue[] asArray() {
        return tags.clone();
    }

    @NotNull
    @Override
    public Iterator<TagValue> iterator() {
        return Arrays.asList(tags).iterator();
    }

    @Override
    public int compareTo(TagTuple o) {
        int l = Math.min(tags.length, o.tags.length);
        int c;
        for (int i = 0; i < l; i++)
            if ((c = tags[i].compareTo(o.tags[i])) != 0)
                return c;
        return Integer.compare(tags.length, o.tags.length);
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
        StringBuilder sb = new StringBuilder();
        for (TagValue tag : tags) {
            if (sb.length() != 0)
                sb.append('+');
            sb.append(tag.toString());
        }
        return sb.toString();
    }
}
