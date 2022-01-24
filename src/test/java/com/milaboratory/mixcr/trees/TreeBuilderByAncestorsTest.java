package com.milaboratory.mixcr.trees;

import com.google.common.collect.Lists;
import com.milaboratory.mixcr.trees.TreeBuilderByAncestors.Observed;
import com.milaboratory.mixcr.trees.TreeBuilderByAncestors.ObservedOrReconstructed;
import com.milaboratory.mixcr.trees.TreeBuilderByAncestors.Reconstructed;
import org.junit.Ignore;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.*;

public class TreeBuilderByAncestorsTest {
    private final TreePrinter<ObservedOrReconstructed<List<Integer>, List<Integer>>> treePrinter = new NewickTreePrinter<>(
            node -> node.getContent().convert(this::print, content -> "'" + print(content) + "'"),
            true,
            false
    );
    private final TreePrinter<ObservedOrReconstructed<List<Integer>, List<Integer>>> xmlTreePrinter = new XmlTreePrinter<>(
            nodeWithParent -> nodeWithParent.getNode().getContent().convert(this::print, content -> "-" + print(content) + "-")
    );
    private final NewickTreePrinter<List<Integer>> treePrinterOnlyReal = new NewickTreePrinter<>(
            node -> print(node.getContent()),
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
        Tree<ObservedOrReconstructed<List<Integer>, List<Integer>>> tree = treeBuilder(3)
                .addNode(Lists.newArrayList(1, 1, 0))
                .addNode(Lists.newArrayList(1, 0, 0))
                .getTree();
        assertTree("(((110:0)'110':1,100:0)'100':1)'000';", tree);
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
        Tree<ObservedOrReconstructed<List<Integer>, List<Integer>>> tree = treeBuilder(3)
                .addNode(Lists.newArrayList(1, 0, 1))
                .addNode(Lists.newArrayList(1, 1, 0))
                .addNode(Lists.newArrayList(1, 0, 0))
                .getTree();
        assertTree("(((101:0)'101':1,(110:0)'110':1,100:0)'100':1)'000';", tree);
    }

