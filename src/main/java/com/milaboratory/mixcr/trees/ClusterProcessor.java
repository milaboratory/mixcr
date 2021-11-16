package com.milaboratory.mixcr.trees;

import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.AffineGapAlignmentScoring;
import com.milaboratory.core.alignment.Aligner;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequenceBuilder;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGene;
import org.apache.commons.math3.util.Pair;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.milaboratory.mixcr.trees.ClusteringCriteria.*;
import static io.repseq.core.GeneFeature.CDR3;
import static io.repseq.core.GeneType.*;
import static io.repseq.core.ReferencePoint.CDR3Begin;
import static io.repseq.core.ReferencePoint.CDR3End;

//TODO use scores, not mutations count for calculating distances
class ClusterProcessor {
    private final NucleotideSequence JSequence1;
    private final NucleotideSequence VSequence1;
    private final Cluster<CloneWrapper> originalCluster;


    ClusterProcessor(Cluster<CloneWrapper> originalCluster) {
        if (originalCluster.cluster.isEmpty()) {
            throw new IllegalArgumentException();
        }
        this.originalCluster = originalCluster;
        Clone clone = originalCluster.cluster.get(0).clone;
        JSequence1 = clone.getBestHit(Joining).getAlignment(0).getSequence1();
        VSequence1 = clone.getBestHit(Variable).getAlignment(0).getSequence1();
    }

    /**
     * (1) Parameters that may be used to determinate belonging to one tree:
     * - Distance from germline by V and J mutations
     * - Distance between NDN segments of two clonotypes
     * - Distance between V and J segments of two clonotypes
     * <p>
     * On stage of clustering we can't use VEnd and JBegin marking because gyppermutation on P region affects accuracy.
     * While alignment in some cases it's not possible to determinate mutation of P segment from shorter V or J version and other N nucleotides.
     * So, there will be used CDR3 instead of NDN, VBegin-CDR3Begin instead V and CDR3End-JEnd instead J
     * <p>
     * Within the tree you may find D gene by max sum score on entire tree.
     * <p>
     * Algorithm:
     * 1. Clustering by (1)
     * 2. Build a tree for every cluster
     * 3. Add possible common ancestors
     * 4. Iterate over remain clonotypes and try to add them to build trees with possible ancestors. Try to merge trees
     */
    Collection<Tree<CloneWrapper, NucleotideSequence>> buildTrees() {
        List<CloneWithMutationsFromVJGermline> clones = originalCluster.cluster.stream()
                .map(cloneWrapper -> new CloneWithMutationsFromVJGermline(
                        new MutationsFromVJGermline(
                                getAbsoluteMutationsWithoutCDR3(cloneWrapper.clone, Variable),
                                getAbsoluteMutationsWithoutCDR3(cloneWrapper.clone, Joining),
                                cloneWrapper.clone.getNFeature(CDR3),
                                getSequence1RangesWithoutCDR3(cloneWrapper.clone, Variable),
                                getSequence1RangesWithoutCDR3(cloneWrapper.clone, Joining)
                        ),
                        cloneWrapper
                ))
                .collect(Collectors.toList());

        List<Cluster.Builder<CloneWithMutationsFromVJGermline>> clusteredClones = new ArrayList<>();

        for (CloneWithMutationsFromVJGermline cloneDescriptor : clones) {
            Optional<Pair<Cluster.Builder<CloneWithMutationsFromVJGermline>, Double>> nearestCluster = clusteredClones.stream()
                    .map(cluster -> Pair.create(cluster, distanceToCluster(cloneDescriptor, cluster)))
                    .min(Comparator.comparing(Pair::getSecond));

            //TODO to parameters
            double threshold = 0.2;
            if (nearestCluster.isPresent() && nearestCluster.get().getSecond() < threshold) {
                nearestCluster.get().getFirst().add(cloneDescriptor);
            } else {
                Cluster.Builder<CloneWithMutationsFromVJGermline> builder = new Cluster.Builder<>();
                builder.add(cloneDescriptor);
                clusteredClones.add(builder);
            }
        }

        List<Tree<CloneWithMutationsFromReconstructedRoot, MutationsFromReconstructedRoot>> firstStepTrees = clusteredClones.stream()
                .map(Cluster.Builder::build)
                .filter(it -> it.cluster.size() > 1)
                .map(this::buildATree)
                .collect(Collectors.toList());

        return firstStepTrees.stream()
                .map(tree -> tree.map(it -> it.cloneWrapper, this::buildSequence))
                .collect(Collectors.toList());
    }

