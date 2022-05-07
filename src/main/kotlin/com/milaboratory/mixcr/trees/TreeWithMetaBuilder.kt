package com.milaboratory.mixcr.trees;

import com.milaboratory.mixcr.basictypes.Clone;
import io.repseq.core.GeneType;

import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.milaboratory.mixcr.trees.MutationsUtils.mutationsBetween;

class TreeWithMetaBuilder {
    private final LinkedList<Integer> clonesAdditionHistory;
    private final TreeBuilderByAncestors<CloneWithMutationsFromReconstructedRoot, SyntheticNode, MutationsDescription> treeBuilder;
    private final RootInfo rootInfo;
    private final ClonesRebase clonesRebase;
    private final TreeId treeId;

    public TreeWithMetaBuilder copy() {
        return new TreeWithMetaBuilder(treeBuilder.copy(), rootInfo, clonesRebase, clonesAdditionHistory, treeId);
    }

    public TreeWithMetaBuilder(
            TreeBuilderByAncestors<CloneWithMutationsFromReconstructedRoot, SyntheticNode, MutationsDescription> treeBuilder,
            RootInfo rootInfo,
            ClonesRebase clonesRebase,
            LinkedList<Integer> clonesAdditionHistory,
            TreeId treeId
    ) {
        this.clonesRebase = clonesRebase;
        this.treeBuilder = treeBuilder;
        this.rootInfo = rootInfo;
        this.clonesAdditionHistory = clonesAdditionHistory;
        this.treeId = treeId;
    }

    public TreeId getTreeId() {
        return treeId;
    }

    public LinkedList<Integer> getClonesAdditionHistory() {
        return clonesAdditionHistory;
    }

    int clonesCount() {
        return clonesAdditionHistory.size();
    }

    /**
     * first parent is copy of clone, we need grandparent
     */
    public SyntheticNode getEffectiveParent(Clone clone) {
        return treeBuilder.getTree().allNodes()
                .filter(nodeWithParent -> nodeWithParent.getNode().getLinks().stream()
                        .map(link -> link.getNode().getContent().convert(it -> Optional.of(it.getClone().clone.getId()), it -> Optional.<Integer>empty()))
                        .flatMap(Optional::stream)
                        .anyMatch(cloneId -> clone.getId() == cloneId)
                )
                .map(Tree.NodeWithParent::getParent)
                .findAny()
                .orElseThrow(() -> new IllegalStateException("clone not found in the tree"))
                .getContent().convert(it -> Optional.<SyntheticNode>empty(), Optional::of)
                .orElseThrow(IllegalStateException::new);
    }

    CloneWithMutationsFromReconstructedRoot rebaseClone(CloneWithMutationsFromVJGermline clone) {
        return clonesRebase.rebaseClone(rootInfo, clone.getMutations(), clone.getCloneWrapper());
    }

    void addClone(CloneWithMutationsFromReconstructedRoot rebasedClone) {
        treeBuilder.addNode(rebasedClone);
        clonesAdditionHistory.add(rebasedClone.getClone().clone.getId());
    }

    RootInfo getRootInfo() {
        return rootInfo;
    }

    SyntheticNode oldestReconstructedAncestor() {
        //TODO check that there is only one direct child of the root
        TreeBuilderByAncestors.Reconstructed<CloneWithMutationsFromReconstructedRoot, SyntheticNode> oldestReconstructedAncestor = (TreeBuilderByAncestors.Reconstructed<CloneWithMutationsFromReconstructedRoot, SyntheticNode>) treeBuilder.getTree().getRoot()
                .getLinks()
                .get(0)
                .getNode()
                .getContent();
        return oldestReconstructedAncestor.getContent();
    }

    Tree<CloneOrFoundAncestor> buildResult() {
        var reconstructedRoot = oldestReconstructedAncestor();
        var fromGermlineToReconstructedRoot = reconstructedRoot.getFromRootToThis();
        return treeBuilder.getTree().map((parent, node) -> {
            MutationsDescription fromGermlineToParent;
            if (parent != null) {
                fromGermlineToParent = asMutations(parent);
            } else {
                fromGermlineToParent = null;
            }
            var nodeAsMutationsFromGermline = asMutations(node);
            BigDecimal distanceFromReconstructedRootToNode;
            if (parent != null) {
                distanceFromReconstructedRootToNode = treeBuilder.distance.apply(
                        reconstructedRoot,
                        mutationsBetween(reconstructedRoot.getFromRootToThis(), nodeAsMutationsFromGermline)
                );
            } else {
                distanceFromReconstructedRootToNode = null;
            }
            var rootAsNode = treeBuilder.getTree().getRoot().getContent()
                    .convert(it -> Optional.<SyntheticNode>empty(), Optional::of)
                    .orElseThrow();
            var distanceFromGermlineToNode = treeBuilder.distance.apply(rootAsNode, nodeAsMutationsFromGermline);
            return node.convert(
                    c -> new CloneOrFoundAncestor.CloneInfo(
                            c.getClone(),
                            node.getId(),
                            nodeAsMutationsFromGermline,
                            fromGermlineToReconstructedRoot,
                            fromGermlineToParent,
                            distanceFromReconstructedRootToNode,
                            distanceFromGermlineToNode
                    ),
                    ancestor -> new CloneOrFoundAncestor.AncestorInfo(
                            node.getId(),
                            nodeAsMutationsFromGermline,
                            fromGermlineToReconstructedRoot,
                            fromGermlineToParent,
                            distanceFromReconstructedRootToNode,
                            distanceFromGermlineToNode
                    )
            );
        });
    }

