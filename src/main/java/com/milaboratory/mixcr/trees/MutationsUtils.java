package com.milaboratory.mixcr.trees;

import com.milaboratory.core.mutations.Mutation;
import com.milaboratory.core.mutations.MutationType;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.mutations.MutationsBuilder;
import com.milaboratory.core.sequence.NucleotideAlphabet;
import com.milaboratory.core.sequence.NucleotideSequence;
import org.apache.commons.math3.util.Pair;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class MutationsUtils {
    private MutationsUtils() {
    }

    public static Mutations<NucleotideSequence> intersection(Mutations<NucleotideSequence> first, Mutations<NucleotideSequence> second) {
        RebuildFromNBase rebuildFromNBase = rebuildFromNBase(first, second);
        Mutations<NucleotideSequence> intersection = intersectionForSubstitutes(rebuildFromNBase.first, rebuildFromNBase.second);
        return rebuildFromOriginal(intersection, rebuildFromNBase.reversedIndexForFirst);
    }

    public static Mutations<NucleotideSequence> difference(Mutations<NucleotideSequence> from, Mutations<NucleotideSequence> to) {
        RebuildFromNBase rebuildFromNBase = rebuildFromNBase(from, to);
        return replaceSubstitutionsWithInsertions(
                rebuildFromNBase.first.invert().combineWith(rebuildFromNBase.second),
                rebuildFromNBase.reversedIndexForFirst.keySet()
        );
    }

    private static Mutations<NucleotideSequence> rebuildFromOriginal(Mutations<NucleotideSequence> forRebuild, Map<Integer, Integer> original) {
        MutationsBuilder<NucleotideSequence> builder = new MutationsBuilder<>(NucleotideSequence.ALPHABET);
        int positionShift = 0;
        for (int i = 0; i < forRebuild.size(); i++) {
            int mutation = forRebuild.getMutation(i);
            int position = Mutation.getPosition(mutation) + positionShift;
            int mappedMutation;
            if (Mutation.getType(mutation) == MutationType.Insertion && Mutation.getTo(mutation) == NucleotideAlphabet.N) {
                mappedMutation = Mutation.createInsertion(position, NucleotideAlphabet.N);
            } else {
                int originalMutation = original.get(mutation);
                mappedMutation = Mutation.createMutation(
                        Mutation.getType(originalMutation),
                        position,
                        Mutation.getFrom(originalMutation),
                        Mutation.getTo(mutation)
                );
            }
            if (Mutation.getType(mappedMutation) == MutationType.Insertion) {
                positionShift--;
            }
            builder.append(mappedMutation);
        }
        return builder.createAndDestroy();
    }

    private static Mutations<NucleotideSequence> intersectionForSubstitutes(
            Mutations<NucleotideSequence> first,
            Mutations<NucleotideSequence> second
    ) {
        Set<Integer> mutationsOfFirstAsSet = Arrays.stream(first.getRAWMutations()).boxed().collect(Collectors.toSet());
        int[] intersection = Arrays.stream(second.getRAWMutations())
                .map(mutation -> {
                    if (mutationsOfFirstAsSet.contains(mutation)) {
                        return mutation;
                    } else if (Mutation.getFrom(mutation) == NucleotideAlphabet.N) {
                        return Mutation.createMutation(MutationType.Insertion, Mutation.getPosition(mutation), NucleotideAlphabet.N, NucleotideAlphabet.N);
                    } else {
                        return -1;
                    }
                })
                .filter(it -> it != -1)
                .toArray();
        return new Mutations<>(NucleotideSequence.ALPHABET, intersection);
    }

    private static RebuildFromNBase rebuildFromNBase(
            Mutations<NucleotideSequence> first, Mutations<NucleotideSequence> second
    ) {
        Map<Integer, Long> positionsMaxCount = positionsMaxCount(first, second);

        Pair<Mutations<NucleotideSequence>, Map<Integer, Integer>> firstRebased = rebuildFromNBase(first, positionsMaxCount);
        Pair<Mutations<NucleotideSequence>, Map<Integer, Integer>> secondRebased = rebuildFromNBase(second, positionsMaxCount);
        return new RebuildFromNBase(
                firstRebased.getFirst(),
                secondRebased.getFirst(),
                firstRebased.getSecond()
        );
    }

    private static Pair<Mutations<NucleotideSequence>, Map<Integer, Integer>> rebuildFromNBase(Mutations<NucleotideSequence> mutations, Map<Integer, Long> positionsMaxCount) {
        Map<Integer, Integer> reversedMapping = new HashMap<>();
        MutationsBuilder<NucleotideSequence> builder = new MutationsBuilder<>(NucleotideSequence.ALPHABET);
        Map<Integer, Long> positionsCount = Arrays.stream(mutations.getRAWMutations())
                .map(Mutation::getPosition)
                .boxed()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        Map<Integer, Long> NToInsertInPositions = positionsMaxCount.keySet().stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        position -> positionsMaxCount.get(position) - positionsCount.getOrDefault(position, 0L)
                ));

        int positionShift = 0;
        for (int i = 0; i < mutations.size(); i++) {
            int mutation = mutations.getMutation(i);
            long NToInsert = NToInsertInPositions.getOrDefault(Mutation.getPosition(mutation), 0L);
            if (NToInsert != 0L) {
                for (long l = 0; l <= NToInsert; l++) {
                    int position = Mutation.getPosition(mutation) + positionShift;
                    builder.append(Mutation.createSubstitution(position, NucleotideAlphabet.N, NucleotideAlphabet.N));
                    positionShift++;
                }
                NToInsertInPositions.remove(Mutation.getPosition(mutation));
            }

            int position = Mutation.getPosition(mutation) + positionShift;
            if (positionsMaxCount.get(Mutation.getPosition(mutation)) != 1) {
                positionShift++;
            }
            byte from;
            byte to;
            if (Mutation.getType(mutation) == MutationType.Insertion) {
                from = NucleotideAlphabet.N;
                to = Mutation.getTo(mutation);
            } else if (Mutation.getType(mutation) == MutationType.Deletion) {
                from = Mutation.getFrom(mutation);
                to = NucleotideAlphabet.N;
            } else {
                from = Mutation.getFrom(mutation);
                to = Mutation.getTo(mutation);
            }
            int mapped = Mutation.createSubstitution(position, from, to);
            reversedMapping.put(mapped, mutation);
            builder.append(mapped);
        }
        return Pair.create(builder.createAndDestroy(), reversedMapping);
    }

    private static Map<Integer, Long> positionsMaxCount(Mutations<NucleotideSequence> first, Mutations<NucleotideSequence> second) {
        Map<Integer, Long> positionsCountForFirst = Arrays.stream(first.getRAWMutations())
                .map(Mutation::getPosition)
                .boxed()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        Map<Integer, Long> positionsCountForSecond = Arrays.stream(second.getRAWMutations())
                .map(Mutation::getPosition)
                .boxed()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        return Stream.concat(
                        positionsCountForFirst.keySet().stream(),
                        positionsCountForSecond.keySet().stream()
                )
                .distinct()
                .collect(Collectors.toMap(Function.identity(), position -> Math.max(
                        positionsCountForFirst.getOrDefault(position, 0L),
                        positionsCountForSecond.getOrDefault(position, 0L)
                )));
    }

    private static Mutations<NucleotideSequence> replaceSubstitutionsWithInsertions(Mutations<NucleotideSequence> base, Set<Integer> rebasedMutations) {
        MutationsBuilder<NucleotideSequence> builder = new MutationsBuilder<>(NucleotideSequence.ALPHABET);
        int positionShift = 0;
        for (int i = 0; i < base.size(); i++) {
            int mutation = base.getMutation(i);
            int position = Mutation.getPosition(mutation) + positionShift;
            if (!rebasedMutations.contains(mutation) && Mutation.getType(mutation) == MutationType.Substitution && Mutation.getFrom(mutation) == NucleotideAlphabet.N) {
                builder.append(Mutation.createInsertion(position, Mutation.getTo(mutation)));
                positionShift--;
            } else {
                builder.append(Mutation.createMutation(Mutation.getType(mutation), position, Mutation.getFrom(mutation), Mutation.getTo(mutation)));
            }
        }
        return builder.createAndDestroy();
    }

    private static class RebuildFromNBase {
        private final Mutations<NucleotideSequence> first;
        private final Mutations<NucleotideSequence> second;
        private final Map<Integer, Integer> reversedIndexForFirst;

        public RebuildFromNBase(Mutations<NucleotideSequence> first, Mutations<NucleotideSequence> second, Map<Integer, Integer> reversedIndexForFirst) {
            this.first = first;
            this.second = second;
            this.reversedIndexForFirst = reversedIndexForFirst;
        }
    }
}
