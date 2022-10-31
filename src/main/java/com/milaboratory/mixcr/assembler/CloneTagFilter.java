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
package com.milaboratory.mixcr.assembler;

import cc.redberry.pipe.CUtils;
import com.milaboratory.mitool.helpers.PipeKt;
import com.milaboratory.mitool.refinement.gfilter.*;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.tag.TagCountAggregator;
import com.milaboratory.mixcr.basictypes.tag.TagInfo;
import com.milaboratory.mixcr.basictypes.tag.TagTuple;
import com.milaboratory.mixcr.basictypes.tag.TagsInfo;
import com.milaboratory.mixcr.util.Tuple2;
import com.milaboratory.util.sorting.SortingUtil;
import gnu.trove.iterator.TObjectDoubleIterator;
import gnu.trove.map.hash.TIntObjectHashMap;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Applies a clone*tag filtering to a list of clones */
public final class CloneTagFilter {
    private CloneTagFilter() {
    }

    public static final class CloneTagFilteringResult {
        public final List<Clone> clones;
        public final List<KeyedFilterReport> reports;

        public CloneTagFilteringResult(List<Clone> clones, List<KeyedFilterReport> reports) {
            this.clones = clones;
            this.reports = reports;
        }
    }

    public static List<CloneTag> toCloneTags(List<Clone> clones) {
        List<CloneTag> result = new ArrayList<>();
        for (Clone clone : clones) {
            TObjectDoubleIterator<TagTuple> it = clone.getTagCount().iterator();
            while (it.hasNext()) {
                it.advance();
                result.add(new CloneTag(clone, it.key(), it.value()));
            }
        }
        return result;
    }

    public static CloneTagFilteringResult filter(TagsInfo tagsInfo, List<KeyedRecordFilter> filters, List<Clone> clones) {
        // Converting to a clone*tag list
        List<CloneTag> cloneTags = toCloneTags(clones);
        List<KeyedFilterReport> reports = new ArrayList<>();
        // Filtering the clone*tag list
        for (KeyedRecordFilter filter : filters) {
            Tuple2<List<CloneTag>, KeyedFilterReport> result = filter(tagsInfo, filter, cloneTags);
            cloneTags = result._1;
            reports.add(result._2);
        }
        // Reassembling into a clone list
        TIntObjectHashMap<TagCountAggregator> tagReAggregators = new TIntObjectHashMap<>(clones.size());
        for (CloneTag cloneTag : cloneTags) {
            int id = cloneTag.clone.getId();
            TagCountAggregator agg = tagReAggregators.get(id);
            if (agg == null)
                tagReAggregators.put(id, agg = new TagCountAggregator());
            agg.add(cloneTag.tags, cloneTag.weight);
        }
        List<Clone> newClones = new ArrayList<>(clones.size());
        for (Clone clone : clones) {
            TagCountAggregator agg = tagReAggregators.get(clone.getId());
            if (agg == null)
                // the clone was filtered out
                continue;
            newClones.add(clone.setTagCount(agg.createAndDestroy(), true));
        }
        return new CloneTagFilteringResult(newClones, reports);
    }

    public static Tuple2<List<CloneTag>, KeyedFilterReport> filter(TagsInfo tagsInfo, KeyedRecordFilter filter, List<CloneTag> cloneTags) {
        List<String> expectedSorting = filter.getExpectedSorting();
        List<CloneTagKey> keys = expectedSorting.stream()
                .map(keyName -> getKeyByName(tagsInfo, keyName))
                .collect(Collectors.toList());
        List<CloneTag> grouped = SortingUtil.hGroup(cloneTags, keys);
        CloneTagStreamGrouping streamGrouping = new CloneTagStreamGrouping(expectedSorting, keys);
        KeyedFilterContext<CloneTag> ctx = new KeyedFilterContext<>(null, streamGrouping, Collections.emptyList());
        FilteredOutputPortFactory<CloneTag> filtered = filter.filter(ctx, PipeKt.asOutputPortFactory(grouped));
        List<CloneTag> result = CUtils.toList(filtered.createPort());
        KeyedFilterReport report = filtered.getReport();
        return new Tuple2<>(result, report);
    }

