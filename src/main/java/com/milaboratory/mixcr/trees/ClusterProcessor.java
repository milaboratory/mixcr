package com.milaboratory.mixcr.trees;

import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.alignment.AlignmentScoring;
import com.milaboratory.core.alignment.AlignmentUtils;
import com.milaboratory.core.mutations.Mutation;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideAlphabet;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequenceBuilder;
import com.milaboratory.core.sequence.Wildcard;
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
import static com.milaboratory.mixcr.trees.ClonesRebase.NDNRangeInKnownNDN;
import static io.repseq.core.GeneType.Joining;
import static io.repseq.core.GeneType.Variable;
import static io.repseq.core.ReferencePoint.*;

class ClusterProcessor {
    private final double threshold;
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


    ClusterProcessor(
            SHMTreeBuilderParameters parameters,
            AlignmentScoring<NucleotideSequence> VScoring,
            AlignmentScoring<NucleotideSequence> JScoring,
            Cluster<CloneWrapper> originalCluster
    ) {
        this.threshold = parameters.maxDistanceWithinCluster;
        this.hideTreesLessThanSize = parameters.hideTreesLessThanSize;
        this.commonMutationsCountForClustering = parameters.commonMutationsCountForClustering;
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
                                            VRangeInCDR3,
                                            false,
                                            true
                                    ),
                                    getVMutationsWithinNDN(cloneWrapper.clone, VRangeInCDR3.getUpper()),
                                    CDR3.getRange(VRangeInCDR3.length(), CDR3.size() - JRangeInCDR3.length()),
                                    getJMutationsWithinNDN(cloneWrapper.clone, JRangeInCDR3.getLower()),
                                    new MutationsWithRange(
                                            JSequence1,
                                            getMutationsForRange(cloneWrapper.clone, JRangeInCDR3, Joining),
                                            JRangeInCDR3,
                                            true,
                                            false
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

//        //try to add as nodes clones that wasn't picked up by clustering
//        for (CloneWithMutationsFromVJGermline clone : clusteringResult.clonesNotInClusters) {
//            Optional<Pair<Runnable, Double>> nearestTree = firstStepTrees.stream()
//                    .map(treeWithMeta -> {
//                        CloneWithMutationsFromReconstructedRoot rebasedClone = clonesRebase.rebaseClone(treeWithMeta.rootInfo, clone.mutations, clone.cloneWrapper);
//                        return Pair.<Runnable, Double>create(
//                                () -> treeWithMeta.treeBuilder.addNode(rebasedClone),
//                                distanceFromTree(rebasedClone.getMutationsFromRoot(), treeWithMeta.treeBuilder.getTree())
//                        );
//                    })
//                    .max(Comparator.comparing(Pair::getSecond));
//
//            if (nearestTree.isPresent() && nearestTree.get().getSecond() < threshold) {
//                nearestTree.get().getFirst().run();
//            }
//        }
//
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
        List<Cluster.Builder<CloneWithMutationsFromVJGermline>> clusteredClones = new ArrayList<>();

        for (int i = clones.size() - 1; i >= 0; i--) {
            CloneWithMutationsFromVJGermline cloneDescriptor = clones.get(i);
            if (cloneDescriptor.mutations.getVJMutationsCount() < commonMutationsCountForClustering) {
                continue;
            }
            Optional<Pair<Cluster.Builder<CloneWithMutationsFromVJGermline>, Integer>> nearestCluster = clusteredClones.stream()
                    .flatMap(cluster -> cluster.getCurrentCluster().stream()
                            .map(compareTo -> Pair.create(
                                    cluster,
                                    commonMutationsCount(cloneDescriptor.mutations, compareTo.mutations))
                            )
                    )
                    .max(Comparator.comparing(Pair::getSecond));

            if (nearestCluster.isPresent() && nearestCluster.get().getSecond() > commonMutationsCountForClustering) {
                nearestCluster.get().getFirst().add(cloneDescriptor);
            } else {
                Cluster.Builder<CloneWithMutationsFromVJGermline> builder = new Cluster.Builder<>();
                builder.add(cloneDescriptor);
                clusteredClones.add(builder);
            }
        }

        List<Cluster<CloneWithMutationsFromVJGermline>> clusters = new ArrayList<>();
        List<CloneWithMutationsFromVJGermline> clonesNotInClusters = new ArrayList<>();
        for (Cluster.Builder<CloneWithMutationsFromVJGermline> cloneWithMutationsFromVJGermlineBuilder : clusteredClones) {
            Cluster<CloneWithMutationsFromVJGermline> cluster = cloneWithMutationsFromVJGermlineBuilder.build();
            if (cluster.cluster.size() > 1) {
                clusters.add(cluster);
            } else {
                clonesNotInClusters.add(cluster.cluster.get(0));
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
                (a, b, range) -> MutationsUtils.intersection(a.getMutations(), b.getMutations(), range.intersection, range.includeLastMutations).size()
        ).stream().mapToInt(it -> it).sum();
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
                this::distance,
                this::mutationsBetween,
                this::combineWith,
                (parent, observed) -> {
                    SyntheticNode syntheticNode = new SyntheticNode(
                            parent.getFromRootToThis(),
                            mutationsBetween(parent.getFromRootToThis(), observed.getMutationsFromRoot())
                    );
                    AncestorInfo ancestorInfo = ancestorInfoBuilder.buildAncestorInfo(syntheticNode.getFromRootToThis());
                    //TODO remove
                    if (!observed.getClone().clone.getNFeature(GeneFeature.CDR3).equals(ancestorInfo.getSequence().getRange(ancestorInfo.getCDR3Begin(), ancestorInfo.getCDR3End()))) {
                        throw new IllegalStateException();
                    }
                    if (!observed.getClone().clone.getTarget(0).getSequence().equals(ancestorInfo.getSequence())) {
                        throw new IllegalStateException();
                    }
                    return syntheticNode;
                },
                this::commonMutations
        );

        XmlTreePrinter<ObservedOrReconstructed<CloneWithMutationsFromReconstructedRoot, SyntheticNode>> printer = new XmlTreePrinter<>(
                node -> node.getContent().convert(
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
                                    " NDN: " + mutations.getFromParentToThis().getKnownNDN().getMutations() +
                                    " widening: " + widening;
                        }
                )
        );
        for (CloneWithMutationsFromReconstructedRoot cloneWithMutationsFromReconstructedRoot : rebasedCluster) {
            treeBuilderByAncestors.addNode(cloneWithMutationsFromReconstructedRoot);
            System.out.println(printer.print(treeBuilderByAncestors.getTree()));
        }
        System.out.println();
        return new TreeWithMetaBuilder(
                treeBuilderByAncestors,
                rootInfo
        );
    }

    private SyntheticNode buildARootForATree(RootInfo rootInfo, List<CloneWithMutationsFromReconstructedRoot> rebasedCluster) {
        MutationsDescription emptyMutations = new MutationsDescription(
                overlap(rebasedCluster.stream()
                        .map(it -> it.getMutationsFromRoot().getVMutationsWithoutCDR3().stream()
                                .map(MutationsWithRange::getSequence1Range)
                                .collect(Collectors.toList())
                        ).collect(Collectors.toList())
                ).stream()
                        .map(it -> new MutationsWithRange(VSequence1, EMPTY_NUCLEOTIDE_MUTATIONS, it, true, false))
                        .collect(Collectors.toList()),
                new MutationsWithRange(
                        VSequence1,
                        EMPTY_NUCLEOTIDE_MUTATIONS,
                        rootInfo.getVRangeInCDR3(),
                        false, true),
                new MutationsWithRange(
                        rootInfo.getReconstructedNDN(),
                        EMPTY_NUCLEOTIDE_MUTATIONS,
                        new Range(0, rootInfo.getReconstructedNDN().size()),
                        true, true),
                new MutationsWithRange(
                        JSequence1,
                        EMPTY_NUCLEOTIDE_MUTATIONS,
                        rootInfo.getJRangeInCDR3(),
                        true, false),
                overlap(rebasedCluster.stream()
                        .map(it -> it.getMutationsFromRoot().getJMutationsWithoutCDR3().stream()
                                .map(MutationsWithRange::getSequence1Range)
                                .collect(Collectors.toList())
                        ).collect(Collectors.toList())
                ).stream()
                        .map(it -> new MutationsWithRange(JSequence1, EMPTY_NUCLEOTIDE_MUTATIONS, it, true, false))
                        .collect(Collectors.toList())
        );
        return new SyntheticNode(emptyMutations, emptyMutations);
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
                                    range,
                                    true,
                                    true
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

    private BigDecimal distance(MutationsDescription mutations) {
        double VPenalties = maxScore(mutations.getVMutationsWithoutCDR3(), VScoring) - score(mutations.getVMutationsWithoutCDR3(), VScoring) +
                maxScore(mutations.getVMutationsInCDR3WithoutNDN(), VScoring) - score(mutations.getVMutationsInCDR3WithoutNDN(), VScoring);
        double VLength = mutations.getVMutationsWithoutCDR3().stream().mapToDouble(it -> it.getSequence1Range().length()).sum() +
                mutations.getVMutationsInCDR3WithoutNDN().getSequence1Range().length();

        double JPenalties = maxScore(mutations.getJMutationsWithoutCDR3(), JScoring) - score(mutations.getJMutationsWithoutCDR3(), JScoring) +
                maxScore(mutations.getJMutationsInCDR3WithoutNDN(), JScoring) - score(mutations.getJMutationsInCDR3WithoutNDN(), JScoring);
        double JLength = mutations.getJMutationsWithoutCDR3().stream().mapToDouble(it -> it.getSequence1Range().length()).sum() +
                mutations.getJMutationsInCDR3WithoutNDN().getSequence1Range().length();

        int NDNPenalties = maxScore(mutations.getKnownNDN(), NDNScoring) - score(mutations.getKnownNDN(), NDNScoring);
        double NDNLength = mutations.getKnownNDN().getSequence1Range().length();

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
                        first.getKnownNDN().getSequence1Range(),
                        true,
                        true
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

    private List<MutationsWithRange> combineWith(List<MutationsWithRange> firstMutations, List<MutationsWithRange> secondMutations) {
        return foldByIntersection(firstMutations, secondMutations, ClusterProcessor::combineWith);
    }

    private static MutationsWithRange intersection(MutationsWithRange from, MutationsWithRange to) {
        return ClusterProcessor.intersection(from, to, simpleIntersection(from, to));
    }

    private static RangeInfo simpleIntersection(MutationsWithRange from, MutationsWithRange to) {
        if (!from.getSequence1Range().equals(to.getSequence1Range()) || from.isIncludeFirstMutations() != to.isIncludeFirstMutations() || from.isIncludeLastMutations() != to.isIncludeLastMutations()) {
            throw new IllegalArgumentException();
        }
        return new RangeInfo(from.getSequence1Range(), from.isIncludeFirstMutations(), from.isIncludeLastMutations());
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
                .flatMap(base -> secondMutations.stream().flatMap(comparison -> {
                    Range intersection = base.getSequence1Range().intersection(comparison.getSequence1Range());
                    if (intersection != null) {
                        boolean includeFirstMutations;
                        if (intersection.getLower() == base.getSequence1Range().getLower()) {
                            includeFirstMutations = base.isIncludeFirstMutations();
                        } else if (intersection.getLower() == comparison.getSequence1Range().getLower()) {
                            includeFirstMutations = comparison.isIncludeFirstMutations();
                        } else {
                            includeFirstMutations = true;
                        }
                        boolean includeLastMutations;
                        if (intersection.getUpper() == base.getSequence1Range().getUpper()) {
                            includeLastMutations = base.isIncludeLastMutations();
                        } else if (intersection.getUpper() == comparison.getSequence1Range().getUpper()) {
                            includeLastMutations = comparison.isIncludeLastMutations();
                        } else {
                            includeLastMutations = true;
                        }
                        RangeInfo rangeInfo = new RangeInfo(
                                intersection,
                                includeFirstMutations,
                                includeLastMutations
                        );
                        return Stream.of(folder.apply(base, comparison, rangeInfo));
                    } else {
                        return Stream.empty();
                    }
                }))
                .collect(Collectors.toList());
    }

    private static MutationsWithRange mutationsBetween(MutationsWithRange base, MutationsWithRange comparison, RangeInfo intersection) {
        return new MutationsWithRange(
                base.getMutations().mutate(base.getSequence1()),
                MutationsUtils.difference(
                        base.getMutations(),
                        comparison.getMutations()
                ),
                intersection.intersection,
                intersection.includeFirstMutations,
                intersection.includeLastMutations
        );
    }

    private static MutationsWithRange mutationsBetween(MutationsWithRange base, MutationsWithRange comparison) {
        checkSameRange(base, comparison);
        return new MutationsWithRange(
                base.getMutations().mutate(base.getSequence1()),
                MutationsUtils.difference(
                        base.getMutations(),
                        comparison.getMutations()
                ),
                base.getSequence1Range(),
                base.isIncludeFirstMutations(),
                base.isIncludeLastMutations()
        );
    }

    private static MutationsWithRange combineWith(MutationsWithRange base, MutationsWithRange comparison, RangeInfo intersection) {
        return new MutationsWithRange(
                base.getSequence1(),
                base.getMutations().invert()
                        .combineWith(comparison.getMutations()),
                intersection.intersection,
                intersection.includeFirstMutations,
                intersection.includeLastMutations
        );
    }

    private static MutationsWithRange combineWith(MutationsWithRange base, MutationsWithRange comparison) {
        checkSameRange(base, comparison);
        return new MutationsWithRange(
                base.getSequence1(),
                base.getMutations().invert()
                        .combineWith(comparison.getMutations()),
                base.getSequence1Range(),
                base.isIncludeFirstMutations(),
                base.isIncludeLastMutations()
        );
    }

    private static MutationsWithRange intersection(MutationsWithRange base, MutationsWithRange comparison, RangeInfo intersection) {
        return new MutationsWithRange(
                base.getSequence1(),
                MutationsUtils.intersection(
                        base.getMutations(),
                        comparison.getMutations(),
                        intersection.intersection,
                        intersection.includeLastMutations
                ),
                intersection.intersection,
                intersection.includeFirstMutations,
                intersection.includeLastMutations
        );
    }

    private static void checkSameRange(MutationsWithRange first, MutationsWithRange second) {
        if (!first.getSequence1Range().equals(second.getSequence1Range())) {
            throw new IllegalArgumentException();
        }
        if (first.isIncludeFirstMutations() != second.isIncludeFirstMutations()) {
            throw new IllegalArgumentException();
        }
        if (first.isIncludeLastMutations() != second.isIncludeLastMutations()) {
            throw new IllegalArgumentException();
        }
    }

    private double distanceFromTree(MutationsDescription mutations, Tree<ObservedOrReconstructed<CloneWithMutationsFromReconstructedRoot, MutationsDescription>> tree) {
        return tree.allNodes()
                .map(node -> node.getContent().convert(CloneWithMutationsFromReconstructedRoot::getMutationsFromRoot, it -> it))
                .mapToDouble(compareTo -> fitnessFunction(fitnessFunctionParams(mutations, compareTo)))
                .min()
                .orElseThrow(IllegalArgumentException::new);
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
                mutations.getMutations().extractRelativeMutationsForRange(
                        MutationsUtils.projectRange(
                                mutations.getMutations(),
                                mutations.getSequence1Range(),
                                mutations.isIncludeFirstMutations(),
                                mutations.isIncludeLastMutations()
                        )
                ),
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

    private static class RangeInfo {
        private final Range intersection;
        private final boolean includeFirstMutations;
        private final boolean includeLastMutations;

        private RangeInfo(Range intersection, boolean includeFirstMutations, boolean includeLastMutations) {
            this.intersection = intersection;
            this.includeFirstMutations = includeFirstMutations;
            this.includeLastMutations = includeLastMutations;
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
