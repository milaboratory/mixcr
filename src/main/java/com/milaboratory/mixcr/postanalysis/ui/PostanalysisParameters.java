package com.milaboratory.mixcr.postanalysis.ui;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.postanalysis.Characteristic;
import com.milaboratory.mixcr.postanalysis.SetPreprocessorFactory;
import com.milaboratory.mixcr.postanalysis.downsampling.DownsamplingUtil;
import com.milaboratory.mixcr.postanalysis.overlap.OverlapGroup;
import com.milaboratory.mixcr.postanalysis.preproc.OverlapPreprocessorAdapter;
import com.milaboratory.primitivio.annotations.Serializable;

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

    @JsonAutoDetect(
            fieldVisibility = JsonAutoDetect.Visibility.ANY,
            isGetterVisibility = JsonAutoDetect.Visibility.NONE,
            getterVisibility = JsonAutoDetect.Visibility.NONE)
    public static class PreprocessorParameters {
        public String downsampling;
        public Boolean onlyProductive;
        public Boolean dropOutliers;

        public SetPreprocessorFactory<Clone> preproc(PostanalysisParameters base) {
            SetPreprocessorFactory<Clone> p = parseDownsampling(base);

            boolean onlyProductive = this.onlyProductive == null ? base.defaultOnlyProductive : this.onlyProductive;
            if (onlyProductive)
                p = DownsamplingUtil.filterOnlyProductive(p);
            return p;
        }

        public SetPreprocessorFactory<OverlapGroup<Clone>> overlapPreproc(PostanalysisParameters base) {
            return new OverlapPreprocessorAdapter.Factory<>(preproc(base));
        }

        SetPreprocessorFactory<Clone> parseDownsampling(PostanalysisParameters base) {
            return DownsamplingUtil.parseDownsampling(
                    this.downsampling == null ? base.defaultDownsampling : this.downsampling,
                    this.dropOutliers == null ? base.defaultDropOutliers : this.dropOutliers
            );
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

    static <K, T> List<Characteristic<?, T>> groupByPreproc(Map<K, SetPreprocessorFactory<T>> preprocs,
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
