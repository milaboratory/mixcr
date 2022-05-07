package com.milaboratory.mixcr.trees

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class TreesPrinterTest {
    @Test
    fun treeWithDistancesAndRoot() {
        val treePrinter: TreePrinter<String> = NewickTreePrinter(
            nameExtractor = { it.content },
            printDistances = true,
            printOnlyLeafNames = false
        )
        val tree = sampleTree()
        assertEquals("((C:0.3,D:0.4)E:0.5,A:0.1,B:0.2)F;", treePrinter.print(tree))
    }

    @Test
    fun treeWithDistancesAndLeafName() {
        val treePrinter: TreePrinter<String> = NewickTreePrinter(
            nameExtractor = { it.content },
            printDistances = true,
            printOnlyLeafNames = true
        )
        val tree = sampleTree()
        assertEquals("((C:0.3,D:0.4):0.5,A:0.1,B:0.2);", treePrinter.print(tree))
    }

    @Test
    fun allNodesAreNamed() {
        val treePrinter: TreePrinter<String> = NewickTreePrinter(
            nameExtractor = { it.content },
            printDistances = false,
            printOnlyLeafNames = false
        )
        val tree = sampleTree()
        assertEquals("((C,D)E,A,B)F;", treePrinter.print(tree))
    }

    @Test
    fun allLeafNames() {
        val treePrinter: TreePrinter<String> = NewickTreePrinter(
            nameExtractor = { it.content },
            printDistances = false,
            printOnlyLeafNames = true
        )
        val tree = sampleTree()
        assertEquals("((C,D),A,B);", treePrinter.print(tree))
    }

    /**
     * sample from https://en.wikipedia.org/wiki/Newick_format
     */
    private fun sampleTree(): Tree<String> {
        val root = Tree.Node("F")
        val tree = Tree(root)
        root.addChild(Tree.Node("A"), BigDecimal("0.1"))
        root.addChild(Tree.Node("B"), BigDecimal("0.2"))
        val e = Tree.Node("E")
        root.addChild(e, BigDecimal("0.5"))
        e.addChild(Tree.Node("C"), BigDecimal("0.3"))
        e.addChild(Tree.Node("D"), BigDecimal("0.4"))
        return tree
    }
}
