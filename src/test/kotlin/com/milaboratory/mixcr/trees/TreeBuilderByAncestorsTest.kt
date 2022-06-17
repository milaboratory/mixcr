package com.milaboratory.mixcr.trees

import com.google.common.collect.Lists
import com.milaboratory.mixcr.trees.Tree.NodeWithParent
import com.milaboratory.mixcr.trees.TreeBuilderByAncestors.Observed
import com.milaboratory.mixcr.trees.TreeBuilderByAncestors.ObservedOrReconstructed
import com.milaboratory.mixcr.trees.TreeBuilderByAncestors.Reconstructed
import com.milaboratory.mixcr.util.RandomizedTest
import io.kotest.matchers.bigdecimal.shouldBeGreaterThanOrEquals
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test
import java.math.BigDecimal
import java.util.*
import java.util.function.BiFunction
import java.util.function.Consumer
import java.util.function.Supplier
import java.util.stream.Collectors
import java.util.stream.IntStream
import kotlin.random.Random

@Ignore
class TreeBuilderByAncestorsTest {
    private val treePrinter: TreePrinter<ObservedOrReconstructed<List<Int>, List<Int>>> = NewickTreePrinter(
        { node: Tree.Node<ObservedOrReconstructed<List<Int>, List<Int>>> ->
            node.content.convert({ node: List<Int> ->
                this.print(
                    node
                )
            }) { content: List<Int> -> "'" + print(content) + "'" }
        },
        true,
        false
    )
    private val xmlTreePrinter: TreePrinter<ObservedOrReconstructed<List<Int>, List<Int>>> =
        XmlTreePrinter { nodeWithParent: NodeWithParent<ObservedOrReconstructed<List<Int>, List<Int>>> ->
            nodeWithParent.node.content.convert({ node: List<Int> -> this.print(node) }) { content: List<Int> ->
                "-" + print(
                    content
                ) + "-"
            }
        }
    private val treePrinterOnlyReal = NewickTreePrinter(
        { node: Tree.Node<List<Int>> -> print(node.content) },
        true,
        false
    )

    /**
     * <pre>
     * |
     * (A)-0-A
     * =======
     * A ⊂ B,
     * M1: A*M1 = B
     * =======
     * |
     * ----(A)-0-A
     * |
     * |M1|
     * |
     * (B)-0-B
    </pre> *
     */
    @Test
    fun addDirectAncestor() {
        val tree = treeBuilder(3)
            .addNode(Lists.newArrayList(1, 1, 0))
            .addNode(Lists.newArrayList(1, 0, 0))
            .tree
        assertTree("(((110:0)'110':1,100:0)'100':1)'000';", tree)
    }

    /**
     * <pre>
     * |
     * ----(A)-0-A
     * |
     * |M1|
     * |
     * (B)-0-B
     * =======
     * A ⊂ C, B ⊄ C
     * M2: A*M2 = C,
     * M1⋂M2 = ∅
     * =======
     * |
     * ----(A)-------------
     * |         |        |
     * |M1|      |M2|      0
     * |         |        |
     * (B)-0-B   (C)-0-C   A
    </pre> *
     */
    @Test
    fun addSecondDirectAncestor() {
        val tree = treeBuilder(3)
            .addNode(Lists.newArrayList(1, 0, 1))
            .addNode(Lists.newArrayList(1, 1, 0))
            .addNode(Lists.newArrayList(1, 0, 0))
            .tree
        assertTree("(((101:0)'101':1,(110:0)'110':1,100:0)'100':1)'000';", tree)
    }

    @Test
    fun insertAsDescendantInsteadOfSiblingIfItChangeDistancesLess() {
        val original = Tree(
            Tree.Node(parseNode("0000"))
                .addChild(
                    Tree.Node(parseNode("0010"))
                        .addChild(Tree.Node(parseNode("0712")))
                )
                .addChild(
                    Tree.Node(parseNode("0002"))
                        .addChild(
                            Tree.Node(parseNode("7702"))
                                .addChild(Tree.Node(parseNode("7782")))
                        )
                )
        )
        compareTrees(original, rebuildTree(original))
    }

