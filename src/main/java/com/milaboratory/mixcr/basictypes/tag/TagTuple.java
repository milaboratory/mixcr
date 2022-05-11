package com.milaboratory.mixcr.basictypes.tag;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.milaboratory.util.ByteString;

import java.util.Arrays;

/**
 * Class represents a tuple of tags associated with a sequence, alignment, clone or other entity.
 *
 * Tag may be a sample name, cell marker or unique molecular identifier.
 */
public final class TagTuple implements Comparable<TagTuple> {
    public final TagValue[] tags;
    private final int hash;

    @SuppressWarnings("UnstableApiUsage")
    public TagTuple(TagValue... tags) {
        if (tags.length == 0)
            throw new IllegalArgumentException();
        this.tags = tags;
        Hasher hasher = Hashing.murmur3_32_fixed().newHasher();
        for (TagValue tag : tags)
            hasher.putInt(tag.hashCode());
        this.hash = hasher.hash().hashCode();
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