    private static final class CloneTagStreamGrouping implements StreamGrouping<CloneTag> {
        private final List<String> expectedSorting;
        private final List<CloneTagKey> keys;

        public CloneTagStreamGrouping(List<String> expectedSorting, List<CloneTagKey> keys) {
            if (expectedSorting.size() != keys.size())
                throw new IllegalArgumentException();
            this.expectedSorting = expectedSorting;
            this.keys = keys;
        }

        @NotNull
        @Override
        public Pair<KeyExtractor<CloneTag>, StreamGrouping<CloneTag>> getKeyExtractor(@NotNull List<String> tagNames) {
            int n = tagNames.size();
            if (n == 0 || n > expectedSorting.size())
                throw new IllegalArgumentException();
            for (int i = 0; i < n; i++)
                if (!tagNames.get(i).equals(expectedSorting.get(i)))
                    throw new IllegalArgumentException();

            KeyExtractor<CloneTag> ke = n == 1
                    ? keys.get(0)
                    : new KeyExtractor<CloneTag>() {
                @NotNull
                @Override
                public Class<?> getClazz() {
                    return List.class;
                }

                @Override
                public Object get(CloneTag obj) {
                    ArrayList<Object> key = new ArrayList<>();
                    for (int i = 0; i < n; i++)
                        key.add(keys.get(i).get(obj));
                    return key;
                }
            };

            return new Pair<>(
                    ke,
                    new CloneTagStreamGrouping(
                            expectedSorting.subList(n, expectedSorting.size()),
                            keys.subList(n, keys.size()))
            );
        }
    }

    private static final class CloneTag implements KeyedRecord {
        final Clone clone;
        final TagTuple tags;
        final double weight;

        public CloneTag(Clone clone, TagTuple tags, double weight) {
            this.clone = clone;
            this.tags = tags;
            this.weight = weight;
        }

        @Override
        public double getWeight() {
            return weight;
        }
    }

    public static CloneTagKey getKeyByName(TagsInfo tagsInfo, String key) {
        if (key.startsWith("tag:")) {
            String tagName = key.substring(4);
            TagInfo tagInfo = Objects.requireNonNull(tagsInfo.get(tagName));
            return new TagExtractor(tagInfo.getValueType().getValueClass(), tagInfo.getIndex());
        } else if (key.equals("clone"))
            return new CloneIdExtractor();
        else if (key.startsWith("geneLabel:"))
            return new GeneLabelExtractor(key.substring(10));
        else
            throw new IllegalArgumentException("Unrecognized key: " + key);
    }

    public interface CloneTagKey extends KeyExtractor<CloneTag>, Function<CloneTag, Object> {
        @Override
        default Object apply(CloneTag cloneTag) {
            return get(cloneTag);
        }
    }

    public static final class TagExtractor implements CloneTagKey {
        private final Class<?> valueType;
        private final int index;

        public TagExtractor(Class<?> valueType, int index) {
            this.valueType = valueType;
            this.index = index;
        }

        @NotNull
        @Override
        public Class<?> getClazz() {
            return valueType;
        }

        @Override
        public Object get(CloneTag obj) {
            return obj.tags.get(index);
        }
    }

    public static final class CloneIdExtractor implements CloneTagKey {
        @NotNull
        @Override
        public Class<?> getClazz() {
            return Integer.class;
        }

        @Override
        public Object get(CloneTag obj) {
            return obj.clone.getId();
        }
    }

    public static final class GeneLabelExtractor implements CloneTagKey {
        private final String labelName;

        public GeneLabelExtractor(String labelName) {
            this.labelName = labelName;
        }

        @NotNull
        @Override
        public Class<?> getClazz() {
            return String.class;
        }

        @Override
        public Object get(CloneTag obj) {
            return obj.clone.getGeneLabel(labelName);
        }
    }
}
