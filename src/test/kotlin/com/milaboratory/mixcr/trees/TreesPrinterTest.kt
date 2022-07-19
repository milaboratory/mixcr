/*
 * Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
package com.milaboratory.mixcr.trees

import org.junit.Assert.assertEquals
import org.junit.Test

class TreesPrinterTest {
    @Test
    fun treeWithDistancesAndRoot() {
        val treePrinter: TreePrinter<String> = NewickTreePrinter(
            printDistances = true,
            printOnlyLeafNames = false
        ) { it.content }
        val tree = sampleTree()
        assertEquals("((C:0.3,D:0.4)E:0.5,A:0.1,B:0.2)F;", treePrinter.print(tree))
    }

    @Test
    fun treeWithDistancesAndLeafName() {
        val treePrinter: TreePrinter<String> = NewickTreePrinter(
            printDistances = true,
            printOnlyLeafNames = true
        ) { it.content }
        val tree = sampleTree()
        assertEquals("((C:0.3,D:0.4):0.5,A:0.1,B:0.2);", treePrinter.print(tree))
    }

    @Test
    fun allNodesAreNamed() {
        val treePrinter: TreePrinter<String> = NewickTreePrinter(
            printDistances = false,
            printOnlyLeafNames = false
        ) { it.content }
        val tree = sampleTree()
        assertEquals("((C,D)E,A,B)F;", treePrinter.print(tree))
    }

    @Test
    fun allLeafNames() {
        val treePrinter: TreePrinter<String> = NewickTreePrinter(
            printDistances = false,
            printOnlyLeafNames = true
        ) { it.content }
        val tree = sampleTree()
        assertEquals("((C,D),A,B);", treePrinter.print(tree))
    }

    /**
     * sample from https://en.wikipedia.org/wiki/Newick_format
     */
    private fun sampleTree(): Tree<String> {
        val root = Tree.Node("F")
        val tree = Tree(root)
        root.addChild(Tree.Node("A"), 0.1)
        root.addChild(Tree.Node("B"), 0.2)
        val e = Tree.Node("E")
        root.addChild(e, 0.5)
        e.addChild(Tree.Node("C"), 0.3)
        e.addChild(Tree.Node("D"), 0.4)
        return tree
    }
}
