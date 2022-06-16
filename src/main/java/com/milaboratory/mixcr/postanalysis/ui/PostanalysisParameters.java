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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.tag.TagInfo;
import com.milaboratory.mixcr.basictypes.tag.TagsInfo;
import com.milaboratory.mixcr.postanalysis.Characteristic;
import com.milaboratory.mixcr.postanalysis.SetPreprocessorFactory;
import com.milaboratory.mixcr.postanalysis.WeightFunction;
import com.milaboratory.mixcr.postanalysis.WeightFunctions;
import com.milaboratory.mixcr.postanalysis.downsampling.ClonesDownsamplingPreprocessorFactory;
import com.milaboratory.mixcr.postanalysis.downsampling.DownsampleValueChooser;
import com.milaboratory.mixcr.postanalysis.overlap.OverlapGroup;
import com.milaboratory.mixcr.postanalysis.preproc.ElementPredicate;
import com.milaboratory.mixcr.postanalysis.preproc.NoPreprocessing;
import com.milaboratory.mixcr.postanalysis.preproc.OverlapPreprocessorAdapter;
import com.milaboratory.mixcr.postanalysis.preproc.SelectTop;
import com.milaboratory.primitivio.annotations.Serializable;
import io.repseq.core.GeneFeature;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonIgnoreProperties(ignoreUnknown = false)
@Serializable(asJson = true)
public abstract class PostanalysisParameters {
    public String defaultDownsampling;
    public boolean defaultOnlyProductive;
    public boolean defaultDropOutliers;
    public String defaultWeightFunction;

    interface WithParentAndTags {
        void setParent(PostanalysisParameters parent);

        void setTagsInfo(TagsInfo tagsInfo);
    }

    @JsonAutoDetect(
            fieldVisibility = JsonAutoDetect.Visibility.ANY,
            isGetterVisibility = JsonAutoDetect.Visibility.NONE,
            getterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class MetricParameters implements WithParentAndTags {
        public String downsampling;
        public Boolean onlyProductive;
        public Boolean dropOutliers;
        public String weightFunction;

        @JsonIgnore
        private PostanalysisParameters parent;
        @JsonIgnore
        private TagsInfo tagsInfo;

        @Override
        public void setParent(PostanalysisParameters parent) {
            this.parent = parent;
        }

        @Override
        public void setTagsInfo(TagsInfo tagsInfo) {
            this.tagsInfo = tagsInfo;
        }

        public WeightFunction<Clone> weightFunction() {
            return parseWeightFunction(this.weightFunction == null ? parent.defaultWeightFunction : this.weightFunction, tagsInfo);
        }

        public SetPreprocessorFactory<Clone> preproc() {
            SetPreprocessorFactory<Clone> p = parseDownsampling();

            boolean onlyProductive = this.onlyProductive == null ? parent.defaultOnlyProductive : this.onlyProductive;
            if (onlyProductive)
                p = filterOnlyProductive(p);
            return p;
        }

        public PreprocessorAndWeight<Clone> pwTuple() {
            return new PreprocessorAndWeight<>(preproc(), weightFunction());
        }

        public OverlapPreprocessorAndWeight<Clone> opwTuple() {
            return new OverlapPreprocessorAndWeight<>(overlapPreproc(), weightFunction());
        }

        public SetPreprocessorFactory<OverlapGroup<Clone>> overlapPreproc() {
            return new OverlapPreprocessorAdapter.Factory<>(preproc());
        }

        SetPreprocessorFactory<Clone> parseDownsampling() {
            return PostanalysisParameters.parseDownsampling(
                    this.downsampling == null ? parent.defaultDownsampling : this.downsampling,
                    tagsInfo,
                    this.dropOutliers == null ? parent.defaultDropOutliers : this.dropOutliers
            );
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MetricParameters that = (MetricParameters) o;
            return Objects.equals(downsampling, that.downsampling) && Objects.equals(onlyProductive, that.onlyProductive) && Objects.equals(dropOutliers, that.dropOutliers);
        }

        @Override
        public int hashCode() {
            return Objects.hash(downsampling, onlyProductive, dropOutliers);
        }
    }

    static final class PreprocessorAndWeight<T> {
        final SetPreprocessorFactory<T> preproc;
        final WeightFunction<T> weight;

        public PreprocessorAndWeight(SetPreprocessorFactory<T> preproc, WeightFunction<T> weight) {
            this.preproc = preproc;
            this.weight = weight;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PreprocessorAndWeight<?> that = (PreprocessorAndWeight<?>) o;
            return Objects.equals(preproc, that.preproc) && Objects.equals(weight, that.weight);
        }

        @Override
        public int hashCode() {
            return Objects.hash(preproc, weight);
        }
    }

    static final class OverlapPreprocessorAndWeight<T> {
        final SetPreprocessorFactory<OverlapGroup<T>> preproc;
        final WeightFunction<T> weight;

        public OverlapPreprocessorAndWeight(SetPreprocessorFactory<OverlapGroup<T>> preproc, WeightFunction<T> weight) {
            this.preproc = preproc;
            this.weight = weight;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OverlapPreprocessorAndWeight<?> that = (OverlapPreprocessorAndWeight<?>) o;
            return Objects.equals(preproc, that.preproc) && Objects.equals(weight, that.weight);
        }

        @Override
        public int hashCode() {
            return Objects.hash(preproc, weight);
        }
    }

    static <K, T, P> List<Characteristic<?, T>> groupBy(Map<K, P> map,
                                                        BiFunction<P, List<K>, List<? extends Characteristic<?, T>>> factory) {

        Map<P, List<K>> byPreproc = map.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue,
                        e -> Collections.singletonList(e.getKey()),
                        (a, b) -> {
                            List<K> l = new ArrayList<>();
                            l.addAll(a);
                            l.addAll(b);
                            return l;
                        }));

