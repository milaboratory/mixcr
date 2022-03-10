package com.milaboratory.mixcr.trees;

import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.util.Java9Util;
import io.repseq.core.GeneType;

import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class TreeWithMetaBuilder {
    private final LinkedList<Integer> clonesAdditionHistory;
    private final TreeBuilderByAncestors<CloneWithMutationsFromReconstructedRoot, SyntheticNode, MutationsDescription> treeBuilder;
    private final RootInfo rootInfo;
    private final ClonesRebase clonesRebase;

    public TreeWithMetaBuilder copy() {
        return new TreeWithMetaBuilder(treeBuilder.copy(), rootInfo, clonesRebase, clonesAdditionHistory);
    }

    public TreeWithMetaBuilder(
            TreeBuilderByAncestors<CloneWithMutationsFromReconstructedRoot, SyntheticNode, MutationsDescription> treeBuilder,
            RootInfo rootInfo,
            ClonesRebase clonesRebase,
            LinkedList<Integer> clonesAdditionHistory
    ) {
        this.clonesRebase = clonesRebase;
        this.treeBuilder = treeBuilder;
        this.rootInfo = rootInfo;
        this.clonesAdditionHistory = clonesAdditionHistory;
    }

    int clonesCount() {
        return clonesAdditionHistory.size();
    }

    public LinkedList<Integer> getClonesAdditionHistory() {
        return clonesAdditionHistory;
    }

    /**
     * first parent is copy of clone, we need grandparent
     */
    public SyntheticNode getEffectiveParent(Clone clone) {
        return treeBuilder.getTree().allNodes()
                .filter(nodeWithParent -> nodeWithParent.getNode().getLinks().stream()
                        .map(link -> link.getNode().getContent().convert(it -> Optional.of(it.getClone().clone.getId()), it -> Optional.<Integer>empty()))
                        .flatMap(Java9Util::stream)
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

    Tree<TreeBuilderByAncestors.ObservedOrReconstructed<CloneWrapper, AncestorInfo>> buildResult() {
        AncestorInfoBuilder ancestorInfoBuilder = new AncestorInfoBuilder();
        return treeBuilder.getTree().map(node -> node.map(
                CloneWithMutationsFromReconstructedRoot::getClone,
                ancestor -> ancestorInfoBuilder.buildAncestorInfo(ancestor.getFromRootToThis())
        ));
    }

    Stream<CloneWithMutationsFromReconstructedRoot> allClones() {
        return treeBuilder.getTree().allNodes()
                .map(Tree.NodeWithParent::getNode)
                .map(node -> node.getContent().convert(Optional::of, it -> Optional.<CloneWithMutationsFromReconstructedRoot>empty()))
                .flatMap(Java9Util::stream);
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

    String print(XmlTreePrinter<TreeBuilderByAncestors.ObservedOrReconstructed<CloneWithMutationsFromReconstructedRoot, SyntheticNode>> printer) {
        return printer.print(treeBuilder.getTree());
    }

    Snapshot snapshot() {
        return new Snapshot(clonesAdditionHistory, rootInfo);
    }

    static class Snapshot {
        private final LinkedList<Integer> clonesAdditionHistory;
        private final RootInfo rootInfo;

        public Snapshot(LinkedList<Integer> clonesAdditionHistory, RootInfo rootInfo) {
            this.clonesAdditionHistory = clonesAdditionHistory;
            this.rootInfo = rootInfo;
        }

        public LinkedList<Integer> getClonesAdditionHistory() {
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
                    getRootInfo()
            );
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
