package com.milaboratory.mixcr.trees;

import com.milaboratory.core.Range;
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
import static com.milaboratory.mixcr.trees.ClonesRebase.NDNRangeInKnownNDN;
import static io.repseq.core.GeneType.Joining;
import static io.repseq.core.GeneType.Variable;
import static io.repseq.core.ReferencePoint.*;

class ClusterProcessor {
    private final double threshold;
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
                        int widening = 0;
                        for (int i = 0; i < mutations.getFromParentToThis().getKnownNDN().getMutations().size(); i++) {
                            int mutation = mutations.getFromParentToThis().getKnownNDN().getMutations().getMutation(i);
                            if (Mutation.isSubstitution(mutation)) {
                                Wildcard from = NucleotideAlphabet.complementWildcard(Mutation.getFrom(mutation));
                                Wildcard to = NucleotideAlphabet.complementWildcard(Mutation.getTo(mutation));
                                if (to.intersectsWith(from) && to.basicSize() > from.basicSize()) {
                                    widening++;
                                }
                            }
                        }

                        return "V: " + (mutations.getFromParentToThis().getVMutationsWithoutCDR3().stream().mapToInt(MutationsWithRange::mutationsCount).sum() + mutations.getFromParentToThis().getVMutationsInCDR3WithoutNDN().mutationsCount()) +
                                " J: " + (mutations.getFromParentToThis().getJMutationsWithoutCDR3().stream().mapToInt(MutationsWithRange::mutationsCount).sum() + mutations.getFromParentToThis().getJMutationsInCDR3WithoutNDN().mutationsCount()) +
                                " widening: " + widening +
                                " reversedV: " + reversedMutations(mutations.getFromRootToParent().getConcatenatedVMutations(), mutations.getFromParentToThis().getConcatenatedVMutations()) +
                                " reversedJ: " + reversedMutations(mutations.getFromRootToParent().getConcatenatedJMutations(), mutations.getFromParentToThis().getConcatenatedJMutations()) +
                                " NDN: " + mutations.getFromParentToThis().getKnownNDN().getMutations() +
                                " V: " + mutations.getFromParentToThis().getConcatenatedVMutations() +
                                " J: " + mutations.getFromParentToThis().getConcatenatedJMutations()
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
        this.threshold = parameters.maxDistanceWithinCluster;
        this.hideTreesLessThanSize = parameters.hideTreesLessThanSize;
        this.commonMutationsCountForClustering = parameters.commonMutationsCountForClustering;
        this.penaltyForReversedMutations = parameters.penaltyForReversedMutations;
        countOfNodesToProbe = parameters.countOfNodesToProbe;
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
        clonesRebase = new ClonesRebase(VSequence1, JSequence1, NDNScoring);
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

        ClusteringResult clusteringResult = clusterByCommonMutations(clones);
        List<TreeWithMetaBuilder> firstStepTrees = clusteringResult.clusters
                .stream()
                .filter(it -> it.cluster.size() > 1)
                .map(this::buildATree)
                .sorted(Comparator.<TreeWithMetaBuilder>comparingInt(it -> it.treeBuilder.getObservedNodesCount()).reversed())
                .collect(Collectors.toList());

