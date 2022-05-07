package com.milaboratory.mixcr.trees;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;

public class TreesPrinterTest {
    @Test
    public void treeWithDistancesAndRoot() {
        TreePrinter<String> treePrinter = new NewickTreePrinter<>(Tree.Node::getContent, true, false);

        Tree<String> tree = sampleTree();
        assertEquals("((C:0.3,D:0.4)E:0.5,A:0.1,B:0.2)F;", treePrinter.print(tree));
    }

    @Test
    public void treeWithDistancesAndLeafName() {
        TreePrinter<String> treePrinter = new NewickTreePrinter<>(Tree.Node::getContent, true, true);

        Tree<String> tree = sampleTree();
        assertEquals("((C:0.3,D:0.4):0.5,A:0.1,B:0.2);", treePrinter.print(tree));
    }

    @Test
    public void allNodesAreNamed() {
        TreePrinter<String> treePrinter = new NewickTreePrinter<>(Tree.Node::getContent, false, false);

        Tree<String> tree = sampleTree();
        assertEquals("((C,D)E,A,B)F;", treePrinter.print(tree));
    }

    @Test
    public void allLeafNames() {
        TreePrinter<String> treePrinter = new NewickTreePrinter<>(Tree.Node::getContent, false, true);

        Tree<String> tree = sampleTree();
        assertEquals("((C,D),A,B);", treePrinter.print(tree));
    }

    /**
     * sample from https://en.wikipedia.org/wiki/Newick_format
     */
    private Tree<String> sampleTree() {
        Tree.Node<String> root = new Tree.Node<>("F");
        Tree<String> tree = new Tree<>(root);

        root.addChild(new Tree.Node<>("A"), new BigDecimal("0.1"));
        root.addChild(new Tree.Node<>("B"), new BigDecimal("0.2"));
        Tree.Node<String> e = new Tree.Node<>("E");
        root.addChild(e, new BigDecimal("0.5"));

        e.addChild(new Tree.Node<>("C"), new BigDecimal("0.3"));
        e.addChild(new Tree.Node<>("D"), new BigDecimal("0.4"));
        return tree;
    }
}
