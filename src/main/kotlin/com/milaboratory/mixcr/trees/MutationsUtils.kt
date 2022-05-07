package com.milaboratory.mixcr.trees;

import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.AffineGapAlignmentScoring;
import com.milaboratory.core.alignment.AlignmentScoring;
import com.milaboratory.core.mutations.Mutation;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.mutations.MutationsBuilder;
import com.milaboratory.core.sequence.NucleotideAlphabet;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.Wildcard;
import com.milaboratory.mixcr.util.RangeInfo;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

final class MutationsUtils {
    private MutationsUtils() {
    }

    /**
     * Mutate and get result by range in original sequence
     */
    static NucleotideSequence buildSequence(NucleotideSequence sequence1, Mutations<NucleotideSequence> mutations, RangeInfo rangeInfo) {
        return mutations.mutate(sequence1).getRange(projectRange(mutations, rangeInfo));
    }

    static Range projectRange(Mutations<NucleotideSequence> mutations, RangeInfo rangeInfo) {
        //for including inclusions before position one must step left before conversion and step right after
        int from = positionIfNucleotideWasDeleted(mutations.convertToSeq2Position(rangeInfo.getRange().getLower()));
        if (rangeInfo.isIncludeFirstInserts()) {
            from -= IntStream.of(mutations.getRAWMutations())
                    .filter(mutation -> Mutation.getPosition(mutation) == rangeInfo.getRange().getLower() && Mutation.isInsertion(mutation))
                    .count();
        }

        int to = positionIfNucleotideWasDeleted(mutations.convertToSeq2Position(rangeInfo.getRange().getUpper()));
        return new Range(from, to);
    }

    static MutationsDescription mutationsBetween(MutationsDescription first, MutationsDescription second) {
        return new MutationsDescription(
                mutationsBetween(first.getVMutationsWithoutCDR3(), second.getVMutationsWithoutCDR3()),
                first.getVMutationsInCDR3WithoutNDN().differenceWith(second.getVMutationsInCDR3WithoutNDN()),
                first.getKnownNDN().differenceWith(second.getKnownNDN()),
                first.getJMutationsInCDR3WithoutNDN().differenceWith(second.getJMutationsInCDR3WithoutNDN()),
                mutationsBetween(first.getJMutationsWithoutCDR3(), second.getJMutationsWithoutCDR3())
        );
    }

    private static List<MutationsWithRange> mutationsBetween(List<MutationsWithRange> firstMutations, List<MutationsWithRange> secondMutations) {
        return fold(firstMutations, secondMutations, MutationsWithRange::differenceWith);
    }

    static MutationsWithRange intersection(MutationsWithRange base, MutationsWithRange comparison, RangeInfo intersection) {
        return new MutationsWithRange(
                base.getSequence1(),
                MutationsUtils.intersection(
                        base.getMutations(),
                        comparison.getMutations(),
                        intersection
                ),
                intersection
        );
    }

    static MutationsWithRange intersection(MutationsWithRange from, MutationsWithRange to) {
        if (!from.getRangeInfo().equals(to.getRangeInfo())) {
            throw new IllegalArgumentException();
        }
        return new MutationsWithRange(
                from.getSequence1(),
                MutationsUtils.intersection(
                        from.getMutations(),
                        to.getMutations(),
                        from.getRangeInfo()
                ),
                from.getRangeInfo()
        );
    }

    static List<MutationsWithRange> intersection(List<MutationsWithRange> from, List<MutationsWithRange> to) {
        return fold(from, to, (a, b) -> intersection(a, b, a.getRangeInfo().intersection(b.getRangeInfo())));
    }

    static <T> List<T> fold(
            List<MutationsWithRange> firstMutations,
            List<MutationsWithRange> secondMutations,
            BiFunction<MutationsWithRange, MutationsWithRange, T> folder
    ) {
        if (firstMutations.size() != secondMutations.size()) {
            throw new IllegalArgumentException();
        }
        return IntStream.range(0, firstMutations.size())
                .mapToObj(i -> folder.apply(firstMutations.get(0), secondMutations.get(0)))
                .collect(Collectors.toList());
    }

    static int positionIfNucleotideWasDeleted(int position) {
        if (position < -1) {
            return Math.abs(position + 1);
        }
        if (position == -1) {
            return 0;
        }
        return position;
    }