        List<Double> distances = new ArrayList<>();
        //try to add as nodes clones that wasn't picked up by clustering
        for (CloneWithMutationsFromVJGermline clone : clusteringResult.clonesNotInClusters) {
            Optional<Pair<Runnable, Double>> nearestTree = firstStepTrees.stream()
                    .map(treeWithMeta -> {
                        CloneWithMutationsFromReconstructedRoot rebasedClone = clonesRebase.rebaseClone(treeWithMeta.rootInfo, clone.mutations, clone.cloneWrapper);
                        return Pair.<Runnable, Double>create(
                                () -> treeWithMeta.treeBuilder.addNode(rebasedClone),
                                distanceFromTree(rebasedClone, treeWithMeta.treeBuilder)
                        );
                    })
                    .max(Comparator.comparing(Pair::getSecond));
            distances.add(nearestTree.map(Pair::getSecond).orElse(-1.0));
//            if (nearestTree.isPresent() && nearestTree.get().getSecond() < threshold) {
//                nearestTree.get().getFirst().run();
//            }
        }
        distances.stream().sorted().forEach(System.out::println);
        System.out.println();

//        List<TreeWithMetaBuilder> secondStepTrees = new ArrayList<>();
//
//        //trying to grow the biggest trees first
//        while (!firstStepTrees.isEmpty()) {
//            TreeWithMetaBuilder treeToGrow = firstStepTrees.get(0);
//            firstStepTrees.remove(0);
//
//            //trying to add the smallest trees first
//            for (int i = firstStepTrees.size() - 1; i >= 0; i--) {
//                TreeWithMetaBuilder treeToAttach = firstStepTrees.get(i);
//
//                double distanceFromTree = treeToAttach.treeBuilder.getTree().allNodes()
//                        .map(node -> node.getContent().convert(it -> Optional.<MutationsDescription>empty(), Optional::of))
//                        .flatMap(Java9Util::stream)
//                        .map(it -> clonesRebase.rebaseMutations(it, treeToAttach.rootInfo, treeToGrow.rootInfo))
//                        .mapToDouble(it -> distanceFromTree(it, treeToGrow.treeBuilder.getTree()))
//                        .min().orElseThrow(IllegalArgumentException::new);
//
//                if (distanceFromTree < threshold) {
//                    treeToAttach.treeBuilder.getTree().allNodes()
//                            .map(node -> node.getContent().convert(Optional::of, it -> Optional.<CloneWithMutationsFromReconstructedRoot>empty()))
//                            .flatMap(Java9Util::stream)
//                            .map(clone -> clonesRebase.rebaseClone(treeToGrow.rootInfo, clone.getMutationsFromVJGermline(), clone.getClone()))
//                            .sorted(Comparator.<CloneWithMutationsFromReconstructedRoot, BigDecimal>comparing(clone -> distance(clone.getMutationsFromRoot())).reversed())
//                            .forEach(treeToGrow.treeBuilder::addNode);
//                    firstStepTrees.remove(i);
//                }
//            }
//
//            secondStepTrees.add(treeToGrow);
//        }

        List<TreeWithMetaBuilder> secondStepTrees = new ArrayList<>(firstStepTrees);
        return secondStepTrees.stream()
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

