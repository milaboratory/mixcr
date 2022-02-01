package com.milaboratory.mixcr.alleles;

import com.milaboratory.core.mutations.Mutation;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.util.AdjacencyMatrix;
import com.milaboratory.mixcr.util.BitArrayInt;
import com.milaboratory.mixcr.util.Java9Util;
import org.apache.commons.math3.util.Pair;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

class CommonMutationsSearcher {
    private final double minPartOfClones;

    CommonMutationsSearcher(double minPartOfClones) {
        this.minPartOfClones = minPartOfClones;
    }

    List<Mutations<NucleotideSequence>> findAlleles(List<Supplier<IntStream>> mutations) {
        int[] allMutations = mutations.stream()
                .flatMapToInt(Supplier::get)
                .distinct()
                .toArray();
        Map<Integer, Integer> indexesByMutations = objectToIndexMap(allMutations);

        int[][] commonMutationsCount = commonMutationsCount(mutations, allMutations, indexesByMutations);

        Set<Integer> mutationsInAllClones = mutationsInAllClones(allMutations, commonMutationsCount, minPartOfClones * mutations.size() * 2);
        Set<Pair<Integer, Integer>> mutationsInAHalfOfClones = mutationPairsInAHalfOfClones(allMutations, commonMutationsCount, mutationsInAllClones, minPartOfClones * mutations.size());

        List<Mutations<NucleotideSequence>> result = topCliques(mutationsInAHalfOfClones)
                .map(clique -> new Mutations<>(
                        NucleotideSequence.ALPHABET,
                        Stream.concat(
                                        clique.boxed(),
                                        mutationsInAllClones.stream()
                                )
                                .sorted(Comparator.comparingInt(Mutation::getPosition))
                                .mapToInt(it -> it)
                                .toArray()
                ))
                .collect(Collectors.toList());
        if (result.size() < 2) {
            result.add(new Mutations<>(
                    NucleotideSequence.ALPHABET,
                    mutationsInAllClones.stream()
                            .sorted(Comparator.comparingInt(Mutation::getPosition))
                            .mapToInt(it -> it)
                            .toArray()
            ));
        }
        return result;
    }

    private Stream<IntStream> topCliques(Set<Pair<Integer, Integer>> mutationsInAHalfOfClones) {
        int[] allMutations = mutationsInAHalfOfClones.stream()
                .flatMapToInt(pair -> IntStream.of(pair.getFirst(), pair.getSecond()))
                .distinct()
                .toArray();

        Map<Integer, Integer> indexesByMutations = objectToIndexMap(allMutations);

        AdjacencyMatrix matrix = new AdjacencyMatrix(allMutations.length);
        for (Pair<Integer, Integer> pair : mutationsInAHalfOfClones) {
            matrix.setConnected(
                    indexesByMutations.get(pair.getFirst()),
                    indexesByMutations.get(pair.getSecond())
            );
        }
        List<BitArrayInt> cliques = matrix.calculateMaximalCliques();
        Optional<BitArrayInt> bestClique = cliques.stream()
                .max(Comparator.comparing(BitArrayInt::bitCount));

        Optional<BitArrayInt> secondClique;
        //noinspection OptionalIsPresent
        if (bestClique.isPresent()) {
            secondClique = cliques.stream()
                    .filter(clique -> !clique.intersects(bestClique.get()))
                    .max(Comparator.comparing(BitArrayInt::bitCount));
        } else {
            secondClique = Optional.empty();
        }

        return Stream.of(bestClique, secondClique)
                .flatMap(Java9Util::stream)
                .map(clique -> IntStream.of(clique.getBits())
                        .map(it -> allMutations[it]));
    }

    private Set<Pair<Integer, Integer>> mutationPairsInAHalfOfClones(int[] allMutations, int[][] commonMutationsCount, Set<Integer> exclude, double threshold) {
        Set<Pair<Integer, Integer>> mutationsInAHalfOfClones = new HashSet<>();
        for (int i = 0; i < commonMutationsCount.length; i++) {
            for (int j = 0; j <= i; j++) {
                if (commonMutationsCount[i][j] != commonMutationsCount[j][i]) {
                    throw new IllegalStateException();
                }
                int mutationOfFirst = allMutations[i];
                int mutationOfSecond = allMutations[j];
                if (exclude.contains(mutationOfFirst) || exclude.contains(mutationOfSecond)) {
                    continue;
                }
                int count = commonMutationsCount[i][j];
                if (count >= threshold) {
                    mutationsInAHalfOfClones.add(Pair.create(mutationOfFirst, mutationOfSecond));
                }
            }
        }
        return mutationsInAHalfOfClones;
    }

    private Set<Integer> mutationsInAllClones(int[] allMutations, int[][] commonMutationsCount, double threshold) {
        Set<Integer> mutationsInAllClones = new HashSet<>();
        for (int i = 0; i < commonMutationsCount.length; i++) {
            if (commonMutationsCount[i][i] >= threshold) {
                mutationsInAllClones.add(allMutations[i]);
            }
        }
        return mutationsInAllClones;
    }

    private int[][] commonMutationsCount(List<Supplier<IntStream>> mutations, int[] allMutations, Map<Integer, Integer> indexesByMutations) {
        int[][] commonMutationsCount = new int[allMutations.length][allMutations.length];
        for (Supplier<IntStream> mutationsSupplier : mutations) {
            int[] mutationsInAClone = mutationsSupplier.get().toArray();
            for (int i = mutationsInAClone.length - 1; i >= 0; i--) {
                int indexOfFirst = indexesByMutations.get(mutationsInAClone[i]);
                for (int j = 0; j < i; j++) {
                    int indexOfSecond = indexesByMutations.get(mutationsInAClone[j]);
                    commonMutationsCount[indexOfFirst][indexOfSecond]++;
                    commonMutationsCount[indexOfSecond][indexOfFirst]++;
                }
                commonMutationsCount[indexOfFirst][indexOfFirst]++;
            }
        }
        return commonMutationsCount;
    }

    private Map<Integer, Integer> objectToIndexMap(int[] objects) {
        Map<Integer, Integer> objectToIndex = new HashMap<>(objects.length);
        for (int i = 0; i < objects.length; i++) {
            objectToIndex.put(objects[i], i);
        }
        return objectToIndex;
    }
}
