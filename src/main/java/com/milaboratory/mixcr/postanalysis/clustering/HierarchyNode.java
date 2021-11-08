package com.milaboratory.mixcr.postanalysis.clustering;

import java.util.Arrays;
import java.util.Objects;

/**
 *
 */
public final class HierarchyNode {
    public final int id;
    public final int[] children;
    public final double height;

    public HierarchyNode(int id, int[] children, double height) {
        this.id = id;
        this.children = children;
        this.height = height;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HierarchyNode that = (HierarchyNode) o;
        return id == that.id &&
                Double.compare(that.height, height) == 0 &&
                Arrays.equals(children, that.children);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, height);
        result = 31 * result + Arrays.hashCode(children);
        return result;
    }

    @Override
    public String toString() {
        return "HierarchyNode{" +
                "id=" + id +
                ", children=" + Arrays.toString(children) +
                ", height=" + height +
                '}';
    }
}
