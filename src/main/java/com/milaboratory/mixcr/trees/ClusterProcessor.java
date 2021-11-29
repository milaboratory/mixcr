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
import com.milaboratory.mixcr.trees.TreeBuilderByAncestors.ObservedOrReconstructed;
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
     * On stage of clustering we can't use VEnd and JBegin marking because hypermutation on P region affects accuracy.
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
    Collection<Tree<ObservedOrReconstructed<CloneWrapper, NucleotideSequence>>> buildTrees() {
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

        List<TreeWithMeta> firstStepTrees = clusteredClones.stream()
                .map(Cluster.Builder::build)
                .filter(it -> it.cluster.size() > 1)
                .map(this::buildATree)
                .collect(Collectors.toList());

        return firstStepTrees.stream()
                .map(treeWithMeta -> treeWithMeta.tree.map(node ->
                        node.map(it -> it.cloneWrapper, ancestor -> buildSequence(ancestor, treeWithMeta.CDR3OfRoot))
                ))
                .collect(Collectors.toList());
    }

    private TreeWithMeta buildATree(Cluster<CloneWithMutationsFromVJGermline> cluster) {
        // Build a tree for every cluster
        // fix marks of VEnd and JBegin
        // determine part between VEnd and JBegin
        // resort by mutations count
        // build by next neighbor

        //TODO maybe just use multialignment for finding common part, choose more realistic D gene as common ancestor
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

        MutationsDescription root = new MutationsDescription(
                Mutations.empty(NucleotideSequence.ALPHABET),
                Mutations.empty(NucleotideSequence.ALPHABET),
                overlap(rebasedCluster.stream().map(it -> it.mutations.VRangesWithoutCDR3).collect(Collectors.toList())),
                Mutations.empty(NucleotideSequence.ALPHABET),
                overlap(rebasedCluster.stream().map(it -> it.mutations.JRangesWithoutCDR3).collect(Collectors.toList()))
        );
        TreeBuilderByAncestors<CloneWithMutationsFromReconstructedRoot, MutationsDescription, MutationsDescription> treeBuilderByAncestors = new TreeBuilderByAncestors<>(
                root,
                this::distance,
                this::mutationsBetween,
                this::combineWith,
                CloneWithMutationsFromReconstructedRoot::getMutations,
                this::commonMutations
        );

        rebasedCluster.forEach(treeBuilderByAncestors::addNode);
        return new TreeWithMeta(
                treeBuilderByAncestors.getTree(),
                reconstructedCDR3
        );
    }

    private List<CloneWithMutationsFromReconstructedRoot> rebaseByReconstructedRoot(Cluster<CloneWithMutationsFromVJGermline> cluster, NucleotideSequence reconstructedCDR3) {
        return cluster.cluster.stream()
                .map(clone -> new CloneWithMutationsFromReconstructedRoot(
                        new MutationsDescription(
                                //TODO align only part
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
                .sorted(Comparator.comparing(cloneDescriptor -> distance(cloneDescriptor.mutations)))
                .collect(Collectors.toList());
    }

    private BigDecimal distance(MutationsDescription mutations) {
        return BigDecimal.valueOf(
                mutations.CDR3Mutations.size() + mutations.VMutationsWithoutCDR3.size() + mutations.JMutationsWithoutCDR3.size()
        );
    }

    private MutationsDescription mutationsBetween(MutationsDescription first, MutationsDescription second) {
        return new MutationsDescription(
                difference(first.CDR3Mutations, second.CDR3Mutations),
                difference(first.VMutationsWithoutCDR3, second.VMutationsWithoutCDR3),
                //TODO may be it's wrong, check when there will be holes
                intersection(first.VRangesWithoutCDR3, second.VRangesWithoutCDR3),
                difference(first.JMutationsWithoutCDR3, second.JMutationsWithoutCDR3),
                //TODO may be it's wrong, check when there will be holes
                intersection(first.JRangesWithoutCDR3, second.JRangesWithoutCDR3)
        );
    }

    private MutationsDescription combineWith(MutationsDescription first, MutationsDescription second) {
        return new MutationsDescription(
                first.CDR3Mutations.combineWith(second.CDR3Mutations),
                first.VMutationsWithoutCDR3.combineWith(second.VMutationsWithoutCDR3),
                //TODO may be it's wrong, check when there will be holes
                overlap(Arrays.asList(first.VRangesWithoutCDR3, second.VRangesWithoutCDR3)),
                first.JMutationsWithoutCDR3.combineWith(second.JMutationsWithoutCDR3),
                //TODO may be it's wrong, check when there will be holes
                overlap(Arrays.asList(first.JRangesWithoutCDR3, second.JRangesWithoutCDR3))
        );
    }

    private Mutations<NucleotideSequence> difference(Mutations<NucleotideSequence> from, Mutations<NucleotideSequence> to) {
        return from.invert().combineWith(to);
    }

    private MutationsDescription commonMutations(MutationsDescription first, MutationsDescription second) {
        return new MutationsDescription(
                intersection(first.CDR3Mutations, second.CDR3Mutations),
                intersection(first.VMutationsWithoutCDR3, second.VMutationsWithoutCDR3),
                //TODO may be it's wrong, check when there will be holes
                intersection(first.VRangesWithoutCDR3, second.VRangesWithoutCDR3),
                intersection(first.JMutationsWithoutCDR3, second.JMutationsWithoutCDR3),
                //TODO may be it's wrong, check when there will be holes
                intersection(first.JRangesWithoutCDR3, second.JRangesWithoutCDR3)
        );
    }

    //TODO it is more possible to decrease length of alignment than to increase. It is important on small trees
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

    //TODO it is more possible to decrease length of alignment than to increase. It is important on small trees
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

    //TODO check when there will be holes
    private List<Range> overlap(List<List<Range>> ranges) {
        List<Range> sorted = ranges.stream()
                .flatMap(Collection::stream)
                .sorted(Comparator.comparing(Range::getLower))
                .collect(Collectors.toList());
        List<Range> result = new ArrayList<>();
        int currentLeft = sorted.get(0).getLower();
        int currentRight = sorted.get(0).getUpper();
        for (Range range : sorted.subList(1, sorted.size())) {
            if (range.getLower() <= currentRight) {
                currentRight = Math.min(currentRight, range.getUpper());
            } else {
                result.add(new Range(currentLeft, currentRight));
                currentLeft = range.getLower();
                currentRight = range.getUpper();
            }
        }
        result.add(new Range(currentLeft, currentRight));
        return result;
    }

    private Mutations<NucleotideSequence> intersection(Mutations<NucleotideSequence> first, Mutations<NucleotideSequence> second) {
        Set<Integer> mutationsOfFirstAsSet = Arrays.stream(first.getRAWMutations()).boxed().collect(Collectors.toSet());
        int[] intersection = Arrays.stream(second.getRAWMutations()).filter(it -> !mutationsOfFirstAsSet.contains(it)).toArray();
        return new Mutations<>(NucleotideSequence.ALPHABET, intersection);
    }

    //TODO two matches from the same DGene but in different positions
    private List<VDJCGene> topScoredDGenes(Cluster<CloneWithMutationsFromVJGermline> cluster) {
        //TODO use distance from germline as fine
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
        Mutations<NucleotideSequence> VMutations = difference(base.VMutationsWithoutCDR3, compareWith.VMutationsWithoutCDR3);
        Mutations<NucleotideSequence> JMutations = difference(base.JMutationsWithoutCDR3, compareWith.JMutationsWithoutCDR3);

        //TODO use more optimized variant
        //TODO compare only part between V and J
        Mutations<NucleotideSequence> CDR3Mutations = Aligner.alignGlobal(
                AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                base.CDR3,
                compareWith.CDR3
        ).getAbsoluteMutations();

        double CDR3Length = (base.CDR3.size() + compareWith.CDR3.size()) / 2.0;
        double VLength = (totalLength(base.VRangesWithoutCDR3) + totalLength(compareWith.VRangesWithoutCDR3)) / 2.0;
        double JLength = (totalLength(base.JRangesWithoutCDR3) + totalLength(compareWith.JRangesWithoutCDR3)) / 2.0;

        //TODO don't use average length
        double normalizedDistanceFromCloneToGermline = (base.VMutationsWithoutCDR3.size() + base.JMutationsWithoutCDR3.size()) / (VLength + JLength);
        //TODO don't use average length
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

    private static class MutationsDescription {
        private final Mutations<NucleotideSequence> CDR3Mutations;

        private final Mutations<NucleotideSequence> VMutationsWithoutCDR3;
        private final List<Range> VRangesWithoutCDR3;

        private final Mutations<NucleotideSequence> JMutationsWithoutCDR3;
        private final List<Range> JRangesWithoutCDR3;

        public MutationsDescription(Mutations<NucleotideSequence> CDR3Mutations,
                                    Mutations<NucleotideSequence> VMutationsWithoutCDR3,
                                    List<Range> VRangesWithoutCDR3,
                                    Mutations<NucleotideSequence> JMutationsWithoutCDR3,
                                    List<Range> JRangesWithoutCDR3) {
            this.CDR3Mutations = CDR3Mutations;
            this.VMutationsWithoutCDR3 = VMutationsWithoutCDR3;
            this.VRangesWithoutCDR3 = VRangesWithoutCDR3;
            this.JMutationsWithoutCDR3 = JMutationsWithoutCDR3;
            this.JRangesWithoutCDR3 = JRangesWithoutCDR3;
        }
    }

    private NucleotideSequence buildSequence(MutationsDescription ancestor, NucleotideSequence CDR3OfRoot) {
        SequenceBuilder<NucleotideSequence> builder = NucleotideSequence.ALPHABET.createBuilder();
        ancestor.VRangesWithoutCDR3.stream().map(VSequence1::getRange).forEach(builder::append);
        builder.append(ancestor.CDR3Mutations.mutate(CDR3OfRoot));
        ancestor.JRangesWithoutCDR3.stream().map(JSequence1::getRange).forEach(builder::append);
        return builder.createAndDestroy();
    }

    private static class TreeWithMeta {
        private final Tree<ObservedOrReconstructed<CloneWithMutationsFromReconstructedRoot, MutationsDescription>> tree;
        private final NucleotideSequence CDR3OfRoot;

        public TreeWithMeta(Tree<ObservedOrReconstructed<CloneWithMutationsFromReconstructedRoot, MutationsDescription>> tree, NucleotideSequence CDR3OfRoot) {
            this.tree = tree;
            this.CDR3OfRoot = CDR3OfRoot;
        }
    }

    private static class CloneWithMutationsFromReconstructedRoot {
        private final MutationsDescription mutations;
        private final CloneWrapper cloneWrapper;

        private CloneWithMutationsFromReconstructedRoot(MutationsDescription mutations, CloneWrapper cloneWrapper) {
            this.mutations = mutations;
            this.cloneWrapper = cloneWrapper;
        }

        public MutationsDescription getMutations() {
            return mutations;
        }

        public CloneWrapper getCloneWrapper() {
            return cloneWrapper;
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