    private Tree<CloneWithMutationsFromReconstructedRoot, MutationsFromReconstructedRoot> buildATree(Cluster<CloneWithMutationsFromVJGermline> cluster) {
        // Build a tree for every cluster
        // determine D gene
        // fix marks of VEnd and JBegin
        // resort by mutations count
        // build by next neighbor

        List<VDJCGene> DGeneScores = topScoredDGenes(cluster);
        //TODO there is several similar D matches
        //TODO there is no D match
        VDJCGene DGene = DGeneScores.get(0);

        Alignment<NucleotideSequence> bestDAlignment = findBestDAlignment(cluster, DGene);

        NucleotideSequence reconstructedCDR3 = NucleotideSequence.ALPHABET.createBuilder()
                .append(findVPartInCDR3(cluster))
                .append(bestDAlignment.getSequence1().getRange(bestDAlignment.getSequence1Range()))
                .append(findJPartInCDR3(cluster))
                .createAndDestroy();

        List<CloneWithMutationsFromReconstructedRoot> rebasedCluster = rebaseByReconstructedRoot(cluster, reconstructedCDR3);

        CloneWithMutationsFromReconstructedRoot firstClone = rebasedCluster.get(0);

        TreeBuilderByAncestors<CloneWithMutationsFromReconstructedRoot, MutationsFromReconstructedRoot> treeBuilderByAncestors = new TreeBuilderByAncestors<>(
                firstClone,
                this::distance,
                this::findCommonAncestor,
                it -> it.mutations
        );

        rebasedCluster.subList(1, rebasedCluster.size()).forEach(treeBuilderByAncestors::addNode);
        return treeBuilderByAncestors.getTree();
    }

    private List<CloneWithMutationsFromReconstructedRoot> rebaseByReconstructedRoot(Cluster<CloneWithMutationsFromVJGermline> cluster, NucleotideSequence reconstructedCDR3) {
        return cluster.cluster.stream()
                .map(clone -> new CloneWithMutationsFromReconstructedRoot(
                        new MutationsFromReconstructedRoot(
                                reconstructedCDR3,
                                Aligner.alignGlobal(
                                        AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                                        reconstructedCDR3,
                                        clone.mutations.CDR3
                                ).getAbsoluteMutations(),
                                clone.mutations.VMutationsWithoutCDR3,
                                clone.mutations.VRangesWithoutCDR3,
                                clone.mutations.JMutationsWithoutCDR3,
                                clone.mutations.JRangesWithoutCDR3
                        ),
                        clone.cloneWrapper
                ))
                .sorted(Comparator.comparing(cloneDescriptor -> distanceFromRoot(cloneDescriptor.mutations)))
                .collect(Collectors.toList());
    }

    private int distanceFromRoot(MutationsFromReconstructedRoot mutations) {
        return mutations.CDR3Mutations.size() + mutations.VMutationsWithoutCDR3.size() + mutations.JMutationsWithoutCDR3.size();
    }

    private BigDecimal distance(MutationsFromReconstructedRoot first, MutationsFromReconstructedRoot second) {
        Mutations<NucleotideSequence> VMutations = first.VMutationsWithoutCDR3.invert().combineWith(second.VMutationsWithoutCDR3);
        Mutations<NucleotideSequence> JMutations = first.JMutationsWithoutCDR3.invert().combineWith(second.JMutationsWithoutCDR3);
        Mutations<NucleotideSequence> CDR3Mutations = first.CDR3Mutations.invert().combineWith(second.CDR3Mutations);
        return BigDecimal.valueOf(VMutations.size() + JMutations.size() + CDR3Mutations.size());
    }

