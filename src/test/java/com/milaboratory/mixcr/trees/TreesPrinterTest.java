package com.milaboratory.mixcr.trees;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.Objects;

import static org.junit.Assert.assertEquals;

public class TreesPrinterTest {
    @Test
    public void treeWithDistancesAndRoot() {
        TreePrinter<String> treePrinter = new NewickTreePrinter<>(s -> s, true, false);

        Tree<String> tree = sampleTree();
        assertEquals("(A:0.1,B:0.2,(C:0.3,D:0.4)E:0.5)F;", treePrinter.print(tree));
    }

    @Test
    public void treeWithDistancesAndLeafName() {
        TreePrinter<String> treePrinter = new NewickTreePrinter<>(s -> s, true, true);

        Tree<String> tree = sampleTree();
        assertEquals("(A:0.1,B:0.2,(C:0.3,D:0.4):0.5);", treePrinter.print(tree));
    }

    @Test
    public void allNodesAreNamed() {
        TreePrinter<String> treePrinter = new NewickTreePrinter<>(s -> s, false, false);

        Tree<String> tree = sampleTree();
        assertEquals("(A,B,(C,D)E)F;", treePrinter.print(tree));
    }

    @Test
    public void allLeafNames() {
        TreePrinter<String> treePrinter = new NewickTreePrinter<>(s -> s, false, true);

        Tree<String> tree = sampleTree();
        assertEquals("(A,B,(C,D));", treePrinter.print(tree));
    }

    /**
     * sample from https://en.wikipedia.org/wiki/Newick_format
     */
    private Tree<String> sampleTree() {
        Tree.Node<String> root = new Tree.Node<>("F");
        Tree<String> tree = new Tree<>(root);

        root.addChild("A", new BigDecimal("0.1"));
        root.addChild("B", new BigDecimal("0.2"));
        root.addChild("E", new BigDecimal("0.5"));

        Tree.Node<String> child = root.findChild(it -> Objects.equals(it, "E"));
        child.addChild("C", new BigDecimal("0.3"));
        child.addChild("D", new BigDecimal("0.4"));
        return tree;
    }
}
