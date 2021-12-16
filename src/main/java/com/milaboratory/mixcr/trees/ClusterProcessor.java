package com.milaboratory.mixcr.trees;

import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.AffineGapAlignmentScoring;
import com.milaboratory.core.alignment.Aligner;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.alignment.AlignmentUtils;
import com.milaboratory.core.mutations.Mutation;
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
import static com.milaboratory.mixcr.trees.ClusteringCriteria.score;
import static io.repseq.core.GeneType.Joining;
import static io.repseq.core.GeneType.Variable;
import static io.repseq.core.ReferencePoint.*;

class ClusterProcessor {
    private final double threshold;

    private final NucleotideSequence VSequence1;
    private final Function<ReferencePoint, Integer> getVRelativePosition;

    private final NucleotideSequence JSequence1;
    private final Function<ReferencePoint, Integer> getJRelativePosition;

    private final Cluster<CloneWrapper> originalCluster;


    ClusterProcessor(SHMTreeBuilderParameters parameters, Cluster<CloneWrapper> originalCluster) {
        this.threshold = parameters.maxDistanceWithinCluster;
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

        ClusteringResult clusteringResult = clusterByDistance(clones);
        List<TreeWithMeta> firstStepTrees = clusteringResult.clusters
                .stream()
                .filter(it -> it.cluster.size() > 1)
                .map(this::buildATree)
                .sorted(Comparator.<TreeWithMeta>comparingInt(it -> it.treeBuilder.getNodesCount()).reversed())
                .collect(Collectors.toList());

        //try to add as nodes clones that wasn't picked up by clustering
        for (CloneWithMutationsFromVJGermline clone : clusteringResult.clonesNotInClusters) {
            Optional<Pair<Runnable, Double>> nearestTree = firstStepTrees.stream()
                    .map(treeWithMeta -> {
                        CloneWithMutationsFromReconstructedRoot rebasedClone = rebaseClone(clone, treeWithMeta.rootInfo);
                        return Pair.<Runnable, Double>create(
                                () -> treeWithMeta.treeBuilder.addNode(rebasedClone),
                                distanceFromTree(rebasedClone.mutations, treeWithMeta.treeBuilder.getTree())
                        );
                    })
                    .min(Comparator.comparing(Pair::getSecond));

            if (nearestTree.isPresent() && nearestTree.get().getSecond() < threshold) {
                nearestTree.get().getFirst().run();
            }
        }

        List<TreeWithMeta> secondStepTrees = new ArrayList<>();

        //trying to grow the biggest trees first
        while (!firstStepTrees.isEmpty()) {
            TreeWithMeta treeToGrow = firstStepTrees.get(0);
            firstStepTrees.remove(0);

            //trying to add the smallest trees first
            for (int i = firstStepTrees.size() - 1; i >= 0; i--) {
                TreeWithMeta treeToAttach = firstStepTrees.get(i);

                double distanceFromTree = treeToAttach.treeBuilder.getTree().allNodes()
                        .map(node -> node.getContent().convert(it -> Optional.<MutationsDescription>empty(), Optional::of))
                        .flatMap(Java9Util::stream)
                        .map(it -> rebaseMutations(it, treeToAttach.rootInfo, treeToGrow.rootInfo))
                        .mapToDouble(it -> distanceFromTree(it, treeToGrow.treeBuilder.getTree()))
                        .min().orElseThrow(IllegalArgumentException::new);

                if (distanceFromTree < threshold) {
                    treeToAttach.treeBuilder.getTree().allNodes()
                            .map(node -> node.getContent().convert(it -> Optional.of(it.original), it -> Optional.<CloneWithMutationsFromVJGermline>empty()))
                            .flatMap(Java9Util::stream)
                            .map(clone -> rebaseClone(clone, treeToGrow.rootInfo))
                            .sorted(Comparator.comparing(clone -> distance(clone.mutations)))
                            .forEach(treeToGrow.treeBuilder::addNode);
                    firstStepTrees.remove(i);
                }
            }

            secondStepTrees.add(treeToGrow);
        }

        return secondStepTrees.stream()
                .map(treeWithMeta -> treeWithMeta.treeBuilder.getTree().map(node ->
                        node.map(it -> it.original.cloneWrapper, this::buildAncestorInfo)
                ))
                .collect(Collectors.toList());
    }

