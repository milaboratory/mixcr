package com.milaboratory.mixcr.trees;

import com.google.common.collect.Lists;
import com.milaboratory.mixcr.trees.TreeBuilderByAncestors.Real;
import com.milaboratory.mixcr.trees.TreeBuilderByAncestors.RealOrSynthetic;
import com.milaboratory.mixcr.trees.TreeBuilderByAncestors.Synthetic;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

public class TreeBuilderByAncestorsTest {
    private final TreePrinter<RealOrSynthetic<List<Integer>, List<Integer>>> treePrinter = new NewickTreePrinter<>(
            node -> node.convert(this::print, content -> "'" + print(content) + "'"),
            true,
            false
    );


    /**
     * <pre>
     *          |
     *         (A)-0-A
     * =======
     *  A ⊂ B,
     *  M1: A*M1 = B
     * =======
     *          |
     *     ----(A)-0-A
     *     |
     *    |M1|
     *     |
     *    (B)-0-B
     * </pre>
     */
    @Test
    public void addDirectAncestor() {
        TreeBuilderByAncestors<List<Integer>, List<Integer>> treeBuilder = treeBuilder(Lists.newArrayList(1, 0, 0))
                .addNode(Lists.newArrayList(1, 1, 0));
        assertTreeBuilder("((110:0)'110':1,100:0)'100';", treeBuilder);
    }

    /**
     * <pre>
     *          |
     *     ----(A)-0-A
     *     |
     *    |M1|
     *     |
     *    (B)-0-B
     * =======
     *  A ⊂ C, B ⊄ C
     *  M2: A*M2 = C,
     *  M1⋂M2 = ∅
     * =======
     *          |
     *     ----(A)-------------
     *     |         |        |
     *    |M1|      |M2|      0
     *     |         |        |
     *    (B)-0-B   (C)-0-C   A
     * </pre>
     */
    @Test
    public void addSecondDirectAncestor() {
        TreeBuilderByAncestors<List<Integer>, List<Integer>> treeBuilder = treeBuilder(Lists.newArrayList(1, 0, 0))
                .addNode(Lists.newArrayList(1, 1, 0))
                .addNode(Lists.newArrayList(1, 0, 1));
        assertTreeBuilder("((101:0)'101':1,(110:0)'110':1,100:0)'100';", treeBuilder);
    }

    /**
     * <pre>
     *          |
     *     ----(A)-------------
     *     |         |        |
     *    |M1|      |M2|      0
     *     |         |        |
     *    (B)-0-B   (C)-0-C   A
     * =======
     *  A ⊂ D, B ⊄ D, C ⊄ D
     *  M3: A*M3 = D,
     *  M1⋂M3 = ∅, M2⋂M3 = ∅,
     * =======
     *          |
     *     ----(A)----------------------
     *     |         |        |        |
     *    |M1|      |M2|     |M3|      0
     *     |         |        |        |
     *    (B)-0-B   (C)-0-C  (D)-0-D   A
     * </pre>
     */
    @Test
    public void addThirdDirectAncestor() {
        TreeBuilderByAncestors<List<Integer>, List<Integer>> treeBuilder = treeBuilder(Lists.newArrayList(0, 0, 0))
                .addNode(Lists.newArrayList(1, 0, 0))
                .addNode(Lists.newArrayList(0, 1, 0))
                .addNode(Lists.newArrayList(0, 0, 1));
        assertTreeBuilder("((001:0)'001':1,(010:0)'010':1,(100:0)'100':1,000:0)'000';", treeBuilder);
    }

    /**
     * <pre>
     *          |
     *         (A)-0-A
     * =======
     *  A ⊄ B,
     *  C: C ⊂ A, C ⊂ B, A ⊂ C
     *  M1: C*M1 = A,
     *  M2: C*M2 = B
     * =======
     *          |
     *     ----(C)----
     *     |         |
     *    |M1|      |M2|
     *     |         |
     *    (A)-0-A   (B)-0-B
     * </pre>
     */
    @Test
    public void addNodeWithIntersection() {
        TreeBuilderByAncestors<List<Integer>, List<Integer>> treeBuilder = treeBuilder(Lists.newArrayList(1, 1, 0))
                .addNode(Lists.newArrayList(1, 0, 1));
        assertTreeBuilder("((101:0)'101':1,(110:0)'110':1)'100';", treeBuilder);
    }

