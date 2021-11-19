package com.milaboratory.mixcr.trees;

import com.google.common.collect.Lists;
import com.milaboratory.mixcr.trees.TreeBuilderByAncestors.Observed;
import com.milaboratory.mixcr.trees.TreeBuilderByAncestors.ObservedOrReconstructed;
import com.milaboratory.mixcr.trees.TreeBuilderByAncestors.Reconstructed;
import org.junit.Ignore;
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
import static org.junit.Assert.assertTrue;

public class TreeBuilderByAncestorsTest {
    private final TreePrinter<ObservedOrReconstructed<List<Integer>, List<Integer>>> treePrinter = new NewickTreePrinter<>(
            node -> node.convert(this::print, content -> "'" + print(content) + "'"),
            true,
            false
    );
    private final NewickTreePrinter<List<Integer>> treePrinterOnlyReal = new NewickTreePrinter<>(
            this::print,
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
    public void chooseInsertionNearerToRealNode() {
        Tree<List<Integer>> orignial = new Tree<>(
                new Tree.Node<>(parseNode("0000000000"))
                        .addChild(new Tree.Node<>(parseNode("1010000000"))
                                .addChild(new Tree.Node<>(parseNode("1110000001"))
                                        .addChild(new Tree.Node<>(parseNode("1110001101")))))
                        .addChild(new Tree.Node<>(parseNode("0000000001"))
                                .addChild(new Tree.Node<>(parseNode("0100000001"))
                                        .addChild(new Tree.Node<>(parseNode("0110001001"))
                                                .addChild(new Tree.Node<>(parseNode("1111001001"))
                                                        .addChild(new Tree.Node<>(parseNode("1111001101")))))))
                        .addChild(new Tree.Node<>(parseNode("0100000000"))
                                .addChild(new Tree.Node<>(parseNode("1100100000"))
                                        .addChild(new Tree.Node<>(parseNode("1110100000"))
                                                .addChild(new Tree.Node<>(parseNode("1110101000"))
                                                        .addChild(new Tree.Node<>(parseNode("1110101011")))))))
        );
        TreeBuilderByAncestors<List<Integer>, List<Integer>> treeBuilder = treeBuilder(parseNode("0000000000"));
        List<List<Integer>> allNodes = orignial.allNodes().map(Tree.Node::getContent)
                .sorted(Comparator.comparing(it -> it.stream().mapToInt(i -> i).sum()))
                .collect(Collectors.toList());
        allNodes.subList(1, allNodes.size()).forEach(toAdd -> {
            System.out.println(treePrinter.print(treeBuilder.getTree()));
            treeBuilder.addNode(toAdd);
        });

        BigDecimal sumOfDistancesInOriginal = calculateDistances(orignial, this::distance).getRoot().sumOfDistancesToDescendants();
        BigDecimal sumOfDistancesInBuild = calculateDistances(withoutSynthetic(treeBuilder.getTree()), this::distance).getRoot().sumOfDistancesToDescendants();
        System.out.println("expected:");
        System.out.println(treePrinterOnlyReal.print(calculateDistances(orignial, this::distance)));
        System.out.println("actual:");
        System.out.println(treePrinterOnlyReal.print(calculateDistances(withoutSynthetic(treeBuilder.getTree()), this::distance)));
        System.out.println(treePrinter.print(treeBuilder.getTree()));
        assertTrue(sumOfDistancesInOriginal.compareTo(sumOfDistancesInBuild) >= 0);
    }

    @Ignore
    @Test
    public void randomizedTest() {
        List<Long> failedSeeds = IntStream.range(0, 1_000_000)
                .mapToObj(it -> ThreadLocalRandom.current().nextLong())
                .filter(seed -> {
                    Random random = new Random(seed);

                    int arrayLength = 10;
                    int depth = 5;
                    Supplier<Integer> branchesCount = () -> 1 + random.nextInt(2);
                    Supplier<Integer> mutationsCount = () -> 1 + random.nextInt(2);

//                    int arrayLength = 5;
//                    int depth = 3;
//                    int branchesCount = 1 + random.nextInt(3);
//                    Supplier<Integer> mutationsCount = () -> 1 + random.nextInt(1);

                    boolean print = false;

                    List<Integer> root = new ArrayList<>();
                    for (int i = 0; i < arrayLength; i++) {
                        root.add(0);
                    }

                    Tree.Node<List<Integer>> rootNode = new Tree.Node<>(root);
                    Tree<List<Integer>> tree = new Tree<>(rootNode);
                    Set<List<Integer>> insertedLeaves = new HashSet<>();

                    for (int branchNumber = 0; branchNumber < branchesCount.get(); branchNumber++) {
                        List<Tree.Node<List<Integer>>> nodes = Collections.singletonList(rootNode);
                        for (int j = 0; j < depth; j++) {
                            nodes = nodes.stream()
                                    .flatMap(node -> insetChildren(random, mutationsCount, insertedLeaves, node, branchesCount).stream())
                                    .collect(Collectors.toList());
                            if (nodes.isEmpty()) break;
                        }
                    }

                    TreeBuilderByAncestors<List<Integer>, List<Integer>> treeBuilder = treeBuilder(root);
                    insertedLeaves.stream()
                            .sorted(Comparator.comparing(leaf -> distance(root, leaf)))
                            .forEach(toAdd -> {
                                //noinspection ConstantConditions
                                if (print) {
                                    System.out.println(treePrinter.print(treeBuilder.getTree()));
                                }
                                treeBuilder.addNode(toAdd);
                            });

                    BigDecimal sumOfDistancesInOriginal = sumOfDistances(calculateDistances(tree, this::distance));
                    BigDecimal sumOfDistancesInConstructed = sumOfDistances(calculateDistances(withoutSynthetic(treeBuilder.getTree()), this::distance));
                    boolean fails = sumOfDistancesInOriginal.compareTo(sumOfDistancesInConstructed) < 0;
                    if (fails) {
                        System.out.println("expected:");
                        System.out.println(treePrinterOnlyReal.print(tree));
                        System.out.println("actual:");
                        System.out.println(treePrinterOnlyReal.print(calculateDistances(withoutSynthetic(treeBuilder.getTree()), this::distance)));
                        System.out.println(treePrinter.print(this.calculateDistances(treeBuilder.getTree(), (a, b) -> distance(getContent(a), getContent(b)))));
                        System.out.println("seed:");
                        System.out.println(seed);
                        System.out.println();
                    }
                    return fails;
                })
                .collect(Collectors.toList());

        assertEquals(Collections.emptyList(), failedSeeds);
    }

    private List<Tree.Node<List<Integer>>> insetChildren(Random random, Supplier<Integer> mutationsCount, Set<List<Integer>> insertedLeaves, Tree.Node<List<Integer>> parent, Supplier<Integer> branchesCount) {
        return IntStream.range(0, branchesCount.get())
                .mapToObj(index -> {
                    List<Integer> possiblePositionsToMutate = IntStream.range(0, parent.getContent().size())
                            .filter(i -> parent.getContent().get(i) == 0)
                            .boxed()
                            .collect(Collectors.toList());
                    Collections.shuffle(possiblePositionsToMutate, random);
                    possiblePositionsToMutate = possiblePositionsToMutate
                            .subList(0, Math.min(possiblePositionsToMutate.size() - 1, mutationsCount.get()));
                    List<Integer> leaf = new ArrayList<>(parent.getContent());
                    possiblePositionsToMutate.forEach(it -> leaf.set(it, 1));
                    if (insertedLeaves.contains(leaf)) {
                        return null;
                    } else {
                        insertedLeaves.add(leaf);
                        Tree.Node<List<Integer>> inserted = new Tree.Node<>(leaf);
                        parent.addChild(inserted, distance(parent.getContent(), leaf));
                        return inserted;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private BigDecimal sumOfDistances(Tree<?> tree) {
        return tree.getRoot().sumOfDistancesToDescendants();
    }

    private Tree<List<Integer>> withoutSynthetic(Tree<ObservedOrReconstructed<List<Integer>, List<Integer>>> original) {
        List<Integer> rootContent = ((Reconstructed<List<Integer>, List<Integer>>) original.getRoot().getContent()).getContent();
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

    private void copyRealNodes(Tree.Node<ObservedOrReconstructed<List<Integer>, List<Integer>>> copyFrom,
                               Tree.Node<List<Integer>> copyTo) {
        for (Tree.NodeLink<ObservedOrReconstructed<List<Integer>, List<Integer>>> link : copyFrom.getLinks()) {
            if (Objects.equals(link.getDistance(), BigDecimal.ZERO)) {
                continue;
            }
            Tree.Node<ObservedOrReconstructed<List<Integer>, List<Integer>>> node = link.getNode();

            if (node.getContent() instanceof TreeBuilderByAncestors.Observed<?, ?>) {
                List<Integer> content = ((Observed<List<Integer>, List<Integer>>) node.getContent()).getContent();
                copyTo.addChild(new Tree.Node<>(content));
            } else if (node.getContent() instanceof TreeBuilderByAncestors.Reconstructed<?, ?>) {
                Optional<Observed<List<Integer>, List<Integer>>> realWithDistanceZero = node.getLinks().stream()
                        .filter(it -> Objects.equals(it.getDistance(), BigDecimal.ZERO))
                        .map(it -> (Observed<List<Integer>, List<Integer>>) it.getNode().getContent())
                        .findAny();
                Tree.Node<List<Integer>> nextNode;
                if (realWithDistanceZero.isPresent()) {
                    nextNode = new Tree.Node<>(realWithDistanceZero.get().getContent());
                    copyTo.addChild(nextNode);
                } else {
                    nextNode = copyTo;
                }
                copyRealNodes(node, nextNode);
            } else {
                throw new IllegalArgumentException();
            }
        }
    }

    private List<Integer> parseNode(String node) {
        return node.chars().mapToObj(number -> Integer.valueOf(String.valueOf((char) number))).collect(Collectors.toList());
    }

    private List<Integer> getContent(ObservedOrReconstructed<List<Integer>, List<Integer>> content) {
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
