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
import com.milaboratory.mixcr.basictypes.tag.TagsInfo;
import com.milaboratory.mixcr.postanalysis.Characteristic;
import com.milaboratory.mixcr.postanalysis.overlap.OverlapCharacteristic;
import com.milaboratory.mixcr.postanalysis.overlap.OverlapGroup;
import com.milaboratory.mixcr.postanalysis.overlap.OverlapType;
import io.repseq.core.Chains;

import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
public class PostanalysisParametersOverlap extends PostanalysisParameters {
    public static final String Overlap = "Overlap";

    public MetricParameters relativeDiversity = new MetricParameters();
    public MetricParameters sharedClonotypes = new MetricParameters();
    public MetricParameters f1Index = new MetricParameters();
    public MetricParameters f2Index = new MetricParameters();
    public MetricParameters jaccardIndex = new MetricParameters();
    public MetricParameters pearson = new MetricParameters();
    public MetricParameters pearsonAll = new MetricParameters();

    public List<CharacteristicGroup<?, OverlapGroup<Clone>>> getGroups(int nSamples, Chains chains, TagsInfo tagsInfo) {
        for (MetricParameters m : Arrays.asList(relativeDiversity, sharedClonotypes, f1Index, f2Index, jaccardIndex, pearson, pearsonAll)) {
            m.setParent(this);
            m.setTagsInfo(tagsInfo);
        }

        List<Characteristic<?, OverlapGroup<Clone>>> chars = groupBy(
                new HashMap<OverlapType, OverlapPreprocessorAndWeight<Clone>>() {{
                    put(OverlapType.RelativeDiversity, relativeDiversity.opwTuple(chains));
                    put(OverlapType.SharedClonotypes, sharedClonotypes.opwTuple(chains));
                    put(OverlapType.F1Index, f1Index.opwTuple(chains));
                    put(OverlapType.F2Index, f2Index.opwTuple(chains));
                    put(OverlapType.JaccardIndex, jaccardIndex.opwTuple(chains));
                    put(OverlapType.Pearson, pearson.opwTuple(chains));
                    put(OverlapType.PearsonAll, pearsonAll.opwTuple(chains));
                }},
                (p, l) -> {
                    List<OverlapCharacteristic<Clone>> overlaps = new ArrayList<>();
                    for (int i = 0; i < nSamples; ++i)
                        for (int j = i; j < nSamples; ++j) // j=i to include diagonal elements
                            overlaps.add(new OverlapCharacteristic<>(
                                    "overlap_" + i + "_" + j + " / " + l.stream().map(t -> t.shortDescription).collect(Collectors.joining(" / ")),
                                    p.weight,
                                    p.preproc,
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
        return Objects.equals(relativeDiversity, that.relativeDiversity) && Objects.equals(sharedClonotypes, that.sharedClonotypes) && Objects.equals(f1Index, that.f1Index) && Objects.equals(f2Index, that.f2Index) && Objects.equals(jaccardIndex, that.jaccardIndex) && Objects.equals(pearson, that.pearson) && Objects.equals(pearsonAll, that.pearsonAll);
    }

    @Override
    public int hashCode() {
        return Objects.hash(relativeDiversity, sharedClonotypes, f1Index, f2Index, jaccardIndex, pearson, pearsonAll);
    }
}
