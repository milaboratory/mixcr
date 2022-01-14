package com.milaboratory.mixcr.trees;

import com.milaboratory.core.Range;
import com.milaboratory.core.mutations.Mutation;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequenceBuilder;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static com.milaboratory.mixcr.trees.MutationsUtils.positionIfNucleotideWasDeleted;

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

    private Optional<Integer> getPositionInSequence2(List<MutationsWithRange> mutations, int positionInSequence1) {
        int rangesBefore = mutations.stream()
                .filter(mutation -> mutation.getSequence1Range().getUpper() <= positionInSequence1)
                .mapToInt(mutation -> mutation.getSequence1Range().length() + mutation.lengthDelta())
                .sum();

        if (mutations.stream().anyMatch(mutation -> mutation.getSequence1Range().getUpper() == positionInSequence1)) {
            return Optional.of(rangesBefore);
        }

        return mutations.stream()
                .filter(it -> it.getSequence1Range().contains(positionInSequence1))
                .map(mutation -> {
                    Mutations<NucleotideSequence> mutationsWithinRange = removeMutationsNotInRange(
                            mutation.getMutations(), mutation.getSequence1Range(), mutation.isIncludeLastMutations()
                    );
                    int position = positionIfNucleotideWasDeleted(mutationsWithinRange.convertToSeq2Position(positionInSequence1 - 1)) + 1;
                    return position - mutation.getSequence1Range().getLower();
                })
                .findFirst()
                .map(it -> it + rangesBefore);
    }

    private Mutations<NucleotideSequence> removeMutationsNotInRange(Mutations<NucleotideSequence> mutations, Range sequence1Range, boolean includeLastInserts) {
        return new Mutations<>(
                NucleotideSequence.ALPHABET,
                IntStream.of(mutations.getRAWMutations())
                        .filter(mutation -> {
                            int position = Mutation.getPosition(mutation);
                            return sequence1Range.contains(position) || (includeLastInserts && position == sequence1Range.getUpper());
                        })
                        .toArray()
        );
    }

}