    /**
     * <pre>
     *          |
     *     ----(A)------------
     *     |         |       |
     *    |M1|      |M2|     0
     *     |         |       |
     *    (B)-0-B   (C)-0-C  A
     * =======
     *  D ⊄ A, D ⊄ B
     *  B <-> D < A <-> D
     *  B <-> D < C <-> D
     *  E: E ⊂ B, E ⊂ D, A ⊂ E
     *  M3: A*M3 = E,
     *  M4: E*M4 = B,
     *  M5: E*M5 = D
     * =======
     *            |
     *       ----(A)------------
     *       |         |       |
     *      |M3|      |M2|     0
     *       |         |       |
     *  ----(E)----   (C)-0-C  A
     *  |         |
     * |M4|      |M5|
     *  |         |
     * (B)-0-B   (D)-0-D
     *
     * </pre>
     */
    @Test
    public void addNodeWithIntersectionOnSecondLevel() {
        TreeBuilderByAncestors<List<Integer>, List<Integer>> treeBuilder = treeBuilder(Lists.newArrayList(0, 0, 0, 0, 0))//A
                .addNode(Lists.newArrayList(0, 0, 0, 0, 1))//C
                .addNode(Lists.newArrayList(1, 1, 1, 0, 0))//B
                .addNode(Lists.newArrayList(1, 0, 1, 1, 0));//D
        assertTreeBuilder("(((10110:0)'10110':1,(11100:0)'11100':1)'10100':2,(00001:0)'00001':1,00000:0)'00000';", treeBuilder);
    }

    /**
     * <pre>
     *          |
     *     ----(A)------------
     *     |         |       |
     *    |M1|      |M2|     0
     *     |         |       |
     *    (B)-0-B   (C)-0-C  A
     * =======
     *  D ⊄ A, D ⊄ B
     *  B <-> D > A <-> D
     *  B <-> D < C <-> D
     *  E: E ⊂ B, E ⊂ D, A ⊂ E
     *  M3: A*M3 = E,
     *  M4: E*M4 = B,
     *  M5: E*M5 = D
     * =======
     *            |
     *       ----(A)------------
     *       |         |       |
     *      |M3|      |M2|     0
     *       |         |       |
     *  ----(E)----   (C)-0-C  A
     *  |         |
     * |M4|      |M5|
     *  |         |
     * (B)-0-B   (D)-0-D
     *
     * </pre>
     */
    @Test
    public void addNodeWithIntersectionOnSecondLevelNearerRoot() {
        TreeBuilderByAncestors<List<Integer>, List<Integer>> treeBuilder = treeBuilder(Lists.newArrayList(0, 0, 0, 0, 0, 0))//A
                .addNode(Lists.newArrayList(0, 0, 0, 0, 0, 1))//C
                .addNode(Lists.newArrayList(1, 1, 1, 0, 0, 0))//B
                .addNode(Lists.newArrayList(1, 0, 0, 1, 1, 0));//D
        assertTreeBuilder("(((100110:0)'100110':2,(111000:0)'111000':2)'100000':1,(000001:0)'000001':1,000000:0)'000000';", treeBuilder);
    }

    @Test
    public void chooseBestResultOfInsertion() {
        TreeBuilderByAncestors<List<Integer>, List<Integer>> treeBuilder = treeBuilder(Lists.newArrayList(0, 0, 0, 0, 0))
                .addNode(Lists.newArrayList(1, 0, 1, 0, 0))
                .addNode(Lists.newArrayList(0, 1, 1, 0, 1))
                .addNode(Lists.newArrayList(1, 1, 1, 0, 1))
                .addNode(Lists.newArrayList(1, 0, 1, 1, 1));
        assertTreeBuilder("((((10111:0)'10111':2,10100:0)'10100':1,((11101:0)'11101':1,01101:0)'01101':2)'00100':1,00000:0)'00000';", treeBuilder);
    }

