package com.milaboratory.mixcr.trees;

import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.Aligner;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.alignment.AlignmentScoring;
import com.milaboratory.core.alignment.AlignmentUtils;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.mutations.MutationsBuilder;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequenceBuilder;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.cli.BuildSHMTreeStep;
import com.milaboratory.mixcr.trees.TreeBuilderByAncestors.ObservedOrReconstructed;
import com.milaboratory.mixcr.trees.TreeWithMetaBuilder.DecisionInfo;
import com.milaboratory.mixcr.trees.TreeWithMetaBuilder.MetricDecisionInfo;
import com.milaboratory.mixcr.trees.TreeWithMetaBuilder.ZeroStepDecisionInfo;
import com.milaboratory.mixcr.util.*;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.ReferencePoint;
import io.repseq.core.VDJCGene;
import org.apache.commons.math3.util.Pair;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.milaboratory.core.mutations.Mutations.EMPTY_NUCLEOTIDE_MUTATIONS;
import static com.milaboratory.mixcr.trees.ClonesRebase.NDNRangeInKnownNDN;
import static com.milaboratory.mixcr.trees.MutationsUtils.*;
import static io.repseq.core.GeneType.Joining;
import static io.repseq.core.GeneType.Variable;
import static io.repseq.core.ReferencePoint.CDR3Begin;
import static io.repseq.core.ReferencePoint.CDR3End;

class ClusterProcessor {
    private final SHMTreeBuilderParameters parameters;
    private final AlignmentScoring<NucleotideSequence> VScoring;
    private final AlignmentScoring<NucleotideSequence> JScoring;
    private final AlignmentScoring<NucleotideSequence> NDNScoring;

    private final NucleotideSequence VSequence1;
    private final NucleotideSequence JSequence1;

    private final Cluster<CloneWrapper> originalCluster;

    private final CalculatedClusterInfo clusterInfo;
    private final IdGenerator idGenerator;

    private ClusterProcessor(
            SHMTreeBuilderParameters parameters,
            AlignmentScoring<NucleotideSequence> VScoring,
            AlignmentScoring<NucleotideSequence> JScoring,
            Cluster<CloneWrapper> originalCluster,
            NucleotideSequence VSequence1,
            NucleotideSequence JSequence1,
            AlignmentScoring<NucleotideSequence> NDNScoring,
            CalculatedClusterInfo clusterInfo,
            IdGenerator idGenerator
    ) {
        this.parameters = parameters;
        this.VScoring = VScoring;
        this.JScoring = JScoring;
        this.originalCluster = originalCluster;
        this.VSequence1 = VSequence1;
        this.JSequence1 = JSequence1;
        this.NDNScoring = NDNScoring;
        this.clusterInfo = clusterInfo;
        this.idGenerator = idGenerator;
    }

    static ClusterProcessor build(
            SHMTreeBuilderParameters parameters,
            AlignmentScoring<NucleotideSequence> VScoring,
            AlignmentScoring<NucleotideSequence> JScoring,
            Cluster<CloneWrapper> originalCluster,
            CalculatedClusterInfo calculatedClusterInfo,
            IdGenerator idGenerator
    ) {
        if (originalCluster.cluster.isEmpty()) {
            throw new IllegalArgumentException();
        }

        CloneWrapper anyClone = originalCluster.cluster.get(0);
        return new ClusterProcessor(
                parameters,
                VScoring,
                JScoring,
                originalCluster,
                anyClone.getHit(Variable).getAlignment(0).getSequence1(),
                anyClone.getHit(Joining).getAlignment(0).getSequence1(),
                MutationsUtils.NDNScoring(),
                calculatedClusterInfo,
                idGenerator
        );
    }

    static CalculatedClusterInfo calculateClusterInfo(Cluster<CloneWrapper> originalCluster, double minPortionOfClonesForCommonAlignmentRanges) {
        return new CalculatedClusterInfo(
                ClonesAlignmentRanges.commonAlignmentRanges(
                        originalCluster.cluster,
                        minPortionOfClonesForCommonAlignmentRanges,
                        Variable,
                        it -> it.getHit(Variable)
                ),
                ClonesAlignmentRanges.commonAlignmentRanges(
                        originalCluster.cluster,
                        minPortionOfClonesForCommonAlignmentRanges,
                        Joining,
                        it -> it.getHit(Joining)
                ),
                minRangeInCDR3(originalCluster, ClusterProcessor::VRangeInCDR3),
                minRangeInCDR3(originalCluster, ClusterProcessor::JRangeInCDR3)
        );
    }