    /**
     * <pre>
     * |
     * ----(A)-------------
     * |         |        |
     * |M1|      |M2|      0
     * |         |        |
     * (B)-0-B   (C)-0-C   A
     * =======
     * A ⊂ D, B ⊄ D, C ⊄ D
     * M3: A*M3 = D,
     * M1⋂M3 = ∅, M2⋂M3 = ∅,
     * =======
     * |
     * ----(A)----------------------
     * |         |        |        |
     * |M1|      |M2|     |M3|      0
     * |         |        |        |
     * (B)-0-B   (C)-0-C  (D)-0-D   A
    </pre> *
     */
    @Test
    fun addThirdDirectAncestor() {
        val tree = treeBuilder(3)
            .addNode(Lists.newArrayList(0, 0, 1))
            .addNode(Lists.newArrayList(1, 0, 0))
            .addNode(Lists.newArrayList(0, 1, 0))
            .addNode(Lists.newArrayList(0, 0, 0))
            .tree
        assertTree("((001:0)'001':1,(010:0)'010':1,(100:0)'100':1,000:0)'000';", tree)
    }

    /**
     * <pre>
     * |
     * (A)-0-A
     * =======
     * A ⊄ B,
     * C: C ⊂ A, C ⊂ B, A ⊂ C
     * M1: C*M1 = A,
     * M2: C*M2 = B
     * =======
     * |
     * ----(C)----
     * |         |
     * |M1|      |M2|
     * |         |
     * (A)-0-A   (B)-0-B
    </pre> *
     */
    @Test
    fun addNodeWithIntersection() {
        val tree = treeBuilder(3)
            .addNode(Lists.newArrayList(1, 0, 1))
            .addNode(Lists.newArrayList(1, 1, 0))
            .tree
        assertTree("(((101:0)'101':1,(110:0)'110':1)'100':1)'000';", tree)
    }

    /**
     * <pre>
     * |
     * ----(A)------------
     * |         |       |
     * |M1|      |M2|     0
     * |         |       |
     * (B)-0-B   (C)-0-C  A
     * =======
     * D ⊄ A, D ⊄ B
     * A <-> D < B <-> D
     * A <-> D < C <-> D
     * E: E ⊂ B, E ⊂ D, A ⊂ E
     * M3: A*M3 = E,
     * M4: E*M4 = B,
     * M5: E*M5 = D
     * =======
     * |
     * ----(A)------------
     * |         |       |
     * |M3|      |M2|     0
     * |         |       |
     * ----(E)----   (C)-0-C  A
     * |         |
     * |M4|      |M5|
     * |         |
     * (B)-0-B   (D)-0-D
     *
    </pre> *
     */
    @Test
    fun addNodeWithReplacementOfSibling() {
        val tree = treeBuilder(6)
            .addNode(Lists.newArrayList(1, 0, 0, 1, 1, 0)) //D
            .addNode(Lists.newArrayList(1, 1, 1, 0, 0, 0)) //B
            .addNode(Lists.newArrayList(0, 0, 0, 0, 0, 1)) //C
            .addNode(Lists.newArrayList(0, 0, 0, 0, 0, 0)) //A
            .tree
        assertTree(
            "(((100110:0)'100110':2,(111000:0)'111000':2)'100000':1,(000001:0)'000001':1,000000:0)'000000';",
            tree
        )
    }

    /**
     * If there are several nodes with the same distance from added node with must choose those that will minimize change of distances
     */
    @Test
    fun chooseBestResultOfInsertion() {
        val original = Tree(
            Tree.Node(parseNode("00000"))
                .addChild(
                    Tree.Node(parseNode("01101"))
                        .addChild(Tree.Node(parseNode("11101")))
                )
                .addChild(
                    Tree.Node(parseNode("10100"))
                        .addChild(Tree.Node(parseNode("10111")))
                )
        )
        compareTrees(original, rebuildTree(original))
    }