    private MutationsDescription asMutations(TreeBuilderByAncestors.ObservedOrReconstructed<CloneWithMutationsFromReconstructedRoot, SyntheticNode> parent) {
        return parent.convert(CloneWithMutationsFromReconstructedRoot::getMutationsFromRoot, SyntheticNode::getFromRootToThis);
    }

    Stream<Tree.NodeWithParent<TreeBuilderByAncestors.ObservedOrReconstructed<CloneWithMutationsFromReconstructedRoot, SyntheticNode>>> allNodes() {
        return treeBuilder.getTree().allNodes();
    }

    TreeBuilderByAncestors.Action bestAction(CloneWithMutationsFromReconstructedRoot rebasedClone) {
        TreeBuilderByAncestors.Action bestAction = treeBuilder.bestActionForObserved(rebasedClone);
        return new TreeBuilderByAncestors.Action() {
            @Override
            protected BigDecimal changeOfDistance() {
                return bestAction.changeOfDistance();
            }

            @Override
            protected BigDecimal distanceFromObserved() {
                return bestAction.distanceFromObserved();
            }

            @Override
            protected void apply() {
                bestAction.apply();
                clonesAdditionHistory.add(rebasedClone.getClone().clone.getId());
            }
        };
    }

    double distanceFromRootToClone(CloneWithMutationsFromReconstructedRoot rebasedClone) {
        return treeBuilder.distanceFromRootToObserved(rebasedClone).doubleValue();
    }

    Snapshot snapshot() {
        return new Snapshot(clonesAdditionHistory, rootInfo, treeId);
    }

    public static class TreeId {
        private final int id;
        private final VJBase VJBase;

        public TreeId(int id, VJBase VJBase) {
            this.id = id;
            this.VJBase = VJBase;
        }

        public int getId() {
            return id;
        }

        public String encode() {
            var result = new StringBuilder()
                    .append(VJBase.VGeneName);
            if (VJBase.CDR3length != null) {
                result.append("-").append(VJBase.CDR3length);
            }
            result.append("-").append(VJBase.JGeneName)
                    .append("-").append(id);
            return result.toString();
        }
    }

    static class Snapshot {
        //TODO save position and action description to skip recalculation
        private final List<Integer> clonesAdditionHistory;
        private final RootInfo rootInfo;
        private final TreeId treeId;

        public Snapshot(List<Integer> clonesAdditionHistory, RootInfo rootInfo, TreeId treeId) {
            this.clonesAdditionHistory = clonesAdditionHistory;
            this.rootInfo = rootInfo;
            this.treeId = treeId;
        }

        public TreeId getTreeId() {
            return treeId;
        }

        public List<Integer> getClonesAdditionHistory() {
            return clonesAdditionHistory;
        }

        public RootInfo getRootInfo() {
            return rootInfo;
        }

        Snapshot excludeClones(Set<Integer> toExclude) {
            return new Snapshot(
                    getClonesAdditionHistory().stream()
                            .filter(it -> !toExclude.contains(it))
                            .collect(Collectors.toCollection(LinkedList::new)),
                    getRootInfo(),
                    treeId);
        }
    }

    interface DecisionInfo {
    }

    static class ZeroStepDecisionInfo implements DecisionInfo {
        private final int commonMutationsCount;
        private final String VGeneName;
        private final String JGeneName;

        private final float VHitScore;
        private final float JHitScore;

        public ZeroStepDecisionInfo(int commonMutationsCount, String VGeneName, String JGeneName, float VHitScore, float JHitScore) {
            this.commonMutationsCount = commonMutationsCount;
            this.VGeneName = VGeneName;
            this.JGeneName = JGeneName;
            this.VHitScore = VHitScore;
            this.JHitScore = JHitScore;
        }

        public int getCommonMutationsCount() {
            return commonMutationsCount;
        }

        public String getGeneName(GeneType geneType) {
            switch (geneType) {
                case Variable:
                    return VGeneName;
                case Joining:
                    return JGeneName;
                default:
                    throw new IllegalArgumentException();
            }
        }

        public float getScore(GeneType geneType) {
            switch (geneType) {
                case Variable:
                    return VHitScore;
                case Joining:
                    return JHitScore;
                default:
                    throw new IllegalArgumentException();
            }
        }
    }


    static class MetricDecisionInfo implements DecisionInfo {
        private final double metric;

        public MetricDecisionInfo(double metric) {
            this.metric = metric;
        }

        public double getMetric() {
            return metric;
        }
    }

}