    @Test
    public void randomizedTest() {
        List<Long> failedSeeds = IntStream.range(0, 1_000)
                .mapToObj(it -> ThreadLocalRandom.current().nextLong())
                .filter(seed -> {
                    seed = -3093562264026028548L;
                    Random random = new Random(seed);

                    int arrayLength = 10;
                    int depth = 5;
                    int branchesCount = 1 + random.nextInt(3);
                    Supplier<Integer> mutationsCount = () -> 1 + random.nextInt(2);

//                    int arrayLength = 5;
//                    int depth = 3;
//                    int branchesCount = 1 + random.nextInt(3);
//                    Supplier<Integer> mutationsCount = () -> 1 + random.nextInt(1);

                    boolean print = true;

                    List<Integer> root = new ArrayList<>();
                    for (int i = 0; i < arrayLength; i++) {
                        root.add(0);
                    }

                    Tree.Node<List<Integer>> rootNode = new Tree.Node<>(root);
                    Tree<List<Integer>> tree = new Tree<>(rootNode);
                    Set<List<Integer>> insertedLeaves = new HashSet<>();

                    for (int branchNumber = 0; branchNumber < branchesCount; branchNumber++) {
                        Tree.Node<List<Integer>> parent = rootNode;
                        for (int j = 0; j < depth; j++) {
                            Tree.Node<List<Integer>> parentFinal = parent;
                            List<Integer> possiblePositionsToMutate = IntStream.range(0, parent.getContent().size())
                                    .filter(i -> parentFinal.getContent().get(i) == 0)
                                    .boxed()
                                    .collect(Collectors.toList());
                            Collections.shuffle(possiblePositionsToMutate, random);
                            possiblePositionsToMutate = possiblePositionsToMutate
                                    .subList(0, Math.min(possiblePositionsToMutate.size() - 1, mutationsCount.get()));
                            List<Integer> leaf = new ArrayList<>(parent.getContent());
                            possiblePositionsToMutate.forEach(it -> leaf.set(it, 1));
                            if (insertedLeaves.contains(leaf)) {
                                break;
                            } else {
                                insertedLeaves.add(leaf);
                                Tree.Node<List<Integer>> inserted = new Tree.Node<>(leaf);
                                parent.addChild(inserted, distance(parent.getContent(), leaf));
                                parent = inserted;
                            }
                        }
                    }

                    TreeBuilderByAncestors<List<Integer>, List<Integer>> treeBuilder = treeBuilder(root);
                    insertedLeaves.stream()
                            .sorted(Comparator.comparing(leaf -> distance(root, leaf)))
                            .forEach(toAdd -> {
                                if (print) {
                                    System.out.println(this.treePrinter.print(treeBuilder.getTree()));
                                }
                                treeBuilder.addNode(toAdd);
                            });

                    NewickTreePrinter<List<Integer>> treePrinter = new NewickTreePrinter<>(
                            this::print,
                            true,
                            false
                    );

                    BigDecimal sumOfDistancesInOriginal = sumOfDistances(calculateDistances(tree, this::distance));
                    BigDecimal sumOfDistancesInConstructed = sumOfDistances(calculateDistances(withoutSynthetic(treeBuilder.getTree()), this::distance));
                    boolean fails = sumOfDistancesInOriginal.compareTo(sumOfDistancesInConstructed) < 0;
                    if (fails) {
                        System.out.println("expected:");
                        System.out.println(treePrinter.print(tree));
                        System.out.println("actual:");
                        System.out.println(treePrinter.print(calculateDistances(withoutSynthetic(treeBuilder.getTree()), this::distance)));
                        System.out.println(this.treePrinter.print(this.calculateDistances(treeBuilder.getTree(), (a, b) -> distance(getContent(a), getContent(b)))));
                        System.out.println("seed:");
                        System.out.println(seed);
                        System.out.println();
                    }
                    return fails;
                })
                .collect(Collectors.toList());

        assertEquals(Collections.emptyList(), failedSeeds);
    }

    private BigDecimal sumOfDistances(Tree<?> tree) {
        return tree.getRoot().sumOfDistancesToDescendants();
    }