    @Test
    public void insertAsDescendantInsteadOfSiblingIfItChangeDistancesLess() {
        Tree<List<Integer>> original = new Tree<>(
                new Tree.Node<>(parseNode("0000"))
                        .addChild(new Tree.Node<>(parseNode("0010"))
                                .addChild(new Tree.Node<>(parseNode("0712"))))
                        .addChild(new Tree.Node<>(parseNode("0002"))
                                .addChild(new Tree.Node<>(parseNode("7702"))
                                        .addChild(new Tree.Node<>(parseNode("7782")))))
        );
        compareTrees(original, rebuildTree(original));
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
        Tree<ObservedOrReconstructed<List<Integer>, List<Integer>>> tree = treeBuilder(3)
                .addNode(Lists.newArrayList(0, 0, 1))
                .addNode(Lists.newArrayList(1, 0, 0))
                .addNode(Lists.newArrayList(0, 1, 0))
                .addNode(Lists.newArrayList(0, 0, 0))
                .getTree();
        assertTree("((001:0)'001':1,(010:0)'010':1,(100:0)'100':1,000:0)'000';", tree);
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
        Tree<ObservedOrReconstructed<List<Integer>, List<Integer>>> tree = treeBuilder(3)
                .addNode(Lists.newArrayList(1, 0, 1))
                .addNode(Lists.newArrayList(1, 1, 0))
                .getTree();
        assertTree("(((101:0)'101':1,(110:0)'110':1)'100':1)'000';", tree);
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
     *  A <-> D < B <-> D
     *  A <-> D < C <-> D
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
    public void addNodeWithReplacementOfSibling() {
        Tree<ObservedOrReconstructed<List<Integer>, List<Integer>>> tree = treeBuilder(6)
                .addNode(Lists.newArrayList(1, 0, 0, 1, 1, 0))//D
                .addNode(Lists.newArrayList(1, 1, 1, 0, 0, 0))//B
                .addNode(Lists.newArrayList(0, 0, 0, 0, 0, 1))//C
                .addNode(Lists.newArrayList(0, 0, 0, 0, 0, 0))//A
                .getTree();
        assertTree("(((100110:0)'100110':2,(111000:0)'111000':2)'100000':1,(000001:0)'000001':1,000000:0)'000000';", tree);
    }

    /**
     * If there are several nodes with the same distance from added node with must choose those that will minimize change of distances
     */
    @Test
    public void chooseBestResultOfInsertion() {
        Tree<List<Integer>> original = new Tree<>(
                new Tree.Node<>(parseNode("00000"))
                        .addChild(new Tree.Node<>(parseNode("01101"))
                                .addChild(new Tree.Node<>(parseNode("11101"))))
                        .addChild(new Tree.Node<>(parseNode("10100"))
                                .addChild(new Tree.Node<>(parseNode("10111")))));
        compareTrees(original, rebuildTree(original));
    }

    @Test
    public void dontAddReconstructedWithZeroDistanceFromParent() {
        Tree<List<Integer>> original = new Tree<>(
                new Tree.Node<>(parseNode("00000"))
                        .addChild(new Tree.Node<>(parseNode("42200"))
                                .addChild(new Tree.Node<>(parseNode("42204"))
                                        .addChild(new Tree.Node<>(parseNode("02204"))))));
        compareTrees(original, rebuildTree(original));
    }

    @Test
    public void useMinDistanceFromObservedForOptimization() {
        Tree<List<Integer>> original = new Tree<>(
                new Tree.Node<>(parseNode("00000"))
                        .addChild(new Tree.Node<>(parseNode("00405")))
                        .addChild(new Tree.Node<>(parseNode("08055")))
                        .addChild(new Tree.Node<>(parseNode("30000"))
                                .addChild(new Tree.Node<>(parseNode("38005")))));
        compareTrees(original, rebuildTree(original));
    }

    @Test
    public void chooseInsertionNearerToRealNode() {
        Tree<List<Integer>> original = new Tree<>(
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
        compareTrees(original, rebuildTree(original));
    }

    @Test
    public void chooseInsertionNearerToRealNodeWithReplaceOfReal() {
        Tree<List<Integer>> original = new Tree<>(
                new Tree.Node<>(parseNode("00000000"))
                        .addChild(new Tree.Node<>(parseNode("60000030"))
                                .addChild(new Tree.Node<>(parseNode("65000030"))
                                        .addChild(new Tree.Node<>(parseNode("65230030")))
                                        .addChild(new Tree.Node<>(parseNode("65000237"))))
                                .addChild(new Tree.Node<>(parseNode("60200032"))
                                        .addChild(new Tree.Node<>(parseNode("65250032")))
                                        .addChild(new Tree.Node<>(parseNode("60230032")))))
        );
        compareTrees(original, rebuildTree(original));
    }

    @Test
    public void commonAncestorCouldNotHaveMutationsThatNotContainsInAParent() {
        Tree<List<Integer>> original = new Tree<>(
                new Tree.Node<>(parseNode("0000"))
                        .addChild(new Tree.Node<>(parseNode("6000"))
                                .addChild(new Tree.Node<>(parseNode("6077"))))
                        .addChild(new Tree.Node<>(parseNode("0007"))
                                .addChild(new Tree.Node<>(parseNode("1077"))))
        );
        //direct comparison of 1077 and 6077 will yield 0077, but with parent 6000 we want them to yield 6077
        compareTrees(original, rebuildTree(original));
    }

    @Test
    public void reconstructedNodeEqualsToNextObserved() {
        Tree<List<Integer>> original = new Tree<>(
                new Tree.Node<>(parseNode("000"))
                        .addChild(new Tree.Node<>(parseNode("404"))
                                .addChild(new Tree.Node<>(parseNode("456")))
                                .addChild(new Tree.Node<>(parseNode("154")))
                                .addChild(new Tree.Node<>(parseNode("454"))))
        );
        Tree<ObservedOrReconstructed<List<Integer>, List<Integer>>> result = rebuildTree(original);
        compareTrees(original, result);
        Tree.Node<ObservedOrReconstructed<List<Integer>, List<Integer>>> reconstructedNodeEqualToObserved = result.allNodes()
                .map(Tree.NodeWithParent::getNode)
                .filter(it -> it.getContent() instanceof Reconstructed<?, ?>)
                .filter(it -> ((Reconstructed<List<Integer>, List<Integer>>) it.getContent()).getContent().equals(parseNode("454")))
                .findFirst()
                .orElseThrow(IllegalArgumentException::new);
        Optional<Tree.Node<ObservedOrReconstructed<List<Integer>, List<Integer>>>> observedChild = reconstructedNodeEqualToObserved.getLinks().stream()
                .filter(it -> Objects.equals(it.getDistance(), BigDecimal.ZERO))
                .map(Tree.NodeLink::getNode)
                .findFirst();
        assertTrue(observedChild.isPresent());
        assertEquals(parseNode("454"), ((Observed<List<Integer>, List<Integer>>) observedChild.get().getContent()).getContent());
        assertEquals(BigDecimal.ZERO, ((Reconstructed<List<Integer>, List<Integer>>) reconstructedNodeEqualToObserved.getContent()).getMinDistanceFromObserved());
    }

    @Test
    public void useMinDistanceFromToChooseNearestNodes() {
        Tree<List<Integer>> original = new Tree<>(
                new Tree.Node<>(parseNode("000000"))
                        .addChild(new Tree.Node<>(parseNode("053101")))
                        .addChild(new Tree.Node<>(parseNode("200141")))
                        .addChild(new Tree.Node<>(parseNode("004000"))
                                .addChild(new Tree.Node<>(parseNode("004121"))))
        );
        compareTrees(original, rebuildTree(original));
    }

    private void compareTrees(Tree<List<Integer>> original, Tree<ObservedOrReconstructed<List<Integer>, List<Integer>>> result) {
        boolean assertion;
        try {
            assertion = compareSumOfDistances(original, result);
        } catch (Throwable e) {
            System.out.println("expected:");
            System.out.println(treePrinterOnlyReal.print(calculateDistances(original, this::distance)));
            System.out.println("actual:");
            System.out.println(treePrinter.print(result));
            throw e;
        }
        if (!assertion) {
            System.out.println("expected:");
            System.out.println(treePrinterOnlyReal.print(calculateDistances(original, this::distance)));
            System.out.println("actual:");
            System.out.println(treePrinterOnlyReal.print(calculateDistances(withoutReconstructed(result), this::distance)));
            System.out.println(treePrinter.print(result));
        }
        assertTrue(assertion);
    }

    private Tree<ObservedOrReconstructed<List<Integer>, List<Integer>>> rebuildTree(Tree<List<Integer>> original) {
        return rebuildTree(original, true);
    }

    private Tree<ObservedOrReconstructed<List<Integer>, List<Integer>>> rebuildTree(Tree<List<Integer>> original, Boolean print) {
        List<Integer> root = IntStream.range(0, original.getRoot().getContent().size())
                .mapToObj(it -> 0)
                .collect(Collectors.toList());
        TreeBuilderByAncestors<List<Integer>, List<Integer>, List<Integer>> treeBuilder = treeBuilder(root.size());
        original.allNodes()
                .map(Tree.NodeWithParent::getNode)
                .map(Tree.Node::getContent)
//                .sorted(Comparator.comparing(lead -> distance(root, lead)))
                .sorted(Comparator.<List<Integer>, BigDecimal>comparing(lead -> distance(root, lead)).reversed())
                .forEach(toAdd -> {
                    if (print) {
                        System.out.println(xmlTreePrinter.print(treeBuilder.getTree()));
                        System.out.println("<item>" + print(toAdd) + "</item>");
                    }
                    treeBuilder.addNode(toAdd);
                });
        if (print) {
            System.out.println(xmlTreePrinter.print(treeBuilder.getTree()));
        }
        return treeBuilder.getTree();
    }

    @Ignore
    @Test
    public void randomizedTest() {
        Instant begin = Instant.now();
        AtomicInteger count = new AtomicInteger(0);

        int numberOfRuns = 400_000_000;
        List<Long> failedSeeds = IntStream.range(0, numberOfRuns)
                .mapToObj(it -> ThreadLocalRandom.current().nextLong())
                .parallel()
                .filter(seed -> {
                    boolean result = testRandomTree(seed, false);
                    long current = count.incrementAndGet();
                    if (current % 10_000 == 0) {
                        Duration runFor = Duration.between(begin, Instant.now());
                        System.out.print("\r current is " + current + " run for " + runFor + " ETC: " + runFor.multipliedBy((numberOfRuns - current) / current));
                        System.out.flush();
                    }
                    return result;
                })
                .collect(Collectors.toList());

        assertEquals(Collections.emptyList(), failedSeeds);
    }

    @Test
    public void reproduceRandom() {
        for (long seed : Lists.newArrayList(-4412672463225238115L)) {
            assertFalse(testRandomTree(seed, true));
        }
    }

    //TODO erase random nodes near root
    private boolean testRandomTree(Long seed, boolean print) {
        Random random = new Random(seed);

        int arrayLength = 5 + random.nextInt(15);
        int depth = 3 + random.nextInt(5);
        int alphabetSize = 3 + random.nextInt(5);

//                    int arrayLength = 5;
//                    int depth = 3;
        Supplier<Integer> branchesCount = () -> 1 + random.nextInt(2);
        Supplier<Integer> mutationsPercentage = () -> random.nextInt(30);

        List<Integer> root = new ArrayList<>();
        for (int i = 0; i < arrayLength; i++) {
            root.add(0);
        }

        Tree.Node<List<Integer>> rootNode = new Tree.Node<>(root);
        Tree<List<Integer>> original = new Tree<>(rootNode);
        Set<List<Integer>> insertedLeaves = new HashSet<>();
        insertedLeaves.add(root);

        for (int branchNumber = 0; branchNumber < branchesCount.get(); branchNumber++) {
            List<Tree.Node<List<Integer>>> nodes = Collections.singletonList(rootNode);
            for (int j = 0; j < depth; j++) {
                nodes = nodes.stream()
                        .flatMap(node -> insetChildren(random, mutationsPercentage, insertedLeaves, node, branchesCount, alphabetSize).stream())
                        .collect(Collectors.toList());
                if (nodes.isEmpty()) break;
            }
        }

        Tree<ObservedOrReconstructed<List<Integer>, List<Integer>>> rebuild;
        try {
            rebuild = rebuildTree(original, print);
        } catch (Exception e) {
            System.out.println("expected:");
            System.out.println(treePrinterOnlyReal.print(original));
            System.out.println("seed:");
            System.out.println(seed);
            e.printStackTrace();
            System.out.println();
            return true;
        }

        try {
            boolean success = compareSumOfDistances(original, rebuild);
            if (!success) {
                System.out.println("expected:");
                System.out.println(treePrinterOnlyReal.print(original));
                System.out.println("actual:");
                System.out.println(treePrinterOnlyReal.print(calculateDistances(withoutReconstructed(rebuild), this::distance)));
                System.out.println("seed:");
                System.out.println(seed);
                System.out.println();
            }
            return !success;
        } catch (Throwable e) {
            System.out.println(treePrinterOnlyReal.print(original));
            System.out.println(treePrinter.print(rebuild));
            System.out.println("seed:");
            System.out.println(seed);
            e.printStackTrace();
            System.out.println();
            return true;
        }
    }

    private boolean compareSumOfDistances(Tree<List<Integer>> original, Tree<ObservedOrReconstructed<List<Integer>, List<Integer>>> result) {
        BigDecimal sumOfDistancesInOriginal = sumOfDistances(calculateDistances(original, this::distance));
        BigDecimal sumOfDistancesInReconstructed = sumOfDistances(calculateDistances(withoutReconstructed(result), this::distance));
        boolean success = sumOfDistancesInOriginal.compareTo(sumOfDistancesInReconstructed) >= 0;
        if (!success) {
            System.out.println();
            System.out.println("sumOfDistancesInOriginal " + sumOfDistancesInOriginal + " sumOfDistancesInReconstructed " + sumOfDistancesInReconstructed);
        }
        return success;
    }

    private List<Tree.Node<List<Integer>>> insetChildren(Random random, Supplier<Integer> mutationsPercentage, Set<List<Integer>> insertedLeaves, Tree.Node<List<Integer>> parent, Supplier<Integer> branchesCount, int alphabetSize) {
        return IntStream.range(0, branchesCount.get())
                .mapToObj(index -> {
                    List<Integer> possiblePositionsToMutate = IntStream.range(0, parent.getContent().size())
                            .filter(i -> parent.getContent().get(i) == 0)
                            .boxed()
                            .collect(Collectors.toList());
                    Collections.shuffle(possiblePositionsToMutate, random);
                    int mutationsCount = (int) Math.ceil(Math.max(1, insertedLeaves.iterator().next().size() * mutationsPercentage.get()) / (double) 100);
                    possiblePositionsToMutate = possiblePositionsToMutate
                            .subList(0, Math.min(possiblePositionsToMutate.size() - 1, mutationsCount));
                    List<Integer> leaf = new ArrayList<>(parent.getContent());
                    possiblePositionsToMutate.forEach(it -> leaf.set(it, random.nextInt(alphabetSize - 1)));
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
        return tree.allNodes().map(Tree.NodeWithParent::getDistance)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Tree<List<Integer>> withoutReconstructed(Tree<ObservedOrReconstructed<List<Integer>, List<Integer>>> original) {
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

    private void assertTree(String expected, Tree<ObservedOrReconstructed<List<Integer>, List<Integer>>> tree) {
        assertEquals(expected, treePrinter.print(tree));
    }

    private TreeBuilderByAncestors<List<Integer>, List<Integer>, List<Integer>> treeBuilder(int sizeOfNode) {
        List<Integer> root = IntStream.range(0, sizeOfNode).mapToObj(it -> 0).collect(Collectors.toList());
        return new TreeBuilderByAncestors<>(
                root,
                (parent, mutation) -> BigDecimal.valueOf(mutation.stream().filter(it -> it != -1).count()),
                (from, to) -> {
                    List<Integer> result = new ArrayList<>();
                    for (int i = 0; i < from.size(); i++) {
                        if (Objects.equals(from.get(i), to.get(i))) {
                            result.add(-1);
                        } else {
                            result.add(to.get(i));
                        }
                    }
                    return result;
                },
                (base, mutation) -> {
                    List<Integer> result = new ArrayList<>();
                    for (int i = 0; i < base.size(); i++) {
                        if (mutation.get(i) == -1) {
                            result.add(base.get(i));
                        } else {
                            result.add(mutation.get(i));
                        }
                    }
                    return result;
                },
                (parent, node) -> node,
                (firstMutation, secondMutation) -> {
                    List<Integer> result = new ArrayList<>();
                    for (int i = 0; i < firstMutation.size(); i++) {
                        if (Objects.equals(firstMutation.get(i), secondMutation.get(i))) {
                            result.add(firstMutation.get(i));
                        } else {
                            result.add(-1);
                        }
                    }
                    return result;
                },
                (parent, child) -> child, 3
        );
    }

    private BigDecimal distance(List<Integer> first, List<Integer> second) {
        double result = 0;
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