    /**
     * sort order of clones will be saved in ClusteringResult::clonesNotInClusters
     */
    private ClusteringResult clusterByCommonMutations(List<CloneWithMutationsFromVJGermline> clones) {
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
        BitArrayInt concatCliques = new BitArrayInt(clones.size());
        for (BitArrayInt clique : notOverlappedCliques) {
            concatCliques.append(clique);
            clusters.add(new Cluster<>(Arrays.stream(clique.getBits())
                    .mapToObj(clones::get)
                    .collect(Collectors.toList())
            ));
        }
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

        List<CloneWithMutationsFromVJGermline> clonesNotInClusters = new ArrayList<>();
        for (int i = 0; i < clones.size(); i++) {
            if (!concatCliques.get(i)) {
                clonesNotInClusters.add(clones.get(i));
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
                (a, b, rangeInfo) -> MutationsUtils.intersection(a.getMutations(), b.getMutations(), rangeInfo).size()
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
                (a, b, rangeInfo) -> MutationsUtils.intersection(a.getMutations(), b.getMutations(), rangeInfo)
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
                result.add(MutationsUtils.intersection(
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
        // Build a tree for every cluster
        // fix marks of VEnd and JBegin
        // determine part between VEnd and JBegin
        // resort by mutations count
        // build by next neighbor

        RootInfo rootInfo = buildRootInfo(cluster);

        List<CloneWithMutationsFromReconstructedRoot> rebasedCluster = cluster.cluster.stream()
                .map(clone -> clonesRebase.rebaseClone(rootInfo, clone.mutations, clone.cloneWrapper))
                .sorted(Comparator.<CloneWithMutationsFromReconstructedRoot, BigDecimal>comparing(cloneDescriptor -> distance(cloneDescriptor.getMutationsFromRoot())).reversed())
                .collect(Collectors.toList());

        SyntheticNode root = buildARootForATree(rootInfo, rebasedCluster);

        TreeBuilderByAncestors<CloneWithMutationsFromReconstructedRoot, SyntheticNode, MutationsDescription> treeBuilderByAncestors = new TreeBuilderByAncestors<>(
                root,
                (base, mutations) -> distance(mutations).add(penaltyForReversedMutations(base.getFromRootToThis(), mutations)),
                this::mutationsBetween,
                this::combineWith,
                (parent, observed) -> {
                    SyntheticNode syntheticNode = new SyntheticNode(
                            parent.getFromRootToThis(),
                            mutationsBetween(parent.getFromRootToThis(), observed.getMutationsFromRoot())
                    );
                    try {
                        AncestorInfo ancestorInfo = ancestorInfoBuilder.buildAncestorInfo(syntheticNode.getFromRootToThis());
                        //TODO remove
                        if (!observed.getClone().clone.getNFeature(GeneFeature.CDR3).equals(ancestorInfo.getSequence().getRange(ancestorInfo.getCDR3Begin(), ancestorInfo.getCDR3End()))) {
                            throw new IllegalStateException();
                        }
                        if (!observed.getClone().clone.getTarget(0).getSequence().equals(ancestorInfo.getSequence())) {
                            throw new IllegalStateException();
                        }
                    } catch (Throwable e) {
                        new SyntheticNode(
                                parent.getFromRootToThis(),
                                mutationsBetween(parent.getFromRootToThis(), observed.getMutationsFromRoot())
                        );
                        throw e;
                    }
                    return syntheticNode;
                },
                this::commonMutations,
                (parent, child) -> new SyntheticNode(
                        parent.getFromRootToThis(),
                        child.getFromParentToThis().withKnownNDNMutations(
                                new MutationsWithRange(
                                        child.getFromParentToThis().getKnownNDN().getSequence1(),
                                        MutationsUtils.difference(
                                                parent.getFromRootToThis().getKnownNDN().getMutations(),
                                                parent.getFromParentToThis().getKnownNDN().getRangeInfo(),
                                                MutationsUtils.concreteNDNChild(
                                                        parent.getFromRootToThis().getKnownNDN().getMutations(),
                                                        child.getFromRootToThis().getKnownNDN().getMutations()
                                                ),
                                                //TODO indels
                                                parent.getFromParentToThis().getKnownNDN().getRangeInfo()
                                        ),
                                        child.getFromParentToThis().getKnownNDN().getRangeInfo()
                                )
                        )
                ),
                countOfNodesToProbe
        );

        for (CloneWithMutationsFromReconstructedRoot cloneWithMutationsFromReconstructedRoot : rebasedCluster) {
            treeBuilderByAncestors.addNode(cloneWithMutationsFromReconstructedRoot);
            System.out.println(printer.print(treeBuilderByAncestors.getTree()));
            System.out.println(sumOfDistances(treeBuilderByAncestors));
        }
        System.out.println();
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
        MutationsDescription emptyMutations = new MutationsDescription(
                overlap(rebasedCluster.stream()
                        .map(clone -> clone.getMutationsFromRoot().getVMutationsWithoutCDR3().stream()
                                .map(it -> it.getRangeInfo().getRange())
                                .collect(Collectors.toList())
                        ).collect(Collectors.toList())
                ).stream()
                        .map(it -> new MutationsWithRange(VSequence1, EMPTY_NUCLEOTIDE_MUTATIONS, new RangeInfo(it, false)))
                        .collect(Collectors.toList()),
                new MutationsWithRange(
                        VSequence1,
                        EMPTY_NUCLEOTIDE_MUTATIONS,
                        new RangeInfo(rootInfo.getVRangeInCDR3(), false)
                ),
                new MutationsWithRange(
                        rootInfo.getReconstructedNDN(),
                        EMPTY_NUCLEOTIDE_MUTATIONS,
                        new RangeInfo(new Range(0, rootInfo.getReconstructedNDN().size()), true)
                ),
                new MutationsWithRange(
                        JSequence1,
                        EMPTY_NUCLEOTIDE_MUTATIONS,
                        new RangeInfo(rootInfo.getJRangeInCDR3(), true)
                ),
                overlap(rebasedCluster.stream()
                        .map(clone -> clone.getMutationsFromRoot().getJMutationsWithoutCDR3().stream()
                                .map(it -> it.getRangeInfo().getRange())
                                .collect(Collectors.toList())
                        ).collect(Collectors.toList())
                ).stream()
                        .map(it -> new MutationsWithRange(JSequence1, EMPTY_NUCLEOTIDE_MUTATIONS, new RangeInfo(it, false)))
                        .collect(Collectors.toList())
        );
        return new SyntheticNode(emptyMutations, emptyMutations, emptyMutations);
    }

    private RootInfo buildRootInfo(Cluster<CloneWithMutationsFromVJGermline> cluster) {
        //TODO try to choose root by top clones and direction of mutations
        MutationsFromVJGermline rootBasedOn = cluster.cluster.get(0).mutations;

        //TODO may be just get from root?
        Range VRangeInCDR3 = mostLikableVRangeInCDR3(cluster);
        Range JRangeInCDR3 = mostLikableJRangeInCDR3(cluster);

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

//        return BigDecimal.valueOf((NDNPenalties + VPenalties + JPenalties) / (NDNLength + VLength + JLength));
        return BigDecimal.valueOf((NDNPenalties + VPenalties + JPenalties) / (NDNLength + VLength + JLength));
    }

    private MutationsDescription mutationsBetween(SyntheticNode first, SyntheticNode second) {
        return mutationsBetween(first.getFromRootToThis(), second.getFromRootToThis());
    }

    private MutationsDescription mutationsBetween(MutationsDescription first, MutationsDescription second) {
        return new MutationsDescription(
                mutationsBetween(first.getVMutationsWithoutCDR3(), second.getVMutationsWithoutCDR3()),
                mutationsBetween(first.getVMutationsInCDR3WithoutNDN(), second.getVMutationsInCDR3WithoutNDN()),
                mutationsBetween(first.getKnownNDN(), second.getKnownNDN()),
                mutationsBetween(first.getJMutationsInCDR3WithoutNDN(), second.getJMutationsInCDR3WithoutNDN()),
                mutationsBetween(first.getJMutationsWithoutCDR3(), second.getJMutationsWithoutCDR3())
        );
    }

    private SyntheticNode combineWith(SyntheticNode first, MutationsDescription second) {
        return new SyntheticNode(
                first.getFromRootToThis(),
                second
        );
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

    //TODO it is more possible to decrease length of alignment than to increase. It is important on small trees
    private Range mostLikableVRangeInCDR3(Cluster<CloneWithMutationsFromVJGermline> cluster) {
        return cluster.cluster.stream()
                .map(clone -> clone.cloneWrapper.clone)
                .map(this::VRangeInCDR3)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseThrow(IllegalStateException::new);
    }

    //TODO it is more possible to decrease length of alignment than to increase. It is important on small trees
    private Range mostLikableJRangeInCDR3(Cluster<CloneWithMutationsFromVJGermline> cluster) {
        return cluster.cluster.stream()
                .map(clone -> clone.cloneWrapper.clone)
                .map(this::JRangeInCDR3)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseThrow(IllegalStateException::new);
    }

    private Range minVRangeInCDR3(Cluster<CloneWrapper> cluster) {
        return cluster.cluster.stream()
                .map(clone -> clone.clone)
                .map(this::VRangeInCDR3)
                .min(Comparator.comparing(Range::length))
                .orElseThrow(IllegalStateException::new);
    }

    private Range minJRangeInCDR3(Cluster<CloneWrapper> cluster) {
        return cluster.cluster.stream()
                .map(clone -> clone.clone)
                .map(this::JRangeInCDR3)
                .min(Comparator.comparing(Range::length))
                .orElseThrow(IllegalStateException::new);
    }

    private Range VRangeInCDR3(Clone clone) {
        int positionOfCDR3Begin = getVRelativePosition.apply(CDR3Begin);
        return new Range(
                positionOfCDR3Begin,
                positionOfCDR3Begin + clone.getNFeature(new GeneFeature(CDR3Begin, VEndTrimmed)).size()
        );
    }

    private Range JRangeInCDR3(Clone clone) {
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

    private static List<MutationsWithRange> mutationsBetween(List<MutationsWithRange> firstMutations, List<MutationsWithRange> secondMutations) {
        return foldByIntersection(firstMutations, secondMutations, ClusterProcessor::mutationsBetween);
    }

    private static MutationsWithRange intersection(MutationsWithRange from, MutationsWithRange to) {
        if (!from.getRangeInfo().equals(to.getRangeInfo())) {
            throw new IllegalArgumentException();
        }
        return ClusterProcessor.intersection(from, to, from.getRangeInfo());
    }

    private static List<MutationsWithRange> intersection(List<MutationsWithRange> from, List<MutationsWithRange> to) {
        return foldByIntersection(from, to, ClusterProcessor::intersection);
    }

    private static <T> List<T> foldByIntersection(
            List<MutationsWithRange> firstMutations,
            List<MutationsWithRange> secondMutations,
            TriFunction<MutationsWithRange, MutationsWithRange, RangeInfo, T> folder
    ) {
        return firstMutations.stream()
                .flatMap(base -> secondMutations.stream()
                        .map(comparison -> Optional.ofNullable(base.projectedRange().intersection(comparison.getRangeInfo()))
                                .map(it -> folder.apply(base, comparison, it)))
                        .flatMap(Java9Util::stream)
                )
                .collect(Collectors.toList());
    }

    private static MutationsWithRange mutationsBetween(MutationsWithRange base, MutationsWithRange comparison, RangeInfo intersection) {
        return new MutationsWithRange(
                base.getMutations().mutate(base.getSequence1()),
                MutationsUtils.difference(
                        base.getMutations(),
                        base.getRangeInfo(),
                        comparison.getMutations(),
                        comparison.getRangeInfo()
                ),
                intersection
        );
    }

    static MutationsWithRange mutationsBetween(MutationsWithRange base, MutationsWithRange comparison) {
        if (!base.getRangeInfo().getRange().equals(comparison.getRangeInfo().getRange())) {
            throw new IllegalArgumentException();
        }
        return new MutationsWithRange(
                base.getMutations().mutate(base.getSequence1()),
                MutationsUtils.difference(
                        base.getMutations(),
                        base.getRangeInfo(),
                        comparison.getMutations(),
                        comparison.getRangeInfo()
                ),
                base.getRangeInfo().intersection(comparison.getRangeInfo())
        );
    }

    private static MutationsWithRange intersection(MutationsWithRange base, MutationsWithRange comparison, RangeInfo intersection) {
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

    private double distanceFromTree(CloneWithMutationsFromReconstructedRoot clone, TreeBuilderByAncestors<CloneWithMutationsFromReconstructedRoot, SyntheticNode, MutationsDescription> treeBuilder) {
        return treeBuilder.getTree().allNodes()
                .map(Tree.NodeWithParent::getNode)
                .map(node -> node.getContent().convert(it -> Optional.<SyntheticNode>empty(), Optional::of))
                .flatMap(Java9Util::stream)
                .map(syntheticNode -> treeBuilder.distanceFromNode(syntheticNode, clone))
                .min(Comparator.naturalOrder())
                .orElseThrow(IllegalArgumentException::new)
                .doubleValue();
    }

    private FitnessFunctionParams fitnessFunctionParams(MutationsFromVJGermline first, MutationsFromVJGermline second) {
        throw new UnsupportedOperationException();
//        MutationsWithRange CDR3MutationsBetween = clonesRebase.NDNMutations(first.getKnownNDN(), second.getKnownNDN());
//
//        List<MutationsWithRange> VMutationsBetween = mutationsBetween(first.getVMutationsWithoutNDN(), second.getVMutationsWithoutNDN());
//        double VMutationsBetweenScore = score(VMutationsBetween, VScoring);
//
//        List<MutationsWithRange> JMutationsBetween = mutationsBetween(first.getJMutationsWithoutNDN(), second.getJMutationsWithoutNDN());
//        double JMutationsBetweenScore = score(JMutationsBetween, JScoring);
//
//        double CDR3MutationsBetweenScore = score(CDR3MutationsBetween, NDNScoring);
//
//        double maxScoreForFirstVJ = maxScore(first.getVMutationsWithoutNDN(), VScoring) + maxScore(first.getJMutationsWithoutNDN(), JScoring);
//        double maxScoreForSecondVJ = maxScore(second.getVMutationsWithoutNDN(), VScoring) + maxScore(second.getJMutationsWithoutNDN(), JScoring);
//        double maxScoreForVJ = Math.max(maxScoreForFirstVJ, maxScoreForSecondVJ);
//        double maxScoreForNDN = Math.max(maxScore(first.getKnownNDN(), NDNScoring), maxScore(second.getKnownNDN(), NDNScoring));
//
//        // TODO maybe (maxScore - score) / length
//        double normalizedDistanceFromFirstToGermline = 1 - (score(first.getVMutationsWithoutNDN(), VScoring) + score(first.getJMutationsWithoutNDN(), JScoring)) / maxScoreForFirstVJ;
//        double normalizedDistanceFromSecondToGermline = 1 - (score(second.getVMutationsWithoutNDN(), VScoring) + score(second.getJMutationsWithoutNDN(), JScoring)) / maxScoreForSecondVJ;
//        double normalizedDistanceBetweenClones = 1 - (VMutationsBetweenScore + JMutationsBetweenScore + CDR3MutationsBetweenScore) /
//                (maxScoreForVJ + maxScoreForNDN);
//        double normalizedDistanceBetweenClonesInNDN = 1 - (CDR3MutationsBetweenScore) / maxScoreForNDN;
//        double normalizedDistanceBetweenClonesWithoutNDN = 1 - (VMutationsBetweenScore + JMutationsBetweenScore) / (maxScoreForVJ + maxScoreForNDN);
//
//        return new FitnessFunctionParams(
//                normalizedDistanceBetweenClonesInNDN,
//                normalizedDistanceBetweenClones,
//                normalizedDistanceBetweenClonesWithoutNDN,
//                normalizedDistanceFromFirstToGermline,
//                normalizedDistanceFromSecondToGermline,
//                Math.min(normalizedDistanceFromFirstToGermline, normalizedDistanceFromSecondToGermline)
//        );
    }

    //TODO duplicated code
    private FitnessFunctionParams fitnessFunctionParams(MutationsDescription first, MutationsDescription second) {
        throw new UnsupportedOperationException();
//        MutationsWithRange CDR3MutationsBetween = mutationsBetween(first.getKnownNDN(), second.getKnownNDN());
//
//        List<MutationsWithRange> VMutationsBetween = mutationsBetween(first.getVMutationsWithoutNDN(), second.getVMutationsWithoutNDN());
//        double VMutationsBetweenScore = score(VMutationsBetween, VScoring);
//
//        List<MutationsWithRange> JMutationsBetween = mutationsBetween(first.getJMutationsWithoutNDN(), second.getJMutationsWithoutNDN());
//        double JMutationsBetweenScore = score(JMutationsBetween, JScoring);
//
//        double CDR3MutationsBetweenScore = score(CDR3MutationsBetween, NDNScoring);
//
//        double maxScoreForFirstVJ = maxScore(first.getVMutationsWithoutNDN(), VScoring) + maxScore(first.getJMutationsWithoutNDN(), JScoring);
//        double maxScoreForSecondVJ = maxScore(second.getVMutationsWithoutNDN(), VScoring) + maxScore(second.getJMutationsWithoutNDN(), JScoring);
//        double maxScoreForVJ = Math.max(maxScoreForFirstVJ, maxScoreForSecondVJ);
//        double maxScoreForCDR3 = Math.max(maxScore(first.getKnownNDN(), NDNScoring), maxScore(second.getKnownNDN(), NDNScoring));
//
//
//        // TODO maybe (maxScore - score) / length
//        double normalizedDistanceFromFirstToGermline = 1 - (score(first.getVMutationsWithoutNDN(), VScoring) + score(first.getJMutationsWithoutNDN(), JScoring)) / maxScoreForFirstVJ;
//        double normalizedDistanceFromSecondToGermline = 1 - (score(second.getVMutationsWithoutNDN(), VScoring) + score(second.getJMutationsWithoutNDN(), JScoring)) / maxScoreForSecondVJ;
//        double normalizedDistanceBetweenClones = 1 - (VMutationsBetweenScore + JMutationsBetweenScore + CDR3MutationsBetweenScore) /
//                (maxScoreForVJ + maxScoreForCDR3);
//        double normalizedDistanceBetweenClonesInNDN = 1 - (CDR3MutationsBetweenScore) / maxScoreForCDR3;
//        double normalizedDistanceBetweenClonesWithoutNDN = 1 - (VMutationsBetweenScore + JMutationsBetweenScore) / (maxScoreForVJ + maxScoreForCDR3);
//
//        return new FitnessFunctionParams(
//                normalizedDistanceBetweenClonesInNDN,
//                normalizedDistanceBetweenClones,
//                normalizedDistanceBetweenClonesWithoutNDN,
//                normalizedDistanceFromFirstToGermline,
//                normalizedDistanceFromSecondToGermline,
//                Math.min(normalizedDistanceFromFirstToGermline, normalizedDistanceFromSecondToGermline)
//        );
    }

    private double fitnessFunction(FitnessFunctionParams params) {
        //TODO move constants to params
        return Math.pow(params.distanceBetweenClonesWithoutNDN, 1.0) +
                4 * Math.pow(params.distanceBetweenClonesInNDN, 1.0) * Math.pow(params.minDistanceToGermline - 1, 6.0);
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
        //TODO modify score to get the same result
        return sequence.size() * scoring.getMaximalMatchScore();
//        return AlignmentUtils.calculateScore(
//                sequence,
//                EMPTY_NUCLEOTIDE_MUTATIONS,
//                scoring
//        );
    }

    private static class TreeWithMetaBuilder {
        private final TreeBuilderByAncestors<CloneWithMutationsFromReconstructedRoot, SyntheticNode, MutationsDescription> treeBuilder;
        private final RootInfo rootInfo;

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
