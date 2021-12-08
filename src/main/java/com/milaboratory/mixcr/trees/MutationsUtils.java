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
import java.util.stream.Collectors;

final class MutationsUtils {
    private MutationsUtils() {
    }

    public static Mutations<NucleotideSequence> intersection(Mutations<NucleotideSequence> first, Mutations<NucleotideSequence> second) {
        Pair<Mutations<NucleotideSequence>, Map<Integer, Integer>> firstBasedOnN = rebuildFromNBase(first);
        Pair<Mutations<NucleotideSequence>, Map<Integer, Integer>> secondBasedOnN = rebuildFromNBase(second);
        Mutations<NucleotideSequence> intersection = intersectionForSubstitutes(firstBasedOnN.getFirst(), secondBasedOnN.getFirst());
        return rebuildFromOriginal(intersection, firstBasedOnN.getSecond());
    }

    public static Mutations<NucleotideSequence> difference(Mutations<NucleotideSequence> from, Mutations<NucleotideSequence> to) {
        Pair<Mutations<NucleotideSequence>, Map<Integer, Integer>> rebasedFrom = rebuildFromNBase(from);
        Pair<Mutations<NucleotideSequence>, Map<Integer, Integer>> rebasedTo = rebuildFromNBase(to);
        return replaceSubstitutionsWithInsertions(rebasedFrom.getFirst().invert().combineWith(rebasedTo.getFirst()), rebasedTo.getSecond().keySet());
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

    private static Pair<Mutations<NucleotideSequence>, Map<Integer, Integer>> rebuildFromNBase(Mutations<NucleotideSequence> mutations) {
        Map<Integer, Integer> reversedMapping = new HashMap<>();
        MutationsBuilder<NucleotideSequence> builder = new MutationsBuilder<>(NucleotideSequence.ALPHABET);
        int positionShift = 0;
        for (int i = 0; i < mutations.size(); i++) {
            int mutation = mutations.getMutation(i);
            int position = Mutation.getPosition(mutation) + positionShift;
            if (Mutation.getType(mutation) == MutationType.Insertion) {
                positionShift++;
            }
            byte from;
            byte to;
            if (Mutation.getType(mutation) == MutationType.Insertion) {
                if (Mutation.getTo(mutation) == NucleotideAlphabet.N) {
                    continue;
                }
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

    static Mutations<NucleotideSequence> replaceSubstitutionsWithInsertions(Mutations<NucleotideSequence> base, Set<Integer> rebasedMutations) {
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
}
