package com.milaboratory.mixcr.trees;

import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequenceBuilder;

class AncestorInfoBuilder {
    AncestorInfo buildAncestorInfo(MutationsDescription ancestor) {
        SequenceBuilder<NucleotideSequence> builder = NucleotideSequence.ALPHABET.createBuilder();
        ancestor.getVMutationsWithoutCDR3().stream()
                .map(MutationsWithRange::buildSequence)
                .forEach(builder::append);
        int CDR3Begin = builder.size();
        builder.append(ancestor.getVMutationsInCDR3WithoutNDN().buildSequence());
        builder.append(ancestor.getKnownNDN().buildSequence());
        builder.append(ancestor.getJMutationsInCDR3WithoutNDN().buildSequence());
        int CDR3End = builder.size();
        ancestor.getJMutationsWithoutCDR3().stream()
                .map(MutationsWithRange::buildSequence)
                .forEach(builder::append);
        return new AncestorInfo(
                builder.createAndDestroy(),
                CDR3Begin,
                CDR3End
        );
    }
}
