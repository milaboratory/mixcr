package com.milaboratory.mixcr.trees;

import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.AffineGapAlignmentScoring;
import com.milaboratory.core.alignment.Aligner;
import com.milaboratory.core.alignment.AlignmentScoring;
import com.milaboratory.core.alignment.LinearGapAlignmentScoring;
import com.milaboratory.core.mutations.Mutation;
import com.milaboratory.core.mutations.MutationType;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.mutations.MutationsBuilder;
import com.milaboratory.core.sequence.NucleotideAlphabet;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequenceBuilder;
import com.milaboratory.core.sequence.Wildcard;

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
    public static NucleotideSequence buildSequence(NucleotideSequence sequence1, Mutations<NucleotideSequence> mutations, Range sequence1Range, boolean includeFirstMutations, boolean includeLastInsertions) {
        return mutations.mutate(sequence1)
                .getRange(projectRange(mutations, sequence1Range, includeFirstMutations, includeLastInsertions));
    }

    public static Range projectRange(Mutations<NucleotideSequence> mutations, Range sequence1Range, boolean includeFirstMutations, boolean includeLastMutations) {
        //for including inclusions before position one must step left before conversion and step right after
        int from;
        if (includeFirstMutations) {
            from = positionIfNucleotideWasDeleted(mutations.convertToSeq2Position(sequence1Range.getLower() - 1)) + 1;
        } else {
            from = positionIfNucleotideWasDeleted(mutations.convertToSeq2Position(sequence1Range.getLower()));
        }
        int to;
        if (includeLastMutations) {
            to = positionIfNucleotideWasDeleted(mutations.convertToSeq2Position(sequence1Range.getUpper()));
        } else {
            to = positionIfNucleotideWasDeleted(mutations.convertToSeq2Position(sequence1Range.getUpper() - 1)) + 1;
        }
        return new Range(from, to);
    }

    static int positionIfNucleotideWasDeleted(int position) {
        if (position < -1) {
            return Math.abs(position + 2);
        }
        return position;
    }

    public static AlignmentScoring<NucleotideSequence> NDNScoring() {
        return new AffineGapAlignmentScoring<>(
                NucleotideSequence.ALPHABET,
                calculateSubstitutionMatrix(5, -4, 2, NucleotideSequence.ALPHABET),
                -10,
                -1
        );
    }

    private static int[] calculateSubstitutionMatrix(int match, int mismatch, int multiplierOfAsymmetry, NucleotideAlphabet alphabet) {
        int codes = alphabet.size();
        int[] matrix = new int[codes * codes];
        Arrays.fill(matrix, mismatch);
        for (int i = 0; i < codes; ++i)
            matrix[i + codes * i] = match;
        return fillWildcardScoresMatches(matrix, alphabet, match, mismatch, multiplierOfAsymmetry);
    }

    private static int[] fillWildcardScoresMatches(int[] matrix, NucleotideAlphabet alphabet, int match, int mismatch, int multiplierOfAsymmetry) {
        int alSize = alphabet.size();

        if (matrix.length != alSize * alSize)
            throw new IllegalArgumentException("Wrong matrix size.");

        //TODO remove excludeSet from milib
        for (Wildcard wc1 : alphabet.getAllWildcards())
            for (Wildcard wc2 : alphabet.getAllWildcards()) {
                if (wc1.isBasic() && wc2.isBasic())
                    continue;
                int sumScore = 0;
                for (int i = 0; i < wc1.basicSize(); i++) {
                    if (wc2.matches(wc1.getMatchingCode(i))) {
                        sumScore += match;
                    } else {
                        sumScore += mismatch;
                    }
                }
                for (int i = 0; i < wc2.basicSize(); i++) {
                    if (wc1.matches(wc2.getMatchingCode(i))) {
                        sumScore += match * multiplierOfAsymmetry;
                    } else {
                        sumScore += mismatch * multiplierOfAsymmetry;
                    }
                }
                sumScore /= wc1.basicSize() + wc2.basicSize() * multiplierOfAsymmetry;
                matrix[wc1.getCode() + wc2.getCode() * alSize] = sumScore;
            }

        return matrix;
    }

    public static Mutations<NucleotideSequence> intersection(
            Mutations<NucleotideSequence> first,
            Mutations<NucleotideSequence> second,
            Range range,
            boolean includeLastMutations
    ) {
        return simpleIntersection(
                first,
                second,
                range,
                includeLastMutations
        );
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

    //TODO removals and inserts
    private static Mutations<NucleotideSequence> simpleIntersection(
            Mutations<NucleotideSequence> first,
            Mutations<NucleotideSequence> second,
            Range range,
            boolean includeLastMutations
    ) {
        Set<Integer> mutationsOfFirstAsSet = Arrays.stream(first.getRAWMutations()).boxed().collect(Collectors.toSet());

        MutationsBuilder<NucleotideSequence> mutationsBuilder = new MutationsBuilder<>(NucleotideSequence.ALPHABET);
        for (int i = 0; i < second.size(); i++) {
            int mutation = second.getMutation(i);
            int position = Mutation.getPosition(mutation);
            if (range.contains(position) || (includeLastMutations && range.getUpper() == position && Mutation.isInsertion(mutation))) {
                if (mutationsOfFirstAsSet.contains(mutation)) {
                    mutationsBuilder.append(mutation);
                }
            }
        }
        return mutationsBuilder.createAndDestroy();
    }

    //TODO removals and inserts
    static Mutations<NucleotideSequence> findNDNCommonAncestor(Mutations<NucleotideSequence> first, Mutations<NucleotideSequence> second) {
        Map<Integer, Set<Integer>> mutationsOfFirstByPositions = Arrays.stream(first.getRAWMutations())
                .boxed()
                .collect(Collectors.groupingBy(Mutation::getPosition, Collectors.toSet()));

        MutationsBuilder<NucleotideSequence> mutationsBuilder = new MutationsBuilder<>(NucleotideSequence.ALPHABET);
        for (int i = 0; i < second.size(); i++) {
            int mutation = second.getMutation(i);
            int position = Mutation.getPosition(mutation);
            Set<Integer> mutationsOfFirst = mutationsOfFirstByPositions.get(position);
            if (mutationsOfFirst != null) {
                if (mutationsOfFirst.contains(mutation)) {
                    mutationsBuilder.append(mutation);
                } else if (Mutation.isSubstitution(mutation)) {
                    mutationsOfFirst.stream()
                            .filter(Mutation::isSubstitution)
                            .findFirst()
                            .map(otherSubstitution -> Mutation.createSubstitution(
                                    position,
                                    Mutation.getFrom(mutation),
                                    combine(Mutation.getTo(mutation), Mutation.getTo(otherSubstitution))
                            ))
                            .ifPresent(mutationsBuilder::append);
                }
            }
        }
        return mutationsBuilder.createAndDestroy();
    }

    private static byte combine(byte firstSymbol, byte secondSymbol) {
        if (firstSymbol == secondSymbol) {
            return firstSymbol;
        } else if (NucleotideSequence.ALPHABET.isWildcard(firstSymbol) && matchesStrictly(NucleotideSequence.ALPHABET.codeToWildcard(firstSymbol), secondSymbol)) {
            return secondSymbol;
        } else if (NucleotideSequence.ALPHABET.isWildcard(secondSymbol) && matchesStrictly(NucleotideSequence.ALPHABET.codeToWildcard(secondSymbol), firstSymbol)) {
            return firstSymbol;
        } else {
            long basicMask = 0;
            if (!NucleotideSequence.ALPHABET.isWildcard(firstSymbol)) {
                basicMask |= 1L << firstSymbol;
            } else {
                basicMask |= NucleotideSequence.ALPHABET.codeToWildcard(firstSymbol).getBasicMask();
            }
            if (!NucleotideSequence.ALPHABET.isWildcard(secondSymbol)) {
                basicMask |= 1L << secondSymbol;
            } else {
                basicMask |= NucleotideSequence.ALPHABET.codeToWildcard(secondSymbol).getBasicMask();
            }
            return NucleotideSequence.ALPHABET.maskToWildcard(basicMask).getCode();
        }
    }

    private static boolean matchesStrictly(Wildcard wildcard, byte secondSymbol) {
        if (!NucleotideSequence.ALPHABET.isWildcard(secondSymbol)) {
            return wildcard.matches(secondSymbol);
        } else {
            Wildcard secondAsWildcard = NucleotideSequence.ALPHABET.codeToWildcard(secondSymbol);
            return ((wildcard.getBasicMask() ^ secondAsWildcard.getBasicMask()) & secondAsWildcard.getBasicMask()) == 0;
        }
    }

}
