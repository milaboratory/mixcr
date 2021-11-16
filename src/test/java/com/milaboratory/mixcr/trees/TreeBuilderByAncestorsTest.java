package com.milaboratory.mixcr.trees;

import com.milaboratory.mixcr.trees.TreeBuilderByAncestors.RealOrSynthetic;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class TreeBuilderByAncestorsTest {
    private final TreePrinter<RealOrSynthetic<int[], int[]>> treePrinter = new NewickTreePrinter<>(
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
        TreeBuilderByAncestors<int[], int[]> treeBuilder = treeBuilder(new int[]{1, 0, 0})
                .addNode(new int[]{1, 1, 0});
        assertTreeBuilder("(100:0,(110:0)'110':1)'100';", treeBuilder);
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
        TreeBuilderByAncestors<int[], int[]> treeBuilder = treeBuilder(new int[]{1, 0, 0})
                .addNode(new int[]{1, 1, 0})
                .addNode(new int[]{1, 0, 1});
        assertTreeBuilder("(100:0,(110:0)'110':1,(101:0)'101':1)'100';", treeBuilder);
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
        TreeBuilderByAncestors<int[], int[]> treeBuilder = treeBuilder(new int[]{0, 0, 0})
                .addNode(new int[]{1, 0, 0})
                .addNode(new int[]{0, 1, 0})
                .addNode(new int[]{0, 0, 1});
        assertTreeBuilder("(000:0,(100:0)'100':1,(010:0)'010':1,(001:0)'001':1)'000';", treeBuilder);
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
        TreeBuilderByAncestors<int[], int[]> treeBuilder = treeBuilder(new int[]{1, 1, 0})
                .addNode(new int[]{1, 0, 1});
        assertTreeBuilder("((110:0)'110':1,(101:0)'101':1)'100';", treeBuilder);
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
        TreeBuilderByAncestors<int[], int[]> treeBuilder = treeBuilder(new int[]{0, 0, 0, 0})
                .addNode(new int[]{1, 1, 1, 0})
                .addNode(new int[]{0, 0, 0, 1})
                .addNode(new int[]{1, 1, 0, 1});
        assertTreeBuilder("(0000:0,(0001:0)'0001':1,((1110:0)'1110':1,(1101:0)'1101':1)'1100':2)'0000';", treeBuilder);
    }

    private void assertTreeBuilder(String expected, TreeBuilderByAncestors<int[], int[]> treeBuilder) {
        assertEquals(expected, treePrinter.print(treeBuilder.getTree()));
    }

    private TreeBuilderByAncestors<int[], int[]> treeBuilder(int[] root) {
        return new TreeBuilderByAncestors<>(
                root,
                this::distance,
                this::commonAncestor,
                Function.identity()
        );
    }

    private int[] commonAncestor(int[] first, int[] second) {
        int[] result = new int[first.length];
        for (int i = 0; i < first.length; i++) {
            if (first[i] != second[i]) {
                result[i] = 0;
            } else {
                result[i] = first[i];
            }
        }
        return result;
    }

    private BigDecimal distance(int[] first, int[] second) {
        int result = 0;
        for (int i = 0; i < first.length; i++) {
            if (first[i] != second[i]) {
                result++;
            }
        }
        return BigDecimal.valueOf(result);
    }

    private String print(int[] node) {
        return Arrays.stream(node).mapToObj(String::valueOf).collect(Collectors.joining(""));
    }
}
