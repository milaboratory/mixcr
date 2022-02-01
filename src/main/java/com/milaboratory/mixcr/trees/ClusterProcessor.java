package com.milaboratory.mixcr.trees;

import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.Aligner;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.alignment.AlignmentScoring;
import com.milaboratory.core.alignment.AlignmentUtils;
import com.milaboratory.core.mutations.Mutation;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.mutations.MutationsBuilder;
import com.milaboratory.core.sequence.NucleotideAlphabet;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequenceBuilder;
import com.milaboratory.core.sequence.Wildcard;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.trees.TreeBuilderByAncestors.ObservedOrReconstructed;
import com.milaboratory.mixcr.util.AdjacencyMatrix;
import com.milaboratory.mixcr.util.BitArrayInt;
import com.milaboratory.mixcr.util.Cluster;
import com.milaboratory.mixcr.util.Java9Util;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.ReferencePoint;
import org.apache.commons.math3.util.Pair;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.milaboratory.core.mutations.Mutations.EMPTY_NUCLEOTIDE_MUTATIONS;
import static com.milaboratory.mixcr.trees.ClonesRebase.NDNRangeInKnownNDN;
import static com.milaboratory.mixcr.trees.MutationsUtils.*;
import static io.repseq.core.GeneType.Joining;
import static io.repseq.core.GeneType.Variable;
import static io.repseq.core.ReferencePoint.*;

class ClusterProcessor {
    private final double thresholdForFreeClones;
    private final double thresholdForCombineByNDN;
    private final int topToVoteOnNDNSize;
    private final List<String> stepsOrder;
    private final double NDNScoreMultiplier;
    private final double penaltyForReversedMutations;
    private final int commonMutationsCountForClustering;
    private final int hideTreesLessThanSize;
    private final AlignmentScoring<NucleotideSequence> VScoring;
    private final AlignmentScoring<NucleotideSequence> JScoring;

    private final NucleotideSequence VSequence1;
    private final Function<ReferencePoint, Integer> getVRelativePosition;

    private final NucleotideSequence JSequence1;
    private final Function<ReferencePoint, Integer> getJRelativePosition;

    private final Cluster<CloneWrapper> originalCluster;
    private final ClonesRebase clonesRebase;
    private final AncestorInfoBuilder ancestorInfoBuilder;
    private final AlignmentScoring<NucleotideSequence> NDNScoring;
    private final int countOfNodesToProbe;

    private final XmlTreePrinter<ObservedOrReconstructed<CloneWithMutationsFromReconstructedRoot, SyntheticNode>> printer = new XmlTreePrinter<>(
            nodeWithParent -> nodeWithParent.getNode().getContent().convert(
                    clone -> String.valueOf(clone.getClone().clone.getId()),
                    mutations -> {
                        Optional<MutationsDescription> parentMutations = Optional.ofNullable(nodeWithParent.getParent())
                                .flatMap(parent -> parent.getContent().convert(it -> Optional.empty(), it -> Optional.of(it.getFromRootToThis())));

                        Optional<MutationsDescription> fromParentToThis = parentMutations.map(parent -> mutationsBetween(parent, mutations.getFromRootToThis()));

                        int widening = 0;
                        if (fromParentToThis.isPresent()) {
                            for (int i = 0; i < fromParentToThis.get().getKnownNDN().getMutations().size(); i++) {
                                int mutation = fromParentToThis.get().getKnownNDN().getMutations().getMutation(i);
                                if (Mutation.isSubstitution(mutation)) {
                                    Wildcard from = NucleotideAlphabet.complementWildcard(Mutation.getFrom(mutation));
                                    Wildcard to = NucleotideAlphabet.complementWildcard(Mutation.getTo(mutation));
                                    if (to.intersectsWith(from) && to.basicSize() > from.basicSize()) {
                                        widening++;
                                    }
                                }
                            }
                        }


                        return "V: " + fromParentToThis.map(it -> it.getVMutationsWithoutCDR3().stream().mapToInt(MutationsWithRange::mutationsCount).sum() + it.getVMutationsInCDR3WithoutNDN().mutationsCount()).orElse(0) +
                                " J: " + fromParentToThis.map(it -> it.getJMutationsWithoutCDR3().stream().mapToInt(MutationsWithRange::mutationsCount).sum() + it.getJMutationsInCDR3WithoutNDN().mutationsCount()).orElse(0) +
                                " widening: " + widening +
                                " reversedV: " + fromParentToThis.flatMap(it -> parentMutations.map(parent -> reversedMutations(parent.getConcatenatedVMutations(), it.getConcatenatedVMutations()))).orElse(Collections.emptyList()) +
                                " reversedJ: " + fromParentToThis.flatMap(it -> parentMutations.map(parent -> reversedMutations(parent.getConcatenatedJMutations(), it.getConcatenatedJMutations()))).orElse(Collections.emptyList()) +
                                " NDN: " + mutations.getFromRootToThis().getKnownNDN().buildSequence() + " " + fromParentToThis.map(it -> it.getKnownNDN().getMutations()).orElse(EMPTY_NUCLEOTIDE_MUTATIONS) +
                                " V: " + fromParentToThis.map(MutationsDescription::getConcatenatedVMutations).orElse(EMPTY_NUCLEOTIDE_MUTATIONS) +
                                " J: " + fromParentToThis.map(MutationsDescription::getConcatenatedJMutations).orElse(EMPTY_NUCLEOTIDE_MUTATIONS)
                                ;
                    }
            )
    );