    @Test
    fun dontAddReconstructedWithZeroDistanceFromParent() {
        val original = Tree(
            Tree.Node(parseNode("00000"))
                .addChild(
                    Tree.Node(parseNode("42200"))
                        .addChild(
                            Tree.Node(parseNode("42204"))
                                .addChild(Tree.Node(parseNode("02204")))
                        )
                )
        )
        compareTrees(original, rebuildTree(original))
    }

    @Test
    fun useMinDistanceFromObservedForOptimization() {
        val original = Tree(
            Tree.Node(parseNode("00000"))
                .addChild(Tree.Node(parseNode("00405")))
                .addChild(Tree.Node(parseNode("08055")))
                .addChild(
                    Tree.Node(parseNode("30000"))
                        .addChild(Tree.Node(parseNode("38005")))
                )
        )
        compareTrees(original, rebuildTree(original))
    }

    @Test
    fun chooseInsertionNearerToRealNode() {
        val original = Tree(
            Tree.Node(parseNode("0000000000"))
                .addChild(
                    Tree.Node(parseNode("1010000000"))
                        .addChild(
                            Tree.Node(parseNode("1110000001"))
                                .addChild(Tree.Node(parseNode("1110001101")))
                        )
                )
                .addChild(
                    Tree.Node(parseNode("0000000001"))
                        .addChild(
                            Tree.Node(parseNode("0100000001"))
                                .addChild(
                                    Tree.Node(parseNode("0110001001"))
                                        .addChild(
                                            Tree.Node(parseNode("1111001001"))
                                                .addChild(Tree.Node(parseNode("1111001101")))
                                        )
                                )
                        )
                )
                .addChild(
                    Tree.Node(parseNode("0100000000"))
                        .addChild(
                            Tree.Node(parseNode("1100100000"))
                                .addChild(
                                    Tree.Node(parseNode("1110100000"))
                                        .addChild(
                                            Tree.Node(parseNode("1110101000"))
                                                .addChild(Tree.Node(parseNode("1110101011")))
                                        )
                                )
                        )
                )
        )
        compareTrees(original, rebuildTree(original))
    }

    @Test
    fun chooseInsertionNearerToRealNodeWithReplaceOfReal() {
        val original = Tree(
            Tree.Node(parseNode("00000000"))
                .addChild(
                    Tree.Node(parseNode("60000030"))
                        .addChild(
                            Tree.Node(parseNode("65000030"))
                                .addChild(Tree.Node(parseNode("65230030")))
                                .addChild(Tree.Node(parseNode("65000237")))
                        )
                        .addChild(
                            Tree.Node(parseNode("60200032"))
                                .addChild(Tree.Node(parseNode("65250032")))
                                .addChild(Tree.Node(parseNode("60230032")))
                        )
                )
        )
        compareTrees(original, rebuildTree(original))
    }

    @Test
    fun commonAncestorCouldNotHaveMutationsThatNotContainsInAParent() {
        val original = Tree(
            Tree.Node(parseNode("0000"))
                .addChild(
                    Tree.Node(parseNode("6000"))
                        .addChild(Tree.Node(parseNode("6077")))
                )
                .addChild(
                    Tree.Node(parseNode("0007"))
                        .addChild(Tree.Node(parseNode("1077")))
                )
        )
        //direct comparison of 1077 and 6077 will yield 0077, but with parent 6000 we want them to yield 6077
        compareTrees(original, rebuildTree(original))
    }

