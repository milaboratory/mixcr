package com.milaboratory.mixcr.trees;

import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.Aligner;
import com.milaboratory.core.alignment.LinearGapAlignmentScoring;
import com.milaboratory.core.mutations.Mutation;
import com.milaboratory.core.mutations.MutationType;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.mutations.MutationsBuilder;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequenceBuilder;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.milaboratory.core.sequence.NucleotideAlphabet.N;

final class MutationsUtils {
    private MutationsUtils() {
    }

    /**
     * Mutate and get result by range in original sequence
     */
    public static NucleotideSequence buildSequence(NucleotideSequence sequence1, Mutations<NucleotideSequence> mutations, Range sequence1Range, boolean includeLastInsertions) {
        return mutations.mutate(sequence1)
                .getRange(projectRange(mutations, sequence1Range, includeLastInsertions));
    }

    public static Range projectRange(Mutations<NucleotideSequence> mutations, Range sequence1Range, boolean includeLastInsertions) {
        //for including inclusions before position one must step left before conversion and step right after
        int from = positionIfNucleotideWasDeleted(mutations.convertToSeq2Position(sequence1Range.getLower() - 1)) + 1;
        int to;
        if (includeLastInsertions) {
            to = positionIfNucleotideWasDeleted(mutations.convertToSeq2Position(sequence1Range.getUpper()));
        } else {
            to = positionIfNucleotideWasDeleted(mutations.convertToSeq2Position(sequence1Range.getUpper() - 1)) + 1;
        }
        return new Range(from, to);
    }

    private static int positionIfNucleotideWasDeleted(int position) {
        if (position < -1) {
            return Math.abs(position + 2);
        }
        return position;
    }


    public static Mutations<NucleotideSequence> intersection(Mutations<NucleotideSequence> first, Mutations<NucleotideSequence> second) {
        return simpleIntersection(
                first,
                second
        );
//        return simpleIntersection(
//                rebaseByNBase(first, second),
//                rebaseByNBase(second, first)
//        );
    }

    private static Mutations<NucleotideSequence> rebaseByNBase(Mutations<NucleotideSequence> original, Mutations<NucleotideSequence> second) {
        Map<Integer, List<Integer>> originalMutationsByPositions = Arrays.stream(original.getRAWMutations())
                .boxed()
                .collect(Collectors.groupingBy(Mutation::getPosition, Collectors.toList()));

        Map<Integer, List<Integer>> secondMutationsByPositions = Arrays.stream(second.getRAWMutations())
                .boxed()
                .collect(Collectors.groupingBy(Mutation::getPosition, Collectors.toList()));

        MutationsBuilder<NucleotideSequence> mutationsBuilder = new MutationsBuilder<>(NucleotideSequence.ALPHABET);

        Stream.concat(
                        originalMutationsByPositions.keySet().stream(),
                        secondMutationsByPositions.keySet().stream()
                )
                .distinct()
                .collect(Collectors.toMap(
                        Function.identity(),
                        position -> {
                            List<Integer> result = new ArrayList<>();
                            originalMutationsByPositions.getOrDefault(position, Collections.emptyList()).stream()
                                    .filter(it -> Mutation.getType(it) != MutationType.Insertion)
                                    .forEach(result::add);
                            List<Integer> insertionsInOriginal = originalMutationsByPositions.getOrDefault(position, Collections.emptyList()).stream()
                                    .filter(it -> Mutation.getType(it) == MutationType.Insertion)
                                    .collect(Collectors.toList());
                            List<Integer> insertionsInSecond = secondMutationsByPositions.getOrDefault(position, Collections.emptyList()).stream()
                                    .filter(it -> Mutation.getType(it) == MutationType.Insertion)
                                    .collect(Collectors.toList());
                            if (insertionsInSecond.isEmpty() || insertionsInOriginal.isEmpty()) {
                                for (int i = 0; i < Math.max(insertionsInOriginal.size(), insertionsInSecond.size()); i++) {
                                    result.add(Mutation.createInsertion(position, N));
                                }
                            } else {
                                NucleotideSequence insertResultOfOriginal = buildInsertions(insertionsInOriginal);
                                NucleotideSequence insertResultOfSecond = buildInsertions(insertionsInSecond);
                                Mutations<NucleotideSequence> fromOriginalToSecond = Aligner.alignGlobal(
                                        LinearGapAlignmentScoring.getNucleotideBLASTScoring(),
                                        insertResultOfOriginal,
                                        insertResultOfSecond
                                ).getAbsoluteMutations();

                                MutationsBuilder<NucleotideSequence> mutationsOfInsertResult = new MutationsBuilder<>(NucleotideSequence.ALPHABET);
                                for (int i = 0; i < fromOriginalToSecond.size(); i++) {
                                    int mutation = fromOriginalToSecond.getMutation(i);
                                    if (Mutation.getType(mutation) == MutationType.Deletion) {
                                        mutationsOfInsertResult.append(Mutation.createSubstitution(Mutation.getPosition(mutation), Mutation.getFrom(mutation), N));
                                    } else {
                                        mutationsOfInsertResult.append(Mutation.createInsertion(Mutation.getPosition(mutation), N));
                                    }
                                }
                                for (byte insert : mutationsOfInsertResult.createAndDestroy().mutate(insertResultOfOriginal).asArray()) {
                                    result.add(Mutation.createInsertion(position, insert));
                                }
                            }
                            return result;
                        }
                )).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .flatMap(Collection::stream)
                .forEach(mutationsBuilder::append);


        return mutationsBuilder.createAndDestroy();
    }

    private static NucleotideSequence buildInsertions(List<Integer> insertions) {
        SequenceBuilder<NucleotideSequence> builder = NucleotideSequence.ALPHABET.createBuilder();
        for (int mutation : insertions) {
            builder.append(Mutation.getTo(mutation));
        }
        return builder.createAndDestroy();
    }

    public static Mutations<NucleotideSequence> difference(Mutations<NucleotideSequence> from, Mutations<NucleotideSequence> to) {
        return from.invert().combineWith(to);
    }

    private static Mutations<NucleotideSequence> simpleIntersection(
            Mutations<NucleotideSequence> first,
            Mutations<NucleotideSequence> second
    ) {
        Set<Integer> mutationsOfFirstAsSet = Arrays.stream(first.getRAWMutations()).boxed().collect(Collectors.toSet());

        MutationsBuilder<NucleotideSequence> mutationsBuilder = new MutationsBuilder<>(NucleotideSequence.ALPHABET);
        for (int i = 0; i < second.size(); i++) {
            int mutation = second.getMutation(i);
            if (mutationsOfFirstAsSet.contains(mutation)) {
                mutationsBuilder.append(mutation);
            }
        }
        return mutationsBuilder.createAndDestroy();
    }
}
