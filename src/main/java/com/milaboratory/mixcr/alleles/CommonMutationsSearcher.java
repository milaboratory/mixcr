package com.milaboratory.mixcr.alleles;

import com.google.common.collect.Lists;
import com.milaboratory.core.alignment.AlignmentScoring;
import com.milaboratory.core.alignment.AlignmentUtils;
import com.milaboratory.core.mutations.Mutation;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.util.AdjacencyMatrix;
import com.milaboratory.mixcr.util.BitArrayInt;
import org.apache.commons.math3.util.Pair;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.milaboratory.core.mutations.Mutations.EMPTY_NUCLEOTIDE_MUTATIONS;

class CommonMutationsSearcher {
    private final FindAllelesParameters parameters;
    private final AlignmentScoring<NucleotideSequence> scoring;
    private final NucleotideSequence sequence1;

    CommonMutationsSearcher(FindAllelesParameters parameters, AlignmentScoring<NucleotideSequence> scoring, NucleotideSequence sequence1) {
        this.parameters = parameters;
        this.scoring = scoring;
        this.sequence1 = sequence1;
    }

    List<Mutations<NucleotideSequence>> findAlleles(List<CloneDescription> clones) {
        if (diversity(clones) < parameters.minDiversityToSearchAlleles) {
            return Collections.singletonList(EMPTY_NUCLEOTIDE_MUTATIONS);
        }
        if (diversity(clones) < parameters.minDiversityToFindSecondAllele) {
            return Collections.singletonList(alleleThatCoversEveryClone(clones));
        }
        FindFirstAlleleResult findFirstAlleleResult = findFirstAllele(clones);
        Mutations<NucleotideSequence> firstAllele = findFirstAlleleResult.firstAllele;
        List<CloneDescription> clonesWithoutFirstAllele;
        if (firstAllele.equals(EMPTY_NUCLEOTIDE_MUTATIONS)) {
            clonesWithoutFirstAllele = withoutAnyMutations(clones);
        } else {
            clonesWithoutFirstAllele = withoutAllele(clones, firstAllele);
        }
        if (clonesWithoutFirstAllele.isEmpty()) {
            return Collections.singletonList(firstAllele);
        }
        if (diversity(clonesWithoutFirstAllele) == 1) {
            return Lists.newArrayList(firstAllele, findFirstAlleleResult.mutationsInAlmostAllClones);
        }
        Mutations<NucleotideSequence> secondAllele = findSecondAllele(firstAllele, clonesWithoutFirstAllele)
                .orElse(EMPTY_NUCLEOTIDE_MUTATIONS);
        // if second allele wasn't found but there are mutations covers almost all clones,
        // we must assume that first allele is `findFirstAlleleResult.mutationsInAlmostAllClones`,
        // that will increase coverage of found alleles
        if (secondAllele.equals(EMPTY_NUCLEOTIDE_MUTATIONS) &&
                !findFirstAlleleResult.mutationsInAlmostAllClones.equals(EMPTY_NUCLEOTIDE_MUTATIONS) &&
                !findFirstAlleleResult.mutationsInAlmostAllClones.equals(firstAllele)) {
            firstAllele = findFirstAlleleResult.mutationsInAlmostAllClones;
            clonesWithoutFirstAllele = withoutAllele(clones, firstAllele);
            if (diversity(clonesWithoutFirstAllele) == 1) {
                return Lists.newArrayList(firstAllele, EMPTY_NUCLEOTIDE_MUTATIONS);
            }
            secondAllele = findSecondAllele(firstAllele, clonesWithoutFirstAllele)
                    .orElse(EMPTY_NUCLEOTIDE_MUTATIONS);
        }

        if (firstAllele.equals(EMPTY_NUCLEOTIDE_MUTATIONS) && secondAllele.equals(EMPTY_NUCLEOTIDE_MUTATIONS)) {
            return Collections.singletonList(EMPTY_NUCLEOTIDE_MUTATIONS);
        }

        if (secondAllele.equals(firstAllele)) {
            throw new IllegalStateException();
        }
        return Lists.newArrayList(firstAllele, secondAllele);
    }