    ClusterProcessor(
            SHMTreeBuilderParameters parameters,
            AlignmentScoring<NucleotideSequence> VScoring,
            AlignmentScoring<NucleotideSequence> JScoring,
            Cluster<CloneWrapper> originalCluster
    ) {
        this.thresholdForFreeClones = parameters.thresholdForFreeClones;
        this.thresholdForCombineByNDN = parameters.thresholdForCombineByNDN;
        this.topToVoteOnNDNSize = parameters.topToVoteOnNDNSize;
        this.NDNScoreMultiplier = parameters.NDNScoreMultiplier;
        this.hideTreesLessThanSize = parameters.hideTreesLessThanSize;
        this.commonMutationsCountForClustering = parameters.commonMutationsCountForClustering;
        this.penaltyForReversedMutations = parameters.penaltyForReversedMutations;
        this.countOfNodesToProbe = parameters.countOfNodesToProbe;
        this.stepsOrder = parameters.stepsOrder;
        this.VScoring = VScoring;
        this.JScoring = JScoring;
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

        NDNScoring = MutationsUtils.NDNScoring();
        clonesRebase = new ClonesRebase(VSequence1, VScoring, NDNScoring, JSequence1, JScoring);
        ancestorInfoBuilder = new AncestorInfoBuilder();
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
    List<TreeWithMeta> buildTrees() {
        Range VRangeInCDR3 = minRangeInCDR3(originalCluster, this::VRangeInCDR3);
        Range JRangeInCDR3 = minRangeInCDR3(originalCluster, this::JRangeInCDR3);

        List<CloneWithMutationsFromVJGermline> clones = originalCluster.cluster.stream()
                .map(cloneWrapper -> {
                    NucleotideSequence CDR3 = cloneWrapper.clone.getNFeature(GeneFeature.CDR3);
                    return new CloneWithMutationsFromVJGermline(
                            new MutationsFromVJGermline(
                                    getMutationsWithoutRange(
                                            cloneWrapper.clone,
                                            Variable,
                                            new Range(VRangeInCDR3.getLower(), VSequence1.size())
                                    ),
                                    new MutationsWithRange(
                                            VSequence1,
                                            getMutationsForRange(cloneWrapper.clone, VRangeInCDR3, Variable),
                                            new RangeInfo(VRangeInCDR3, false)
                                    ),
                                    getVMutationsWithinNDN(cloneWrapper.clone, VRangeInCDR3.getUpper()),
                                    CDR3.getRange(VRangeInCDR3.length(), CDR3.size() - JRangeInCDR3.length()),
                                    getJMutationsWithinNDN(cloneWrapper.clone, JRangeInCDR3.getLower()),
                                    new MutationsWithRange(
                                            JSequence1,
                                            getMutationsForRange(cloneWrapper.clone, JRangeInCDR3, Joining),
                                            new RangeInfo(JRangeInCDR3, false)
                                    ),
                                    getMutationsWithoutRange(
                                            cloneWrapper.clone,
                                            Joining,
                                            new Range(0, JRangeInCDR3.getLower())
                                    )
                            ),
                            cloneWrapper
                    );
                })
                .collect(Collectors.toList());

        ClusteringResult clusteringResult = clusterByCommonMutations(clones, false);
        List<CloneWithMutationsFromVJGermline> clonesNotInClusters = clusteringResult.clonesNotInClusters;

        List<TreeWithMetaBuilder> resultTrees = clusteringResult.clusters
                .stream()
                .filter(it -> it.cluster.size() > 1)
                .map(this::buildATree)
                .sorted(Comparator.<TreeWithMetaBuilder>comparingInt(it -> it.treeBuilder.getObservedNodesCount()).reversed())
                .collect(Collectors.toList());
        printTress(resultTrees);

        Map<String, BiFunction<List<TreeWithMetaBuilder>, List<CloneWithMutationsFromVJGermline>, Pair<List<TreeWithMetaBuilder>, List<CloneWithMutationsFromVJGermline>>>> stepsByNames = new HashMap<>();
        stepsByNames.put("attachClonesByNDN", (trees, freeClones) -> attachClonesByNDNIfPossible(trees, false, freeClones));
        stepsByNames.put("combineTrees", (trees, freeClones) -> Pair.create(combineTreesIfPossible(trees, false), freeClones));
        stepsByNames.put("attachClonesByDistanceChange", (trees, freeClones) -> attachClonesByDistanceChangeIfPossible(trees, false, freeClones));

        List<BiFunction<List<TreeWithMetaBuilder>, List<CloneWithMutationsFromVJGermline>, Pair<List<TreeWithMetaBuilder>, List<CloneWithMutationsFromVJGermline>>>> steps = stepsOrder.stream()
                .map(stepsByNames::get)
                .collect(Collectors.toList());

        for (BiFunction<List<TreeWithMetaBuilder>, List<CloneWithMutationsFromVJGermline>, Pair<List<TreeWithMetaBuilder>, List<CloneWithMutationsFromVJGermline>>> step : steps) {
            Pair<List<TreeWithMetaBuilder>, List<CloneWithMutationsFromVJGermline>> stepResult = step.apply(resultTrees, clonesNotInClusters);
            resultTrees = stepResult.getFirst();
            clonesNotInClusters = stepResult.getSecond();
            printTress(resultTrees);
        }

        return resultTrees.stream()
                .filter(treeWithMetaBuilder -> treeWithMetaBuilder.treeBuilder.getObservedNodesCount() >= hideTreesLessThanSize)
                .map(treeWithMetaBuilder -> new TreeWithMeta(
                        treeWithMetaBuilder.treeBuilder.getTree().map(node ->
                                node.map(
                                        CloneWithMutationsFromReconstructedRoot::getClone,
                                        ancestor -> ancestorInfoBuilder.buildAncestorInfo(ancestor.getFromRootToThis())
                                )
                        ),
                        treeWithMetaBuilder.rootInfo
                ))
                .collect(Collectors.toList());
    }

    private List<TreeWithMetaBuilder> combineTreesIfPossible(List<TreeWithMetaBuilder> originalTrees, boolean print) {
        List<TreeWithMetaBuilder> result = new ArrayList<>();

        List<TreeWithMetaBuilder> originalTreesCopy = new ArrayList<>(originalTrees);
        //trying to grow the biggest trees first
        while (!originalTreesCopy.isEmpty()) {
            TreeWithMetaBuilder treeToGrow = originalTreesCopy.get(0);
            originalTreesCopy.remove(0);

            //trying to add the smallest trees first
            for (int i = originalTreesCopy.size() - 1; i >= 0; i--) {
                TreeWithMetaBuilder treeToAttach = originalTreesCopy.get(i);

                SyntheticNode oldestAncestorOfTreeToAttach = oldestReconstructedAncestor(treeToAttach);
                SyntheticNode oldestAncestorOfTreeToGrow = oldestReconstructedAncestor(treeToGrow);

                Range NDNRangeInCDR3OfTreeToAttach = new Range(
                        treeToAttach.rootInfo.getVRangeInCDR3().length(),
                        treeToAttach.rootInfo.getVRangeInCDR3().length() + treeToAttach.rootInfo.getReconstructedNDN().size()
                );
                Range NDNRangeInCDR3OfTreeToGrow = new Range(
                        treeToGrow.rootInfo.getVRangeInCDR3().length(),
                        treeToGrow.rootInfo.getVRangeInCDR3().length() + treeToGrow.rootInfo.getReconstructedNDN().size()
                );
                Range intersection = NDNRangeInCDR3OfTreeToAttach.intersection(NDNRangeInCDR3OfTreeToGrow);
                Range wideIntersection = new Range(
                        Math.min(NDNRangeInCDR3OfTreeToAttach.getLower(), NDNRangeInCDR3OfTreeToGrow.getLower()),
                        Math.max(NDNRangeInCDR3OfTreeToAttach.getUpper(), NDNRangeInCDR3OfTreeToGrow.getUpper())
                );
                if (intersection == null) {
                    continue;
                }

                NucleotideSequence NDNOfTreeToGrow = oldestAncestorOfTreeToGrow.getFromRootToThis().getKnownNDN().buildSequence();
                NucleotideSequence NDNOfTreeToAttach = oldestAncestorOfTreeToAttach.getFromRootToThis().getKnownNDN().buildSequence();

                NucleotideSequence intersectionForTreeToGrow = NDNOfTreeToGrow.getRange(intersection.move(-treeToGrow.rootInfo.getVRangeInCDR3().length()));
                NucleotideSequence intersectionForTreeToAttach = NDNOfTreeToAttach.getRange(intersection.move(-treeToAttach.rootInfo.getVRangeInCDR3().length()));

                float score_1 = Aligner.alignGlobal(
                        NDNScoring,
                        NDNOfTreeToAttach,
                        NDNOfTreeToGrow,
                        intersection.getLower() - treeToAttach.rootInfo.getVRangeInCDR3().length(),
                        intersection.length(),
                        intersection.getLower() - treeToGrow.rootInfo.getVRangeInCDR3().length(),
                        intersection.length()
                ).getScore();
                float score_2 = Aligner.alignGlobal(
                        NDNScoring,
                        NDNOfTreeToGrow,
                        NDNOfTreeToAttach,
                        intersection.getLower() - treeToGrow.rootInfo.getVRangeInCDR3().length(),
                        intersection.length(),
                        intersection.getLower() - treeToAttach.rootInfo.getVRangeInCDR3().length(),
                        intersection.length()
                ).getScore();
                double metric_1 = ((double) (NDNScoring.getMaximalMatchScore() * intersection.length()) - score_1) / intersection.length();
                double metric_2 = ((double) (NDNScoring.getMaximalMatchScore() * wideIntersection.length()) - score_2) / wideIntersection.length();

                if (print) {
                    NucleotideSequence wideIntersectionForTreeToGrow =
                            VSequence1.getRange(treeToGrow.rootInfo.getVRangeInCDR3()).getRange(wideIntersection.getLower(), treeToGrow.rootInfo.getVRangeInCDR3().length())
                                    .concatenate(NDNOfTreeToGrow)
                                    .concatenate(JSequence1.getRange(treeToGrow.rootInfo.getJRangeInCDR3()).getRange(0, wideIntersection.getUpper() - treeToGrow.rootInfo.getVRangeInCDR3().length() - NDNOfTreeToGrow.size()));
                    NucleotideSequence wideIntersectionForTreeToAttach =
                            VSequence1.getRange(treeToAttach.rootInfo.getVRangeInCDR3()).getRange(wideIntersection.getLower(), treeToAttach.rootInfo.getVRangeInCDR3().length())
                                    .concatenate(NDNOfTreeToAttach)
                                    .concatenate(JSequence1.getRange(treeToAttach.rootInfo.getJRangeInCDR3()).getRange(0, wideIntersection.getUpper() - treeToAttach.rootInfo.getVRangeInCDR3().length() - NDNOfTreeToAttach.size()));

                    System.out.printf(
                            "metric_1: %.3f,                   treeToGrow: %s %s %s %s %s  %s success: %s%n",
                            metric_1,
                            VSequence1.getRange(treeToGrow.rootInfo.getVRangeInCDR3()),
                            NDNOfTreeToGrow.getRange(0, intersection.getLower() - treeToGrow.rootInfo.getVRangeInCDR3().length()),
                            intersectionForTreeToGrow,
                            NDNOfTreeToGrow.getRange(intersection.getUpper() - treeToGrow.rootInfo.getVRangeInCDR3().length(), NDNOfTreeToGrow.size()),
                            JSequence1.getRange(treeToGrow.rootInfo.getJRangeInCDR3()),
                            wideIntersectionForTreeToGrow,
                            metric_1 <= thresholdForCombineByNDN
                    );
                    System.out.printf(
                            "metric_2: %.3f,                 treeToAttach: %s %s %s %s %s  %s success: %s%n",
                            metric_2,
                            VSequence1.getRange(treeToAttach.rootInfo.getVRangeInCDR3()),
                            NDNOfTreeToAttach.getRange(0, intersection.getLower() - treeToAttach.rootInfo.getVRangeInCDR3().length()),
                            intersectionForTreeToAttach,
                            NDNOfTreeToAttach.getRange(intersection.getUpper() - treeToAttach.rootInfo.getVRangeInCDR3().length(), NDNOfTreeToAttach.size()),
                            JSequence1.getRange(treeToAttach.rootInfo.getJRangeInCDR3()),
                            wideIntersectionForTreeToAttach,
                            metric_2 <= thresholdForCombineByNDN
                    );
                }

                if (metric_1 < thresholdForCombineByNDN || metric_2 < thresholdForCombineByNDN) {
                    treeToGrow = buildATree(new Cluster<>(
                            Stream.of(treeToGrow, treeToAttach)
                                    .flatMap(it -> it.treeBuilder.getTree().allNodes())
                                    .map(Tree.NodeWithParent::getNode)
                                    .map(node -> node.getContent().convert(it -> Optional.of(new CloneWithMutationsFromVJGermline(it.getMutationsFromVJGermline(), it.getClone())), it -> Optional.<CloneWithMutationsFromVJGermline>empty()))
                                    .flatMap(Java9Util::stream)
                                    .collect(Collectors.toList())
                    ));
                    originalTreesCopy.remove(i);
                }
            }

            result.add(treeToGrow);
        }
        return result;
    }

    private Pair<List<TreeWithMetaBuilder>, List<CloneWithMutationsFromVJGermline>> attachClonesByNDNIfPossible(List<TreeWithMetaBuilder> originalTrees, boolean print, List<CloneWithMutationsFromVJGermline> clonesNotInClusters) {
        List<TreeWithMetaBuilder> result = originalTrees.stream().map(TreeWithMetaBuilder::copy).collect(Collectors.toList());
        List<CloneWithMutationsFromVJGermline> clonesNotInClustersLeft = new ArrayList<>();

        clonesNotInClusters.stream()
                .filter(clone -> clone.mutations.getVJMutationsCount() <= commonMutationsCountForClustering)
                .forEach(clone -> {
                    Optional<Pair<Double, Runnable>> bestTreeToAttach = originalTrees.stream()
                            .map(tree -> {
                                SyntheticNode oldestAncestorOfTreeToGrow = oldestReconstructedAncestor(tree);
                                CloneWithMutationsFromReconstructedRoot rebasedClone = clonesRebase.rebaseClone(tree.rootInfo, clone.mutations, clone.cloneWrapper);
                                SyntheticNode oldestAncestorOfTreeToAttach = SyntheticNode.createFromMutations(rebasedClone.getMutationsFromRoot());
                                NucleotideSequence NDNOfTreeToGrow = oldestAncestorOfTreeToGrow.getFromRootToThis().getKnownNDN().buildSequence();
                                NucleotideSequence NDNOfTreeToAttach = oldestAncestorOfTreeToAttach.getFromRootToThis().getKnownNDN().buildSequence();
                                float score_1 = Aligner.alignGlobal(
                                        NDNScoring,
                                        NDNOfTreeToAttach,
                                        NDNOfTreeToGrow
                                ).getScore();
                                float score_2 = Aligner.alignGlobal(
                                        NDNScoring,
                                        NDNOfTreeToGrow,
                                        NDNOfTreeToAttach
                                ).getScore();
                                double metric_1 = ((double) (NDNScoring.getMaximalMatchScore() * tree.rootInfo.getReconstructedNDN().size()) - score_1) / tree.rootInfo.getReconstructedNDN().size();
                                double metric_2 = ((double) (NDNScoring.getMaximalMatchScore() * tree.rootInfo.getReconstructedNDN().size()) - score_2) / tree.rootInfo.getReconstructedNDN().size();
                                if (print) {
                                    System.out.printf(
                                            "metric_1: %.3f,    treeToGrow: %s cloneId: %d, success: %s%n",
                                            metric_1,
                                            NDNOfTreeToGrow,
                                            clone.cloneWrapper.clone.getId(),
                                            metric_1 <= thresholdForCombineByNDN || metric_2 <= thresholdForCombineByNDN
                                    );
                                    System.out.printf(
                                            "metric_2: %.3f, cloneToAttach: %s%n",
                                            metric_2,
                                            NDNOfTreeToAttach
                                    );
                                }
                                return Pair.<Double, Runnable>create(Math.min(metric_1, metric_2), () -> tree.treeBuilder.addNode(rebasedClone));
                            })
                            .min(Comparator.comparing(Pair::getFirst));
                    if (bestTreeToAttach.isPresent()) {
                        double metric = bestTreeToAttach.get().getFirst();
                        if (metric <= thresholdForCombineByNDN) {
                            bestTreeToAttach.get().getSecond().run();
                        } else {
                            clonesNotInClustersLeft.add(clone);
                        }
                    } else {
                        clonesNotInClustersLeft.add(clone);
                    }
                });
        return Pair.create(result, clonesNotInClustersLeft);
    }

    private Pair<List<TreeWithMetaBuilder>, List<CloneWithMutationsFromVJGermline>> attachClonesByDistanceChangeIfPossible(List<TreeWithMetaBuilder> originalTrees, boolean print, List<CloneWithMutationsFromVJGermline> clonesNotInClusters) {
        List<TreeWithMetaBuilder> result = originalTrees.stream().map(TreeWithMetaBuilder::copy).collect(Collectors.toList());
        List<CloneWithMutationsFromVJGermline> clonesNotInClustersLeft = new ArrayList<>();

        //try to add as nodes clones that wasn't picked up by clustering
        for (int i = clonesNotInClusters.size() - 1; i >= 0; i--) {
            CloneWithMutationsFromVJGermline clone = clonesNotInClusters.get(i);
            Optional<Pair<TreeBuilderByAncestors.Action, Double>> bestActionAndDistanceFromRoot = result.stream()
                    .map(treeWithMeta -> {
                        CloneWithMutationsFromReconstructedRoot rebasedClone = clonesRebase.rebaseClone(treeWithMeta.rootInfo, clone.mutations, clone.cloneWrapper);
                        TreeBuilderByAncestors.Action bestAction = treeWithMeta.treeBuilder.bestActionForObserved(rebasedClone);
                        return Pair.create(
                                bestAction,
                                treeWithMeta.treeBuilder.distanceFromRootToObserved(rebasedClone).doubleValue()
                        );
                    })
                    .min(Comparator.comparing(p -> p.getFirst().changeOfDistance()));

            if (print) {
                bestActionAndDistanceFromRoot.ifPresent(pair -> {
                    double changeOfDistance = pair.getFirst().changeOfDistance().doubleValue();
                    Double distanceFromRoot = pair.getSecond();
                    double metric = distanceFromRoot / changeOfDistance;
                    System.out.printf("distanceFromRoot: %.4f, action: %.4f, metric: %.2f, success: %5s, cloneId: %d%n",
                            distanceFromRoot,
                            changeOfDistance,
                            metric,
                            metric >= thresholdForFreeClones,
                            clone.cloneWrapper.clone.getId()
                    );
                });
            }

            if (bestActionAndDistanceFromRoot.isPresent()) {
                TreeBuilderByAncestors.Action bestAction = bestActionAndDistanceFromRoot.get().getFirst();
                double distanceFromRoot = bestActionAndDistanceFromRoot.get().getSecond();
                if (distanceFromRoot / bestAction.changeOfDistance().doubleValue() >= thresholdForFreeClones) {
                    bestAction.apply();
                    clonesNotInClusters.remove(i);
                } else {
                    clonesNotInClustersLeft.add(clone);
                }
            } else {
                clonesNotInClustersLeft.add(clone);
            }
        }
        return Pair.create(result, clonesNotInClustersLeft);
    }

    private void printTress(List<TreeWithMetaBuilder> trees) {
        trees.stream()
                .sorted(Comparator.<TreeWithMetaBuilder>comparingInt(it -> it.treeBuilder.getObservedNodesCount()).reversed())
                .map(it -> it.treeBuilder.getTree())
                .map(printer::print)
                .forEach(System.out::println);
        System.out.println();
    }

    private SyntheticNode oldestReconstructedAncestor(TreeWithMetaBuilder treeToAttach) {
        //TODO check that there is only one direct child of the root
        TreeBuilderByAncestors.Reconstructed<CloneWithMutationsFromReconstructedRoot, SyntheticNode> oldestReconstructedAncestor = (TreeBuilderByAncestors.Reconstructed<CloneWithMutationsFromReconstructedRoot, SyntheticNode>) treeToAttach
                .treeBuilder.getTree().getRoot()
                .getLinks()
                .get(0)
                .getNode()
                .getContent();
        return oldestReconstructedAncestor.getContent();
    }

    /**
     * sort order of clones will be saved in ClusteringResult::clonesNotInClusters
     */
    private ClusteringResult clusterByCommonMutations(List<CloneWithMutationsFromVJGermline> clones, boolean print) {
        AdjacencyMatrix matrix = new AdjacencyMatrix(clones.size());
        for (int i = 0; i < clones.size(); i++) {
            for (int j = 0; j < clones.size(); j++) {
                int commonMutationsCount = commonMutationsCount(clones.get(i).mutations, clones.get(j).mutations);
                if (commonMutationsCount >= commonMutationsCountForClustering) {
                    matrix.setConnected(i, j);
                }
            }
        }

        List<BitArrayInt> notOverlappedCliques = new ArrayList<>();

        matrix.calculateMaximalCliques().stream()
                .filter(it -> it.bitCount() > 1)
                .sorted(Comparator.comparing(BitArrayInt::bitCount).reversed())
                .forEach(clique -> {
                    if (notOverlappedCliques.stream().noneMatch(it -> it.intersects(clique))) {
                        notOverlappedCliques.add(clique);
                    }
                });

        List<Cluster<CloneWithMutationsFromVJGermline>> clusters = new ArrayList<>();
        for (BitArrayInt clique : notOverlappedCliques) {
            clusters.add(new Cluster<>(Arrays.stream(clique.getBits())
                    .mapToObj(clones::get)
                    .collect(Collectors.toList())
            ));
        }
        if (print) {
            for (BitArrayInt clique : notOverlappedCliques) {
                List<Integer> clonesInClique = Arrays.stream(clique.getBits()).boxed().collect(Collectors.toList());
                System.out.println("      |" + clonesInClique.stream()
                        .map(i -> clones.get(i).cloneWrapper.clone.getId())
                        .map(id -> String.format("%6d|", id))
                        .collect(Collectors.joining()));
                for (int i : clonesInClique) {
                    CloneWithMutationsFromVJGermline base = clones.get(i);
                    System.out.println(String.format("%6d|", base.cloneWrapper.clone.getId()) +
                            clonesInClique.stream()
                                    .map(clones::get)
                                    .map(it -> String.format("%6d|", commonMutationsCount(base.mutations, it.mutations)))
                                    .collect(Collectors.joining())
                    );
                }

                List<List<Mutations<NucleotideSequence>>> commonMutationsByPairs = new ArrayList<>();
                for (int i : clonesInClique) {
                    CloneWithMutationsFromVJGermline base = clones.get(i);
                    clonesInClique.stream()
                            .map(clones::get)
                            .map(it -> commonMutations(base.mutations, it.mutations))
                            .forEach(commonMutationsByPairs::add);
                }
                List<Mutations<NucleotideSequence>> commonMutations = commonMutationsByPairs.get(0);
                for (List<Mutations<NucleotideSequence>> commonMutationsByPair : commonMutationsByPairs) {
                    commonMutations = commonMutationsTemp(commonMutations, commonMutationsByPair);
                }
                System.out.println(commonMutations.stream().mapToInt(Mutations::size).sum());

                System.out.println();
            }
        }

        List<CloneWithMutationsFromVJGermline> clonesNotInClusters = new ArrayList<>();
        for (int i = 0; i < clones.size(); i++) {
            int finalI = i;
            if (notOverlappedCliques.stream().noneMatch(it -> it.get(finalI))) {
                clonesNotInClusters.add(clones.get(finalI));
            }
        }
        return new ClusteringResult(
                clusters,
                clonesNotInClusters
        );
    }

    private int commonMutationsCount(MutationsFromVJGermline first, MutationsFromVJGermline second) {
        return commonMutationsCount(first.getVMutationsWithoutCDR3(), second.getVMutationsWithoutCDR3()) +
                commonMutationsCount(first.getVMutationsInCDR3WithoutNDN(), second.getVMutationsInCDR3WithoutNDN()) +
                commonMutationsCount(first.getJMutationsWithoutCDR3(), second.getJMutationsWithoutCDR3()) +
                commonMutationsCount(first.getJMutationsInCDR3WithoutNDN(), second.getJMutationsInCDR3WithoutNDN());
    }

    private int commonMutationsCount(MutationsWithRange first, MutationsWithRange second) {
        return commonMutationsCount(Collections.singletonList(first), Collections.singletonList(second));
    }

    private int commonMutationsCount(List<MutationsWithRange> first, List<MutationsWithRange> second) {
        return foldByIntersection(
                first, second,
                (a, b, rangeInfo) -> intersection(a.getMutations(), b.getMutations(), rangeInfo).size()
        ).stream().mapToInt(it -> it).sum();
    }

    private List<Mutations<NucleotideSequence>> commonMutations(MutationsFromVJGermline first, MutationsFromVJGermline second) {
        return Stream.of(
                        commonMutations(first.getVMutationsWithoutCDR3(), second.getVMutationsWithoutCDR3()),
                        commonMutations(first.getVMutationsInCDR3WithoutNDN(), second.getVMutationsInCDR3WithoutNDN()),
                        commonMutations(first.getJMutationsWithoutCDR3(), second.getJMutationsWithoutCDR3()),
                        commonMutations(first.getJMutationsInCDR3WithoutNDN(), second.getJMutationsInCDR3WithoutNDN())
                )
                .collect(Collectors.toList());
    }

    private Mutations<NucleotideSequence> commonMutations(MutationsWithRange first, MutationsWithRange second) {
        return commonMutations(Collections.singletonList(first), Collections.singletonList(second));
    }

    private Mutations<NucleotideSequence> commonMutations(List<MutationsWithRange> first, List<MutationsWithRange> second) {
        MutationsBuilder<NucleotideSequence> builder = new MutationsBuilder<>(NucleotideSequence.ALPHABET);
        foldByIntersection(
                first, second,
                (a, b, rangeInfo) -> intersection(a.getMutations(), b.getMutations(), rangeInfo)
        ).forEach(builder::append);
        return builder.createAndDestroy();
    }

    private List<Mutations<NucleotideSequence>> commonMutationsTemp(List<Mutations<NucleotideSequence>> first, List<Mutations<NucleotideSequence>> second) {
        List<Mutations<NucleotideSequence>> result = new ArrayList<>();
        for (int i = 0; i < first.size(); i++) {
            Range firstRange = first.get(i).getMutatedRange();
            Range secondRange = second.get(i).getMutatedRange();
            if (firstRange == null || secondRange == null) {
                result.add(EMPTY_NUCLEOTIDE_MUTATIONS);
            } else {
                result.add(intersection(
                        first.get(i),
                        second.get(i),
                        new RangeInfo(
                                new Range(Math.min(firstRange.getLower(), secondRange.getLower()), Math.max(firstRange.getUpper(), secondRange.getUpper())),
                                false
                        )
                ));
            }
        }
        return result;
    }


    private TreeWithMetaBuilder buildATree(Cluster<CloneWithMutationsFromVJGermline> cluster) {
        RootInfo rootInfo = buildRootInfo(cluster);

        List<CloneWithMutationsFromReconstructedRoot> rebasedCluster = cluster.cluster.stream()
                .map(clone -> clonesRebase.rebaseClone(rootInfo, clone.mutations, clone.cloneWrapper))
                .sorted(Comparator.<CloneWithMutationsFromReconstructedRoot, BigDecimal>comparing(cloneDescriptor -> distance(cloneDescriptor.getMutationsFromRoot())).reversed())
                .collect(Collectors.toList());

        SyntheticNode root = buildARootForATree(rootInfo, rebasedCluster);

        TreeBuilderByAncestors<CloneWithMutationsFromReconstructedRoot, SyntheticNode, MutationsDescription> treeBuilderByAncestors = new TreeBuilderByAncestors<>(
                root,
                (base, mutations) -> distance(mutations).add(penaltyForReversedMutations(base.getFromRootToThis(), mutations)),
                MutationsUtils::mutationsBetween,
                (parent, mutations) -> SyntheticNode.createFromParentAndDiffOfParentAndChild(parent.getFromRootToThis(), mutations),
                observed -> SyntheticNode.createFromMutations(observed.getMutationsFromRoot()),
                this::commonMutations,
                (parent, child) -> SyntheticNode.createFromMutations(
                        child.getFromRootToThis().withKnownNDNMutations(
                                new MutationsWithRange(
                                        child.getFromRootToThis().getKnownNDN().getSequence1(),
                                        MutationsUtils.concreteNDNChild(
                                                parent.getFromRootToThis().getKnownNDN().getMutations(),
                                                child.getFromRootToThis().getKnownNDN().getMutations()
                                        ),
                                        child.getFromRootToThis().getKnownNDN().getRangeInfo()
                                )
                        )
                ),
                countOfNodesToProbe
        );

        for (CloneWithMutationsFromReconstructedRoot cloneWithMutationsFromReconstructedRoot : rebasedCluster) {
            treeBuilderByAncestors.addNode(cloneWithMutationsFromReconstructedRoot);
        }
        return new TreeWithMetaBuilder(
                treeBuilderByAncestors,
                rootInfo
        );
    }

    private BigDecimal sumOfDistances(TreeBuilderByAncestors<CloneWithMutationsFromReconstructedRoot, SyntheticNode, MutationsDescription> treeBuilderByAncestors) {
        return treeBuilderByAncestors.getTree().allNodes()
                .map(Tree.NodeWithParent::getDistance)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<Integer> reversedMutations(Mutations<NucleotideSequence> fromRootToBase, Mutations<NucleotideSequence> mutations) {
        Map<Integer, List<Integer>> fromRootToBaseByPositions = IntStream.range(0, fromRootToBase.size())
                .mapToObj(fromRootToBase::getMutation)
                .collect(Collectors.groupingBy(Mutation::getPosition));
        Map<Integer, List<Integer>> mutationsByPositions = IntStream.range(0, mutations.size())
                .mapToObj(mutations::getMutation)
                .collect(Collectors.groupingBy(Mutation::getPosition));

        return Stream.concat(fromRootToBaseByPositions.keySet().stream(), mutationsByPositions.keySet().stream())
                .distinct()
                .map(position -> Pair.create(
                        fromRootToBaseByPositions.getOrDefault(position, Collections.emptyList()).stream()
                                .filter(Mutation::isSubstitution)
                                .findFirst(),
                        mutationsByPositions.getOrDefault(position, Collections.emptyList()).stream()
                                .filter(Mutation::isSubstitution)
                                .findFirst()
                ))
                .filter(pair -> pair.getFirst().isPresent() && pair.getSecond().isPresent())
                .map(pair -> Pair.create(pair.getFirst().get(), pair.getSecond().get()))
                .filter(pair -> Mutation.getTo(pair.getFirst()) == Mutation.getFrom(pair.getSecond()) && Mutation.getFrom(pair.getFirst()) == Mutation.getTo(pair.getSecond()))
                .map(pair -> Mutation.getPosition(pair.getFirst()))
                .collect(Collectors.toList());
    }

    private SyntheticNode buildARootForATree(RootInfo rootInfo, List<CloneWithMutationsFromReconstructedRoot> rebasedCluster) {
        return SyntheticNode.createRoot(
                overlap(rebasedCluster.stream()
                        .map(clone -> clone.getMutationsFromRoot().getVMutationsWithoutCDR3().stream()
                                .map(it -> it.getRangeInfo().getRange())
                                .collect(Collectors.toList())
                        ).collect(Collectors.toList())
                ),
                VSequence1,
                rootInfo,
                overlap(rebasedCluster.stream()
                        .map(clone -> clone.getMutationsFromRoot().getJMutationsWithoutCDR3().stream()
                                .map(it -> it.getRangeInfo().getRange())
                                .collect(Collectors.toList())
                        ).collect(Collectors.toList())
                ),
                JSequence1
        );
    }

    private RootInfo buildRootInfo(Cluster<CloneWithMutationsFromVJGermline> cluster) {
        MutationsFromVJGermline rootBasedOn = cluster.cluster.get(0).mutations;

        //TODO may be just get from root?
        Range VRangeInCDR3 = mostLikableRangeInCDR3(cluster, this::VRangeInCDR3);
        Range JRangeInCDR3 = mostLikableRangeInCDR3(cluster, this::JRangeInCDR3);

        Range NDNRangeInKnownNDN = NDNRangeInKnownNDN(rootBasedOn, VRangeInCDR3, JRangeInCDR3);

        SequenceBuilder<NucleotideSequence> NDNBuilder = NucleotideSequence.ALPHABET.createBuilder();
        IntStream.range(0, NDNRangeInKnownNDN.length()).forEach(it -> NDNBuilder.append(NucleotideSequence.N));

        return new RootInfo(
                VRangeInCDR3,
                NDNBuilder.createAndDestroy(),
                JRangeInCDR3
        );
    }

    private List<MutationsWithRange> getMutationsWithoutRange(Clone clone, GeneType geneType, Range without) {
        VDJCHit bestHit = clone.getBestHit(geneType);
        return IntStream.range(0, bestHit.getAlignments().length)
                .boxed()
                .flatMap(index -> {
                    Alignment<NucleotideSequence> alignment = bestHit.getAlignment(index);
                    Mutations<NucleotideSequence> mutations = alignment.getAbsoluteMutations();
                    List<Range> rangesWithout = alignment.getSequence1Range().without(without);
                    return rangesWithout.stream()
                            .filter(range -> !range.isEmpty())
                            .map(range -> new MutationsWithRange(
                                    alignment.getSequence1(),
                                    mutations,
                                    new RangeInfo(range, alignment.getSequence1Range().getLower() == range.getLower())
                            ));
                })
                .collect(Collectors.toList());
    }

    private Pair<Mutations<NucleotideSequence>, Range> getVMutationsWithinNDN(Clone clone, int from) {
        VDJCHit bestHit = clone.getBestHit(Variable);
        int CDR3Begin = getVRelativePosition.apply(ReferencePoint.CDR3Begin);
        return IntStream.range(0, bestHit.getAlignments().length)
                .boxed()
                .map(bestHit::getAlignment)
                .filter(alignment -> alignment.getSequence1Range().contains(CDR3Begin))
                .map(alignment -> {
                    if (alignment.getSequence1Range().contains(from)) {
                        return Optional.of(Pair.create(
                                alignment.getAbsoluteMutations(),
                                new Range(from, alignment.getSequence1Range().getUpper())
                        ));
                    } else {
                        return Optional.<Pair<Mutations<NucleotideSequence>, Range>>empty();
                    }
                })
                .flatMap(Java9Util::stream)
                .findFirst()
                .orElseGet(() -> Pair.create(EMPTY_NUCLEOTIDE_MUTATIONS, new Range(from, from)));
    }

    private Mutations<NucleotideSequence> getMutationsForRange(Clone clone, Range range, GeneType geneType) {
        VDJCHit bestHit = clone.getBestHit(geneType);
        return IntStream.range(0, bestHit.getAlignments().length)
                .boxed()
                .map(bestHit::getAlignment)
                .filter(alignment -> alignment.getSequence1Range().contains(range))
                .map(Alignment::getAbsoluteMutations)
                .findFirst()
                .orElseThrow(IllegalArgumentException::new);
    }

    private Pair<Mutations<NucleotideSequence>, Range> getJMutationsWithinNDN(Clone clone, int to) {
        VDJCHit bestHit = clone.getBestHit(Joining);
        int CDR3End = getJRelativePosition.apply(ReferencePoint.CDR3End);
        return IntStream.range(0, bestHit.getAlignments().length)
                .boxed()
                .map(bestHit::getAlignment)
                .filter(alignment -> alignment.getSequence1Range().contains(CDR3End))
                .map(alignment -> {
                    if (alignment.getSequence1Range().contains(to)) {
                        return Optional.of(Pair.create(
                                alignment.getAbsoluteMutations(),
                                new Range(alignment.getSequence1Range().getLower(), to)
                        ));
                    } else {
                        return Optional.<Pair<Mutations<NucleotideSequence>, Range>>empty();
                    }
                })
                .flatMap(Java9Util::stream)
                .findFirst()
                .orElseGet(() -> Pair.create(EMPTY_NUCLEOTIDE_MUTATIONS, new Range(to, to)));
    }

    private BigDecimal penaltyForReversedMutations(MutationsDescription fromRootToBase, MutationsDescription mutations) {
        int reversedMutationsCount = reversedMutations(fromRootToBase.getConcatenatedVMutations(), mutations.getConcatenatedVMutations()).size() +
                reversedMutations(fromRootToBase.getConcatenatedJMutations(), mutations.getConcatenatedJMutations()).size();
        return BigDecimal.valueOf(penaltyForReversedMutations).multiply(BigDecimal.valueOf(reversedMutationsCount));
    }

    private BigDecimal distance(MutationsDescription mutations) {
        double VPenalties = maxScore(mutations.getVMutationsWithoutCDR3(), VScoring) - score(mutations.getVMutationsWithoutCDR3(), VScoring) +
                maxScore(mutations.getVMutationsInCDR3WithoutNDN(), VScoring) - score(mutations.getVMutationsInCDR3WithoutNDN(), VScoring);
        double VLength = mutations.getVMutationsWithoutCDR3().stream().mapToDouble(it -> it.getRangeInfo().getRange().length()).sum() +
                mutations.getVMutationsInCDR3WithoutNDN().getRangeInfo().getRange().length();

        double JPenalties = maxScore(mutations.getJMutationsWithoutCDR3(), JScoring) - score(mutations.getJMutationsWithoutCDR3(), JScoring) +
                maxScore(mutations.getJMutationsInCDR3WithoutNDN(), JScoring) - score(mutations.getJMutationsInCDR3WithoutNDN(), JScoring);
        double JLength = mutations.getJMutationsWithoutCDR3().stream().mapToDouble(it -> it.getRangeInfo().getRange().length()).sum() +
                mutations.getJMutationsInCDR3WithoutNDN().getRangeInfo().getRange().length();

        int NDNPenalties = maxScore(mutations.getKnownNDN(), NDNScoring) - score(mutations.getKnownNDN(), NDNScoring);
        double NDNLength = mutations.getKnownNDN().getRangeInfo().getRange().length();

//        return BigDecimal.valueOf(NDNPenalties / NDNLength + (VPenalties + JPenalties) / (VLength + JLength));
        return BigDecimal.valueOf((NDNPenalties * NDNScoreMultiplier + VPenalties + JPenalties) / (NDNLength + VLength + JLength));
    }

    private MutationsDescription commonMutations(MutationsDescription first, MutationsDescription second) {
        return new MutationsDescription(
                intersection(first.getVMutationsWithoutCDR3(), second.getVMutationsWithoutCDR3()),
                intersection(first.getVMutationsInCDR3WithoutNDN(), second.getVMutationsInCDR3WithoutNDN()),
                new MutationsWithRange(
                        first.getKnownNDN().getSequence1(),
                        MutationsUtils.findNDNCommonAncestor(
                                first.getKnownNDN().getMutations(),
                                second.getKnownNDN().getMutations()
                        ),
                        first.getKnownNDN().getRangeInfo()
                ),
                intersection(first.getJMutationsInCDR3WithoutNDN(), second.getJMutationsInCDR3WithoutNDN()),
                intersection(first.getJMutationsWithoutCDR3(), second.getJMutationsWithoutCDR3())
        );
    }

    //TODO it is more possible to decrease length of alignment than to increase
    private Range mostLikableRangeInCDR3(Cluster<CloneWithMutationsFromVJGermline> cluster, Function<Clone, Range> rangeSupplier) {
        return cluster.cluster.stream()
                .sorted(Comparator.comparing(it -> it.mutations.getVJMutationsCount()))
                .limit(topToVoteOnNDNSize)
                .map(clone -> clone.cloneWrapper.clone)
                .map(rangeSupplier)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseThrow(IllegalStateException::new);
    }

    private Range minRangeInCDR3(Cluster<CloneWrapper> cluster, Function<Clone, Range> rangeSupplier) {
        return cluster.cluster.stream()
                .map(clone -> clone.clone)
                .map(rangeSupplier)
                .min(Comparator.comparing(Range::length))
                .orElseThrow(IllegalStateException::new);
    }

    private Range VRangeInCDR3(Clone clone) {
        //TODO try to use alignment to calculate most possible position
        int positionOfCDR3Begin = getVRelativePosition.apply(CDR3Begin);
        return new Range(
                positionOfCDR3Begin,
                positionOfCDR3Begin + clone.getNFeature(new GeneFeature(CDR3Begin, VEndTrimmed)).size()
        );
    }

    private Range JRangeInCDR3(Clone clone) {
        //TODO try to use alignment to calculate most possible position
        int positionOfCDR3End = getJRelativePosition.apply(CDR3End);
        return new Range(
                positionOfCDR3End - clone.getNFeature(new GeneFeature(JBeginTrimmed, CDR3End)).size(),
                positionOfCDR3End
        );
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

    /**
     * sum score of given mutations
     */
    private static double score(List<MutationsWithRange> mutationsWithRanges, AlignmentScoring<NucleotideSequence> scoring) {
        return mutationsWithRanges.stream()
                .mapToDouble(mutations -> score(mutations, scoring))
                .sum();
    }

    private static int score(MutationsWithRange mutations, AlignmentScoring<NucleotideSequence> scoring) {
        return AlignmentUtils.calculateScore(
                mutations.getSequence1(),
                mutations.mutationsForRange(),
                scoring
        );
    }

    private static double maxScore(List<MutationsWithRange> vMutationsBetween, AlignmentScoring<NucleotideSequence> scoring) {
        return vMutationsBetween.stream()
                .mapToDouble(mutations -> maxScore(mutations, scoring))
                .sum();
    }

    private static int maxScore(MutationsWithRange mutations, AlignmentScoring<NucleotideSequence> scoring) {
        return maxScore(mutations.getSequence1(), scoring);
    }

    private static int maxScore(NucleotideSequence sequence, AlignmentScoring<NucleotideSequence> scoring) {
        return sequence.size() * scoring.getMaximalMatchScore();
    }

    private static class TreeWithMetaBuilder {
        private final TreeBuilderByAncestors<CloneWithMutationsFromReconstructedRoot, SyntheticNode, MutationsDescription> treeBuilder;
        private final RootInfo rootInfo;

        public TreeWithMetaBuilder copy() {
            return new TreeWithMetaBuilder(treeBuilder.copy(), rootInfo);
        }

        public TreeWithMetaBuilder(
                TreeBuilderByAncestors<CloneWithMutationsFromReconstructedRoot, SyntheticNode, MutationsDescription> treeBuilder,
                RootInfo rootInfo
        ) {
            this.treeBuilder = treeBuilder;
            this.rootInfo = rootInfo;
        }
    }

    private static class ClusteringResult {
        private final List<Cluster<CloneWithMutationsFromVJGermline>> clusters;
        /**
         * sorted by score from VJ
         */
        private final List<CloneWithMutationsFromVJGermline> clonesNotInClusters;

        private ClusteringResult(List<Cluster<CloneWithMutationsFromVJGermline>> clusters, List<CloneWithMutationsFromVJGermline> clonesNotInClusters) {
            this.clusters = clusters;
            this.clonesNotInClusters = clonesNotInClusters;
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