    //TODO test with randomized test
    private MutationsDescription rebaseMutations(
            MutationsDescription originalRoot,
            RootInfo originalRootInfo,
            RootInfo rebaseTo
    ) {
        NucleotideSequence originalKnownNDN = buildSequence(originalRoot.knownNDN);
        List<MutationsWithRange> VMutationsWithoutNDN;
        if (originalRootInfo.VRangeInCDR3.length() < rebaseTo.VRangeInCDR3.length()) {
            VMutationsWithoutNDN = new ArrayList<>(originalRoot.VMutationsWithoutNDN);
            Range difference = new Range(originalRootInfo.VRangeInCDR3.getUpper(), rebaseTo.VRangeInCDR3.getUpper());

            Mutations<NucleotideSequence> absoluteMutations = Aligner.alignGlobal(
                    AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                    VSequence1,
                    originalKnownNDN,
                    difference.getLower(),
                    difference.length(),
                    0,
                    difference.length()
            ).getAbsoluteMutations();
            VMutationsWithoutNDN.add(new MutationsWithRange(
                    VSequence1,
                    EMPTY_NUCLEOTIDE_MUTATIONS,
                    absoluteMutations,
                    difference,
                    true
            ));
        } else if (originalRootInfo.VRangeInCDR3.length() == rebaseTo.VRangeInCDR3.length()) {
            VMutationsWithoutNDN = originalRoot.VMutationsWithoutNDN;
        } else {
            VMutationsWithoutNDN = originalRoot.VMutationsWithoutNDN.stream()
                    .flatMap(mutations -> {
                        Range intersection = mutations.getSequence1Range().intersection(new Range(0, rebaseTo.VRangeInCDR3.getUpper()));
                        if (intersection == null) {
                            return Stream.empty();
                        } else {
                            boolean includeLastInserts;
                            if (intersection.getUpper() == mutations.getSequence1Range().getUpper()) {
                                includeLastInserts = mutations.isIncludeLastInserts();
                            } else {
                                includeLastInserts = true;
                            }
                            return Stream.of(new MutationsWithRange(
                                    mutations.getSequence1(),
                                    mutations.getFromBaseToParent(),
                                    mutations.getFromParentToThis(),
                                    intersection,
                                    includeLastInserts
                            ));
                        }
                    })
                    .collect(Collectors.toList());
        }
        List<MutationsWithRange> JMutationsWithoutNDN;
        if (originalRootInfo.JRangeInCDR3.length() < rebaseTo.JRangeInCDR3.length()) {
            JMutationsWithoutNDN = new ArrayList<>(originalRoot.JMutationsWithoutNDN);
            Range difference = new Range(rebaseTo.JRangeInCDR3.getLower(), originalRootInfo.JRangeInCDR3.getLower());

            Mutations<NucleotideSequence> absoluteMutations = Aligner.alignGlobal(
                    AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                    JSequence1,
                    originalKnownNDN,
                    difference.getLower(),
                    difference.length(),
                    originalKnownNDN.size() - difference.length(),
                    difference.length()
            ).getAbsoluteMutations();
            JMutationsWithoutNDN.add(0, new MutationsWithRange(
                    JSequence1,
                    EMPTY_NUCLEOTIDE_MUTATIONS,
                    absoluteMutations,
                    difference,
                    true
            ));
        } else if (originalRootInfo.JRangeInCDR3.length() == rebaseTo.JRangeInCDR3.length()) {
            JMutationsWithoutNDN = originalRoot.JMutationsWithoutNDN;
        } else {
            JMutationsWithoutNDN = originalRoot.JMutationsWithoutNDN.stream()
                    .flatMap(mutations -> {
                        Range intersection = mutations.getSequence1Range().intersection(new Range(rebaseTo.JRangeInCDR3.getLower(), JSequence1.size()));
                        if (intersection == null) {
                            return Stream.empty();
                        } else {
                            boolean includeLastInserts;
                            if (intersection.getUpper() == mutations.getSequence1Range().getUpper()) {
                                includeLastInserts = mutations.isIncludeLastInserts();
                            } else {
                                includeLastInserts = true;
                            }
                            return Stream.of(new MutationsWithRange(
                                    mutations.getSequence1(),
                                    mutations.getFromBaseToParent(),
                                    mutations.getFromParentToThis(),
                                    intersection,
                                    includeLastInserts
                            ));
                        }
                    })
                    .collect(Collectors.toList());
        }

        SequenceBuilder<NucleotideSequence> knownNDNBuilder = NucleotideSequence.ALPHABET.createBuilder();
        if (originalRootInfo.VRangeInCDR3.length() > rebaseTo.VRangeInCDR3.length()) {
            Range rangeToAdd = new Range(rebaseTo.VRangeInCDR3.getUpper(), originalRootInfo.VRangeInCDR3.getUpper());
            originalRoot.VMutationsWithoutNDN.stream()
                    .map(mutations -> {
                        Range intersection = mutations.getSequence1Range().intersection(rangeToAdd);
                        if (intersection == null) {
                            return Optional.<NucleotideSequence>empty();
                        } else {
                            return Optional.of(buildSequence(
                                    new MutationsWithRange(
                                            mutations.getSequence1(),
                                            mutations.getFromBaseToParent(),
                                            mutations.getFromParentToThis(),
                                            intersection,
                                            true
                                    )
                            ));
                        }
                    })
                    .flatMap(Java9Util::stream)
                    .forEach(knownNDNBuilder::append);
        }

        knownNDNBuilder.append(originalKnownNDN.getRange(
                Math.max(0, rebaseTo.VRangeInCDR3.length() - originalRootInfo.VRangeInCDR3.length()),
                originalKnownNDN.size() - Math.max(0, rebaseTo.JRangeInCDR3.length() - originalRootInfo.JRangeInCDR3.length())
        ));

        if (originalRootInfo.JRangeInCDR3.length() > rebaseTo.JRangeInCDR3.length()) {
            Range rangeToAdd = new Range(originalRootInfo.JRangeInCDR3.getLower(), rebaseTo.JRangeInCDR3.getLower());
            originalRoot.JMutationsWithoutNDN.stream()
                    .map(mutations -> {
                        Range intersection = mutations.getSequence1Range().intersection(rangeToAdd);
                        if (intersection == null) {
                            return Optional.<NucleotideSequence>empty();
                        } else {
                            return Optional.of(buildSequence(
                                    new MutationsWithRange(
                                            mutations.getSequence1(),
                                            mutations.getFromBaseToParent(),
                                            mutations.getFromParentToThis(),
                                            intersection,
                                            true
                                    )
                            ));
                        }
                    })
                    .flatMap(Java9Util::stream)
                    .forEach(knownNDNBuilder::append);
        }

        NucleotideSequence rebasedKnownNDN = knownNDNBuilder
                .createAndDestroy();
        MutationsDescription result = new MutationsDescription(
                VMutationsWithoutNDN,
                mutations(rebaseTo.reconstructedNDN, rebasedKnownNDN),
                JMutationsWithoutNDN
        );
        //TODO remove after testing on more data
        if (result.VMutationsWithoutNDN.stream().mapToInt(it -> it.getSequence1Range().getUpper()).max().getAsInt() != rebaseTo.VRangeInCDR3.getUpper()) {
            throw new IllegalArgumentException();
        }
        if (result.JMutationsWithoutNDN.stream().mapToInt(it -> it.getSequence1Range().getLower()).min().getAsInt() != rebaseTo.JRangeInCDR3.getLower()) {
            throw new IllegalArgumentException();
        }
        AncestorInfo resultAncestorInfo = buildAncestorInfo(result);
        AncestorInfo originalAncestorInfo = buildAncestorInfo(originalRoot);
        if (!resultAncestorInfo.getSequence().equals(originalAncestorInfo.getSequence())) {
            throw new IllegalArgumentException();
        }
        if (!resultAncestorInfo.getSequence().getRange(resultAncestorInfo.getCDR3Begin(), resultAncestorInfo.getCDR3End())
                .equals(originalAncestorInfo.getSequence().getRange(originalAncestorInfo.getCDR3Begin(), originalAncestorInfo.getCDR3End()))) {
            throw new IllegalArgumentException();
        }
        return result;
    }