    private FindFirstAlleleResult findFirstAllele(List<CloneDescription> clones) {
        int[] allMutations = clones.stream()
                .flatMapToInt(cloneDescription -> cloneDescription.mutationsSupplier.get())
                .distinct()
                .toArray();
        int[][] commonMutationsDiversity = commonMutationsDiversity(clones, allMutations);

        int diversityOfACluster = diversity(clones);

        Set<Integer> mutationsInAlmostAllClones = mutationsInAllClones(
                allMutations,
                commonMutationsDiversity,
                parameters.minPartOfClonesToDeterminateAllele * diversityOfACluster
        );
        Set<Pair<Integer, Integer>> mutationPairsInAHalfOfClones = mutationPairsWithDiversityMoreThan(
                allMutations,
                commonMutationsDiversity,
                mutationsInAlmostAllClones,
                (parameters.minPartOfClonesToDeterminateAllele / 2.0) * diversityOfACluster
        );

        Optional<IntStream> bestClique = bestCliques(mutationPairsInAHalfOfClones).findFirst();
        Mutations<NucleotideSequence> firstAllele;
        if (bestClique.isPresent()) {
            firstAllele = asMutations(Stream.concat(
                    bestClique.get().boxed(),
                    mutationsInAlmostAllClones.stream()
            ));
        } else if (!mutationsInAlmostAllClones.isEmpty()) {
            firstAllele = asMutations(mutationsInAlmostAllClones.stream());
        } else {
            firstAllele = EMPTY_NUCLEOTIDE_MUTATIONS;
        }
        return new FindFirstAlleleResult(
                asMutations(mutationsInAlmostAllClones.stream()),
                firstAllele
        );
    }

    private int diversity(List<CloneDescription> clones) {
        return (int) clones.stream()
                .map(clone -> clone.clusterIdentity)
                .distinct()
                .count();
    }

    private Optional<Mutations<NucleotideSequence>> findSecondAllele(Mutations<NucleotideSequence> firstAllele, List<CloneDescription> clonesWithoutFirstAllele) {
        int[] allMutations = clonesWithoutFirstAllele.stream()
                .flatMapToInt(cloneDescription -> cloneDescription.mutationsSupplier.get())
                .distinct()
                .toArray();
        int diversityOfAllClones = diversity(clonesWithoutFirstAllele);

        int[][] commonMutationsDiversityInClones = commonMutationsDiversity(clonesWithoutFirstAllele, allMutations);

        Set<Pair<Integer, Integer>> mutationPairsInAlmostAllClones = mutationPairsWithDiversityMoreThan(
                allMutations,
                commonMutationsDiversityInClones,
                Collections.emptySet(),
                diversityOfAllClones * parameters.minPartOfClonesToDeterminateAllele
        );
        Optional<Mutations<NucleotideSequence>> result = bestCliques(mutationPairsInAlmostAllClones)
                .map(IntStream::boxed)
                .map(this::asMutations)
                .filter(secondAllele -> {
                    if (firstAllele.equals(EMPTY_NUCLEOTIDE_MUTATIONS)) {
                        //all clones that not marked as second allele may be marked as first allele
                        return true;
                    } else {
                        //check that all clones may be marked as second allele
                        return withoutAllele(clonesWithoutFirstAllele, secondAllele).isEmpty();
                    }
                })
                .findFirst();
        if (result.isPresent()) {
            return result;
        } else {
            //try to find allele that definitely will cover all clones
            Set<Pair<Integer, Integer>> mutationPairsInAllClones = mutationPairsWithDiversityMoreThan(
                    allMutations,
                    commonMutationsDiversityInClones,
                    Collections.emptySet(),
                    diversityOfAllClones
            );
            return bestCliques(mutationPairsInAllClones)
                    .map(IntStream::boxed)
                    .map(this::asMutations)
                    .findFirst();
        }
    }