    static AlignmentScoring<NucleotideSequence> NDNScoring() {
        return new AffineGapAlignmentScoring<>(
                NucleotideSequence.ALPHABET,
                calculateSubstitutionMatrix(5, -4, 4, NucleotideSequence.ALPHABET),
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

    static Mutations<NucleotideSequence> intersection(
            Mutations<NucleotideSequence> first,
            Mutations<NucleotideSequence> second,
            RangeInfo rangeInfo
    ) {
        return simpleIntersection(
                first,
                second,
                rangeInfo.getRange()
        );
    }

    //TODO removals and inserts
    private static Mutations<NucleotideSequence> simpleIntersection(
            Mutations<NucleotideSequence> first,
            Mutations<NucleotideSequence> second,
            Range range
    ) {
        Set<Integer> mutationsOfFirstAsSet = Arrays.stream(first.getRAWMutations()).boxed().collect(Collectors.toSet());

        MutationsBuilder<NucleotideSequence> mutationsBuilder = new MutationsBuilder<>(NucleotideSequence.ALPHABET);
        for (int i = 0; i < second.size(); i++) {
            int mutation = second.getMutation(i);
            int position = Mutation.getPosition(mutation);
            if (range.contains(position)) {
                if (mutationsOfFirstAsSet.contains(mutation)) {
                    mutationsBuilder.append(mutation);
                }
            }
        }
        return mutationsBuilder.createAndDestroy();
    }

    //TODO removals and inserts
    static Mutations<NucleotideSequence> concreteNDNChild(Mutations<NucleotideSequence> parent, Mutations<NucleotideSequence> child) {
        Map<Integer, Set<Integer>> mutationsOfParentByPositions = Arrays.stream(parent.getRAWMutations())
                .boxed()
                .collect(Collectors.groupingBy(Mutation::getPosition, Collectors.toSet()));
        MutationsBuilder<NucleotideSequence> mutationsBuilder = new MutationsBuilder<>(NucleotideSequence.ALPHABET);
        for (int i = 0; i < child.size(); i++) {
            int mutationOfChild = child.getMutation(i);
            if (Mutation.isInDel(mutationOfChild)) {
                mutationsBuilder.append(mutationOfChild);
            } else {
                int position = Mutation.getPosition(mutationOfChild);
                Optional<Integer> mutationsOfParent = mutationsOfParentByPositions.getOrDefault(position, Collections.emptySet()).stream()
                        .filter(Mutation::isSubstitution)
                        .findFirst();
                if (!mutationsOfParent.isPresent()) {
                    mutationsBuilder.append(mutationOfChild);
                } else {
                    byte from = Mutation.getFrom(mutationOfChild);
                    byte to = concreteChild(Mutation.getTo(mutationsOfParent.get()), Mutation.getTo(mutationOfChild));
                    if (from != to) {
                        mutationsBuilder.append(Mutation.createSubstitution(position, from, to));
                    }
                }
            }
        }

        return mutationsBuilder.createAndDestroy();
    }

    private static byte concreteChild(byte parentSymbol, byte childSymbol) {
        if (parentSymbol == childSymbol) {
            return childSymbol;
        } else if (NucleotideSequence.ALPHABET.isWildcard(childSymbol)) {
            if (matchesStrictly(NucleotideSequence.ALPHABET.codeToWildcard(childSymbol), parentSymbol)) {
                return parentSymbol;
            } else {
                long basicMask = NucleotideSequence.ALPHABET.codeToWildcard(parentSymbol).getBasicMask()
                        | NucleotideSequence.ALPHABET.codeToWildcard(childSymbol).getBasicMask();
                return NucleotideSequence.ALPHABET.maskToWildcard(basicMask).getCode();
            }
        } else {
            return childSymbol;
        }
    }

    //TODO removals and inserts
    static Mutations<NucleotideSequence> findNDNCommonAncestor(Mutations<NucleotideSequence> first, Mutations<NucleotideSequence> second) {
        Map<Integer, Set<Integer>> mutationsOfFirstByPositions = Arrays.stream(first.getRAWMutations())
                .boxed()
                .collect(Collectors.groupingBy(Mutation::getPosition, Collectors.toSet()));

        MutationsBuilder<NucleotideSequence> mutationsBuilder = new MutationsBuilder<>(NucleotideSequence.ALPHABET);
        for (int i = 0; i < second.size(); i++) {
            int mutationOfSecond = second.getMutation(i);
            int position = Mutation.getPosition(mutationOfSecond);
            Set<Integer> mutationsOfFirst = mutationsOfFirstByPositions.getOrDefault(position, Collections.emptySet());
            if (mutationsOfFirst.contains(mutationOfSecond)) {
                mutationsBuilder.append(mutationOfSecond);
            } else if (Mutation.isSubstitution(mutationOfSecond)) {
                mutationsOfFirst.stream()
                        .filter(Mutation::isSubstitution)
                        .findFirst()
                        .map(otherSubstitution -> Mutation.createSubstitution(
                                position,
                                Mutation.getFrom(mutationOfSecond),
                                combine(Mutation.getTo(mutationOfSecond), Mutation.getTo(otherSubstitution))
                        ))
                        .ifPresent(mutationsBuilder::append);
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
            long basicMask = NucleotideSequence.ALPHABET.codeToWildcard(firstSymbol).getBasicMask()
                    | NucleotideSequence.ALPHABET.codeToWildcard(secondSymbol).getBasicMask();
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

    static List<MutationsWithRange> combine(List<MutationsWithRange> base, List<MutationsWithRange> combineWith) {
        return fold(base, combineWith, MutationsWithRange::combineWith);
    }
}
