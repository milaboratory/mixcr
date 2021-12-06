package com.milaboratory.mixcr.trees;

import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.AffineGapAlignmentScoring;
import com.milaboratory.core.alignment.Aligner;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.mutations.Mutation;
import com.milaboratory.core.mutations.MutationType;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.mutations.MutationsBuilder;
import com.milaboratory.core.sequence.NucleotideAlphabet;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequenceBuilder;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.trees.TreeBuilderByAncestors.ObservedOrReconstructed;
import io.repseq.core.GeneType;
import org.apache.commons.math3.util.Pair;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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
//            double threshold = 0.2;
            double threshold = 100;
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
                        node.map(it -> it.cloneWrapper, ancestor -> buildAncestorInfo(ancestor, treeWithMeta.CDR3OfRoot))
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
//                .append(vPartInCDR3)
//                .append(cluster.cluster.get(0).mutations.CDR3.getRange(vPartInCDR3.size(), cluster.cluster.get(0).mutations.CDR3.size() - jPartInCDR3.size()))
//                .append(jPartInCDR3)
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
                                mutations(reconstructedCDR3, clone.mutations.CDR3),
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
                score(mutations.CDR3Mutations) + score(mutations.VMutationsWithoutCDR3) + score(mutations.JMutationsWithoutCDR3)
        );
    }

    private int score(Mutations<NucleotideSequence> mutations) {
        return (int) Arrays.stream(mutations.getRAWMutations())
                .filter(mutation -> !(Mutation.getType(mutation) == MutationType.Insertion && Mutation.getTo(mutation) == NucleotideAlphabet.N))
                .count();
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

    static Mutations<NucleotideSequence> difference(Mutations<NucleotideSequence> from, Mutations<NucleotideSequence> to) {
        Mutations<NucleotideSequence> fromBasedOnN = rebuildFromNBase(from).getFirst();
        Mutations<NucleotideSequence> toBasedOnN = rebuildFromNBase(to).getFirst();
        try {
            return fromBasedOnN.invert().combineWith(toBasedOnN);
        } catch (Exception e) {
            throw e;
        }
    }

    static Mutations<NucleotideSequence> intersection(Mutations<NucleotideSequence> first, Mutations<NucleotideSequence> second) {
        Pair<Mutations<NucleotideSequence>, Map<Integer, Integer>> firstBasedOnN = rebuildFromNBase(first);
        Pair<Mutations<NucleotideSequence>, Map<Integer, Integer>> secondBasedOnN = rebuildFromNBase(second);
        Mutations<NucleotideSequence> intersection = intersectionForSubstitutes(firstBasedOnN.getFirst(), secondBasedOnN.getFirst());
        return rebuildFromOriginal(intersection, firstBasedOnN.getSecond());
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

    private static Mutations<NucleotideSequence> rebuildFromOriginal(Mutations<NucleotideSequence> forRebuild, Map<Integer, Integer> original) {
        MutationsBuilder<NucleotideSequence> builder = new MutationsBuilder<>(NucleotideSequence.ALPHABET);
        int positionShift = 0;
        for (int i = 0; i < forRebuild.size(); i++) {
            int mutation = forRebuild.getMutation(i);
            int position = Mutation.getPosition(mutation) + positionShift;
            int mappedMutation;
            if (Mutation.getType(mutation) == MutationType.Insertion && Mutation.getTo(mutation) == NucleotideAlphabet.N) {
                mappedMutation = Mutation.createMutation(
                        MutationType.Insertion,
                        position,
                        NucleotideAlphabet.N,
                        NucleotideAlphabet.N
                );
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

    private static Pair<Mutations<NucleotideSequence>, Map<Integer, Integer>> rebuildFromNBase(Mutations<NucleotideSequence> mutations) {
        Map<Integer, Integer> reversedMapping = new HashMap<>();
        MutationsBuilder<NucleotideSequence> builder = new MutationsBuilder<>(NucleotideSequence.ALPHABET);
        int positionShift = 0;
        for (int i = 0; i < mutations.size(); i++) {
            int mutation = mutations.getMutation(i);
            int position = Mutation.getPosition(mutation) + positionShift;
            byte from;
            if (Mutation.getType(mutation) == MutationType.Insertion) {
                from = NucleotideAlphabet.N;
            } else {
                from = Mutation.getFrom(mutation);
            }
            if (Mutation.getType(mutation) == MutationType.Insertion) {
                positionShift++;
            }
            int mapped = Mutation.createMutation(MutationType.Substitution, position, from, Mutation.getTo(mutation));
            reversedMapping.put(mapped, mutation);
            builder.append(mapped);
        }
        return Pair.create(builder.createAndDestroy(), reversedMapping);
    }

    private double distanceToCluster(CloneWithMutationsFromVJGermline cloneDescriptor, Cluster.Builder<CloneWithMutationsFromVJGermline> cluster) {
        return cluster.getCurrentCluster().stream()
                .mapToDouble(compareTo -> computeDistance(cloneDescriptor.mutations, compareTo.mutations))
                .min()
                .orElseThrow(IllegalArgumentException::new);
    }

    private FitnessFunctionParams fitnessFunctionParams(MutationsFromVJGermline first, MutationsFromVJGermline second) {
        Mutations<NucleotideSequence> VMutations = difference(first.VMutationsWithoutCDR3, second.VMutationsWithoutCDR3);
        Mutations<NucleotideSequence> JMutations = difference(first.JMutationsWithoutCDR3, second.JMutationsWithoutCDR3);

        //TODO use more optimized variant
        //TODO compare only part between V and J
        Mutations<NucleotideSequence> CDR3Mutations = mutations(first.CDR3, second.CDR3);


        int vMutationsBetween = VMutations.size();
        int jMutationsBetween = JMutations.size();
        int cdr3MutationsBetween = CDR3Mutations.size();
        int VPlusJLengthOfFirst = totalLength(first.VRangesWithoutCDR3) + totalLength(first.JRangesWithoutCDR3);
        int VPlusJLengthOfSecond = totalLength(second.VRangesWithoutCDR3) + totalLength(second.JRangesWithoutCDR3);
        int averageVPlusJLength = (VPlusJLengthOfFirst + VPlusJLengthOfSecond) / 2;
        int averageCDR3Length = first.CDR3.size() + second.CDR3.size();
        double normalizedDistanceFromFirstToGermline = (first.VMutationsWithoutCDR3.size() + first.JMutationsWithoutCDR3.size()) / (double) VPlusJLengthOfFirst;
        double normalizedDistanceFromSecondToGermline = (second.VMutationsWithoutCDR3.size() + second.JMutationsWithoutCDR3.size()) / (double) VPlusJLengthOfSecond;
        double normalizedAverageDistanceToGermline = (normalizedDistanceFromFirstToGermline + normalizedDistanceFromSecondToGermline) / 2.0;
        double normalizedCommonAncestorDistanceFromGermline = (intersection(first.VMutationsWithoutCDR3, second.VMutationsWithoutCDR3).size()
                + intersection(first.JMutationsWithoutCDR3, second.JMutationsWithoutCDR3).size()) / (double) averageVPlusJLength;
        double normalizedDistanceBetweenClones = (vMutationsBetween + jMutationsBetween + cdr3MutationsBetween) / (double) (averageVPlusJLength + averageCDR3Length);
        double normalizedDistanceBetweenClonesInCDR3 = (cdr3MutationsBetween) / (double) averageCDR3Length;
        double normalizedDistanceBetweenClonesWithoutCDR3 = (vMutationsBetween + jMutationsBetween) / (double) (averageVPlusJLength);

        double a = (first.VMutationsWithoutCDR3.size() + first.JMutationsWithoutCDR3.size());
        double b = (second.VMutationsWithoutCDR3.size() + second.JMutationsWithoutCDR3.size());
        double c = (vMutationsBetween + jMutationsBetween);
        double p = 0.5 * (a + b + c);
        double areaOfTriangle = Math.sqrt(p * (p - a) * (p - b) * (p - c));
        double baseAngleOfTriangle;
        if (c == 0.0) {
            baseAngleOfTriangle = 0.0;
        } else if (c == a + b) {
            baseAngleOfTriangle = Math.PI / 2;
        } else {
            baseAngleOfTriangle = Math.acos((a * a + b * b - c * c) / (2.0 * a * b));
        }
        double baseHeightOfTriangle = areaOfTriangle * 2.0 / c;
        double radiusOfCircumscribedCircle;
        if (areaOfTriangle == 0) {
            radiusOfCircumscribedCircle = Math.max(a, b);
        } else {
            radiusOfCircumscribedCircle = 4 * a * b * c / areaOfTriangle;
        }


        return new FitnessFunctionParams(normalizedDistanceBetweenClonesInCDR3,
                normalizedDistanceBetweenClones,
                normalizedDistanceBetweenClonesWithoutCDR3,
                Math.min(normalizedDistanceFromFirstToGermline, normalizedDistanceFromSecondToGermline),
                normalizedAverageDistanceToGermline,
                normalizedCommonAncestorDistanceFromGermline,
                areaOfTriangle,
                baseAngleOfTriangle,
                baseHeightOfTriangle,
                radiusOfCircumscribedCircle
        );
    }

    static Mutations<NucleotideSequence> mutations(NucleotideSequence first, NucleotideSequence second) {
        return Aligner.alignGlobal(
                AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                first,
                second
        ).getAbsoluteMutations();
    }

    private double computeDistance(MutationsFromVJGermline first, MutationsFromVJGermline second) {
        Mutations<NucleotideSequence> VMutations = difference(first.VMutationsWithoutCDR3, second.VMutationsWithoutCDR3);
        Mutations<NucleotideSequence> JMutations = difference(first.JMutationsWithoutCDR3, second.JMutationsWithoutCDR3);

        //TODO use more optimized variant
        //TODO compare only part between V and J
        Mutations<NucleotideSequence> CDR3Mutations = mutations(first.CDR3, second.CDR3);

        double CDR3Length = (first.CDR3.size() + second.CDR3.size()) / 2.0;
        double VLength = (totalLength(first.VRangesWithoutCDR3) + totalLength(second.VRangesWithoutCDR3)) / 2.0;
        double JLength = (totalLength(first.JRangesWithoutCDR3) + totalLength(second.JRangesWithoutCDR3)) / 2.0;

        //TODO don't use average length
        double normalizedDistanceFromCloneToGermline = (first.VMutationsWithoutCDR3.size() + first.JMutationsWithoutCDR3.size()) / (VLength + JLength);
        //TODO don't use average length
        double normalizedDistanceFromCompareToGermline = (second.VMutationsWithoutCDR3.size() + second.JMutationsWithoutCDR3.size()) / (VLength + JLength);

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

    private AncestorInfo buildAncestorInfo(MutationsDescription ancestor, NucleotideSequence CDR3OfRoot) {
        SequenceBuilder<NucleotideSequence> builder = NucleotideSequence.ALPHABET.createBuilder();
        NucleotideSequence vSequence = ancestor.VMutationsWithoutCDR3.mutate(VSequence1);
        ancestor.VRangesWithoutCDR3.stream().map(vSequence::getRange).forEach(builder::append);
        int CDR3Begin = builder.size();
        builder.append(ancestor.CDR3Mutations.mutate(CDR3OfRoot));
        int CDR3End = builder.size();
        NucleotideSequence jSequence = ancestor.JMutationsWithoutCDR3.mutate(JSequence1);
        ancestor.JRangesWithoutCDR3.stream().map(jSequence::getRange).forEach(builder::append);
        return new AncestorInfo(
                builder.createAndDestroy(),
                CDR3Begin,
                CDR3End
        );
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