    private Tree<List<Integer>> withoutSynthetic(Tree<RealOrSynthetic<List<Integer>, List<Integer>>> original) {
        List<Integer> rootContent = ((Synthetic<List<Integer>, List<Integer>>) original.getRoot().getContent()).getContent();
        Tree.Node<List<Integer>> rootNode = new Tree.Node<>(rootContent);
        copyRealNodes(original.getRoot(), rootNode);
        return new Tree<>(rootNode);
    }

    private <T> Tree<T> calculateDistances(Tree<T> tree, BiFunction<T, T, BigDecimal> distance) {
        Tree.Node<T> rootNode = new Tree.Node<>(tree.getRoot().getContent());
        copyWithDistance(tree.getRoot(), rootNode, distance);
        return new Tree<>(rootNode);
    }

    private <T> void copyWithDistance(Tree.Node<T> copyFrom, Tree.Node<T> copyTo, BiFunction<T, T, BigDecimal> distance) {
        for (Tree.NodeLink<T> link : copyFrom.getLinks()) {
            Tree.Node<T> from = link.getNode();
            Tree.Node<T> node = new Tree.Node<>(from.getContent());
            copyTo.addChild(node, distance.apply(copyFrom.getContent(), from.getContent()));
            copyWithDistance(from, node, distance);
        }
    }

    private void copyRealNodes(Tree.Node<RealOrSynthetic<List<Integer>, List<Integer>>> copyFrom,
                               Tree.Node<List<Integer>> copyTo) {
        for (Tree.NodeLink<RealOrSynthetic<List<Integer>, List<Integer>>> link : copyFrom.getLinks()) {
            if (Objects.equals(link.getDistance(), BigDecimal.ZERO)) {
                continue;
            }
            Tree.Node<RealOrSynthetic<List<Integer>, List<Integer>>> node = link.getNode();

            if (node.getContent() instanceof Real<?, ?>) {
                List<Integer> content = ((Real<List<Integer>, List<Integer>>) node.getContent()).getContent();
                copyTo.addChild(new Tree.Node<>(content), null);
            } else if (node.getContent() instanceof Synthetic<?, ?>) {
                Optional<Real<List<Integer>, List<Integer>>> realWithDistanceZero = node.getLinks().stream()
                        .filter(it -> Objects.equals(it.getDistance(), BigDecimal.ZERO))
                        .map(it -> (Real<List<Integer>, List<Integer>>) it.getNode().getContent())
                        .findAny();
                Tree.Node<List<Integer>> nextNode;
                if (realWithDistanceZero.isPresent()) {
                    nextNode = new Tree.Node<>(realWithDistanceZero.get().getContent());
                    copyTo.addChild(nextNode, null);
                } else {
                    nextNode = copyTo;
                }
                copyRealNodes(node, nextNode);
            } else {
                throw new IllegalArgumentException();
            }
        }
    }

    private List<Integer> getContent(RealOrSynthetic<List<Integer>, List<Integer>> content) {
        return content.convert(Function.identity(), Function.identity());
    }

    private void assertTreeBuilder(String expected, TreeBuilderByAncestors<List<Integer>, List<Integer>> treeBuilder) {
        assertEquals(expected, treePrinter.print(treeBuilder.getTree()));
    }

    private TreeBuilderByAncestors<List<Integer>, List<Integer>> treeBuilder(List<Integer> root) {
        return new TreeBuilderByAncestors<>(
                root,
                this::distance,
                this::commonAncestor,
                Function.identity()
        );
    }

    private List<Integer> commonAncestor(List<Integer> first, List<Integer> second) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < first.size(); i++) {
            if (!Objects.equals(first.get(i), second.get(i))) {
                result.add(0);
            } else {
                result.add(first.get(i));
            }
        }
        return result;
    }

    private BigDecimal distance(List<Integer> first, List<Integer> second) {
        int result = 0;
        for (int i = 0; i < first.size(); i++) {
            if (!Objects.equals(first.get(i), second.get(i))) {
                result++;
            }
        }
        return BigDecimal.valueOf(result);
    }

    private String print(List<Integer> node) {
        return node.stream().map(String::valueOf).collect(Collectors.joining(""));
    }
}
