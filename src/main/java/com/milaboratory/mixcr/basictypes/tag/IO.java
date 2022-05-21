package com.milaboratory.mixcr.basictypes.tag;

import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.PrimitivO;
import com.milaboratory.primitivio.Serializer;
import gnu.trove.impl.Constants;
import gnu.trove.iterator.TObjectDoubleIterator;
import gnu.trove.map.hash.TObjectDoubleHashMap;

import java.util.Objects;

public final class IO {
    private IO() {
    }

    public static class SequenceAndQualityTagValueSerializer implements Serializer<SequenceAndQualityTagValue> {
        @Override
        public void write(PrimitivO output, SequenceAndQualityTagValue obj) {
            output.writeObject(obj.data);
        }

        @Override
        public SequenceAndQualityTagValue read(PrimitivI input) {
            return new SequenceAndQualityTagValue(input.readObject(NSequenceWithQuality.class));
        }

        @Override
        public boolean isReference() {
            return true;
        }

        @Override
        public boolean handlesReference() {
            return false;
        }
    }

    public static class SequenceTagValueSerializer implements Serializer<SequenceTagValue> {
        @Override
        public void write(PrimitivO output, SequenceTagValue obj) {
            output.writeObject(obj.sequence);
        }

        @Override
        public SequenceTagValue read(PrimitivI input) {
            return new SequenceTagValue(input.readObject(NucleotideSequence.class));
        }

        @Override
        public boolean isReference() {
            return true;
        }

        @Override
        public boolean handlesReference() {
            return false;
        }
    }

    public static class TagCounterSerializer implements Serializer<TagCounter> {
        @Override
        public void write(PrimitivO output, TagCounter object) {
            output.writeInt(object.tags.size());
            TObjectDoubleIterator<TagTuple> it = object.tags.iterator();
            while (it.hasNext()) {
                it.advance();
                output.writeObject(it.key().tags);
                output.writeDouble(it.value());
            }
        }

        @Override
        public TagCounter read(PrimitivI input) {
            int len = input.readInt();
            if (len == 0)
                return TagCounter.EMPTY;
            TObjectDoubleHashMap<TagTuple> r = new TObjectDoubleHashMap<>(len, Constants.DEFAULT_LOAD_FACTOR, 0.0);
            for (int i = 0; i < len; ++i) {
                TagValue[] tags = input.readObject(TagValue[].class);
                Objects.requireNonNull(tags);
                double count = input.readDouble();
                r.put(new TagTuple(tags), count);
            }
            return new TagCounter(r);
        }

        @Override
        public boolean isReference() {
            return true;
        }

        @Override
        public boolean handlesReference() {
            return false;
        }
    }
}
