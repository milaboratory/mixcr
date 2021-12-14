package com.milaboratory.mixcr.trees;

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
import com.milaboratory.mixcr.util.Java9Util;
import com.milaboratory.mixcr.util.TriFunction;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.ReferencePoint;
import org.apache.commons.math3.util.Pair;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.milaboratory.core.mutations.Mutations.EMPTY_NUCLEOTIDE_MUTATIONS;
import static com.milaboratory.mixcr.trees.ClusteringCriteria.getRelativePosition;
import static com.milaboratory.mixcr.trees.ClusteringCriteria.score;
import static io.repseq.core.GeneType.Joining;
import static io.repseq.core.GeneType.Variable;
import static io.repseq.core.ReferencePoint.CDR3Begin;
import static io.repseq.core.ReferencePoint.CDR3End;

class ClusterProcessor {
    //TODO to parameters
    private final double threshold = 0.9;

    private final NucleotideSequence VSequence1;
    private final Function<ReferencePoint, Integer> getVRelativePosition;

    private final NucleotideSequence JSequence1;
    private final Function<ReferencePoint, Integer> getJRelativePosition;

    private final Cluster<CloneWrapper> originalCluster;


    ClusterProcessor(Cluster<CloneWrapper> originalCluster) {
        if (originalCluster.cluster.isEmpty()) {
            throw new IllegalArgumentException();
        }
        this.originalCluster = originalCluster;
        Clone clone = originalCluster.cluster.get(0).clone;
        VDJCHit bestVHit = clone.getBestHit(Variable);
        VSequence1 = bestVHit.getAlignment(0).getSequence1();
        getVRelativePosition = it -> bestVHit.getGene().getPartitioning().getRelativePosition(bestVHit.getAlignedFeature(), it);

        VDJCHit bestJHit = clone.getBestHit(Joining);
        JSequence1 = bestJHit.getAlignment(0).getSequence1();
        getJRelativePosition = it -> bestJHit.getGene().getPartitioning().getRelativePosition(bestJHit.getAlignedFeature(), it);
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
        Range VRangeInCDR3 = minVRangeInCDR3(originalCluster);
        Range JRangeInCDR3 = minJRangeInCDR3(originalCluster);

        List<CloneWithMutationsFromVJGermline> clones = originalCluster.cluster.stream()
                .map(cloneWrapper -> {
                    NucleotideSequence CDR3 = cloneWrapper.clone.getNFeature(GeneFeature.CDR3);
                    return new CloneWithMutationsFromVJGermline(
                            new MutationsFromVJGermline(
                                    getMutationsWithoutRange(
                                            cloneWrapper.clone,
                                            Variable,
                                            new Range(VRangeInCDR3.getLower(), Math.min(VRangeInCDR3.getLower() + (CDR3.size() - VRangeInCDR3.length()), VSequence1.size()))
                                    ),
                                    getMutationsWithinRange(cloneWrapper.clone, Variable, VRangeInCDR3),
                                    VRangeInCDR3,
                                    JRangeInCDR3,
                                    CDR3.getRange(VRangeInCDR3.length(), CDR3.size() - JRangeInCDR3.length()),
                                    getMutationsWithoutRange(
                                            cloneWrapper.clone,
                                            Joining,
                                            new Range(Math.max(0, JRangeInCDR3.getUpper() - (CDR3.size() - JRangeInCDR3.length())), JRangeInCDR3.getUpper())
                                    ),
                                    getMutationsWithinRange(cloneWrapper.clone, Joining, JRangeInCDR3)
                            ),
                            cloneWrapper
                    );
                })
                .collect(Collectors.toList());

        List<Cluster.Builder<CloneWithMutationsFromVJGermline>> clusteredClones = new ArrayList<>();

        for (CloneWithMutationsFromVJGermline cloneDescriptor : clones) {
            Optional<Pair<Cluster.Builder<CloneWithMutationsFromVJGermline>, Double>> nearestCluster = clusteredClones.stream()
                    .map(cluster -> Pair.create(cluster, distanceToCluster(cloneDescriptor, cluster)))
                    .min(Comparator.comparing(Pair::getSecond));

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

        //TODO try to choose root by top clones and direction of mutations
        MutationsFromVJGermline rootBasedOn = cluster.cluster.get(0).mutations;

        //TODO may be get from root
        Range VRangeInCDR3 = mostLikableVRangeInCDR3(cluster);
        Range JRangeInCDR3 = mostLikableJRangeInCDR3(cluster);

        //TODO it may be done without reconstruction of CDR3
        Range NDNRangeInKnownNDN = new Range(
                VRangeInCDR3.length() - rootBasedOn.VRangeInCDR3.length(),
                rootBasedOn.knownNDN.size() - (JRangeInCDR3.length() - rootBasedOn.JRangeInCDR3.length())
        );
        NucleotideSequence reconstructedNDN = rootBasedOn.knownNDN.getRange(NDNRangeInKnownNDN);
        NucleotideSequence reconstructedCDR3 = NucleotideSequence.ALPHABET.createBuilder()
                .append(VSequence1.getRange(rootBasedOn.VRangeInCDR3))
                .append(rootBasedOn.knownNDN.getRange(0, NDNRangeInKnownNDN.getLower()))
                .append(reconstructedNDN)
                .append(rootBasedOn.knownNDN.getRange(NDNRangeInKnownNDN.getUpper(), rootBasedOn.knownNDN.size()))
                .append(JSequence1.getRange(rootBasedOn.JRangeInCDR3))
                .createAndDestroy();

        List<CloneWithMutationsFromReconstructedRoot> rebasedCluster = cluster.cluster.stream()
                .map(clone -> {
                    List<MutationsWithRange> VMutationsWithoutNDN = new ArrayList<>(clone.mutations.VMutationsWithoutNDN);
                    Range VRange = new Range(clone.mutations.VRangeInCDR3.getLower(), VRangeInCDR3.getUpper());
                    if (!VRange.isEmpty()) {
                        VMutationsWithoutNDN.add(new MutationsWithRange(
                                VSequence1,
                                EMPTY_NUCLEOTIDE_MUTATIONS,
                                clone.mutations.VMutationsWithinNDN.extractAbsoluteMutationsForRange(VRange),
                                VRange
                        ));
                    }
                    List<MutationsWithRange> JMutationsWithoutNDN = clone.mutations.JMutationsWithoutNDN;
                    Range JRange = new Range(JRangeInCDR3.getLower(), clone.mutations.JRangeInCDR3.getLower());
                    if (!JRange.isEmpty()) {
                        JMutationsWithoutNDN.add(new MutationsWithRange(
                                JSequence1,
                                EMPTY_NUCLEOTIDE_MUTATIONS,
                                clone.mutations.JMutationsWithinNDN.extractAbsoluteMutationsForRange(JRange),
                                JRange
                        ));
                    }
                    return new CloneWithMutationsFromReconstructedRoot(
                            new MutationsDescription(
                                    VMutationsWithoutNDN,
                                    mutations(reconstructedNDN, clone.mutations.knownNDN.getRange(NDNRangeInKnownNDN)),
                                    JMutationsWithoutNDN
                            ),
                            clone.cloneWrapper
                    );
                })
                .sorted(Comparator.comparing(cloneDescriptor -> distance(cloneDescriptor.mutations)))
                .collect(Collectors.toList());

        MutationsDescription root = new MutationsDescription(
                overlap(rebasedCluster.stream()
                        .map(it -> it.mutations.VMutationsWithoutNDN.stream()
                                .map(MutationsWithRange::getSequence1Range)
                                .collect(Collectors.toList())
                        ).collect(Collectors.toList())
                ).stream()
                        .map(it -> new MutationsWithRange(VSequence1, EMPTY_NUCLEOTIDE_MUTATIONS, EMPTY_NUCLEOTIDE_MUTATIONS, it))
                        .collect(Collectors.toList()),
                new MutationsWithRange(
                        reconstructedNDN,
                        EMPTY_NUCLEOTIDE_MUTATIONS,
                        EMPTY_NUCLEOTIDE_MUTATIONS,
                        new Range(0, reconstructedNDN.size())
                ),
                overlap(rebasedCluster.stream()
                        .map(it -> it.mutations.JMutationsWithoutNDN.stream()
                                .map(MutationsWithRange::getSequence1Range)
                                .collect(Collectors.toList())
                        ).collect(Collectors.toList())
                ).stream()
                        .map(it -> new MutationsWithRange(JSequence1, EMPTY_NUCLEOTIDE_MUTATIONS, EMPTY_NUCLEOTIDE_MUTATIONS, it))
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

    private List<MutationsWithRange> getMutationsWithoutRange(Clone clone, GeneType geneType, Range without) {
        VDJCHit bestHit = clone.getBestHit(geneType);
        return IntStream.range(0, bestHit.getAlignments().length)
                .boxed()
                .flatMap(index -> {
                    Alignment<NucleotideSequence> alignment = bestHit.getAlignment(index);
                    Mutations<NucleotideSequence> mutations = alignment.getAbsoluteMutations();
                    List<Range> rangesWithoutNDN = alignment.getSequence1Range().without(without);
                    return rangesWithoutNDN.stream()
                            .map(range -> new MutationsWithRange(
                                    alignment.getSequence1(),
                                    Mutations.EMPTY_NUCLEOTIDE_MUTATIONS,
                                    mutations.extractAbsoluteMutationsForRange(range),
                                    range
                            ));
                })
                .collect(Collectors.toList());
    }

    private Mutations<NucleotideSequence> getMutationsWithinRange(Clone clone, GeneType geneType, Range range) {
        if (range.isEmpty()) {
            return EMPTY_NUCLEOTIDE_MUTATIONS;
        }
        VDJCHit bestHit = clone.getBestHit(geneType);
        return IntStream.range(0, bestHit.getAlignments().length)
                .boxed()
                .map(index -> {
                    Alignment<NucleotideSequence> alignment = bestHit.getAlignment(index);
                    Mutations<NucleotideSequence> mutations = alignment.getAbsoluteMutations();
                    if (alignment.getSequence1Range().intersectsWith(range)) {
                        return Optional.of(mutations.extractAbsoluteMutationsForRange(range));
                    } else {
                        return Optional.<Mutations<NucleotideSequence>>empty();
                    }
                })
                .flatMap(Java9Util::stream)
                .findFirst()
                .orElseThrow(IllegalArgumentException::new);
    }

    private BigDecimal distance(MutationsDescription mutations) {
        return BigDecimal.valueOf(
                1 - (
                        (score(mutations.knownNDN) + score(mutations.VMutationsWithoutNDN) + score(mutations.JMutationsWithoutNDN)) /
                                (maxScore(mutations.knownNDN) + maxScore(mutations.VMutationsWithoutNDN) + maxScore(mutations.JMutationsWithoutNDN))
                )
        );
    }

    private MutationsDescription mutationsBetween(MutationsDescription first, MutationsDescription second) {
        return new MutationsDescription(
                mutationsBetween(first.VMutationsWithoutNDN, second.VMutationsWithoutNDN),
                mutationsBetween(first.knownNDN, second.knownNDN, first.knownNDN.getSequence1Range()),
                mutationsBetween(first.JMutationsWithoutNDN, second.JMutationsWithoutNDN)
        );
    }

    private MutationsDescription combineWith(MutationsDescription first, MutationsDescription second) {
        return new MutationsDescription(
                combineWith(first.VMutationsWithoutNDN, second.VMutationsWithoutNDN),
                combineWith(first.knownNDN, second.knownNDN, first.knownNDN.getSequence1Range()),
                combineWith(first.JMutationsWithoutNDN, second.JMutationsWithoutNDN)
        );
    }

    private MutationsDescription commonMutations(MutationsDescription first, MutationsDescription second) {
        return new MutationsDescription(
                intersection(first.VMutationsWithoutNDN, second.VMutationsWithoutNDN),
                intersection(first.knownNDN, second.knownNDN, first.knownNDN.getSequence1Range()),
                intersection(first.JMutationsWithoutNDN, second.JMutationsWithoutNDN)
        );
    }

    //TODO it is more possible to decrease length of alignment than to increase. It is important on small trees
    private Range mostLikableVRangeInCDR3(Cluster<CloneWithMutationsFromVJGermline> cluster) {
        return cluster.cluster.stream()
                .map(clone -> clone.cloneWrapper.clone)
                .map(this::VRangeInCDR3)
                .flatMap(Java9Util::stream)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
//                .min(Comparator.comparing(it -> it.getKey().size()))
                .map(Map.Entry::getKey)
                .orElseThrow(IllegalStateException::new);
    }

    //TODO it is more possible to decrease length of alignment than to increase. It is important on small trees
    private Range mostLikableJRangeInCDR3(Cluster<CloneWithMutationsFromVJGermline> cluster) {
        return cluster.cluster.stream()
                .map(clone -> clone.cloneWrapper.clone)
                .map(this::JRangeInCDR3)
                .flatMap(Java9Util::stream)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
//                .min(Comparator.comparing(it -> it.getKey().size()))
                .map(Map.Entry::getKey)
                .orElseThrow(IllegalStateException::new);
    }

    private Range minVRangeInCDR3(Cluster<CloneWrapper> cluster) {
        return cluster.cluster.stream()
                .map(clone -> clone.clone)
                .map(this::VRangeInCDR3)
                .flatMap(Java9Util::stream)
                .min(Comparator.comparing(Range::length))
                .orElseThrow(IllegalStateException::new);
    }

    private Range minJRangeInCDR3(Cluster<CloneWrapper> cluster) {
        return cluster.cluster.stream()
                .map(clone -> clone.clone)
                .map(this::JRangeInCDR3)
                .flatMap(Java9Util::stream)
                .min(Comparator.comparing(Range::length))
                .orElseThrow(IllegalStateException::new);
    }

    private Optional<Range> VRangeInCDR3(Clone clone) {
        VDJCHit hit = clone.getBestHit(Variable);

        int positionOfCDR3Begin = getRelativePosition(hit, CDR3Begin);

        for (int i = hit.getAlignments().length - 1; i >= 0; i--) {
            Alignment<NucleotideSequence> alignment = hit.getAlignment(i);
            if (alignment.getSequence1Range().contains(positionOfCDR3Begin)) {
                return Optional.of(new Range(positionOfCDR3Begin, alignment.getSequence1Range().getUpper()));
            }
        }

        return Optional.empty();
    }

    private Optional<Range> JRangeInCDR3(Clone clone) {
        VDJCHit hit = clone.getBestHit(Joining);

        int positionOfCDR3End = getRelativePosition(hit, CDR3End);

        for (int i = hit.getAlignments().length - 1; i >= 0; i--) {
            Alignment<NucleotideSequence> alignment = hit.getAlignment(i);
            if (alignment.getSequence1Range().contains(positionOfCDR3End)) {
                return Optional.of(new Range(alignment.getSequence1Range().getLower(), positionOfCDR3End));
            }
        }

        return Optional.empty();
    }

    private List<Range> overlap(List<List<Range>> ranges) {
        List<Range> sorted = ranges.stream()
                .flatMap(Collection::stream)
                .sorted(Comparator.comparing(Range::getLower))
                .collect(Collectors.toList());
        List<Range> result = new ArrayList<>();
        int currentLeft = sorted.get(0).getLower();
        int currentRight = sorted.get(0).getUpper();
        for (Range range : sorted.subList(1, sorted.size())) {
            if (range.getLower() < currentRight) {
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
        return foldByIntersection(firstMutations, secondMutations, ClusterProcessor::mutationsBetween);
    }

    private List<MutationsWithRange> combineWith(List<MutationsWithRange> firstMutations, List<MutationsWithRange> secondMutations) {
        return foldByIntersection(firstMutations, secondMutations, ClusterProcessor::combineWith);
    }

    private static List<MutationsWithRange> intersection(List<MutationsWithRange> from, List<MutationsWithRange> to) {
        return foldByIntersection(from, to, ClusterProcessor::intersection);
    }

    private static List<MutationsWithRange> foldByIntersection(
            List<MutationsWithRange> firstMutations,
            List<MutationsWithRange> secondMutations,
            TriFunction<MutationsWithRange, MutationsWithRange, Range, MutationsWithRange> folder
    ) {
        return firstMutations.stream()
                .flatMap(base -> secondMutations.stream().flatMap(comparison -> {
                    Range intersection = base.getSequence1Range().intersection(comparison.getSequence1Range());
                    if (intersection != null) {
                        return Stream.of(folder.apply(base, comparison, intersection));
                    } else {
                        return Stream.empty();
                    }
                }))
                .collect(Collectors.toList());
    }

    private static MutationsWithRange mutationsBetween(MutationsWithRange base, MutationsWithRange comparison, Range intersection) {
        return new MutationsWithRange(
                base.getSequence1(),
                base.getCombinedMutations(),
                MutationsUtils.difference(base.getCombinedMutations(), comparison.getCombinedMutations()),
                intersection
        );
    }

    private static MutationsWithRange combineWith(MutationsWithRange base, MutationsWithRange comparison, Range intersection) {
        return new MutationsWithRange(
                base.getSequence1(),
                base.getCombinedMutations(),
                base.getCombinedMutations().invert().combineWith(comparison.getCombinedMutations()),
                intersection
        );
    }

    private static MutationsWithRange intersection(MutationsWithRange base, MutationsWithRange comparison, Range intersection) {
        return new MutationsWithRange(
                base.getSequence1(),
                base.getFromBaseToParent(),
                MutationsUtils.intersection(
                        base.getFromParentToThis(),
                        comparison.getFromParentToThis()
                ),
                intersection
        );
    }

    private double distanceToCluster(CloneWithMutationsFromVJGermline cloneDescriptor, Cluster.Builder<CloneWithMutationsFromVJGermline> cluster) {
        return cluster.getCurrentCluster().stream()
                .mapToDouble(compareTo -> fitnessFunction(fitnessFunctionParams(cloneDescriptor.mutations, compareTo.mutations)))
                .min()
                .orElseThrow(IllegalArgumentException::new);
    }

    private FitnessFunctionParams fitnessFunctionParams(MutationsFromVJGermline first, MutationsFromVJGermline second) {
        MutationsWithRange CDR3MutationsBetween = mutations(first.knownNDN, second.knownNDN);

        List<MutationsWithRange> VMutationsBetween = mutationsBetween(first.VMutationsWithoutNDN, second.VMutationsWithoutNDN);
        double VMutationsBetweenScore = score(VMutationsBetween);

        List<MutationsWithRange> JMutationsBetween = mutationsBetween(first.JMutationsWithoutNDN, second.JMutationsWithoutNDN);
        double JMutationsBetweenScore = score(JMutationsBetween);

        double CDR3MutationsBetweenScore = score(CDR3MutationsBetween);

        double maxScoreForFirstVJ = maxScore(first.VMutationsWithoutNDN) + maxScore(first.JMutationsWithoutNDN);
        double maxScoreForSecondVJ = maxScore(second.VMutationsWithoutNDN) + maxScore(second.JMutationsWithoutNDN);
        double maxScoreForVJ = Math.max(maxScoreForFirstVJ, maxScoreForSecondVJ);
        double maxScoreForCDR3 = Math.max(maxScore(first.knownNDN), maxScore(second.knownNDN));


        double normalizedDistanceFromFirstToGermline = 1 - (score(first.VMutationsWithoutNDN) + score(first.JMutationsWithoutNDN)) / maxScoreForFirstVJ;
        double normalizedDistanceFromSecondToGermline = 1 - (score(second.VMutationsWithoutNDN) + score(second.JMutationsWithoutNDN)) / maxScoreForSecondVJ;
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

    private static double maxScore(List<MutationsWithRange> vMutationsBetween) {
        return vMutationsBetween.stream()
                .mapToDouble(ClusterProcessor::maxScore)
                .sum();
    }

    private static int maxScore(MutationsWithRange mutations) {
        return AlignmentUtils.calculateScore(
                mutations.getFromBaseToParent().mutate(mutations.getSequence1()),
                EMPTY_NUCLEOTIDE_MUTATIONS,
                AffineGapAlignmentScoring.getNucleotideBLASTScoring()
        );
    }

    private static double maxScore(NucleotideSequence sequence) {
        return AlignmentUtils.calculateScore(
                sequence,
                EMPTY_NUCLEOTIDE_MUTATIONS,
                AffineGapAlignmentScoring.getNucleotideBLASTScoring()
        );
    }

    private static MutationsWithRange mutations(NucleotideSequence first, NucleotideSequence second) {
        return new MutationsWithRange(
                first,
                EMPTY_NUCLEOTIDE_MUTATIONS,
                Aligner.alignGlobal(
                        AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                        first,
                        second
                ).getAbsoluteMutations(),
                new Range(0, first.size())
        );
    }

    private static class MutationsFromVJGermline {
        private final List<MutationsWithRange> VMutationsWithoutNDN;
        private final Mutations<NucleotideSequence> VMutationsWithinNDN;
        private final Range VRangeInCDR3;
        private final Range JRangeInCDR3;
        private final NucleotideSequence knownNDN;
        private final List<MutationsWithRange> JMutationsWithoutNDN;
        private final Mutations<NucleotideSequence> JMutationsWithinNDN;

        private MutationsFromVJGermline(
                List<MutationsWithRange> VMutationsWithoutNDN,
                Mutations<NucleotideSequence> VMutationsWithinNDN,
                Range VRangeInCDR3,
                Range JRangeInCDR3,
                NucleotideSequence knownNDN,
                List<MutationsWithRange> JMutationsWithoutNDN,
                Mutations<NucleotideSequence> JMutationsWithinNDN
        ) {
            this.VMutationsWithoutNDN = VMutationsWithoutNDN;
            this.VMutationsWithinNDN = VMutationsWithinNDN;
            this.VRangeInCDR3 = VRangeInCDR3;
            this.JRangeInCDR3 = JRangeInCDR3;
            this.JMutationsWithoutNDN = JMutationsWithoutNDN;
            this.knownNDN = knownNDN;
            this.JMutationsWithinNDN = JMutationsWithinNDN;
        }
    }

    private static class MutationsDescription {
        private final List<MutationsWithRange> VMutationsWithoutNDN;
        private final MutationsWithRange knownNDN;
        private final List<MutationsWithRange> JMutationsWithoutNDN;

        public MutationsDescription(
                List<MutationsWithRange> VMutationsWithoutNDN,
                MutationsWithRange knownNDN,
                List<MutationsWithRange> JMutationsWithoutNDN
        ) {
            this.knownNDN = knownNDN;
            this.VMutationsWithoutNDN = VMutationsWithoutNDN;
            this.JMutationsWithoutNDN = JMutationsWithoutNDN;
        }
    }

    private AncestorInfo buildAncestorInfo(MutationsDescription ancestor) {
        SequenceBuilder<NucleotideSequence> builder = NucleotideSequence.ALPHABET.createBuilder();
        ancestor.VMutationsWithoutNDN.stream()
                .map(ClusterProcessor::buildSequence)
                .forEach(builder::append);
        int CDR3Begin = getPositionInSequence2(ancestor.VMutationsWithoutNDN, getVRelativePosition.apply(ReferencePoint.CDR3Begin))
                .orElseThrow(IllegalArgumentException::new);
        builder.append(buildSequence(ancestor.knownNDN));
        int CDR3End = getPositionInSequence2(ancestor.JMutationsWithoutNDN, getJRelativePosition.apply(ReferencePoint.CDR3End))
                .orElseThrow(IllegalArgumentException::new) + builder.size();
        ancestor.JMutationsWithoutNDN.stream()
                .map(ClusterProcessor::buildSequence)
                .forEach(builder::append);
        return new AncestorInfo(
                builder.createAndDestroy(),
                CDR3Begin,
                CDR3End
        );
    }

    private Optional<Integer> getPositionInSequence2(List<MutationsWithRange> mutations, int positionInSequence1) {
        int rangesBefore = mutations.stream()
                .map(MutationsWithRange::getSequence1Range)
                .filter(it -> it.getUpper() <= positionInSequence1)
                .mapToInt(Range::length)
                .sum();

        return mutations.stream()
                .filter(it -> it.getSequence1Range().contains(positionInSequence1))
                .map(mutation -> {
                    int position = mutation.getCombinedMutations().convertToSeq2Position(positionInSequence1);
                    if (position < -1) {
                        position = -(position + 1);
                    }
                    return position - mutation.getSequence1Range().getLower();
                })
                .findFirst()
                .map(it -> it + rangesBefore);
    }

    private static NucleotideSequence buildSequence(MutationsWithRange mutation) {
        return mutation.getCombinedMutations().mutate(mutation.getSequence1()).getRange(mutation.getSequence1Range());
    }

    private static class TreeWithMeta {
        private final Tree<ObservedOrReconstructed<CloneWithMutationsFromReconstructedRoot, MutationsDescription>> tree;
        private final NucleotideSequence CDR3OfRoot;

        public TreeWithMeta(Tree<ObservedOrReconstructed<CloneWithMutationsFromReconstructedRoot, MutationsDescription>> tree, NucleotideSequence CDR3OfRoot) {
            this.tree = tree;
            this.CDR3OfRoot = CDR3OfRoot;
        }
    }

    private static class RootInfo {

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
