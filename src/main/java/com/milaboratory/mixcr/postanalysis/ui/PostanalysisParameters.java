package com.milaboratory.mixcr.postanalysis.ui;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.postanalysis.Characteristic;
import com.milaboratory.mixcr.postanalysis.SetPreprocessorFactory;
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
public abstract class PostanalysisParameters<T> {
    public String defaultDownsampling;
    public boolean defaultOnlyProductive;
    public boolean defaultDropOutliers;

    @JsonAutoDetect(
            fieldVisibility = JsonAutoDetect.Visibility.ANY,
            isGetterVisibility = JsonAutoDetect.Visibility.NONE,
            getterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class PreprocessorParameters {
        public String downsampling;
        public Boolean onlyProductive;
        public Boolean dropOutliers;
        public Long seed;

        public SetPreprocessorFactory<Clone> preproc(PostanalysisParameters<?> base) {
            SetPreprocessorFactory<Clone> p = parseDownsampling(base);

            boolean onlyProductive = this.onlyProductive == null ? base.defaultOnlyProductive : this.onlyProductive;
            if (onlyProductive) {
                List<ElementPredicate<Clone>> filters = new ArrayList<>();
                filters.add(new ElementPredicate.NoStops(GeneFeature.CDR3));
                filters.add(new ElementPredicate.NoOutOfFrames(GeneFeature.CDR3));
                p = p.filterFirst(filters);
            }
            return p;
        }

        public SetPreprocessorFactory<OverlapGroup<Clone>> overlapPreproc(PostanalysisParameters<?> base) {
            return new OverlapPreprocessorAdapter.Factory<>(preproc(base));
        }

        SetPreprocessorFactory<Clone> parseDownsampling(PostanalysisParameters<?> base) {
            String downsampling = this.downsampling == null ? base.defaultDownsampling : this.downsampling;
            boolean dropOutliers = this.dropOutliers == null ? base.defaultDropOutliers : this.dropOutliers;
            if (downsampling.equalsIgnoreCase("no-downsampling")) {
                return new NoPreprocessing.Factory<>();
            } else if (downsampling.startsWith("umi-count")) {
                if (downsampling.endsWith("auto"))
                    return new ClonesDownsamplingPreprocessorFactory(new DownsampleValueChooser.Auto(), 314, dropOutliers);
                else {
                    return new ClonesDownsamplingPreprocessorFactory(new DownsampleValueChooser.Fixed(downsamplingValue(downsampling)), 314, dropOutliers);
                }
            } else {
                int value = downsamplingValue(downsampling);
                if (downsampling.startsWith("cumulative-top")) {
                    return new SelectTop.Factory<>(WeightFunctions.Count, 1.0 * value / 100.0);
                } else if (downsampling.startsWith("top")) {
                    return new SelectTop.Factory<>(WeightFunctions.Count, value);
                } else {
                    throw new IllegalArgumentException("Illegal downsampling string: " + downsampling);
                }
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PreprocessorParameters that = (PreprocessorParameters) o;
            return Objects.equals(downsampling, that.downsampling) && Objects.equals(onlyProductive, that.onlyProductive) && Objects.equals(dropOutliers, that.dropOutliers);
        }

        @Override
        public int hashCode() {
            return Objects.hash(downsampling, onlyProductive, dropOutliers);
        }
    }

    private static int downsamplingValue(String downsampling) {
        return Integer.parseInt(downsampling.substring(downsampling.lastIndexOf("-") + 1));
    }

    static <K, T> List<Characteristic<?, T>> groupByPreproc(PostanalysisParameters<?> base,
                                                            Map<K, SetPreprocessorFactory<T>> preprocs,
                                                            BiFunction<SetPreprocessorFactory<T>, List<K>, List<? extends Characteristic<?, T>>> factory) {

        Map<SetPreprocessorFactory<T>, List<K>> byPreproc = preprocs.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue,
                        e -> Collections.singletonList(e.getKey()),
                        (a, b) -> {
                            List<K> l = new ArrayList<>();
                            l.addAll(a);
                            l.addAll(b);
                            return l;
                        }));

        List<Characteristic<?, T>> chars = new ArrayList<>();
        for (Map.Entry<SetPreprocessorFactory<T>, List<K>> e : byPreproc.entrySet()) {
            chars.addAll(factory.apply(e.getKey(), e.getValue()));
        }

        return chars;
    }
}
