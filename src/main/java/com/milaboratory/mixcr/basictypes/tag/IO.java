package com.milaboratory.mixcr.basictypes.tag;

import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.PrimitivO;
import com.milaboratory.primitivio.Serializer;
import com.milaboratory.util.ByteString;
import gnu.trove.impl.Constants;
import gnu.trove.iterator.TObjectDoubleIterator;
import gnu.trove.map.hash.TObjectDoubleHashMap;

public final class IO {
    private IO() {
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
                ByteString[] tags = input.readObject(String[].class);
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
