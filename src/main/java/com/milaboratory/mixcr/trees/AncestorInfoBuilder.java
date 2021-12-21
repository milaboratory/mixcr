package com.milaboratory.mixcr.trees;

import com.milaboratory.core.Range;
import com.milaboratory.core.mutations.Mutation;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequenceBuilder;
import io.repseq.core.ReferencePoint;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;

import static com.milaboratory.mixcr.trees.MutationsUtils.positionIfNucleotideWasDeleted;

class AncestorInfoBuilder {
    private final Function<ReferencePoint, Integer> getVRelativePosition;
    private final Function<ReferencePoint, Integer> getJRelativePosition;

    public AncestorInfoBuilder(Function<ReferencePoint, Integer> getVRelativePosition, Function<ReferencePoint, Integer> getJRelativePosition) {
        this.getVRelativePosition = getVRelativePosition;
        this.getJRelativePosition = getJRelativePosition;
    }

    AncestorInfo buildAncestorInfo(MutationsDescription ancestor) {
        SequenceBuilder<NucleotideSequence> builder = NucleotideSequence.ALPHABET.createBuilder();
        ancestor.getVMutationsWithoutNDN().stream()
                .map(MutationsWithRange::buildSequence)
                .forEach(builder::append);
        int CDR3Begin = getPositionInSequence2(ancestor.getVMutationsWithoutNDN(), getVRelativePosition.apply(ReferencePoint.CDR3Begin), false)
                .orElseThrow(IllegalArgumentException::new);
        builder.append(ancestor.getKnownNDN().buildSequence());
        int CDR3End = getPositionInSequence2(ancestor.getJMutationsWithoutNDN(), getJRelativePosition.apply(ReferencePoint.CDR3End), true)
                .orElseThrow(IllegalArgumentException::new) + builder.size();
        ancestor.getJMutationsWithoutNDN().stream()
                .map(MutationsWithRange::buildSequence)
                .forEach(builder::append);
        return new AncestorInfo(
                builder.createAndDestroy(),
                CDR3Begin,
                CDR3End
        );
    }

    private Optional<Integer> getPositionInSequence2(List<MutationsWithRange> mutations, int positionInSequence1, boolean includeLastMutations) {
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
                            mutation.getCombinedMutations(), mutation.getSequence1Range(), mutation.isIncludeLastMutations()
                    );
                    int position;
                    if (includeLastMutations) {
                        position = positionIfNucleotideWasDeleted(mutationsWithinRange.convertToSeq2Position(positionInSequence1 - 1)) + 1;
                    } else {
                        position = positionIfNucleotideWasDeleted(mutationsWithinRange.convertToSeq2Position(positionInSequence1));
                    }
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