    @Test
    fun reconstructedNodeEqualsToNextObserved() {
        val original = Tree(
            Tree.Node(parseNode("000"))
                .addChild(
                    Tree.Node(parseNode("404"))
                        .addChild(Tree.Node(parseNode("456")))
                        .addChild(Tree.Node(parseNode("154")))
                        .addChild(Tree.Node(parseNode("454")))
                )
        )
        val result = rebuildTree(original)
        compareTrees(original, result)
        val reconstructedNodeEqualToObserved = result.allNodes()
            .map { it.node }
            .filter { it.content is Reconstructed<*, *> }
            .filter { (it.content as Reconstructed).content == parseNode("454") }
            .first()
        val observedChild = reconstructedNodeEqualToObserved.links.stream()
            .filter { it.distance == BigDecimal.ZERO }
            .map { it.node }
            .findFirst()
        Assert.assertTrue(observedChild.isPresent)
        Assert.assertEquals(parseNode("454"), (observedChild.get().content as Observed).content)
        Assert.assertEquals(
            BigDecimal.ZERO,
            (reconstructedNodeEqualToObserved.content as Reconstructed).minDistanceFromObserved
        )
    }

    @Test
    fun useMinDistanceFromToChooseNearestNodes() {
        val original = Tree(
            Tree.Node(parseNode("000000"))
                .addChild(Tree.Node(parseNode("053101")))
                .addChild(Tree.Node(parseNode("200141")))
                .addChild(
                    Tree.Node(parseNode("004000"))
                        .addChild(Tree.Node(parseNode("004121")))
                )
        )
        compareTrees(original, rebuildTree(original))
    }

    private fun compareTrees(original: Tree<List<Int>>, result: Tree<ObservedOrReconstructed<List<Int>, List<Int>>>) {
        try {
            compareSumOfDistances(original, result)
        } catch (e: Throwable) {
            println("expected:")
            println(treePrinterOnlyReal.print(calculateDistances(original) { first: List<Int>, second: List<Int> ->
                distance(
                    first,
                    second
                )
            }))
            println("actual:")
            println(treePrinterOnlyReal.print(calculateDistances(withoutReconstructed(result)) { first: List<Int>, second: List<Int> ->
                distance(
                    first,
                    second
                )
            }))
            println(treePrinter.print(result))
            throw e
        }
    }

    private fun rebuildTree(
        original: Tree<List<Int>>,
        print: Boolean = true
    ): Tree<ObservedOrReconstructed<List<Int>, List<Int>>> {
        val root = IntStream.range(0, original.root.content.size)
            .mapToObj { 0 }
            .collect(Collectors.toList())
        val treeBuilder = treeBuilder(root.size)
        original.allNodes()
            .map { it.node.content }
            .sortedWith(Comparator.comparing { lead: List<Int> -> distance(root, lead) }
                .reversed())
            .forEach { toAdd: List<Int> ->
                if (print) {
                    println(xmlTreePrinter.print(treeBuilder.tree))
                    println("<item>" + print(toAdd) + "</item>")
                }
                treeBuilder.addNode(toAdd)
            }
        if (print) {
            println(xmlTreePrinter.print(treeBuilder.tree))
        }
        return treeBuilder.tree
    }

    @Ignore
    @Test
    fun randomizedTest() {
        RandomizedTest.randomized(::testRandomTree, numberOfRuns = 400000000)
    }

    @Test
    fun reproduceRandom() {
        RandomizedTest.reproduce(
            ::testRandomTree,
            -4412672463225238115L
        )
    }

    //TODO erase random nodes near root
    private fun testRandomTree(random: Random, print: Boolean) {
        val arrayLength = 5 + random.nextInt(15)
        val depth = 3 + random.nextInt(5)
        val alphabetSize = 3 + random.nextInt(5)

//                    int arrayLength = 5;
//                    int depth = 3;
        val branchesCount = Supplier { 1 + random.nextInt(2) }
        val mutationsPercentage = Supplier { random.nextInt(30) }
        val root: MutableList<Int> = ArrayList()
        for (i in 0 until arrayLength) {
            root.add(0)
        }
        val rootNode = Tree.Node<List<Int>>(root)
        val original = Tree(rootNode)
        val insertedLeaves: MutableSet<List<Int>> = HashSet()
        insertedLeaves.add(root)
        for (branchNumber in 0 until branchesCount.get()) {
            var nodes = listOf(rootNode)
            for (j in 0 until depth) {
                nodes = nodes.stream()
                    .flatMap { node: Tree.Node<List<Int>> ->
                        insetChildren(
                            random,
                            mutationsPercentage,
                            insertedLeaves,
                            node,
                            branchesCount,
                            alphabetSize
                        ).stream()
                    }
                    .collect(Collectors.toList())
                if (nodes.isEmpty()) break
            }
        }
        val rebuild = rebuildTree(original, print)
        if (print) {
            println("expected:")
            println(treePrinterOnlyReal.print(original))
            println("actual:")
            println(treePrinterOnlyReal.print(calculateDistances(withoutReconstructed(rebuild)) { first: List<Int>, second: List<Int> ->
                distance(
                    first,
                    second
                )
            }))
            println()
        }
        compareSumOfDistances(original, rebuild)
    }

