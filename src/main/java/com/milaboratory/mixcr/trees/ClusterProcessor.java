package com.milaboratory.mixcr.trees;

import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.alignment.AlignmentScoring;
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
        ancestorInfoBuilder = new AncestorInfoBuilder(getVRelativePosition, getJRelativePosition);
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
                                            new Range(VRangeInCDR3.getUpper(), VSequence1.size())
                                    ),
                                    getVMutationsWithinNDN(cloneWrapper.clone, VRangeInCDR3.getUpper()),
                                    VRangeInCDR3,
                                    JRangeInCDR3,
                                    CDR3.getRange(VRangeInCDR3.length(), CDR3.size() - JRangeInCDR3.length()),
                                    getMutationsWithoutRange(
                                            cloneWrapper.clone,
                                            Joining,
                                            new Range(0, JRangeInCDR3.getLower())
                                    ),
                                    getJMutationsWithinNDN(cloneWrapper.clone, JRangeInCDR3.getLower())
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
                                node.map(CloneWithMutationsFromReconstructedRoot::getClone, ancestorInfoBuilder::buildAncestorInfo)
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
        return commonMutationsCount(first.getVMutationsWithoutNDN(), second.getVMutationsWithoutNDN()) +
                commonMutationsCount(first.getJMutationsWithoutNDN(), second.getJMutationsWithoutNDN());
    }

    private int commonMutationsCount(List<MutationsWithRange> first, List<MutationsWithRange> second) {
        return foldByIntersection(
                first, second,
                (a, b, range) -> MutationsUtils.intersection(a.getCombinedMutations(), b.getCombinedMutations(), range.intersection, range.includeLastMutations).size()
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

        MutationsDescription root = buildARootForATree(rootInfo, rebasedCluster);

        TreeBuilderByAncestors<CloneWithMutationsFromReconstructedRoot, MutationsDescription, MutationsDescription> treeBuilderByAncestors = new TreeBuilderByAncestors<>(
                root,
                this::distance,
                this::mutationsBetween,
                this::combineWith,
                CloneWithMutationsFromReconstructedRoot::getMutationsFromRoot,
                this::commonMutations
        );

        XmlTreePrinter<ObservedOrReconstructed<CloneWithMutationsFromReconstructedRoot, MutationsDescription>> printer = new XmlTreePrinter<>(
                node -> node.getContent().convert(
                        clone -> String.valueOf(clone.getClone().clone.getId()),
                        mutations -> "NDN: " + mutations.getKnownNDN().getFromParentToThis().extractRelativeMutationsForRange(mutations.getKnownNDN().getSequence1Range())
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

    private MutationsDescription buildARootForATree(RootInfo rootInfo, List<CloneWithMutationsFromReconstructedRoot> rebasedCluster) {
        return new MutationsDescription(
                overlap(rebasedCluster.stream()
                        .map(it -> it.getMutationsFromRoot().getVMutationsWithoutNDN().stream()
                                .map(MutationsWithRange::getSequence1Range)
                                .collect(Collectors.toList())
                        ).collect(Collectors.toList())
                ).stream()
                        .map(it -> new MutationsWithRange(VSequence1, EMPTY_NUCLEOTIDE_MUTATIONS, EMPTY_NUCLEOTIDE_MUTATIONS, it, true, false))
                        .collect(Collectors.toList()),
                new MutationsWithRange(
                        rootInfo.getReconstructedNDN(),
                        EMPTY_NUCLEOTIDE_MUTATIONS,
                        EMPTY_NUCLEOTIDE_MUTATIONS,
                        new Range(0, rootInfo.getReconstructedNDN().size()),
                        true, true),
                overlap(rebasedCluster.stream()
                        .map(it -> it.getMutationsFromRoot().getJMutationsWithoutNDN().stream()
                                .map(MutationsWithRange::getSequence1Range)
                                .collect(Collectors.toList())
                        ).collect(Collectors.toList())
                ).stream()
                        .map(it -> new MutationsWithRange(JSequence1, EMPTY_NUCLEOTIDE_MUTATIONS, EMPTY_NUCLEOTIDE_MUTATIONS, it, true, false))
                        .collect(Collectors.toList())
        );
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
                                    Mutations.EMPTY_NUCLEOTIDE_MUTATIONS,
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
        double VPenalties = maxScore(mutations.getVMutationsWithoutNDN(), VScoring) - score(mutations.getVMutationsWithoutNDN(), VScoring);
        double VLength = mutations.getVMutationsWithoutNDN().stream().mapToDouble(it -> it.getSequence1Range().length()).sum();

        double JPenalties = maxScore(mutations.getJMutationsWithoutNDN(), JScoring) - score(mutations.getJMutationsWithoutNDN(), JScoring);
        double JLength = mutations.getJMutationsWithoutNDN().stream().mapToDouble(it -> it.getSequence1Range().length()).sum();

        int NDNPenalties = maxScore(mutations.getKnownNDN(), NDNScoring) - score(mutations.getKnownNDN(), NDNScoring);
        double NDNLength = mutations.getKnownNDN().getSequence1Range().length();

//        return BigDecimal.valueOf(NDNPenalties / NDNLength + (VPenalties + JPenalties) / (VLength + JLength));
        return BigDecimal.valueOf((NDNPenalties + VPenalties + JPenalties) / (NDNLength + VLength + JLength));
    }

    private MutationsDescription mutationsBetween(MutationsDescription first, MutationsDescription second) {
        return new MutationsDescription(
                mutationsBetween(first.getVMutationsWithoutNDN(), second.getVMutationsWithoutNDN()),
                mutationsBetween(first.getKnownNDN(), second.getKnownNDN()),
                mutationsBetween(first.getJMutationsWithoutNDN(), second.getJMutationsWithoutNDN())
        );
    }

    private MutationsDescription combineWith(MutationsDescription first, MutationsDescription second) {
        return new MutationsDescription(
                combineWith(first.getVMutationsWithoutNDN(), second.getVMutationsWithoutNDN()),
                combineWith(first.getKnownNDN(), second.getKnownNDN()),
                combineWith(first.getJMutationsWithoutNDN(), second.getJMutationsWithoutNDN())
        );
    }

    private MutationsDescription commonMutations(MutationsDescription first, MutationsDescription second) {
        return new MutationsDescription(
                intersection(first.getVMutationsWithoutNDN(), second.getVMutationsWithoutNDN()),
                new MutationsWithRange(
                        first.getKnownNDN().getSequence1(),
                        first.getKnownNDN().getFromBaseToParent(),
                        MutationsUtils.findNDNCommonAncestor(
                                first.getKnownNDN().getFromParentToThis(),
                                second.getKnownNDN().getFromParentToThis()
                        ),
                        first.getKnownNDN().getSequence1Range(),
                        true,
                        true
                ),
                intersection(first.getJMutationsWithoutNDN(), second.getJMutationsWithoutNDN())
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
                base.getSequence1(),
                base.getCombinedMutations(),
                MutationsUtils.difference(
                        base.getCombinedMutations(),
                        comparison.getCombinedMutations()
                ),
                intersection.intersection,
                intersection.includeFirstMutations,
                intersection.includeLastMutations
        );
    }

    private static MutationsWithRange mutationsBetween(MutationsWithRange base, MutationsWithRange comparison) {
        checkSameRange(base, comparison);
        return new MutationsWithRange(
                base.getSequence1(),
                base.getCombinedMutations(),
                MutationsUtils.difference(
                        base.getCombinedMutations(),
                        comparison.getCombinedMutations()
                ),
                base.getSequence1Range(),
                base.isIncludeFirstMutations(),
                base.isIncludeLastMutations()
        );
    }

    private static MutationsWithRange combineWith(MutationsWithRange base, MutationsWithRange comparison, RangeInfo intersection) {
        return new MutationsWithRange(
                base.getSequence1(),
                base.getCombinedMutations(),
                base.getCombinedMutations().invert()
                        .combineWith(comparison.getCombinedMutations()),
                intersection.intersection,
                intersection.includeFirstMutations,
                intersection.includeLastMutations
        );
    }

    private static MutationsWithRange combineWith(MutationsWithRange base, MutationsWithRange comparison) {
        checkSameRange(base, comparison);
        return new MutationsWithRange(
                base.getSequence1(),
                base.getCombinedMutations(),
                base.getCombinedMutations().invert()
                        .combineWith(comparison.getCombinedMutations()),
                base.getSequence1Range(),
                base.isIncludeFirstMutations(),
                base.isIncludeLastMutations()
        );
    }

    private static MutationsWithRange intersection(MutationsWithRange base, MutationsWithRange comparison, RangeInfo intersection) {
        return new MutationsWithRange(
                base.getSequence1(),
                base.getFromBaseToParent(),
                MutationsUtils.intersection(
                        base.getFromParentToThis(),
                        comparison.getFromParentToThis(),
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
        MutationsWithRange CDR3MutationsBetween = clonesRebase.NDNMutations(first.getKnownNDN(), second.getKnownNDN());

        List<MutationsWithRange> VMutationsBetween = mutationsBetween(first.getVMutationsWithoutNDN(), second.getVMutationsWithoutNDN());
        double VMutationsBetweenScore = score(VMutationsBetween, VScoring);

        List<MutationsWithRange> JMutationsBetween = mutationsBetween(first.getJMutationsWithoutNDN(), second.getJMutationsWithoutNDN());
        double JMutationsBetweenScore = score(JMutationsBetween, JScoring);

        double CDR3MutationsBetweenScore = score(CDR3MutationsBetween, NDNScoring);

        double maxScoreForFirstVJ = maxScore(first.getVMutationsWithoutNDN(), VScoring) + maxScore(first.getJMutationsWithoutNDN(), JScoring);
        double maxScoreForSecondVJ = maxScore(second.getVMutationsWithoutNDN(), VScoring) + maxScore(second.getJMutationsWithoutNDN(), JScoring);
        double maxScoreForVJ = Math.max(maxScoreForFirstVJ, maxScoreForSecondVJ);
        double maxScoreForNDN = Math.max(maxScore(first.getKnownNDN(), NDNScoring), maxScore(second.getKnownNDN(), NDNScoring));

        // TODO maybe (maxScore - score) / length
        double normalizedDistanceFromFirstToGermline = 1 - (score(first.getVMutationsWithoutNDN(), VScoring) + score(first.getJMutationsWithoutNDN(), JScoring)) / maxScoreForFirstVJ;
        double normalizedDistanceFromSecondToGermline = 1 - (score(second.getVMutationsWithoutNDN(), VScoring) + score(second.getJMutationsWithoutNDN(), JScoring)) / maxScoreForSecondVJ;
        double normalizedDistanceBetweenClones = 1 - (VMutationsBetweenScore + JMutationsBetweenScore + CDR3MutationsBetweenScore) /
                (maxScoreForVJ + maxScoreForNDN);
        double normalizedDistanceBetweenClonesInNDN = 1 - (CDR3MutationsBetweenScore) / maxScoreForNDN;
        double normalizedDistanceBetweenClonesWithoutNDN = 1 - (VMutationsBetweenScore + JMutationsBetweenScore) / (maxScoreForVJ + maxScoreForNDN);

        return new FitnessFunctionParams(
                normalizedDistanceBetweenClonesInNDN,
                normalizedDistanceBetweenClones,
                normalizedDistanceBetweenClonesWithoutNDN,
                normalizedDistanceFromFirstToGermline,
                normalizedDistanceFromSecondToGermline,
                Math.min(normalizedDistanceFromFirstToGermline, normalizedDistanceFromSecondToGermline)
        );
    }

    //TODO duplicated code
    private FitnessFunctionParams fitnessFunctionParams(MutationsDescription first, MutationsDescription second) {
        MutationsWithRange CDR3MutationsBetween = mutationsBetween(first.getKnownNDN(), second.getKnownNDN());

        List<MutationsWithRange> VMutationsBetween = mutationsBetween(first.getVMutationsWithoutNDN(), second.getVMutationsWithoutNDN());
        double VMutationsBetweenScore = score(VMutationsBetween, VScoring);

        List<MutationsWithRange> JMutationsBetween = mutationsBetween(first.getJMutationsWithoutNDN(), second.getJMutationsWithoutNDN());
        double JMutationsBetweenScore = score(JMutationsBetween, JScoring);

        double CDR3MutationsBetweenScore = score(CDR3MutationsBetween, NDNScoring);

        double maxScoreForFirstVJ = maxScore(first.getVMutationsWithoutNDN(), VScoring) + maxScore(first.getJMutationsWithoutNDN(), JScoring);
        double maxScoreForSecondVJ = maxScore(second.getVMutationsWithoutNDN(), VScoring) + maxScore(second.getJMutationsWithoutNDN(), JScoring);
        double maxScoreForVJ = Math.max(maxScoreForFirstVJ, maxScoreForSecondVJ);
        double maxScoreForCDR3 = Math.max(maxScore(first.getKnownNDN(), NDNScoring), maxScore(second.getKnownNDN(), NDNScoring));


        // TODO maybe (maxScore - score) / length
        double normalizedDistanceFromFirstToGermline = 1 - (score(first.getVMutationsWithoutNDN(), VScoring) + score(first.getJMutationsWithoutNDN(), JScoring)) / maxScoreForFirstVJ;
        double normalizedDistanceFromSecondToGermline = 1 - (score(second.getVMutationsWithoutNDN(), VScoring) + score(second.getJMutationsWithoutNDN(), JScoring)) / maxScoreForSecondVJ;
        double normalizedDistanceBetweenClones = 1 - (VMutationsBetweenScore + JMutationsBetweenScore + CDR3MutationsBetweenScore) /
                (maxScoreForVJ + maxScoreForCDR3);
        double normalizedDistanceBetweenClonesInNDN = 1 - (CDR3MutationsBetweenScore) / maxScoreForCDR3;
        double normalizedDistanceBetweenClonesWithoutNDN = 1 - (VMutationsBetweenScore + JMutationsBetweenScore) / (maxScoreForVJ + maxScoreForCDR3);

        return new FitnessFunctionParams(
                normalizedDistanceBetweenClonesInNDN,
                normalizedDistanceBetweenClones,
                normalizedDistanceBetweenClonesWithoutNDN,
                normalizedDistanceFromFirstToGermline,
                normalizedDistanceFromSecondToGermline,
                Math.min(normalizedDistanceFromFirstToGermline, normalizedDistanceFromSecondToGermline)
        );
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
                mutations.buildParent(),
                mutations.getFromParentToThis().extractRelativeMutationsForRange(
                        MutationsUtils.projectRange(
                                mutations.getFromBaseToParent(),
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
        return maxScore(mutations.buildParent(), scoring);
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
        private final TreeBuilderByAncestors<CloneWithMutationsFromReconstructedRoot, MutationsDescription, MutationsDescription> treeBuilder;
        private final RootInfo rootInfo;

        public TreeWithMetaBuilder(
                TreeBuilderByAncestors<CloneWithMutationsFromReconstructedRoot, MutationsDescription, MutationsDescription> treeBuilder,
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
