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
import com.milaboratory.mixcr.postanalysis.SetPreprocessorFactory;
import com.milaboratory.mixcr.postanalysis.WeightFunction;
import com.milaboratory.mixcr.postanalysis.WeightFunctions;
import com.milaboratory.mixcr.postanalysis.downsampling.ClonesDownsamplingPreprocessorFactory;
import com.milaboratory.mixcr.postanalysis.downsampling.DownsampleValueChooser;
import com.milaboratory.mixcr.postanalysis.preproc.ElementPredicate;
import com.milaboratory.mixcr.postanalysis.preproc.FilterPreprocessor;
import com.milaboratory.mixcr.postanalysis.preproc.NoPreprocessing;
import com.milaboratory.mixcr.postanalysis.preproc.SelectTop;
import io.repseq.core.Chains;
import io.repseq.core.GeneFeature;

import java.util.ArrayList;
import java.util.List;

/** Default downsampling parameters parsed to be parsed from string */
public final class DownsamplingParameters {
    /** Preprocessor */
    private final SetPreprocessorFactory<Clone> preproc;
    /** Weight function used for downsampling */
    public final WeightFunction<Clone> weightFunction;
    /** Tags info required for umi/cell-based downsampling. May be null. */
    public final TagsInfo tagsInfo;
    /** Tag (umi/cell) used for downsampling. May be null. */
    public final String tag;
    /** Drop outlier samples */
    public final boolean dropOutliers;
    /** Drop OOF & Stops */
    public final boolean onlyProductive;

    private DownsamplingParameters(SetPreprocessorFactory<Clone> preproc,
                                   WeightFunction<Clone> weightFunction,
                                   TagsInfo tagsInfo,
                                   String tag,
                                   boolean dropOutliers,
                                   boolean onlyProductive) {
        this.preproc = preproc;
        this.weightFunction = weightFunction;
        this.tagsInfo = tagsInfo;
        this.tag = tag;
        this.dropOutliers = dropOutliers;
        this.onlyProductive = onlyProductive;
    }

    /** Return preprocessor use to analyze specific chains */
    public SetPreprocessorFactory<Clone> getPreproc(Chains chains) {
        return preproc.filterFirst(weightFunction, new ElementPredicate.IncludeChains(chains));
    }

    /**
     * Parse downsampling from string rep.
     */
    public static DownsamplingParameters parse(
            String downsampling,
            TagsInfo tagsInfo,
            boolean dropOutliers,
            boolean onlyProductive
    ) {
        if (downsampling.equalsIgnoreCase("none")) {
            SetPreprocessorFactory<Clone> preproc;
            if (onlyProductive)
                preproc = new FilterPreprocessor.Factory<>(WeightFunctions.Default(),
                        new ElementPredicate.NoStops(GeneFeature.CDR3),
                        new ElementPredicate.NoOutOfFrames(GeneFeature.CDR3)
                );
            else
                preproc = new NoPreprocessing.Factory<>();

            return new DownsamplingParameters(
                    preproc,
                    WeightFunctions.Default(),
                    tagsInfo,
                    null,
                    dropOutliers,
                    onlyProductive);
        }

        String[] parts = downsampling.split("-");

        String tagStr = parts[1];
        String tag = null;
        WeightFunction<Clone> wt;
        switch (tagStr) {
            case "read":
            case "reads":
                wt = WeightFunctions.Count;
                break;
            default:
                tag = tagStr.replace("-count", "");
                int i = tagsInfo.indexOfIgnoreCase(tag);
                if (i < 0)
                    throw new IllegalArgumentException("Tag " + tagStr + " not found in the input files.");
                wt = new WeightFunctions.TagCount(i);
        }

        SetPreprocessorFactory<Clone> preproc;
        switch (parts[0]) {
            case "count":
                DownsampleValueChooser chooser;
                switch (parts[2]) {
                    case "auto":
                        chooser = new DownsampleValueChooser.Auto();
                        break;
                    case "min":
                        chooser = new DownsampleValueChooser.Minimal();
                        break;
                    case "fixed":
                        chooser = new DownsampleValueChooser.Fixed(Long.parseLong(parts[3]));
                        break;
                    default:
                        throw new IllegalArgumentException("Can't parse downsampling value choose: " + downsampling);
                }
                preproc = new ClonesDownsamplingPreprocessorFactory(chooser, dropOutliers, wt);
                break;

            case "top":
                preproc = new SelectTop.Factory<>(wt, Integer.parseInt(parts[2]));
                break;

            case "cumtop":
                preproc = new SelectTop.Factory<>(wt, Double.parseDouble(parts[2]) / 100.0);
                break;

            default:
                throw new IllegalArgumentException("Illegal downsampling string: " + downsampling);
        }

        if (onlyProductive) {
            List<ElementPredicate<Clone>> filters = new ArrayList<>();
            filters.add(new ElementPredicate.NoStops(GeneFeature.CDR3));
            filters.add(new ElementPredicate.NoOutOfFrames(GeneFeature.CDR3));
            preproc = preproc.filterFirst(wt, filters);
        }

        return new DownsamplingParameters(
                preproc,
                wt,
                tagsInfo,
                tag,
                dropOutliers,
                onlyProductive
        );
    }
}
