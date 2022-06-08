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
package com.milaboratory.mixcr.postanalysis.ui;

import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.postanalysis.Characteristic;
import com.milaboratory.mixcr.postanalysis.SetPreprocessorFactory;
import com.milaboratory.mixcr.postanalysis.WeightFunctions;
import com.milaboratory.mixcr.postanalysis.overlap.OverlapCharacteristic;
import com.milaboratory.mixcr.postanalysis.overlap.OverlapGroup;
import com.milaboratory.mixcr.postanalysis.overlap.OverlapType;

import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
public class PostanalysisParametersOverlap extends PostanalysisParameters {
    public static final String Overlap = "Overlap";

    public PreprocessorParameters d = new PreprocessorParameters();
    public PreprocessorParameters sharedClonotypes = new PreprocessorParameters();
    public PreprocessorParameters f1 = new PreprocessorParameters();
    public PreprocessorParameters f2 = new PreprocessorParameters();
    public PreprocessorParameters jaccard = new PreprocessorParameters();
    public PreprocessorParameters rIntersection = new PreprocessorParameters();
    public PreprocessorParameters rAll = new PreprocessorParameters();

    public List<CharacteristicGroup<?, OverlapGroup<Clone>>> getGroups(int nSamples) {
        PostanalysisParametersOverlap base = this;
        List<Characteristic<?, OverlapGroup<Clone>>> chars = groupByPreproc(
                new HashMap<OverlapType, SetPreprocessorFactory<OverlapGroup<Clone>>>() {{
                    put(OverlapType.D, d.overlapPreproc(base));
                    put(OverlapType.SharedClonotypes, sharedClonotypes.overlapPreproc(base));
                    put(OverlapType.F1, f1.overlapPreproc(base));
                    put(OverlapType.F2, f2.overlapPreproc(base));
                    put(OverlapType.Jaccard, jaccard.overlapPreproc(base));
                    put(OverlapType.R_Intersection, rIntersection.overlapPreproc(base));
                    put(OverlapType.R_All, rAll.overlapPreproc(base));
                }},
                (p, l) -> {
                    List<OverlapCharacteristic<Clone>> overlaps = new ArrayList<>();
                    for (int i = 0; i < nSamples; ++i)
                        for (int j = i; j < nSamples; ++j) // j=i to include diagonal elements
                            overlaps.add(new OverlapCharacteristic<>(
                                    "overlap_" + i + "_" + j + " / " + l.stream().map(t -> t.name).collect(Collectors.joining(" / ")),
                                    new WeightFunctions.Count(),
                                    p,
                                    l.toArray(new OverlapType[0]),
                                    i, j));
                    return overlaps;
                });
        //noinspection unchecked,rawtypes
        return Collections.singletonList(new CharacteristicGroup(Overlap, chars, Arrays.asList(new OverlapSummary<>())));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PostanalysisParametersOverlap that = (PostanalysisParametersOverlap) o;
        return Objects.equals(d, that.d) && Objects.equals(sharedClonotypes, that.sharedClonotypes) && Objects.equals(f1, that.f1) && Objects.equals(f2, that.f2) && Objects.equals(jaccard, that.jaccard) && Objects.equals(rIntersection, that.rIntersection) && Objects.equals(rAll, that.rAll);
    }

    @Override
    public int hashCode() {
        return Objects.hash(d, sharedClonotypes, f1, f2, jaccard, rIntersection, rAll);
    }
}
