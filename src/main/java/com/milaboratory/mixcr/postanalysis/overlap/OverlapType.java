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
package com.milaboratory.mixcr.postanalysis.overlap;

import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public enum OverlapType {
    SharedClonotypes("Shared clonotypes", "Number of shared clonotypes"),
    RelativeDiversity("Relative diversity", "Relative overlap diversity normalized"),
    F1Index("F1 index", "Geometric mean of relative overlap frequencies"),
    F2Index("F2 index", "Сlonotype-wise sum of geometric mean frequencies"),
    JaccardIndex("Jaccard index", "Jaccard index"),
    Pearson("Pearson", "Pearson correlation of clonotype frequencies, restricted only to the overlapping clonotypes"),
    PearsonAll("Pearson (all)", "Pearson correlation of clonotype frequencies (outer merge)");

    public final String shortDescription;
    public final String longDescription;

    OverlapType(String shortDescription, String longDescription) {
        this.shortDescription = shortDescription;
        this.longDescription = longDescription;
    }

    private static Map<String, OverlapType> byNameIgnoreCase;

    static {
        byNameIgnoreCase = new HashMap<>();
        for (OverlapType v : values()) {
            byNameIgnoreCase.put(v.name().toLowerCase(), v);
        }
    }

    public static OverlapType byName(String name) {
        return byNameIgnoreCase.get(name.toLowerCase());
    }

    public static OverlapType byNameOrThrow(String name) {
        OverlapType r = byName(name);
        if (r == null)
            throw new IllegalArgumentException("Unknown metric name: " + name);
        return r;
    }
}