        List<Characteristic<?, T>> chars = new ArrayList<>();
        for (Map.Entry<P, List<K>> e : byPreproc.entrySet()) {
            chars.addAll(factory.apply(e.getKey(), e.getValue()));
        }

        return chars;
    }

    static <K, T> List<Characteristic<?, T>> groupByPreproc(Map<K, SetPreprocessorFactory<T>> preprocs,
                                                            BiFunction<SetPreprocessorFactory<T>, List<K>, List<? extends Characteristic<?, T>>> factory) {

        return groupBy(preprocs, factory);
    }

    public static WeightFunction<Clone> parseWeightFunction(String weight, TagsInfo info) {
        if (weight == null) // default
            return WeightFunctions.Count;
        switch (weight) {
            case "read-count":
            case "reads-count":
                return WeightFunctions.Count;
            case "none":
                return new WeightFunctions.NoWeight<>();
            default:
                int tagIndex = info.indexOf(weight);
                if (tagIndex < 0)
                    throw new IllegalArgumentException("Unknown weight type: " + weight + ". Available types: none, default, read, " + info.stream().map(TagInfo::getName).collect(Collectors.joining(",")));
                return new WeightFunctions.TagCount(tagIndex);
        }
    }

    public static SetPreprocessorFactory<Clone> parseDownsampling(String downsampling,
                                                                  TagsInfo tagsInfo,
                                                                  boolean dropOutliers) {
        if (downsampling.equalsIgnoreCase("none"))
            return new NoPreprocessing.Factory<>();

        String[] parts = downsampling.split("-");

        String tag = parts[1];
        WeightFunction<Clone> wt;
        switch (tag) {
            case "read":
            case "reads":
                wt = WeightFunctions.Count;
                break;
            default:
                int i = tagsInfo.indexOfIgnoreCase(tag.replace("-count", ""));
                if (i < 0)
                    throw new IllegalArgumentException("Tag " + tag + " not found in the input files.");
                wt = new WeightFunctions.TagCount(i);
        }
        ;

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
                return new ClonesDownsamplingPreprocessorFactory(chooser, dropOutliers, wt);

            case "top":
                return new SelectTop.Factory<>(wt, Integer.parseInt(parts[2]));

            case "cumtop":
                return new SelectTop.Factory<>(wt, Double.parseDouble(parts[2]) / 100.0);

            default:
                throw new IllegalArgumentException("Illegal downsampling string: " + downsampling);
        }
    }

    public static SetPreprocessorFactory<Clone> filterOnlyProductive(SetPreprocessorFactory<Clone> p) {
        List<ElementPredicate<Clone>> filters = new ArrayList<>();
        filters.add(new ElementPredicate.NoStops(GeneFeature.CDR3));
        filters.add(new ElementPredicate.NoOutOfFrames(GeneFeature.CDR3));
        return p.filterFirst(filters);
    }
}
