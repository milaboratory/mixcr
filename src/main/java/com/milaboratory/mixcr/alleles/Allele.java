package com.milaboratory.mixcr.alleles;

import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.PrimitivO;
import com.milaboratory.primitivio.Serializer;
import com.milaboratory.primitivio.annotations.Serializable;
import io.repseq.core.VDJCGeneId;

@Serializable(by = Allele.AlleleSerializer.class)
public class Allele {
    private final VDJCGeneId id;
    private final Mutations<NucleotideSequence> mutations;

    public Allele(VDJCGeneId id, Mutations<NucleotideSequence> mutations) {
        this.id = id;
        this.mutations = mutations;
    }

    public VDJCGeneId getId() {
        return id;
    }

    public Mutations<NucleotideSequence> getMutations() {
        return mutations;
    }

    public static class AlleleSerializer implements Serializer<Allele> {
        @Override
        public void write(PrimitivO output, Allele object) {
            output.writeObject(object.id);
            output.writeObject(object.mutations);
        }

        @Override
        public Allele read(PrimitivI input) {
            VDJCGeneId id = input.readObject(VDJCGeneId.class);
            @SuppressWarnings("unchecked")
            Mutations<NucleotideSequence> mutations = input.readObject(Mutations.class);
            return new Allele(id, mutations);
        }

        @Override
        public boolean isReference() {
            return false;
        }

        @Override
        public boolean handlesReference() {
            return false;
        }
    }
}