    /**
     * sort order of clones will be saved in ClusteringResult::clonesNotInClusters
     */
    private ClusteringResult clusterByDistance(List<CloneWithMutationsFromVJGermline> clones) {
        List<Cluster.Builder<CloneWithMutationsFromVJGermline>> clusteredClones = new ArrayList<>();

        for (CloneWithMutationsFromVJGermline cloneDescriptor : clones) {
            Optional<Pair<Cluster.Builder<CloneWithMutationsFromVJGermline>, Double>> nearestCluster = clusteredClones.stream()
                    .map(cluster -> Pair.create(cluster, distanceFromCluster(cloneDescriptor, cluster)))
                    .min(Comparator.comparing(Pair::getSecond));

            if (nearestCluster.isPresent() && nearestCluster.get().getSecond() < threshold) {
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

    private TreeWithMeta buildATree(Cluster<CloneWithMutationsFromVJGermline> cluster) {
        // Build a tree for every cluster
        // fix marks of VEnd and JBegin
        // determine part between VEnd and JBegin
        // resort by mutations count
        // build by next neighbor

        RootInfo rootInfo = buildRootInfo(cluster);

        List<CloneWithMutationsFromReconstructedRoot> rebasedCluster = cluster.cluster.stream()
                .map(clone -> rebaseClone(clone, rootInfo))
                .sorted(Comparator.comparing(cloneDescriptor -> distance(cloneDescriptor.mutations)))
                .collect(Collectors.toList());

        MutationsDescription root = buildARootForATree(rootInfo, rebasedCluster);

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
                treeBuilderByAncestors,
                rootInfo
        );
    }

    private MutationsDescription buildARootForATree(RootInfo rootInfo, List<CloneWithMutationsFromReconstructedRoot> rebasedCluster) {
        return new MutationsDescription(
                overlap(rebasedCluster.stream()
                        .map(it -> it.mutations.VMutationsWithoutNDN.stream()
                                .map(MutationsWithRange::getSequence1Range)
                                .collect(Collectors.toList())
                        ).collect(Collectors.toList())
                ).stream()
                        .map(it -> new MutationsWithRange(VSequence1, EMPTY_NUCLEOTIDE_MUTATIONS, EMPTY_NUCLEOTIDE_MUTATIONS, it, false))
                        .collect(Collectors.toList()),
                new MutationsWithRange(
                        rootInfo.reconstructedNDN,
                        EMPTY_NUCLEOTIDE_MUTATIONS,
                        EMPTY_NUCLEOTIDE_MUTATIONS,
                        new Range(0, rootInfo.reconstructedNDN.size()),
                        true),
                overlap(rebasedCluster.stream()
                        .map(it -> it.mutations.JMutationsWithoutNDN.stream()
                                .map(MutationsWithRange::getSequence1Range)
                                .collect(Collectors.toList())
                        ).collect(Collectors.toList())
                ).stream()
                        .map(it -> new MutationsWithRange(JSequence1, EMPTY_NUCLEOTIDE_MUTATIONS, EMPTY_NUCLEOTIDE_MUTATIONS, it, false))
                        .collect(Collectors.toList())
        );
    }

    //TODO check with randomized test
    private CloneWithMutationsFromReconstructedRoot rebaseClone(CloneWithMutationsFromVJGermline clone, RootInfo rootInfo) {
        Range NDNRangeInKnownNDN = NDNRangeInKnownNDN(clone.mutations, rootInfo.VRangeInCDR3, rootInfo.JRangeInCDR3);

        List<MutationsWithRange> VMutationsWithoutNDN = new ArrayList<>(clone.mutations.VMutationsWithoutNDN);
        Range VRange = new Range(clone.mutations.VRangeInCDR3.getLower(), rootInfo.VRangeInCDR3.getUpper());
        if (!VRange.isEmpty()) {
            Range VMutationsWithinNDNRange = clone.mutations.knownVMutationsWithinNDN.getSecond()
                    .intersection(VRange);
            VMutationsWithinNDNRange = VMutationsWithinNDNRange != null ? VMutationsWithinNDNRange
                    : new Range(clone.mutations.VRangeInCDR3.getUpper(), clone.mutations.VRangeInCDR3.getUpper());
            int lengthDelta = 0;
            if (!VMutationsWithinNDNRange.isEmpty()) {
                MutationsWithRange VMutationsToAdd = new MutationsWithRange(
                        VSequence1,
                        EMPTY_NUCLEOTIDE_MUTATIONS,
                        clone.mutations.knownVMutationsWithinNDN.getFirst(),
                        VMutationsWithinNDNRange,
                        true
                );
                VMutationsWithoutNDN.add(VMutationsToAdd);
                lengthDelta = lengthDelta(VMutationsToAdd);
            }
            Range rangeToAlign = new Range(VMutationsWithinNDNRange.getUpper(), rootInfo.VRangeInCDR3.getUpper() - lengthDelta);
            if (!rangeToAlign.isEmpty() && !rangeToAlign.isReverse()) {
                Mutations<NucleotideSequence> absoluteMutations = Aligner.alignGlobal(
                        AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                        VSequence1,
                        clone.mutations.knownNDN,
                        rangeToAlign.getLower(),
                        rangeToAlign.length(),
                        VMutationsWithinNDNRange.length() + lengthDelta,
                        rangeToAlign.length()
                ).getAbsoluteMutations();
                VMutationsWithoutNDN.add(new MutationsWithRange(
                        VSequence1,
                        EMPTY_NUCLEOTIDE_MUTATIONS,
                        absoluteMutations,
                        rangeToAlign,
                        true
                ));
            } else {
                NDNRangeInKnownNDN = new Range(NDNRangeInKnownNDN.getLower() - lengthDelta, NDNRangeInKnownNDN.getUpper());
            }
        }

        List<MutationsWithRange> JMutationsWithoutNDN = new ArrayList<>(clone.mutations.JMutationsWithoutNDN);
        Range JRange = new Range(rootInfo.JRangeInCDR3.getLower(), clone.mutations.JRangeInCDR3.getLower());
        if (!JRange.isEmpty()) {
            Range JMutationsWithinNDNRange = clone.mutations.knownJMutationsWithinNDN.getSecond()
                    .intersection(JRange);
            JMutationsWithinNDNRange = JMutationsWithinNDNRange != null ? JMutationsWithinNDNRange
                    : new Range(clone.mutations.JRangeInCDR3.getLower(), clone.mutations.JRangeInCDR3.getLower());
            int lengthDelta = 0;
            if (!JMutationsWithinNDNRange.isEmpty()) {
                MutationsWithRange JMutationsToAdd = new MutationsWithRange(
                        JSequence1,
                        EMPTY_NUCLEOTIDE_MUTATIONS,
                        clone.mutations.knownJMutationsWithinNDN.getFirst(),
                        JMutationsWithinNDNRange,
                        true
                );
                JMutationsWithoutNDN.add(0, JMutationsToAdd);

                lengthDelta = lengthDelta(JMutationsToAdd);
            }
            Range rangeToAlign = new Range(rootInfo.JRangeInCDR3.getLower() + lengthDelta, JMutationsWithinNDNRange.getLower());
            if (!rangeToAlign.isEmpty() && !rangeToAlign.isReverse()) {
                Mutations<NucleotideSequence> absoluteMutations = Aligner.alignGlobal(
                        AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                        JSequence1,
                        clone.mutations.knownNDN,
                        rangeToAlign.getLower(),
                        rangeToAlign.length(),
                        NDNRangeInKnownNDN.getUpper(),
                        rangeToAlign.length()
                ).getAbsoluteMutations();
                JMutationsWithoutNDN.add(0, new MutationsWithRange(
                        JSequence1,
                        EMPTY_NUCLEOTIDE_MUTATIONS,
                        absoluteMutations,
                        rangeToAlign,
                        true
                ));
            } else {
                NDNRangeInKnownNDN = new Range(NDNRangeInKnownNDN.getLower(), NDNRangeInKnownNDN.getUpper() - lengthDelta);
            }
        }
        MutationsDescription mutations = new MutationsDescription(
                VMutationsWithoutNDN,
                mutations(rootInfo.reconstructedNDN, clone.mutations.knownNDN.getRange(NDNRangeInKnownNDN)),
                JMutationsWithoutNDN
        );
        //TODO remove after test on more data
        AncestorInfo ancestorInfo = buildAncestorInfo(mutations);
        if (!ancestorInfo.getSequence().equals(clone.cloneWrapper.clone.getTarget(0).getSequence())) {
            throw new IllegalArgumentException();
        }
        if (!ancestorInfo.getSequence().getRange(ancestorInfo.getCDR3Begin(), ancestorInfo.getCDR3End()).equals(clone.cloneWrapper.clone.getNFeature(GeneFeature.CDR3))) {
            throw new IllegalArgumentException();
        }
        return new CloneWithMutationsFromReconstructedRoot(mutations, clone);
    }

    private RootInfo buildRootInfo(Cluster<CloneWithMutationsFromVJGermline> cluster) {
        //TODO try to choose root by top clones and direction of mutations
        MutationsFromVJGermline rootBasedOn = cluster.cluster.get(0).mutations;

        //TODO may be just get from root?
        Range VRangeInCDR3 = mostLikableVRangeInCDR3(cluster);
        Range JRangeInCDR3 = mostLikableJRangeInCDR3(cluster);

        Range NDNRangeInKnownNDN = NDNRangeInKnownNDN(rootBasedOn, VRangeInCDR3, JRangeInCDR3);

        return new RootInfo(
                VRangeInCDR3,
                JRangeInCDR3,
                rootBasedOn.knownNDN.getRange(NDNRangeInKnownNDN)
        );
    }

    private Range NDNRangeInKnownNDN(MutationsFromVJGermline mutations, Range VRangeInCDR3, Range JRangeInCDR3) {
        return new Range(
                VRangeInCDR3.length() - mutations.VRangeInCDR3.length(),
                mutations.knownNDN.size() - (JRangeInCDR3.length() - mutations.JRangeInCDR3.length())
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
                            .map(range -> new MutationsWithRange(
                                    alignment.getSequence1(),
                                    Mutations.EMPTY_NUCLEOTIDE_MUTATIONS,
                                    mutations,
                                    range,
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
                MutationsUtils.difference(
                        base.getCombinedMutations(),
                        comparison.getCombinedMutations()
                ),
                intersection,
                base.isIncludeLastInserts() || comparison.isIncludeLastInserts()
        );
    }

    private static MutationsWithRange combineWith(MutationsWithRange base, MutationsWithRange comparison, Range intersection) {
        return new MutationsWithRange(
                base.getSequence1(),
                base.getCombinedMutations(),
                base.getCombinedMutations().invert()
                        .combineWith(comparison.getCombinedMutations()),
                intersection,
                base.isIncludeLastInserts() || comparison.isIncludeLastInserts()
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
                intersection,
                base.isIncludeLastInserts() || comparison.isIncludeLastInserts()
        );
    }

    private double distanceFromCluster(CloneWithMutationsFromVJGermline clone, Cluster.Builder<CloneWithMutationsFromVJGermline> cluster) {
        return cluster.getCurrentCluster().stream()
                .mapToDouble(compareTo -> fitnessFunction(fitnessFunctionParams(clone.mutations, compareTo.mutations)))
                .min()
                .orElseThrow(IllegalArgumentException::new);
    }

    private double distanceFromTree(MutationsDescription mutations, Tree<ObservedOrReconstructed<CloneWithMutationsFromReconstructedRoot, MutationsDescription>> tree) {
        return tree.allNodes()
                .map(node -> node.getContent().convert(it -> it.mutations, it -> it))
                .mapToDouble(compareTo -> fitnessFunction(fitnessFunctionParams(mutations, compareTo)))
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

    //TODO duplicated code
    private FitnessFunctionParams fitnessFunctionParams(MutationsDescription first, MutationsDescription second) {
        MutationsWithRange CDR3MutationsBetween = mutationsBetween(first.knownNDN, second.knownNDN, first.knownNDN.getSequence1Range());

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
                new Range(0, first.size()),
                true
        );
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

    private static class MutationsFromVJGermline {
        private final List<MutationsWithRange> VMutationsWithoutNDN;
        private final Pair<Mutations<NucleotideSequence>, Range> knownVMutationsWithinNDN;
        private final Range VRangeInCDR3;
        private final Range JRangeInCDR3;
        private final NucleotideSequence knownNDN;
        private final List<MutationsWithRange> JMutationsWithoutNDN;
        private final Pair<Mutations<NucleotideSequence>, Range> knownJMutationsWithinNDN;

        private MutationsFromVJGermline(
                List<MutationsWithRange> VMutationsWithoutNDN,
                Pair<Mutations<NucleotideSequence>, Range> knownVMutationsWithinNDN,
                Range VRangeInCDR3,
                Range JRangeInCDR3,
                NucleotideSequence knownNDN,
                List<MutationsWithRange> JMutationsWithoutNDN,
                Pair<Mutations<NucleotideSequence>, Range> knownJMutationsWithinNDN
        ) {
            this.VMutationsWithoutNDN = VMutationsWithoutNDN;
            this.knownVMutationsWithinNDN = knownVMutationsWithinNDN;
            this.VRangeInCDR3 = VRangeInCDR3;
            this.JRangeInCDR3 = JRangeInCDR3;
            this.JMutationsWithoutNDN = JMutationsWithoutNDN;
            this.knownNDN = knownNDN;
            this.knownJMutationsWithinNDN = knownJMutationsWithinNDN;
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
                .filter(mutation -> mutation.getSequence1Range().getUpper() < positionInSequence1)
                .mapToInt(mutation -> mutation.getSequence1Range().length() + lengthDelta(mutation))
                .sum();

        return mutations.stream()
                .filter(it -> it.getSequence1Range().contains(positionInSequence1) || it.getSequence1Range().getUpper() == positionInSequence1)
                .map(mutation -> {
                    Mutations<NucleotideSequence> mutationsWithinRange = removeMutationsNotInRange(
                            mutation.getCombinedMutations(), mutation.getSequence1Range(), mutation.isIncludeLastInserts()
                    );
                    int position = mutationsWithinRange.convertToSeq2Position(positionInSequence1);
                    if (position < -1) {
                        position = -(position + 1);
                    }
                    return position - mutation.getSequence1Range().getLower();
                })
                .findFirst()
                .map(it -> it + rangesBefore);
    }

    private Mutations<NucleotideSequence> removeMutationsNotInRange(Mutations<NucleotideSequence> mutations, Range sequence1Range, boolean includeLastInserts) {
        return new Mutations<>(
                NucleotideSequence.ALPHABET,
                IntStream.of(mutations.getRAWMutations())
                        .filter(mutation -> {
                            int position = Mutation.getPosition(mutation);
                            return sequence1Range.contains(position) || (includeLastInserts && position == sequence1Range.getUpper());
                        })
                        .toArray()
        );
    }


    private int lengthDelta(MutationsWithRange mutations) {
        Range sequence2Range = MutationsUtils.projectRange(mutations.getCombinedMutations(), mutations.getSequence1Range(), mutations.isIncludeLastInserts());
        return sequence2Range.length() - mutations.getSequence1Range().length();
    }

    private static NucleotideSequence buildSequence(MutationsWithRange mutations) {
        return MutationsUtils.buildSequence(
                mutations.getSequence1(),
                mutations.getCombinedMutations(),
                mutations.getSequence1Range(),
                mutations.isIncludeLastInserts()
        );
    }

    private static class TreeWithMeta {
        private final TreeBuilderByAncestors<CloneWithMutationsFromReconstructedRoot, MutationsDescription, MutationsDescription> treeBuilder;
        private final RootInfo rootInfo;

        public TreeWithMeta(
                TreeBuilderByAncestors<CloneWithMutationsFromReconstructedRoot, MutationsDescription, MutationsDescription> treeBuilder,
                RootInfo rootInfo
        ) {
            this.treeBuilder = treeBuilder;
            this.rootInfo = rootInfo;
        }
    }

    private static class RootInfo {
        private final Range VRangeInCDR3;
        private final Range JRangeInCDR3;
        private final NucleotideSequence reconstructedNDN;

        private RootInfo(Range VRangeInCDR3, Range JRangeInCDR3, NucleotideSequence reconstructedNDN) {
            this.VRangeInCDR3 = VRangeInCDR3;
            this.JRangeInCDR3 = JRangeInCDR3;
            this.reconstructedNDN = reconstructedNDN;
        }
    }

    private static class CloneWithMutationsFromReconstructedRoot {
        private final MutationsDescription mutations;
        private final CloneWithMutationsFromVJGermline original;

        private CloneWithMutationsFromReconstructedRoot(MutationsDescription mutations, CloneWithMutationsFromVJGermline original) {
            this.mutations = mutations;
            this.original = original;
        }

        public MutationsDescription getMutations() {
            return mutations;
        }

        public CloneWithMutationsFromVJGermline getOriginal() {
            return original;
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
