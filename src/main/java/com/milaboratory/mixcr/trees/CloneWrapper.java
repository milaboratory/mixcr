package com.milaboratory.mixcr.trees;

import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.PrimitivO;
import com.milaboratory.primitivio.Serializer;
import com.milaboratory.primitivio.annotations.Serializable;
import io.repseq.core.GeneType;

import java.util.Arrays;
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
    final VJBase VJBase;

    public CloneWrapper(Clone clone, int datasetId, VJBase VJBase) {
        this.clone = clone;
        this.datasetId = datasetId;
        this.VJBase = VJBase;
    }

    public VDJCHit getHit(GeneType geneType) {
        String geneName = VJBase.getGeneName(geneType);
        return Arrays.stream(clone.getHits(geneType))
                .filter(it -> it.getGene().getName().equals(geneName))
                .findAny()
                .orElseThrow(IllegalArgumentException::new);
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
            output.writeUTF(object.VJBase.VGeneName);
            output.writeUTF(object.VJBase.JGeneName);
        }

        @Override
        public CloneWrapper read(PrimitivI input) {
            return new CloneWrapper(
                    input.readObject(Clone.class),
                    input.readInt(),
                    new VJBase(input.readUTF(), input.readUTF())
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