    private NucleotideSequence findJPartInCDR3(Cluster<CloneWithMutationsFromVJGermline> cluster) {
        return cluster.cluster.stream()
                .map(clone -> clone.cloneWrapper.clone)
                .map(clone -> {
                    VDJCHit hit = clone.getBestHit(Joining);

                    SequenceBuilder<NucleotideSequence> sequenceBuilder = NucleotideSequence.ALPHABET.createBuilder();

                    int positionOfCDR3End = getRelativePosition(hit, CDR3End);

                    for (int i = 0; i < hit.getAlignments().length; i++) {
                        Alignment<NucleotideSequence> alignment = hit.getAlignment(i);
                        if (alignment.getSequence1Range().contains(positionOfCDR3End)) {
                            sequenceBuilder.append(alignment.getSequence1().getRange(alignment.getSequence1Range().getLower(), positionOfCDR3End));
                            break;
                        } else {
                            sequenceBuilder.append(alignment.getSequence1().getRange(alignment.getSequence1Range()));
                        }
                    }

                    return sequenceBuilder.createAndDestroy();
                })
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseThrow(IllegalStateException::new);
    }

    private NucleotideSequence findVPartInCDR3(Cluster<CloneWithMutationsFromVJGermline> cluster) {
        return cluster.cluster.stream()
                .map(clone -> clone.cloneWrapper.clone)
                .map(clone -> {
                    VDJCHit hit = clone.getBestHit(Variable);

                    int positionOfCDR3Begin = getRelativePosition(hit, CDR3Begin);

                    Stack<NucleotideSequence> parts = new Stack<>();

                    for (int i = hit.getAlignments().length - 1; i >= 0; i--) {
                        Alignment<NucleotideSequence> alignment = hit.getAlignment(i);
                        if (alignment.getSequence1Range().contains(positionOfCDR3Begin)) {
                            parts.push(alignment.getSequence1().getRange(positionOfCDR3Begin, alignment.getSequence1Range().getUpper()));
                            break;
                        } else {
                            parts.push(alignment.getSequence1().getRange(alignment.getSequence1Range()));
                        }
                    }

                    SequenceBuilder<NucleotideSequence> sequenceBuilder = NucleotideSequence.ALPHABET.createBuilder();

                    while (!parts.isEmpty()) {
                        sequenceBuilder.append(parts.pop());
                    }

                    return sequenceBuilder.createAndDestroy();
                })
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseThrow(IllegalStateException::new);
    }

    private Alignment<NucleotideSequence> findBestDAlignment(Cluster<CloneWithMutationsFromVJGermline> cluster, VDJCGene DGene) {
        return cluster.cluster.stream()
                .map(clone -> clone.cloneWrapper.clone)
                .flatMap(clone -> Arrays.stream(clone.getHits(Diversity))
                        .filter(hit -> hit.getGene().equals(DGene))
                        //TODO if there is a hole
                        .map(hit -> hit.getAlignment(0))
                )
                .collect(Collectors.groupingBy(it -> it.getSequence1Range().length()))
                .entrySet().stream()
                .max(Map.Entry.comparingByKey())
                .orElseThrow(IllegalStateException::new)
                .getValue().stream()
                .max(Comparator.comparing(Alignment::getScore))
                .orElseThrow(IllegalStateException::new);
    }

    private MutationsFromReconstructedRoot findCommonAncestor(MutationsFromReconstructedRoot first, MutationsFromReconstructedRoot second) {
        return new MutationsFromReconstructedRoot(
                first.CDR3OfRoot,
                intersection(first.CDR3Mutations, second.CDR3Mutations),
                intersection(first.VMutationsWithoutCDR3, second.VMutationsWithoutCDR3),
                intersection(first.VRangesWithoutCDR3, second.VRangesWithoutCDR3),
                intersection(first.JMutationsWithoutCDR3, second.JMutationsWithoutCDR3),
                intersection(first.JRangesWithoutCDR3, second.JRangesWithoutCDR3)
        );
    }

    private List<Range> intersection(List<Range> first, List<Range> second) {
        return first.stream()
                .map(range -> {
                    Range result = range;
                    for (Range rangeInSecond : second) {
                        result = range.intersection(rangeInSecond);
                    }
                    return result;
                })
                .collect(Collectors.toList());
    }

