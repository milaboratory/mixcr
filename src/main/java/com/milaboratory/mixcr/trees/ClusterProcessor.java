package com.milaboratory.mixcr.trees;

import com.google.common.collect.Lists;
import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.AffineGapAlignmentScoring;
import com.milaboratory.core.alignment.Aligner;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.alignment.AlignmentUtils;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequenceBuilder;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.trees.TreeBuilderByAncestors.ObservedOrReconstructed;
import org.apache.commons.math3.util.Pair;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.milaboratory.mixcr.trees.ClusteringCriteria.*;
import static io.repseq.core.GeneFeature.CDR3;
import static io.repseq.core.GeneType.Joining;
import static io.repseq.core.GeneType.Variable;
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
    Collection<Tree<ObservedOrReconstructed<CloneWrapper, AncestorInfo>>> buildTrees() {
        List<CloneWithMutationsFromVJGermline> clones = originalCluster.cluster.stream()
                .map(cloneWrapper -> new CloneWithMutationsFromVJGermline(
                        new MutationsFromVJGermline(
                                getMutationsWithoutCDR3(cloneWrapper.clone, Variable),
                                getMutationsWithoutCDR3(cloneWrapper.clone, Joining),
                                cloneWrapper.clone.getNFeature(CDR3)
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
            double threshold = 0.9;
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
                        node.map(it -> it.cloneWrapper, this::buildAncestorInfo)
                ))
                .collect(Collectors.toList());
    }

    private TreeWithMeta buildATree(Cluster<CloneWithMutationsFromVJGermline> cluster) {
        // Build a tree for every cluster
        // fix marks of VEnd and JBegin
        // determine part between VEnd and JBegin
        // resort by mutations count
        // build by next neighbor

        NucleotideSequence vPartInCDR3 = findVPartInCDR3(cluster);
        NucleotideSequence jPartInCDR3 = findJPartInCDR3(cluster);
        NucleotideSequence reconstructedCDR3 = NucleotideSequence.ALPHABET.createBuilder()
                .append(vPartInCDR3)
//                .append(cluster.cluster.get(0).mutations.CDR3.getRange(vPartInCDR3.size(), cluster.cluster.get(0).mutations.CDR3.size() - jPartInCDR3.size()))
                .append(jPartInCDR3)
                .createAndDestroy();

        List<CloneWithMutationsFromReconstructedRoot> rebasedCluster = rebaseByReconstructedRoot(cluster, reconstructedCDR3);

        MutationsDescription root = new MutationsDescription(
                Lists.newArrayList(
                        new MutationsWithRange(
                                reconstructedCDR3,
                                Mutations.empty(NucleotideSequence.ALPHABET),
                                new Range(0, reconstructedCDR3.size())
                        )
                ),
                overlap(rebasedCluster.stream()
                        .map(it -> it.mutations.VMutationsWithoutCDR3.stream()
                                .map(MutationsWithRange::getSequence1Range)
                                .collect(Collectors.toList())
                        ).collect(Collectors.toList())
                ).stream()
                        .map(it -> new MutationsWithRange(VSequence1, Mutations.EMPTY_NUCLEOTIDE_MUTATIONS, it))
                        .collect(Collectors.toList()),
                overlap(rebasedCluster.stream()
                        .map(it -> it.mutations.JMutationsWithoutCDR3.stream()
                                .map(MutationsWithRange::getSequence1Range)
                                .collect(Collectors.toList())
                        ).collect(Collectors.toList())
                ).stream()
                        .map(it -> new MutationsWithRange(JSequence1, Mutations.EMPTY_NUCLEOTIDE_MUTATIONS, it))
                        .collect(Collectors.toList())
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
                                mutations(reconstructedCDR3, clone.mutations.CDR3),
                                clone.mutations.VMutationsWithoutCDR3,
                                clone.mutations.JMutationsWithoutCDR3
                        ),
                        clone.cloneWrapper
                ))
                .sorted(Comparator.comparing(cloneDescriptor -> distance(cloneDescriptor.mutations)))
                .collect(Collectors.toList());
    }

    private BigDecimal distance(MutationsDescription mutations) {
        return BigDecimal.valueOf(
                1 - (
                        (score(mutations.CDR3Mutations) + score(mutations.VMutationsWithoutCDR3) + score(mutations.JMutationsWithoutCDR3)) /
                                (maxScore(mutations.CDR3Mutations) + maxScore(mutations.VMutationsWithoutCDR3) + maxScore(mutations.JMutationsWithoutCDR3))
                )
        );
    }

    private MutationsDescription mutationsBetween(MutationsDescription first, MutationsDescription second) {
        return new MutationsDescription(
                mutationsBetween(first.CDR3Mutations, second.CDR3Mutations),
                mutationsBetween(first.VMutationsWithoutCDR3, second.VMutationsWithoutCDR3),
                mutationsBetween(first.JMutationsWithoutCDR3, second.JMutationsWithoutCDR3)
        );
    }

    private MutationsDescription combineWith(MutationsDescription first, MutationsDescription second) {
        return new MutationsDescription(
                combineWith(first.CDR3Mutations, second.CDR3Mutations),
                combineWith(first.VMutationsWithoutCDR3, second.VMutationsWithoutCDR3),
                combineWith(first.JMutationsWithoutCDR3, second.JMutationsWithoutCDR3)
        );
    }

    private MutationsDescription commonMutations(MutationsDescription first, MutationsDescription second) {
        return new MutationsDescription(
                intersection(first.CDR3Mutations, second.CDR3Mutations),
                intersection(first.VMutationsWithoutCDR3, second.VMutationsWithoutCDR3),
                intersection(first.JMutationsWithoutCDR3, second.JMutationsWithoutCDR3)
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

    private static List<MutationsWithRange> mutationsBetween(List<MutationsWithRange> firstMutations, List<MutationsWithRange> secondMutations) {
        return firstMutations.stream()
                .flatMap(base -> secondMutations.stream().flatMap(comparison -> {
                    Range intersection = base.getSequence1Range().intersection(comparison.getSequence1Range());
                    if (intersection != null) {
                        return Stream.of(new MutationsWithRange(
                                base.getMutations().mutate(base.getSequence1()),
                                MutationsUtils.difference(
                                        base.getMutations().extractAbsoluteMutationsForRange(intersection),
                                        comparison.getMutations().extractAbsoluteMutationsForRange(intersection)
                                ),
                                intersection
                        ));
                    } else {
                        return Stream.empty();
                    }
                }))
                .collect(Collectors.toList());
    }

    private List<MutationsWithRange> combineWith(List<MutationsWithRange> firstMutations, List<MutationsWithRange> secondMutations) {
        return firstMutations.stream()
                .flatMap(base -> secondMutations.stream().flatMap(comparison -> {
                    Range intersection = base.getSequence1Range().intersection(comparison.getSequence1Range());
                    if (intersection != null) {
                        return Stream.of(new MutationsWithRange(
                                base.getSequence1(),
                                base.getMutations().combineWith(comparison.getMutations()),
                                intersection
                        ));
                    } else {
                        return Stream.empty();
                    }
                }))
                .collect(Collectors.toList());
    }

    private static List<MutationsWithRange> intersection(List<MutationsWithRange> from, List<MutationsWithRange> to) {
        return from.stream()
                .flatMap(base -> to.stream().flatMap(comparison -> {
                    Range intersection = base.getSequence1Range().intersection(comparison.getSequence1Range());
                    if (intersection != null) {
                        return Stream.of(new MutationsWithRange(
                                base.getSequence1(),
                                MutationsUtils.intersection(
                                        base.getMutations().extractAbsoluteMutationsForRange(intersection),
                                        comparison.getMutations().extractAbsoluteMutationsForRange(intersection)
                                ),
                                intersection
                        ));
                    } else {
                        return Stream.empty();
                    }
                }))
                .collect(Collectors.toList());

    }

    private double distanceToCluster(CloneWithMutationsFromVJGermline cloneDescriptor, Cluster.Builder<CloneWithMutationsFromVJGermline> cluster) {
        return cluster.getCurrentCluster().stream()
                .mapToDouble(compareTo -> fitnessFunction(fitnessFunctionParams(cloneDescriptor.mutations, compareTo.mutations)))
                .min()
                .orElseThrow(IllegalArgumentException::new);
    }

    private FitnessFunctionParams fitnessFunctionParams(MutationsFromVJGermline first, MutationsFromVJGermline second) {
        List<MutationsWithRange> CDR3MutationsBetween = mutations(first.CDR3, second.CDR3);

        List<MutationsWithRange> VMutationsBetween = mutationsBetween(first.VMutationsWithoutCDR3, second.VMutationsWithoutCDR3);
        double VMutationsBetweenScore = score(VMutationsBetween);

        List<MutationsWithRange> JMutationsBetween = mutationsBetween(first.JMutationsWithoutCDR3, second.JMutationsWithoutCDR3);
        double JMutationsBetweenScore = score(JMutationsBetween);

        double CDR3MutationsBetweenScore = score(CDR3MutationsBetween);

        double maxScoreForFirstVJ = maxScore(first.VMutationsWithoutCDR3) + maxScore(first.JMutationsWithoutCDR3);
        double maxScoreForSecondVJ = maxScore(second.VMutationsWithoutCDR3) + maxScore(second.JMutationsWithoutCDR3);
        double maxScoreForVJ = Math.max(maxScoreForFirstVJ, maxScoreForSecondVJ);
        double maxScoreForCDR3 = Math.max(maxScore(first.CDR3), maxScore(second.CDR3));


        double normalizedDistanceFromFirstToGermline = 1 - (score(first.VMutationsWithoutCDR3) + score(first.JMutationsWithoutCDR3)) / maxScoreForFirstVJ;
        double normalizedDistanceFromSecondToGermline = 1 - (score(second.VMutationsWithoutCDR3) + score(second.JMutationsWithoutCDR3)) / maxScoreForSecondVJ;
        double normalizedDistanceBetweenClones = 1 - (VMutationsBetweenScore + JMutationsBetweenScore + CDR3MutationsBetweenScore) /
                (maxScoreForVJ + maxScoreForCDR3);
        double normalizedDistanceBetweenClonesInCDR3 = 1 - (CDR3MutationsBetweenScore) / maxScoreForCDR3;
        double normalizedDistanceBetweenClonesWithoutCDR3 = 1 - (VMutationsBetweenScore + JMutationsBetweenScore) / (maxScoreForVJ + maxScoreForCDR3);

        return new FitnessFunctionParams(
                normalizedDistanceBetweenClonesInCDR3,
                normalizedDistanceBetweenClones,
                normalizedDistanceBetweenClonesWithoutCDR3,
                Math.min(normalizedDistanceFromFirstToGermline, normalizedDistanceFromSecondToGermline)
        );
    }

    private double fitnessFunction(FitnessFunctionParams params) {
        //TODO move constants to params
        return Math.pow(params.distanceBetweenClonesWithoutCDR3, 1.0) +
                4 * Math.pow(params.distanceBetweenClonesInCDR3, 1.0) * Math.pow(params.minDistanceToGermline - 1, 6.0);
    }


    private double maxScore(List<MutationsWithRange> vMutationsBetween) {
        return vMutationsBetween.stream()
                .mapToDouble(mutations -> AlignmentUtils.calculateScore(
                        mutations.getSequence1(),
                        mutations.getSequence1Range(),
                        Mutations.EMPTY_NUCLEOTIDE_MUTATIONS,
                        AffineGapAlignmentScoring.getNucleotideBLASTScoring()
                ))
                .sum();
    }

    private double maxScore(NucleotideSequence sequence) {
        return AlignmentUtils.calculateScore(
                sequence,
                Mutations.EMPTY_NUCLEOTIDE_MUTATIONS,
                AffineGapAlignmentScoring.getNucleotideBLASTScoring()
        );
    }

    //TODO with holes
    private static List<MutationsWithRange> mutations(NucleotideSequence first, NucleotideSequence second) {
        return Collections.singletonList(
                new MutationsWithRange(
                        first,
                        Aligner.alignGlobal(
                                AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                                first,
                                second
                        ).getAbsoluteMutations(),
                        new Range(0, first.size())
                )
        );
    }

    private static class MutationsFromVJGermline {
        private final NucleotideSequence CDR3;

        private final List<MutationsWithRange> VMutationsWithoutCDR3;
        private final List<MutationsWithRange> JMutationsWithoutCDR3;

        private MutationsFromVJGermline(
                List<MutationsWithRange> VMutationsWithoutCDR3,
                List<MutationsWithRange> JMutationsWithoutCDR3,
                NucleotideSequence CDR3
        ) {
            this.VMutationsWithoutCDR3 = VMutationsWithoutCDR3;
            this.JMutationsWithoutCDR3 = JMutationsWithoutCDR3;
            this.CDR3 = CDR3;
        }
    }

    private static class MutationsDescription {
        private final List<MutationsWithRange> CDR3Mutations;

        private final List<MutationsWithRange> VMutationsWithoutCDR3;
        private final List<MutationsWithRange> JMutationsWithoutCDR3;

        public MutationsDescription(
                List<MutationsWithRange> CDR3Mutations,
                List<MutationsWithRange> VMutationsWithoutCDR3,
                List<MutationsWithRange> JMutationsWithoutCDR3
        ) {
            this.CDR3Mutations = CDR3Mutations;
            this.VMutationsWithoutCDR3 = VMutationsWithoutCDR3;
            this.JMutationsWithoutCDR3 = JMutationsWithoutCDR3;
        }
    }

    private AncestorInfo buildAncestorInfo(MutationsDescription ancestor) {
        SequenceBuilder<NucleotideSequence> builder = NucleotideSequence.ALPHABET.createBuilder();
        ancestor.JMutationsWithoutCDR3.stream()
                .map(this::buildSequence)
                .forEach(builder::append);
        int CDR3Begin = builder.size();
        ancestor.CDR3Mutations.stream()
                .map(this::buildSequence)
                .forEach(builder::append);
        int CDR3End = builder.size();
        ancestor.JMutationsWithoutCDR3.stream()
                .map(this::buildSequence)
                .forEach(builder::append);
        return new AncestorInfo(
                builder.createAndDestroy(),
                CDR3Begin,
                CDR3End
        );
    }

    private NucleotideSequence buildSequence(MutationsWithRange mutation) {
        return mutation.getMutations().mutate(mutation.getSequence1());
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
