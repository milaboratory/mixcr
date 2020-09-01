package com.milaboratory.mixcr.postanalysis.clustering;


import org.junit.Test;

import java.util.List;

public class HierarchicalClusteringTest {
    @Test
    public void test1() {
        double[][] vectors = new double[][]{
                {4, 5, 2, 9, 5, 6, 3, 3, 1},
                {4, 5, 3, 8, 6, 7, 8, 3, 2},
                {1, 2, 4, 7, 5, 6, 3, 6, 3},
                {5, 3, 6, 6, 3, 4, 4, 2, 4},
                {2, 6, 7, 5, 8, 9, 8, 4, 5},
                {6, 6, 6, 4, 6, 0, 6, 6, 6},
                {1, 2, 3, 4, 5, 4, 3, 2, 1},
                {9, 8, 7, 6, 5, 6, 7, 8, 9},
                {0, 1, 0, 1, 0, 1, 0, 1, 6},
                {0, 0, 0, 3, 1, 0, 0, 1, 7},
                {3, 7, 7, 2, 7, 5, 3, 4, 8}
        };

        List<HierarchyNode> r = HierarchicalClustering.clusterize(vectors, 0, HierarchicalClustering::EuclideanDistance);
        for (HierarchyNode hierarchyNode : r) {
            System.out.println(hierarchyNode);
        }
        assert r.size() == 10;
    }

    @Test
    public void test2() {
        double[][] vectors = new double[][]{
                {1, 1, 1, 1},
                {1, 1, 1, 1},
                {1, 1, 1, 1},
                {1, 1, 1, 1},
        };

        List<HierarchyNode> r = HierarchicalClustering.clusterize(vectors, 0, HierarchicalClustering::EuclideanDistance);
        for (HierarchyNode hierarchyNode : r) {
            System.out.println(hierarchyNode);
        }
    }
}