    private Mutations<NucleotideSequence> intersection(Mutations<NucleotideSequence> first, Mutations<NucleotideSequence> second) {
        Set<Integer> mutationsOfFirstAsSet = Arrays.stream(first.getRAWMutations()).boxed().collect(Collectors.toSet());
        int[] intersection = Arrays.stream(second.getRAWMutations()).filter(it -> !mutationsOfFirstAsSet.contains(it)).toArray();
        return new Mutations<>(NucleotideSequence.ALPHABET, intersection);
    }

    //TODO two matches from the same DGene but in different positions
    private List<VDJCGene> topScoredDGenes(Cluster<CloneWithMutationsFromVJGermline> cluster) {
        List<Map.Entry<VDJCGene, Double>> DGeneScores = cluster.cluster.stream()
                .flatMap(it -> Arrays.stream(it.cloneWrapper.clone.getHits(Diversity)))
                .collect(Collectors.groupingBy(VDJCHit::getGene, Collectors.summingDouble(VDJCHit::getScore)))
                .entrySet().stream()
                .sorted(Map.Entry.<VDJCGene, Double>comparingByValue().reversed())
                .collect(Collectors.toList());
        if (DGeneScores.isEmpty()) {
            return Collections.emptyList();
        } else if (DGeneScores.size() == 1) {
            return Collections.singletonList(DGeneScores.get(0).getKey());
        }
        Double maxScore = DGeneScores.get(0).getValue();

        //TODO to parameters
        double scoreDifferenceThreshold = 1.5;
        return Stream.concat(
                Stream.of(DGeneScores.get(0).getKey()),
                DGeneScores.subList(1, DGeneScores.size() - 1).stream()
                        .filter(it -> it.getValue() < maxScore / scoreDifferenceThreshold)
                        .map(Map.Entry::getKey)
        ).collect(Collectors.toList());
    }

    private double distanceToCluster(CloneWithMutationsFromVJGermline cloneDescriptor, Cluster.Builder<CloneWithMutationsFromVJGermline> cluster) {
        return cluster.getCurrentCluster().stream()
                .mapToDouble(compareTo -> computeDistance(cloneDescriptor.mutations, compareTo.mutations))
                .min()
                .orElseThrow(IllegalArgumentException::new);
    }

    private double computeDistance(MutationsFromVJGermline base, MutationsFromVJGermline compareWith) {
        Mutations<NucleotideSequence> VMutations = base.VMutationsWithoutCDR3.invert().combineWith(compareWith.VMutationsWithoutCDR3);
        Mutations<NucleotideSequence> JMutations = base.JMutationsWithoutCDR3.invert().combineWith(compareWith.JMutationsWithoutCDR3);

        Mutations<NucleotideSequence> CDR3Mutations = Aligner.alignGlobal(
                AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                base.CDR3,
                compareWith.CDR3
        ).getAbsoluteMutations();

        double CDR3Length = (base.CDR3.size() + compareWith.CDR3.size()) / 2.0;
        double VLength = (totalLength(base.VRangesWithoutCDR3) + totalLength(compareWith.VRangesWithoutCDR3)) / 2.0;
        double JLength = (totalLength(base.JRangesWithoutCDR3) + totalLength(compareWith.JRangesWithoutCDR3)) / 2.0;

        double normalizedDistanceFromCloneToGermline = (base.VMutationsWithoutCDR3.size() + base.JMutationsWithoutCDR3.size()) / (VLength + JLength);
        double normalizedDistanceFromCompareToGermline = (compareWith.VMutationsWithoutCDR3.size() + compareWith.JMutationsWithoutCDR3.size()) / (VLength + JLength);
        double normalizedAverageDistanceToGermline = (normalizedDistanceFromCloneToGermline + normalizedDistanceFromCompareToGermline) / 2.0;
        double normalizedDistanceBetweenClones = (VMutations.size() + JMutations.size() + CDR3Mutations.size()) / (VLength + JLength + CDR3Length);
        double normalizedDistanceBetweenClonesInCDR3 = (CDR3Mutations.size()) / CDR3Length;

        //TODO parameters
        return normalizedDistanceBetweenClonesInCDR3 + (normalizedDistanceBetweenClones - normalizedAverageDistanceToGermline);
    }

    private int totalLength(List<Range> ranges) {
        return ranges.stream().mapToInt(Range::length).sum();
    }

