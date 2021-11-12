package com.milaboratory.mixcr.trees;

import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.PrimitivO;
import com.milaboratory.primitivio.Serializer;
import com.milaboratory.primitivio.annotations.Serializable;

import java.util.Objects;

/**
 *
 */
@Serializable(by = CloneWrapper.SerializerImpl.class)
public class CloneWrapper {
    /**
     * Original clonotype
     */
    public final Clone clone;
    /**
     * Dataset serial number
     */
    public final int datasetId;

    public CloneWrapper(Clone clone, int datasetId) {
        this.clone = clone;
        this.datasetId = datasetId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CloneWrapper that = (CloneWrapper) o;
        return datasetId == that.datasetId && Objects.equals(clone, that.clone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clone, datasetId);
    }

    public static class SerializerImpl implements Serializer<CloneWrapper> {
        @Override
        public void write(PrimitivO output, CloneWrapper object) {
            output.writeObject(object.clone);
            output.writeInt(object.datasetId);
        }

        @Override
        public CloneWrapper read(PrimitivI input) {
            return new CloneWrapper(
                    input.readObject(Clone.class),
                    input.readInt()
            );
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