    private Mutations<NucleotideSequence> alleleThatCoversEveryClone(List<CloneDescription> clones) {
        int[] allMutations = clones.stream()
                .flatMapToInt(cloneDescription -> cloneDescription.mutationsSupplier.get())
                .distinct()
                .toArray();
        int diversityOfAllClones = diversity(clones);

        int[][] commonMutationsDiversityInClones = commonMutationsDiversity(clones, allMutations);

        Set<Pair<Integer, Integer>> mutationPairsInAllClones = mutationPairsWithDiversityMoreThan(
                allMutations,
                commonMutationsDiversityInClones,
                Collections.emptySet(),
                diversityOfAllClones
        );
        return bestCliques(mutationPairsInAllClones)
                .map(IntStream::boxed)
                .map(this::asMutations)
                .findFirst()
                .orElse(EMPTY_NUCLEOTIDE_MUTATIONS);
    }

    private List<CloneDescription> withoutAnyMutations(List<CloneDescription> clones) {
        return clones.stream()
                .filter(clone -> clone.mutationsSupplier.get().count() > 0)
                .collect(Collectors.toList());
    }

    private List<CloneDescription> withoutAllele(List<CloneDescription> clones, Mutations<NucleotideSequence> allele) {
        Set<Integer> positionsOfMutationsInAllele = IntStream.of(allele.getRAWMutations())
                .mapToObj(Mutation::getPosition)
                .collect(Collectors.toSet());
        return clones.stream()
                .filter(clone -> {
                    Mutations<NucleotideSequence> mutationsOfClone = asMutations(clone.mutationsSupplier.get()
                            .filter(mutation -> positionsOfMutationsInAllele.contains(Mutation.getPosition(mutation)))
                            .boxed()
                    );
                    Mutations<NucleotideSequence> difference = allele.invert().combineWith(mutationsOfClone);
                    int maxScore = scoring.getMaximalMatchScore() * sequence1.size();
                    int score = AlignmentUtils.calculateScore(
                            sequence1,
                            difference,
                            scoring
                    );
                    double penaltyByAlleleMutation = (maxScore - score) / (double) allele.size();
                    return penaltyByAlleleMutation >= parameters.maxPenaltyByAlleleMutation;
                })
                .collect(Collectors.toList());
    }

    private Mutations<NucleotideSequence> asMutations(Stream<Integer> mutations) {
        return new Mutations<>(
                NucleotideSequence.ALPHABET,
                mutations
                        .sorted(Comparator.comparingInt(Mutation::getPosition))
                        .mapToInt(it -> it)
                        .toArray()
        );
    }

    private Stream<IntStream> bestCliques(Set<Pair<Integer, Integer>> mutationsInAHalfOfClones) {
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
        return cliques.stream()
                .sorted(Comparator.comparing(BitArrayInt::bitCount).reversed())
                .map(clique -> IntStream.of(clique.getBits()).map(it -> allMutations[it]));
    }

