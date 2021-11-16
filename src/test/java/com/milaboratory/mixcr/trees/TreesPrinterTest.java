package com.milaboratory.mixcr.trees;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;

public class TreesPrinterTest {
    @Test
    public void treeWithDistancesAndRoot() {
        TreePrinter<String, String> treePrinter = new NewickTreePrinter<>(s -> s, s -> s, true, false);

        Tree<String, String> tree = sampleTree();
        assertEquals("(A:0.1,B:0.2,(C:0.3,D:0.4)E:0.5)F;", treePrinter.print(tree));
    }

    @Test
    public void treeWithDistancesAndLeafName() {
        TreePrinter<String, String> treePrinter = new NewickTreePrinter<>(s -> s, s -> s, true, true);

        Tree<String, String> tree = sampleTree();
        assertEquals("(A:0.1,B:0.2,(C:0.3,D:0.4):0.5);", treePrinter.print(tree));
    }

    @Test
    public void allNodesAreNamed() {
        TreePrinter<String, String> treePrinter = new NewickTreePrinter<>(s -> s, s -> s, false, false);

        Tree<String, String> tree = sampleTree();
        assertEquals("(A,B,(C,D)E)F;", treePrinter.print(tree));
    }

    @Test
    public void allLeafNames() {
        TreePrinter<String, String> treePrinter = new NewickTreePrinter<>(s -> s, s -> s, false, true);

        Tree<String, String> tree = sampleTree();
        assertEquals("(A,B,(C,D));", treePrinter.print(tree));
    }

    /**
     * sample from https://en.wikipedia.org/wiki/Newick_format
     */
    private Tree<String, String> sampleTree() {
        Tree.Node<String, String> root = new Tree.Node.Real<>("F");
        Tree<String, String> tree = new Tree<>(root);

        root.addChild(new Tree.Node.Real<>("A"), new BigDecimal("0.1"));
        root.addChild(new Tree.Node.Real<>("B"), new BigDecimal("0.2"));
        Tree.Node.Real<String, String> e = new Tree.Node.Real<>("E");
        root.addChild(e, new BigDecimal("0.5"));

        e.addChild(new Tree.Node.Real<>("C"), new BigDecimal("0.3"));
        e.addChild(new Tree.Node.Real<>("D"), new BigDecimal("0.4"));
        return tree;
    }
}