    private fun compareSumOfDistances(
        original: Tree<List<Int>>,
        result: Tree<ObservedOrReconstructed<List<Int>, List<Int>>>
    ) {
        val sumOfDistancesInOriginal =
            sumOfDistances(calculateDistances(original) { first: List<Int>, second: List<Int> ->
                distance(
                    first,
                    second
                )
            })
        val sumOfDistancesInReconstructed =
            sumOfDistances(calculateDistances(withoutReconstructed(result)) { first: List<Int>, second: List<Int> ->
                distance(
                    first,
                    second
                )
            })
        sumOfDistancesInOriginal shouldBeGreaterThanOrEquals sumOfDistancesInReconstructed
    }

    private fun insetChildren(
        random: Random,
        mutationsPercentage: Supplier<Int>,
        insertedLeaves: MutableSet<List<Int>>,
        parent: Tree.Node<List<Int>>,
        branchesCount: Supplier<Int>,
        alphabetSize: Int
    ): List<Tree.Node<List<Int>>> {
        return IntStream.range(0, branchesCount.get())
            .mapToObj { index: Int ->
                var possiblePositionsToMutate = IntStream.range(0, parent.content.size)
                    .filter { i: Int -> parent.content[i] == 0 }
                    .boxed()
                    .collect(Collectors.toList())
                possiblePositionsToMutate.shuffle(random)
                val mutationsCount =
                    Math.ceil(Math.max(1, insertedLeaves.iterator().next().size * mutationsPercentage.get()) / 100.0)
                        .toInt()
                possiblePositionsToMutate = possiblePositionsToMutate
                    .subList(0, Math.min(possiblePositionsToMutate.size - 1, mutationsCount))
                val leaf: MutableList<Int> = ArrayList(parent.content)
                possiblePositionsToMutate.forEach(Consumer { it: Int? ->
                    leaf[it!!] = random.nextInt(alphabetSize - 1)
                })
                if (insertedLeaves.contains(leaf)) {
                    return@mapToObj null
                } else {
                    insertedLeaves.add(leaf)
                    val inserted = Tree.Node<List<Int>>(leaf)
                    parent.addChild(inserted, distance(parent.content, leaf))
                    return@mapToObj inserted
                }
            }
            .filter { obj -> Objects.nonNull(obj) }
            .map { it!! }
            .collect(Collectors.toList())
    }

    private fun sumOfDistances(tree: Tree<*>): BigDecimal {
        return tree.allNodes()
            .map { it.distance }
            .filter { obj -> Objects.nonNull(obj) }
            .map { it!! }
            .fold(BigDecimal.ZERO) { obj: BigDecimal, toAdd: BigDecimal -> obj.add(toAdd) }
    }

    private fun withoutReconstructed(original: Tree<ObservedOrReconstructed<List<Int>, List<Int>>>): Tree<List<Int>> {
        val rootContent = (original.root.content as Reconstructed<List<Int>, List<Int>>).content
        val rootNode = Tree.Node(rootContent)
        copyRealNodes(original.root, rootNode)
        return Tree(rootNode)
    }

    private fun <T : Any> calculateDistances(tree: Tree<T>, distance: BiFunction<T, T, BigDecimal>): Tree<T> {
        val rootNode = Tree.Node(tree.root.content)
        copyWithDistance(tree.root, rootNode, distance)
        return Tree(rootNode)
    }