    private Set<Pair<Integer, Integer>> mutationPairsWithDiversityMoreThan(int[] allMutations, int[][] commonMutationsCount, Set<Integer> exclude, double threshold) {
        Set<Pair<Integer, Integer>> result = new HashSet<>();
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
                    result.add(Pair.create(mutationOfFirst, mutationOfSecond));
                }
            }
        }
        return result;
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

    /**
     * Matrix of mutation pairs. Value - sum of clusters where that mutation pair exists at least in a half of all clones.
     */
    private int[][] commonMutationsDiversity(List<CloneDescription> clones, int[] allMutations) {
        var clusters = clones.stream()
                .collect(Collectors.groupingBy(it -> it.clusterIdentity))
                .values();
        Map<Integer, Integer> indexesByMutations = objectToIndexMap(allMutations);

        int[][] result = new int[allMutations.length][allMutations.length];

        int[][] mutationCounts = null;
        for (List<CloneDescription> cluster : clusters) {
            if (mutationCounts == null) {
                mutationCounts = new int[allMutations.length][allMutations.length];
            } else {
                for (int i = 0; i < allMutations.length; i++) {
                    Arrays.fill(mutationCounts[i], 0);
                }
            }
            for (CloneDescription clone : cluster) {
                int[] mutationsInAClone = clone.mutationsSupplier.get().toArray();
                for (int i = 0; i < mutationsInAClone.length; i++) {
                    int indexOfFirst = indexesByMutations.get(mutationsInAClone[i]);
                    for (int j = 0; j < i; j++) {
                        int indexOfSecond = indexesByMutations.get(mutationsInAClone[j]);
                        mutationCounts[indexOfFirst][indexOfSecond]++;
                        mutationCounts[indexOfSecond][indexOfFirst]++;
                    }
                    mutationCounts[indexOfFirst][indexOfFirst]++;
                }
            }

            Set<Integer> mutationsInAlmostAllClones = mutationsInAllClones(
                    allMutations,
                    mutationCounts,
                    parameters.minPartOfClonesToDeterminateAllele * cluster.size()
            );
            Set<Pair<Integer, Integer>> mutationPairsInAHalfOfClones = mutationPairsWithDiversityMoreThan(
                    allMutations,
                    mutationCounts,
                    mutationsInAlmostAllClones,
                    (parameters.minPartOfClonesToDeterminateAllele / 2.0) * cluster.size()
            );

            Optional<IntStream> bestClique = bestCliques(mutationPairsInAHalfOfClones).findFirst();
            Mutations<NucleotideSequence> alleleInACluster;
            if (bestClique.isPresent()) {
                alleleInACluster = asMutations(Stream.concat(
                        bestClique.get().boxed(),
                        mutationsInAlmostAllClones.stream()
                ));
            } else if (!mutationsInAlmostAllClones.isEmpty()) {
                alleleInACluster = asMutations(mutationsInAlmostAllClones.stream());
            } else {
                alleleInACluster = EMPTY_NUCLEOTIDE_MUTATIONS;
            }
            for (int i = 0; i < alleleInACluster.size(); i++) {
                var indexOfFirst = indexesByMutations.get(alleleInACluster.getMutation(i));
                for (int j = 0; j < i; j++) {
                    var indexOfSecond = indexesByMutations.get(alleleInACluster.getMutation(j));
                    result[indexOfFirst][indexOfSecond]++;
                    result[indexOfSecond][indexOfFirst]++;
                }
                result[indexOfFirst][indexOfFirst]++;
            }
        }

        return result;
    }

    private Map<Integer, Integer> objectToIndexMap(int[] objects) {
        Map<Integer, Integer> objectToIndex = new HashMap<>(objects.length);
        for (int i = 0; i < objects.length; i++) {
            objectToIndex.put(objects[i], i);
        }
        return objectToIndex;
    }

    private static class FindFirstAlleleResult {
        private final Mutations<NucleotideSequence> mutationsInAlmostAllClones;
        private final Mutations<NucleotideSequence> firstAllele;

        private FindFirstAlleleResult(Mutations<NucleotideSequence> mutationsInAlmostAllClones, Mutations<NucleotideSequence> firstAllele) {
            this.mutationsInAlmostAllClones = mutationsInAlmostAllClones;
            this.firstAllele = firstAllele;
        }
    }

    static class CloneDescription {
        private final Supplier<IntStream> mutationsSupplier;
        private final ClusterIdentity clusterIdentity;

        public CloneDescription(Supplier<IntStream> mutationsSupplier, int CDR3Length, String complimentaryGeneName) {
            this.mutationsSupplier = mutationsSupplier;
            this.clusterIdentity = new ClusterIdentity(CDR3Length, complimentaryGeneName);
        }
    }

    private static class ClusterIdentity {
        private final int CDR3Length;
        private final String complimentaryGeneName;

        private ClusterIdentity(int CDR3Length, String complimentaryGeneName) {
            this.CDR3Length = CDR3Length;
            this.complimentaryGeneName = complimentaryGeneName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ClusterIdentity that = (ClusterIdentity) o;
            return CDR3Length == that.CDR3Length && complimentaryGeneName.equals(that.complimentaryGeneName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(CDR3Length, complimentaryGeneName);
        }
    }
}