    @SuppressWarnings("unchecked")
    static <E extends DecisionInfo> VJBase makeDecision(Map<VJBase, E> chooses) {
        if (chooses.size() == 1) {
            return chooses.keySet().iterator().next();
        }
        Class<E> decisionType = (Class<E>) chooses.values().iterator().next().getClass();
        if (!chooses.values().stream().allMatch(decisionType::isInstance)) {
            throw new IllegalArgumentException();
        }
        if (decisionType == ZeroStepDecisionInfo.class) {
            return makeDecisionForZero((Map<VJBase, ZeroStepDecisionInfo>) chooses);
        } else if (decisionType == MetricDecisionInfo.class) {
            return makeDecisionByMetric((Map<VJBase, MetricDecisionInfo>) chooses);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    private static VJBase makeDecisionByMetric(Map<VJBase, MetricDecisionInfo> chooses) {
        return chooses.entrySet().stream()
                .min(Comparator.comparing(it -> it.getValue().getMetric()))
                .orElseThrow(IllegalStateException::new)
                .getKey();
    }

    private static VJBase makeDecisionForZero(Map<VJBase, ZeroStepDecisionInfo> chooses) {
        Set<VJBase> filteredByAlleles = chooses.entrySet().stream()
                //group by the same origin VJ pair - group decisions by related alleles
                .collect(Collectors.groupingBy(
                        it -> Pair.create(
                                it.getValue().getGeneName(Variable),
                                it.getValue().getGeneName(Joining)
                        )
                ))
                .values().stream()
                //choose allele pair with decision that is most closed to germline
                .map(withTheSameGeneBase -> withTheSameGeneBase.stream()
                        .min(Comparator.comparing(it -> it.getValue().getCommonMutationsCount()))
                )
                .flatMap(Optional::stream)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        return filteredByAlleles.stream()
                .max(Comparator.comparing(it -> chooses.get(it).getScore(Variable) + chooses.get(it).getScore(Joining)))
                .orElseThrow(IllegalStateException::new);
    }

    StepResult applyStep(BuildSHMTreeStep stepName, List<TreeWithMetaBuilder> currentTrees) {
        Step step = stepByName(stepName);
        return step.next(
                currentTrees,
                () -> {
                    Set<Integer> clonesInTrees = currentTrees.stream()
                            .flatMap(it -> it.getClonesAdditionHistory().stream())
                            .collect(Collectors.toSet());
                    return rebaseFromGermline(originalCluster.cluster.stream().filter(it -> !clonesInTrees.contains(it.clone.getId())));
                }
        );
    }

    private Step stepByName(BuildSHMTreeStep stepName) {
        switch (stepName) {
            case AttachClonesByDistanceChange:
                return this::attachClonesByDistanceChange;
            case CombineTrees:
                return (trees, freeClones) -> combineTrees(trees);
            case AttachClonesByNDN:
                return this::attachClonesByNDN;
            default:
                throw new IllegalArgumentException();
        }
    }

    /**
     * On stage of clustering we can't use VEnd and JBegin marking because hypermutation on P region affects accuracy.
     * While alignment in some cases it's not possible to determinate mutation of P segment from shorter V or J version and other N nucleotides.
     * So, there will be used CDR3 instead of NDN, VBegin-CDR3Begin instead V and CDR3End-JEnd instead J
     */
    StepResult buildTreeTopParts() {
        //use only clones that are at long distance from any germline
        Stream<CloneWrapper> clonesThatNotMatchAnyGermline = originalCluster.cluster.stream()
                .filter(it -> !hasVJPairThatMatchesWithGermline(it.clone));
        List<CloneWithMutationsFromVJGermline> clones = rebaseFromGermline(clonesThatNotMatchAnyGermline);
        List<Pair<List<Pair<Integer, ? extends DecisionInfo>>, TreeWithMetaBuilder>> result = clusterByCommonMutations(clones)
                .stream()
                .filter(it -> it.cluster.size() > 1)
                .map(this::buildATreeWithDecisionsInfo)
                .collect(Collectors.toList());
        return buildStepResult(
                result.stream()
                        .flatMap(it -> it.getFirst().stream())
                        .collect(Collectors.toMap(Pair::getKey, Pair::getValue)),
                result.stream()
                        .map(Pair::getSecond)
                        .collect(Collectors.toList())
        );
    }

    private boolean hasVJPairThatMatchesWithGermline(Clone clone) {
        return Arrays.stream(clone.getHits(Variable))
                .flatMap(VHit -> Arrays.stream(clone.getHits(Joining))
                        .map(JHit -> mutationsCount(VHit) + mutationsCount(JHit)))
                .anyMatch(it -> it < parameters.commonMutationsCountForClustering);
    }

    private int mutationsCount(VDJCHit hit) {
        return Arrays.stream(hit.getAlignments())
                .mapToInt(it -> it.getAbsoluteMutations().size())
                .sum();
    }

    List<TreeWithMetaBuilder> restore(List<TreeWithMetaBuilder.Snapshot> resultTrees) {
        Set<Integer> clonesInTrees = resultTrees.stream()
                .flatMap(it -> it.getClonesAdditionHistory().stream())
                .collect(Collectors.toSet());
        Map<Integer, CloneWrapper> clonesByIds = originalCluster.cluster.stream()
                .filter(it -> clonesInTrees.contains(it.clone.getId()))
                .collect(Collectors.toMap(it -> it.clone.getId(), Function.identity()));
        return resultTrees.stream()
                .map(treeSnapshot -> {
                    TreeWithMetaBuilder treeWithMetaBuilder = new TreeWithMetaBuilder(
                            createTreeBuilder(treeSnapshot.getRootInfo()),
                            treeSnapshot.getRootInfo(),
                            new ClonesRebase(VSequence1, VScoring, NDNScoring, JSequence1, JScoring),
                            new LinkedList<>(),
                            treeSnapshot.getTreeId()
                    );
                    treeSnapshot.getClonesAdditionHistory().forEach(cloneId -> {
                        CloneWithMutationsFromReconstructedRoot rebasedClone = treeWithMetaBuilder.rebaseClone(rebaseFromGermline(clonesByIds.get(cloneId)));
                        treeWithMetaBuilder.addClone(rebasedClone);
                    });
                    return treeWithMetaBuilder;
                })
                .collect(Collectors.toList());
    }

    private List<CloneWithMutationsFromVJGermline> rebaseFromGermline(Stream<CloneWrapper> clones) {
        return clones
                .filter(cloneWrapper -> clusterInfo.commonVAlignmentRanges.containsCloneWrapper(cloneWrapper)
                        && clusterInfo.commonJAlignmentRanges.containsCloneWrapper(cloneWrapper))
                .map(this::rebaseFromGermline)
                .collect(Collectors.toList());
    }

    private CloneWithMutationsFromVJGermline rebaseFromGermline(CloneWrapper cloneWrapper) {
        NucleotideSequence CDR3 = cloneWrapper.getFeature(GeneFeature.CDR3).getSequence();
        MutationsWithRange VMutationsInCDR3WithoutNDN = new MutationsWithRange(
                VSequence1,
                getMutationsForRange(cloneWrapper, clusterInfo.VRangeInCDR3, Variable),
                new RangeInfo(clusterInfo.VRangeInCDR3, false)
        );
        MutationsWithRange JMutationsInCDR3WithoutNDN = new MutationsWithRange(
                JSequence1,
                getMutationsForRange(cloneWrapper, clusterInfo.JRangeInCDR3, Joining),
                new RangeInfo(clusterInfo.JRangeInCDR3, false)
        );
        return new CloneWithMutationsFromVJGermline(
                new MutationsFromVJGermline(
                        getMutationsWithoutCDR3(
                                cloneWrapper,
                                Variable,
                                new Range(clusterInfo.VRangeInCDR3.getLower(), VSequence1.size()),
                                clusterInfo.commonVAlignmentRanges
                        ),
                        VMutationsInCDR3WithoutNDN,
                        getVMutationsWithinNDN(cloneWrapper, clusterInfo.VRangeInCDR3.getUpper()),
                        CDR3.getRange(
                                clusterInfo.VRangeInCDR3.length() + VMutationsInCDR3WithoutNDN.lengthDelta(),
                                CDR3.size() - (clusterInfo.JRangeInCDR3.length() + JMutationsInCDR3WithoutNDN.lengthDelta())
                        ),
                        getJMutationsWithinNDN(cloneWrapper, clusterInfo.JRangeInCDR3.getLower()),
                        JMutationsInCDR3WithoutNDN,
                        getMutationsWithoutCDR3(
                                cloneWrapper,
                                Joining,
                                new Range(0, clusterInfo.JRangeInCDR3.getLower()),
                                clusterInfo.commonJAlignmentRanges
                        )
                ),
                cloneWrapper
        );
    }

    private StepResult combineTrees(List<TreeWithMetaBuilder> originalTrees) {
        ClonesRebase clonesRebase = new ClonesRebase(VSequence1, VScoring, NDNScoring, JSequence1, JScoring);

        List<TreeWithMetaBuilder> result = new ArrayList<>();

        List<TreeWithMetaBuilder> originalTreesCopy = originalTrees.stream()
                .sorted(Comparator.comparingInt(TreeWithMetaBuilder::clonesCount).reversed())
                .collect(Collectors.toList());
        //trying to grow the biggest trees first
        while (!originalTreesCopy.isEmpty()) {
            TreeWithMetaBuilder treeToGrow = originalTreesCopy.get(0);
            originalTreesCopy.remove(0);

            //trying to add the smallest trees first
            for (int i = originalTreesCopy.size() - 1; i >= 0; i--) {
                TreeWithMetaBuilder treeToAttach = originalTreesCopy.get(i);

                BigDecimal distance_1 = distance(clonesRebase, treeToAttach, treeToGrow);
                BigDecimal distance_2 = distance(clonesRebase, treeToGrow, treeToAttach);

                double metric = Math.min(distance_1.doubleValue(), distance_2.doubleValue());

                if (metric <= parameters.thresholdForCombineTrees) {
                    treeToGrow = buildATree(new Cluster<>(
                            Stream.of(treeToGrow, treeToAttach)
                                    .flatMap(treeWithMetaBuilder -> treeWithMetaBuilder.allNodes()
                                            .map(Tree.NodeWithParent::getNode)
                                            .map(node -> node.getContent().convert(Optional::of, it -> Optional.<CloneWithMutationsFromReconstructedRoot>empty()))
                                            .flatMap(Optional::stream)
                                            .map(it -> new CloneWithMutationsFromVJGermline(it.getMutationsFromVJGermline(), it.getClone()))
                                    )
                                    .collect(Collectors.toList())
                    ));
                    originalTreesCopy.remove(i);
                }
            }

            result.add(treeToGrow);
        }
        return buildStepResult(new HashMap<>(), result);
    }

    private BigDecimal distance(ClonesRebase clonesRebase, TreeWithMetaBuilder from, TreeWithMetaBuilder destination) {
        SyntheticNode oldestAncestorOfFrom = from.oldestReconstructedAncestor();
        SyntheticNode oldestAncestorOfDestination = destination.oldestReconstructedAncestor();

        MutationsDescription destinationRebasedOnFrom = clonesRebase.rebaseMutations(oldestAncestorOfDestination.getFromRootToThis(), destination.getRootInfo(), from.getRootInfo());
        return distance(mutationsBetween(oldestAncestorOfFrom.getFromRootToThis(), destinationRebasedOnFrom));
    }

    private StepResult attachClonesByNDN(
            List<TreeWithMetaBuilder> originalTrees, Supplier<List<CloneWithMutationsFromVJGermline>> clonesNotInClusters
    ) {
        Map<Integer, DecisionInfo> decisions = new HashMap<>();

        List<TreeWithMetaBuilder> resultTrees = originalTrees.stream().map(TreeWithMetaBuilder::copy).collect(Collectors.toList());

        clonesNotInClusters.get().stream()
                .filter(clone -> clone.getMutations().getVJMutationsCount() < parameters.commonMutationsCountForClustering)
                .forEach(clone -> {
                    Optional<Pair<Double, Runnable>> bestTreeToAttach = resultTrees.stream()
                            .map(tree -> {
                                CloneWithMutationsFromReconstructedRoot rebasedClone = tree.rebaseClone(clone);
                                SyntheticNode oldestAncestorOfTreeToGrow = tree.oldestReconstructedAncestor();
                                SyntheticNode noeToAttach = SyntheticNode.createFromMutations(rebasedClone.getMutationsFromRoot());
                                NucleotideSequence NDNOfTreeToGrow = oldestAncestorOfTreeToGrow.getFromRootToThis().getKnownNDN().buildSequence();
                                NucleotideSequence NDNOfNodeToAttach = noeToAttach.getFromRootToThis().getKnownNDN().buildSequence();
                                float score_1 = Aligner.alignGlobal(
                                        NDNScoring,
                                        NDNOfNodeToAttach,
                                        NDNOfTreeToGrow
                                ).getScore();
                                float score_2 = Aligner.alignGlobal(
                                        NDNScoring,
                                        NDNOfTreeToGrow,
                                        NDNOfNodeToAttach
                                ).getScore();
                                int NDNLength = tree.getRootInfo().getReconstructedNDN().size();
                                int maxScore = NDNScoring.getMaximalMatchScore() * NDNLength;
                                double metric_1 = (maxScore - score_1) / (double) NDNLength;
                                double metric_2 = (maxScore - score_2) / (double) NDNLength;
                                return Pair.<Double, Runnable>create(Math.min(metric_1, metric_2), () -> tree.addClone(rebasedClone));
                            })
                            .min(Comparator.comparing(Pair::getFirst));
                    if (bestTreeToAttach.isPresent()) {
                        double metric = bestTreeToAttach.get().getFirst();
                        if (metric <= parameters.thresholdForCombineByNDN) {
                            bestTreeToAttach.get().getSecond().run();
                            decisions.put(clone.getCloneWrapper().clone.getId(), new MetricDecisionInfo(metric));
                        }
                    }
                });
        return buildStepResult(decisions, resultTrees);
    }

    private StepResult attachClonesByDistanceChange(
            List<TreeWithMetaBuilder> originalTrees, Supplier<List<CloneWithMutationsFromVJGermline>> clonesNotInClustersSupplier
    ) {
        List<TreeWithMetaBuilder> result = originalTrees.stream().map(TreeWithMetaBuilder::copy).collect(Collectors.toList());
        Map<Integer, DecisionInfo> decisions = new HashMap<>();

        List<CloneWithMutationsFromVJGermline> clonesNotInClusters = clonesNotInClustersSupplier.get();
        //try to add as nodes clones that wasn't picked up by clustering
        for (int i = clonesNotInClusters.size() - 1; i >= 0; i--) {
            CloneWithMutationsFromVJGermline clone = clonesNotInClusters.get(i);
            if (clone.getMutations().getVJMutationsCount() >= parameters.commonMutationsCountForClustering) {
                Optional<Pair<TreeBuilderByAncestors.Action, Double>> bestActionAndDistanceFromRoot = result.stream()
                        .map(treeWithMeta -> {
                            CloneWithMutationsFromReconstructedRoot rebasedClone = treeWithMeta.rebaseClone(clone);
                            return Pair.create(
                                    treeWithMeta.bestAction(rebasedClone),
                                    treeWithMeta.distanceFromRootToClone(rebasedClone)
                            );
                        })
                        .min(Comparator.comparing(p -> p.getFirst().changeOfDistance()));

                if (bestActionAndDistanceFromRoot.isPresent()) {
                    TreeBuilderByAncestors.Action bestAction = bestActionAndDistanceFromRoot.get().getFirst();
                    double distanceFromRoot = bestActionAndDistanceFromRoot.get().getSecond();
                    double metric = bestAction.changeOfDistance().doubleValue() / distanceFromRoot;
                    if (metric <= parameters.thresholdForFreeClones) {
                        decisions.put(clone.getCloneWrapper().clone.getId(), new MetricDecisionInfo(metric));
                        bestAction.apply();
                    }
                }
            }
        }
        return buildStepResult(decisions, result);
    }

    private List<Cluster<CloneWithMutationsFromVJGermline>> clusterByCommonMutations(List<CloneWithMutationsFromVJGermline> clones) {
        AdjacencyMatrix matrix = new AdjacencyMatrix(clones.size());
        for (int i = 0; i < clones.size(); i++) {
            for (int j = 0; j < clones.size(); j++) {
                if (commonMutationsCount(clones.get(i), clones.get(j)) >= parameters.commonMutationsCountForClustering) {
                    if (NDNDistance(clones.get(i), clones.get(j)) <= parameters.maxNDNDistanceForClustering) {
                        matrix.setConnected(i, j);
                    }
                }
            }
        }

        List<BitArrayInt> notOverlappedCliques = new ArrayList<>();
        var cliques = matrix.calculateMaximalCliques();
        cliques.stream()
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

        return clusters;
    }

    private int commonMutationsCount(CloneWithMutationsFromVJGermline first, CloneWithMutationsFromVJGermline second) {
        List<Mutations<NucleotideSequence>> VAllelesMutations = mutationsFromThisAlleleToOthers(Variable, first, second);
        List<Mutations<NucleotideSequence>> JAllelesMutations = mutationsFromThisAlleleToOthers(Joining, first, second);
        return commonMutationsCount(first.getMutations().getVMutations(), second.getMutations().getVMutations(), VAllelesMutations) +
                commonMutationsCount(first.getMutations().getJMutations(), second.getMutations().getJMutations(), JAllelesMutations);
    }

    private double NDNDistance(CloneWithMutationsFromVJGermline first, CloneWithMutationsFromVJGermline second) {
        NucleotideSequence firstNDN = first.getMutations().getKnownNDN();
        NucleotideSequence secondNDN = second.getMutations().getKnownNDN();
        float score = Aligner.alignGlobal(
                NDNScoring,
                firstNDN,
                secondNDN
        ).getScore();
        int maxScore = Math.max(
                maxScore(firstNDN, NDNScoring),
                maxScore(secondNDN, NDNScoring)
        );
        return (maxScore - score) / (double) Math.min(firstNDN.size(), secondNDN.size());
    }

    private int commonMutationsCount(List<MutationsWithRange> first, List<MutationsWithRange> second, List<Mutations<NucleotideSequence>> allelesMutations) {
        return Stream.concat(
                        allelesMutations.stream(),
                        Stream.of(EMPTY_NUCLEOTIDE_MUTATIONS)
                )
                .distinct()
                .mapToInt(alleleMutations -> commonMutationsCount(without(first, alleleMutations), without(second, alleleMutations)))
                .min().orElseThrow(IllegalStateException::new);
    }

    private List<MutationsWithRange> without(List<MutationsWithRange> cloneMutations, Mutations<NucleotideSequence> alleleMutations) {
        Set<Integer> alleleMutationsSet = Arrays.stream(alleleMutations.getRAWMutations()).boxed().collect(Collectors.toSet());
        return cloneMutations.stream()
                .map(mutations -> {
                    MutationsBuilder<NucleotideSequence> builder = new MutationsBuilder<>(NucleotideSequence.ALPHABET);
                    Arrays.stream(mutations.getMutations().getRAWMutations())
                            .filter(mutation -> !alleleMutationsSet.contains(mutation))
                            .forEach(builder::append);
                    return new MutationsWithRange(
                            mutations.getSequence1(),
                            builder.createAndDestroy(),
                            mutations.getRangeInfo()
                    );
                })
                .collect(Collectors.toList());
    }

    private List<Mutations<NucleotideSequence>> mutationsFromThisAlleleToOthers(GeneType geneType, CloneWithMutationsFromVJGermline... clones) {
        List<VDJCGene> baseGenes = Arrays.stream(clones)
                .map(it -> it.getCloneWrapper().getHit(geneType).getGene())
                .distinct()
                .collect(Collectors.toList());
        if (baseGenes.size() != 1) {
            throw new IllegalArgumentException();
        }
        VDJCGene baseGene = baseGenes.get(0);
        Mutations<NucleotideSequence> mutationsOfCurrentAllele = alleleMutations(baseGene);
        return Arrays.stream(clones)
                .flatMap(clone -> Arrays.stream(clone.getCloneWrapper().clone.getHits(geneType)))
                .map(VDJCHit::getGene)
                .distinct()
                .filter(gene -> gene.getGeneName().equals(baseGene.getGeneName()))
                .filter(gene -> !gene.getName().equals(baseGene.getName()))
                .map(this::alleleMutations)
                .map(alleleMutations -> mutationsOfCurrentAllele.invert().combineWith(alleleMutations))
                .collect(Collectors.toList());
    }

    private Mutations<NucleotideSequence> alleleMutations(VDJCGene gene) {
        Mutations<NucleotideSequence> result = gene.getData().getBaseSequence().getMutations();
        return Objects.requireNonNullElse(result, EMPTY_NUCLEOTIDE_MUTATIONS);
    }

    private int commonMutationsCount(List<MutationsWithRange> first, List<MutationsWithRange> second) {
        return fold(
                first, second,
                (a, b) -> intersection(a, b, a.getRangeInfo().intersection(b.getRangeInfo())).mutationsCount()
        ).stream().mapToInt(it -> it).sum();
    }

    private Pair<List<Pair<Integer, ? extends DecisionInfo>>, TreeWithMetaBuilder> buildATreeWithDecisionsInfo(Cluster<CloneWithMutationsFromVJGermline> cluster) {
        TreeWithMetaBuilder treeWithMetaBuilder = buildATree(cluster);

        List<Pair<Integer, ? extends DecisionInfo>> decisionsInfo = cluster.cluster.stream()
                .map(it -> {
                    SyntheticNode effectiveParent = treeWithMetaBuilder.getEffectiveParent(it.getCloneWrapper().clone);
                    VDJCHit VHit = it.getCloneWrapper().getHit(Variable);
                    VDJCHit JHit = it.getCloneWrapper().getHit(Joining);
                    return Pair.create(
                            it.getCloneWrapper().clone.getId(),
                            new ZeroStepDecisionInfo(
                                    effectiveParent.getFromRootToThis().combinedVMutations().size() +
                                            effectiveParent.getFromRootToThis().combinedJMutations().size(),
                                    VHit.getGene().getGeneName(),
                                    JHit.getGene().getGeneName(),
                                    VHit.getScore(),
                                    JHit.getScore()
                            )
                    );
                })
                .collect(Collectors.toList());
        return Pair.create(decisionsInfo, treeWithMetaBuilder);
    }

    private TreeWithMetaBuilder buildATree(Cluster<CloneWithMutationsFromVJGermline> cluster) {
        ClonesRebase clonesRebase = new ClonesRebase(VSequence1, VScoring, NDNScoring, JSequence1, JScoring);

        RootInfo rootInfo = buildRootInfo(cluster);

        List<CloneWithMutationsFromReconstructedRoot> rebasedCluster = cluster.cluster.stream()
                .map(clone -> clonesRebase.rebaseClone(rootInfo, clone.getMutations(), clone.getCloneWrapper()))
                .sorted(Comparator.<CloneWithMutationsFromReconstructedRoot, BigDecimal>comparing(cloneDescriptor -> distance(cloneDescriptor.getMutationsFromRoot())).reversed())
                .collect(Collectors.toList());

        TreeBuilderByAncestors<CloneWithMutationsFromReconstructedRoot, SyntheticNode, MutationsDescription> treeBuilder = createTreeBuilder(rootInfo);

        TreeWithMetaBuilder treeWithMetaBuilder = new TreeWithMetaBuilder(
                treeBuilder,
                rootInfo,
                clonesRebase,
                new LinkedList<>(),
                idGenerator.next(rootInfo.getVJBase())
        );

        for (CloneWithMutationsFromReconstructedRoot cloneWithMutationsFromReconstructedRoot : rebasedCluster) {
            treeWithMetaBuilder.addClone(cloneWithMutationsFromReconstructedRoot);
        }
        return treeWithMetaBuilder;
    }

    private TreeBuilderByAncestors<CloneWithMutationsFromReconstructedRoot, SyntheticNode, MutationsDescription> createTreeBuilder(RootInfo rootInfo) {
        SyntheticNode root = SyntheticNode.createRoot(
                clusterInfo.commonVAlignmentRanges,
                VSequence1,
                rootInfo,
                clusterInfo.commonJAlignmentRanges,
                JSequence1
        );

        return new TreeBuilderByAncestors<>(
                root,
                (base, mutations) -> distance(mutations).add(penaltyForReversedMutations(base, mutations)),
                (first, second) -> mutationsBetween(first.getFromRootToThis(), second.getFromRootToThis()),
                SyntheticNode::mutate,
                observed -> SyntheticNode.createFromMutations(observed.getMutationsFromRoot()),
                this::commonMutations,
                (parent, child) -> SyntheticNode.createFromMutations(
                        child.getFromRootToThis().withKnownNDNMutations(
                                new MutationsWithRange(
                                        child.getFromRootToThis().getKnownNDN().getSequence1(),
                                        concreteNDNChild(
                                                parent.getFromRootToThis().getKnownNDN().getMutations(),
                                                child.getFromRootToThis().getKnownNDN().getMutations()
                                        ),
                                        child.getFromRootToThis().getKnownNDN().getRangeInfo()
                                )
                        )
                ),
                parameters.countOfNodesToProbe
        );
    }

    private static long reversedVMutationsCount(SyntheticNode fromRootToBase, MutationsDescription mutations) {
        var reversedMutationsNotInCDR3 = fold(
                fromRootToBase.getFromRootToThis().getVMutationsWithoutCDR3(),
                mutations.getVMutationsWithoutCDR3(),
                ClusterProcessor::reversedMutationsCount
        ).stream().mapToLong(it -> it).sum();
        var reversedMutationsInCDR3 = reversedMutationsCount(fromRootToBase.getFromRootToThis().getVMutationsInCDR3WithoutNDN(), mutations.getVMutationsInCDR3WithoutNDN());

        return reversedMutationsInCDR3 + reversedMutationsNotInCDR3;
    }

    private static long reversedJMutationsCount(SyntheticNode fromRootToBase, MutationsDescription mutations) {
        var reversedMutationsNotInCDR3 = fold(
                fromRootToBase.getFromRootToThis().getJMutationsWithoutCDR3(),
                mutations.getJMutationsWithoutCDR3(),
                ClusterProcessor::reversedMutationsCount
        ).stream().mapToLong(it -> it).sum();
        var reversedMutationsInCDR3 = reversedMutationsCount(fromRootToBase.getFromRootToThis().getJMutationsInCDR3WithoutNDN(), mutations.getJMutationsInCDR3WithoutNDN());

        return reversedMutationsInCDR3 + reversedMutationsNotInCDR3;
    }

    private static long reversedMutationsCount(MutationsWithRange a, MutationsWithRange b) {
        var reversedMutations = b.mutationsForRange().move(a.getRangeInfo().getRange().getLower()).invert();
        var asSet = Arrays.stream(reversedMutations.getRAWMutations()).boxed().collect(Collectors.toSet());
        return Arrays.stream(a.getMutations().getRAWMutations()).filter(asSet::contains).count();
    }

    private RootInfo buildRootInfo(Cluster<CloneWithMutationsFromVJGermline> cluster) {
        CloneWithMutationsFromVJGermline rootBasedOn = cluster.cluster.get(0);

        //TODO may be just get from root?
        Range VRangeInCDR3 = mostLikableRangeInCDR3(cluster, ClusterProcessor::VRangeInCDR3);
        Range JRangeInCDR3 = mostLikableRangeInCDR3(cluster, ClusterProcessor::JRangeInCDR3);

        Range NDNRangeInKnownNDN = NDNRangeInKnownNDN(rootBasedOn.getMutations(), VRangeInCDR3, JRangeInCDR3);

        SequenceBuilder<NucleotideSequence> NDNBuilder = NucleotideSequence.ALPHABET.createBuilder();
        IntStream.range(0, NDNRangeInKnownNDN.length()).forEach(it -> NDNBuilder.append(NucleotideSequence.N));

        return new RootInfo(
                VRangeInCDR3,
                NDNBuilder.createAndDestroy(),
                JRangeInCDR3,
                rootBasedOn.getCloneWrapper().VJBase
        );
    }

    private List<MutationsWithRange> getMutationsWithoutCDR3(CloneWrapper clone, GeneType geneType, Range CDR3Range, ClonesAlignmentRanges commonAlignmentRanges) {
        VDJCHit hit = clone.getHit(geneType);
        return IntStream.range(0, hit.getAlignments().length)
                .boxed()
                .flatMap(index -> {
                    Alignment<NucleotideSequence> alignment = hit.getAlignment(index);
                    Mutations<NucleotideSequence> mutations = alignment.getAbsoluteMutations();
                    List<Range> rangesWithout = alignment.getSequence1Range().without(CDR3Range);
                    return rangesWithout.stream()
                            .map(commonAlignmentRanges::cutRange)
                            .filter(range -> !range.isEmpty())
                            .map(range -> new MutationsWithRange(
                                    alignment.getSequence1(),
                                    mutations,
                                    new RangeInfo(range, alignment.getSequence1Range().getLower() == range.getLower())
                            ));
                })
                .collect(Collectors.toList());
    }

    private Pair<Mutations<NucleotideSequence>, Range> getVMutationsWithinNDN(CloneWrapper clone, int from) {
        VDJCHit hit = clone.getHit(Variable);
        int CDR3Begin = clone.getRelativePosition(Variable, ReferencePoint.CDR3Begin);
        return IntStream.range(0, hit.getAlignments().length)
                .boxed()
                .map(hit::getAlignment)
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
                .flatMap(Optional::stream)
                .findFirst()
                .orElseGet(() -> Pair.create(EMPTY_NUCLEOTIDE_MUTATIONS, new Range(from, from)));
    }

    private Mutations<NucleotideSequence> getMutationsForRange(CloneWrapper clone, Range range, GeneType geneType) {
        VDJCHit hit = clone.getHit(geneType);
        return IntStream.range(0, hit.getAlignments().length)
                .boxed()
                .map(hit::getAlignment)
                .filter(alignment -> alignment.getSequence1Range().contains(range))
                .map(Alignment::getAbsoluteMutations)
                .findFirst()
                .orElseThrow(IllegalArgumentException::new);
    }

    private Pair<Mutations<NucleotideSequence>, Range> getJMutationsWithinNDN(CloneWrapper clone, int to) {
        VDJCHit hit = clone.getHit(Joining);
        int CDR3End = clone.getRelativePosition(Joining, ReferencePoint.CDR3End);
        return IntStream.range(0, hit.getAlignments().length)
                .boxed()
                .map(hit::getAlignment)
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
                .flatMap(Optional::stream)
                .findFirst()
                .orElseGet(() -> Pair.create(EMPTY_NUCLEOTIDE_MUTATIONS, new Range(to, to)));
    }

    private BigDecimal penaltyForReversedMutations(SyntheticNode fromRootToBase, MutationsDescription mutations) {
        long reversedMutationsCount = reversedVMutationsCount(fromRootToBase, mutations) + reversedJMutationsCount(fromRootToBase, mutations);
        return BigDecimal.valueOf(parameters.penaltyForReversedMutations).multiply(BigDecimal.valueOf(reversedMutationsCount));
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
        return BigDecimal.valueOf((NDNPenalties * parameters.NDNScoreMultiplier + VPenalties + JPenalties) / (NDNLength + VLength + JLength));
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
    private Range mostLikableRangeInCDR3(Cluster<CloneWithMutationsFromVJGermline> cluster, Function<CloneWrapper, Range> rangeSupplier) {
        return cluster.cluster.stream()
                .sorted(Comparator.comparing(it -> it.getMutations().getVJMutationsCount()))
                .limit(parameters.topToVoteOnNDNSize)
                .map(CloneWithMutationsFromVJGermline::getCloneWrapper)
                .map(rangeSupplier)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.<Range, Long>comparingByValue()
                        .thenComparing(Comparator.<Map.Entry<Range, Long>>comparingInt(e -> e.getKey().length()).reversed()))
                .map(Map.Entry::getKey)
                .orElseThrow(IllegalStateException::new);
    }

    private static Range minRangeInCDR3(Cluster<CloneWrapper> cluster, Function<CloneWrapper, Range> rangeSupplier) {
        //TODO try to use alignment to calculate most possible position
        return cluster.cluster.stream()
                .map(rangeSupplier)
                .min(Comparator.comparing(Range::length))
                .orElseThrow(IllegalStateException::new);
    }

    private static Range VRangeInCDR3(CloneWrapper clone) {
        Alignment<NucleotideSequence>[] alignments = clone.getHit(Variable).getAlignments();
        return new Range(
                clone.getRelativePosition(Variable, CDR3Begin),
                alignments[alignments.length - 1].getSequence1Range().getUpper()
        );
    }

    private static Range JRangeInCDR3(CloneWrapper clone) {
        return new Range(
                clone.getHit(Joining).getAlignment(0).getSequence1Range().getLower(),
                clone.getRelativePosition(Joining, CDR3End)
        );
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

    private StepResult buildStepResult(Map<Integer, ? extends DecisionInfo> decisions, List<TreeWithMetaBuilder> trees) {
        return new StepResult(
                decisions,
                trees.stream()
                        .map(TreeWithMetaBuilder::snapshot)
                        .collect(Collectors.toList()),
                trees.stream()
                        .flatMap(tree -> tree.allNodes()
                                .filter(it -> it.getNode().getContent() instanceof TreeBuilderByAncestors.Reconstructed)
                                .map(nodeWithParent -> buildDebugInfo(decisions, tree, nodeWithParent))
                        )
                        .collect(Collectors.toList())
        );
    }

    private DebugInfo buildDebugInfo(Map<Integer, ? extends DecisionInfo> decisions, TreeWithMetaBuilder tree, Tree.NodeWithParent<ObservedOrReconstructed<CloneWithMutationsFromReconstructedRoot, SyntheticNode>> nodeWithParent) {
        SyntheticNode nodeContent = nodeWithParent.getNode()
                .getContent()
                .convert(it -> Optional.<SyntheticNode>empty(), Optional::of)
                .orElseThrow(IllegalArgumentException::new);
        Integer cloneId = nodeWithParent.getNode().getLinks().stream()
                .map(Tree.NodeLink::getNode)
                .map(child -> child.getContent().convert(it -> Optional.of(it.getClone().clone.getId()), __ -> Optional.<Integer>empty()))
                .flatMap(Optional::stream)
                .findAny().orElse(null);
        Double metric = null;
        if (cloneId != null) {
            DecisionInfo decision = decisions.get(cloneId);
            if (decision instanceof MetricDecisionInfo) {
                metric = ((MetricDecisionInfo) decision).getMetric();
            }
        }

        Optional<DebugInfo.MutationsSet> parentMutations = Optional.ofNullable(nodeWithParent.getParent())
                .flatMap(parent -> parent.getContent().convert(it -> Optional.empty(), Optional::of))
                .map(parent -> new DebugInfo.MutationsSet(
                        parent.getFromRootToThis().combinedVMutations().invert()
                                .combineWith(nodeContent.getFromRootToThis().combinedVMutations()),
                        parent.getFromRootToThis().getKnownNDN().getMutations().invert()
                                .combineWith(nodeContent.getFromRootToThis().getKnownNDN().getMutations()),
                        parent.getFromRootToThis().combinedJMutations().invert()
                                .combineWith(nodeContent.getFromRootToThis().combinedJMutations())
                ));


        return new DebugInfo(
                tree.getTreeId(),
                tree.getRootInfo(),
                nodeContent.getFromRootToThis().getVMutationsWithoutCDR3().stream()
                        .map(it -> it.getRangeInfo().getRange())
                        .collect(Collectors.toList()),
                nodeContent.getFromRootToThis().getJMutationsWithoutCDR3().stream()
                        .map(it -> it.getRangeInfo().getRange())
                        .collect(Collectors.toList()),
                cloneId,
                nodeWithParent.getNode().getContent().getId(),
                Optional.ofNullable(nodeWithParent.getParent()).map(it -> it.getContent().getId()).orElse(null),
                nodeContent.getFromRootToThis().getKnownNDN().buildSequence(),
                new DebugInfo.MutationsSet(
                        nodeContent.getFromRootToThis().combinedVMutations(),
                        nodeContent.getFromRootToThis().getKnownNDN().getMutations(),
                        nodeContent.getFromRootToThis().combinedJMutations()
                ),
                parentMutations.orElse(null),
                metric,
                isPublic(tree.getRootInfo())
        );
    }

    private boolean isPublic(RootInfo rootInfo) {
        return rootInfo.getReconstructedNDN().size() <= parameters.NDNSizeLimitForPublicClones;
    }

    public List<DebugInfo> debugInfos(List<TreeWithMetaBuilder> currentTrees) {
        return currentTrees.stream()
                .flatMap(tree -> tree.allNodes()
                        .filter(it -> it.getNode().getContent() instanceof TreeBuilderByAncestors.Reconstructed)
                        .map(nodeWithParent -> buildDebugInfo(Collections.emptyMap(), tree, nodeWithParent))
                )
                .collect(Collectors.toList());
    }

    private interface Step {
        StepResult next(List<TreeWithMetaBuilder> originalTrees, Supplier<List<CloneWithMutationsFromVJGermline>> clonesNotInClusters);
    }

    public static class StepResult {
        private final Map<Integer, ? extends DecisionInfo> decisions;
        private final List<TreeWithMetaBuilder.Snapshot> snapshots;
        private final List<DebugInfo> nodesDebugInfo;

        StepResult(Map<Integer, ? extends DecisionInfo> decisions, List<TreeWithMetaBuilder.Snapshot> snapshots, List<DebugInfo> nodesDebugInfo) {
            this.decisions = decisions;
            this.snapshots = snapshots;
            this.nodesDebugInfo = nodesDebugInfo;
        }

        public List<DebugInfo> getNodesDebugInfo() {
            return nodesDebugInfo;
        }

        public Map<Integer, ? extends DecisionInfo> getDecisions() {
            return decisions;
        }

        public List<TreeWithMetaBuilder.Snapshot> getSnapshots() {
            return snapshots;
        }
    }

    static class CalculatedClusterInfo {
        private final ClonesAlignmentRanges commonVAlignmentRanges;
        private final ClonesAlignmentRanges commonJAlignmentRanges;
        private final Range VRangeInCDR3;
        private final Range JRangeInCDR3;

        public CalculatedClusterInfo(ClonesAlignmentRanges commonVAlignmentRanges, ClonesAlignmentRanges commonJAlignmentRanges, Range VRangeInCDR3, Range JRangeInCDR3) {
            this.commonVAlignmentRanges = commonVAlignmentRanges;
            this.commonJAlignmentRanges = commonJAlignmentRanges;
            this.VRangeInCDR3 = VRangeInCDR3;
            this.JRangeInCDR3 = JRangeInCDR3;
        }
    }
}