    private fun <T> copyWithDistance(
        copyFrom: Tree.Node<T>,
        copyTo: Tree.Node<T>,
        distance: BiFunction<T, T, BigDecimal>
    ) {
        for (link in copyFrom.links) {
            val from = link.node
            val node = Tree.Node(from.content)
            copyTo.addChild(node, distance.apply(copyFrom.content, from.content))
            copyWithDistance(from, node, distance)
        }
    }

    private fun copyRealNodes(
        copyFrom: Tree.Node<ObservedOrReconstructed<List<Int>, List<Int>>>,
        copyTo: Tree.Node<List<Int>>
    ) {
        for (link in copyFrom.links) {
            if (link.distance == BigDecimal.ZERO) {
                continue
            }
            val node = link.node
            if (node.content is Observed<*, *>) {
                val content = (node.content as Observed<List<Int>, List<Int>>).content
                copyTo.addChild(Tree.Node(content))
            } else if (node.content is Reconstructed<*, *>) {
                val realWithDistanceZero = node.links.stream()
                    .filter { it.distance == BigDecimal.ZERO }
                    .map { it.node.content as Observed<List<Int>, List<Int>> }
                    .findAny()
                var nextNode: Tree.Node<List<Int>>
                if (realWithDistanceZero.isPresent) {
                    nextNode = Tree.Node(realWithDistanceZero.get().content)
                    copyTo.addChild(nextNode)
                } else {
                    nextNode = copyTo
                }
                copyRealNodes(node, nextNode)
            } else {
                throw java.lang.IllegalArgumentException()
            }
        }
    }

    private fun parseNode(node: String): List<Int> {
        return node.chars().mapToObj { number: Int -> Integer.valueOf(number.toChar().toString()) }
            .collect(Collectors.toList())
    }

    private fun assertTree(expected: String, tree: Tree<ObservedOrReconstructed<List<Int>, List<Int>>>) {
        Assert.assertEquals(expected, treePrinter.print(tree))
    }

    private fun treeBuilder(sizeOfNode: Int): TreeBuilderByAncestors<List<Int>, List<Int>, List<Int>> {
        val root = IntStream.range(0, sizeOfNode).mapToObj { 0 }.collect(Collectors.toList())
        return TreeBuilderByAncestors(
            root,
            { _, mutation: List<Int> ->
                BigDecimal.valueOf(mutation.stream().filter { it != -1 }
                    .count())
            },
            { from: List<Int>, to: List<Int> ->
                val result: MutableList<Int> = ArrayList()
                for (i in from.indices) {
                    if (from[i] == to[i]) {
                        result.add(-1)
                    } else {
                        result.add(to[i])
                    }
                }
                result
            },
            { base: List<Int>, mutation: List<Int> ->
                val result: MutableList<Int> = ArrayList()
                for (i in base.indices) {
                    if (mutation[i] == -1) {
                        result.add(base[i])
                    } else {
                        result.add(mutation[i])
                    }
                }
                result
            },
            { node: List<Int> -> node },
            { firstMutation: List<Int>, secondMutation: List<Int> ->
                val result: MutableList<Int> = ArrayList()
                for (i in firstMutation.indices) {
                    if (firstMutation[i] == secondMutation[i]) {
                        result.add(firstMutation[i])
                    } else {
                        result.add(-1)
                    }
                }
                result
            },
            { _, child: List<Int> -> child }, 3
        )
    }

    private fun distance(first: List<Int>, second: List<Int>): BigDecimal {
        var result = 0.0
        for (i in first.indices) {
            if (first[i] != second[i]) {
                result++
            }
        }
        return BigDecimal.valueOf(result)
    }

    private fun print(node: List<Int>): String {
        return node.stream().map { obj: Int? -> java.lang.String.valueOf(obj) }.collect(Collectors.joining(""))
    }
}

fun <T : Any> Tree.Node<T>.addChild(node: Tree.Node<T>): Tree.Node<T> = addChild(node, BigDecimal.ZERO)