    private static List<Range> getSequence1RangesWithoutCDR3(Clone clone, GeneType geneType) {
        VDJCHit bestHit = clone.getBestHit(geneType);
        Range CDR3Range = CDR3Sequence1Range(bestHit, 0);

        return Arrays.stream(bestHit.getAlignments())
                .map(Alignment::getSequence1Range)
                .flatMap(sequence1Range -> sequence1Range.without(CDR3Range).stream())
                .collect(Collectors.toList());
    }

    private static class MutationsFromVJGermline {
        private final NucleotideSequence CDR3;

        private final Mutations<NucleotideSequence> VMutationsWithoutCDR3;
        private final List<Range> VRangesWithoutCDR3;

        private final Mutations<NucleotideSequence> JMutationsWithoutCDR3;
        private final List<Range> JRangesWithoutCDR3;

        private MutationsFromVJGermline(Mutations<NucleotideSequence> VMutationsWithoutCDR3,
                                        Mutations<NucleotideSequence> JMutationsWithoutCDR3,
                                        NucleotideSequence CDR3,
                                        List<Range> VRangesWithoutCDR3,
                                        List<Range> JRangesWithoutCDR3) {
            this.VMutationsWithoutCDR3 = VMutationsWithoutCDR3;
            this.JMutationsWithoutCDR3 = JMutationsWithoutCDR3;
            this.CDR3 = CDR3;
            this.VRangesWithoutCDR3 = VRangesWithoutCDR3;
            this.JRangesWithoutCDR3 = JRangesWithoutCDR3;
        }
    }

    private static class MutationsFromReconstructedRoot {
        private final NucleotideSequence CDR3OfRoot;

        private final Mutations<NucleotideSequence> CDR3Mutations;

        private final Mutations<NucleotideSequence> VMutationsWithoutCDR3;
        private final List<Range> VRangesWithoutCDR3;

        private final Mutations<NucleotideSequence> JMutationsWithoutCDR3;
        private final List<Range> JRangesWithoutCDR3;

        public MutationsFromReconstructedRoot(NucleotideSequence CDR3OfRoot,
                                              Mutations<NucleotideSequence> CDR3Mutations,
                                              Mutations<NucleotideSequence> VMutationsWithoutCDR3,
                                              List<Range> VRangesWithoutCDR3,
                                              Mutations<NucleotideSequence> JMutationsWithoutCDR3,
                                              List<Range> JRangesWithoutCDR3) {
            this.CDR3OfRoot = CDR3OfRoot;
            this.CDR3Mutations = CDR3Mutations;
            this.VMutationsWithoutCDR3 = VMutationsWithoutCDR3;
            this.VRangesWithoutCDR3 = VRangesWithoutCDR3;
            this.JMutationsWithoutCDR3 = JMutationsWithoutCDR3;
            this.JRangesWithoutCDR3 = JRangesWithoutCDR3;
        }
    }

    public NucleotideSequence buildSequence(MutationsFromReconstructedRoot ancestor) {
        SequenceBuilder<NucleotideSequence> builder = NucleotideSequence.ALPHABET.createBuilder();
        ancestor.VRangesWithoutCDR3.stream().map(VSequence1::getRange).forEach(builder::append);
        builder.append(ancestor.CDR3Mutations.mutate(ancestor.CDR3OfRoot));
        ancestor.JRangesWithoutCDR3.stream().map(JSequence1::getRange).forEach(builder::append);
        return builder.createAndDestroy();
    }

    private static class CloneWithMutationsFromReconstructedRoot {
        private final MutationsFromReconstructedRoot mutations;
        private final CloneWrapper cloneWrapper;

        private CloneWithMutationsFromReconstructedRoot(MutationsFromReconstructedRoot mutations, CloneWrapper cloneWrapper) {
            this.mutations = mutations;
            this.cloneWrapper = cloneWrapper;
        }
    }

    private static class CloneWithMutationsFromVJGermline {
        private final MutationsFromVJGermline mutations;
        private final CloneWrapper cloneWrapper;

        private CloneWithMutationsFromVJGermline(MutationsFromVJGermline mutations, CloneWrapper cloneWrapper) {
            this.mutations = mutations;
            this.cloneWrapper = cloneWrapper;
        }
    }
}
