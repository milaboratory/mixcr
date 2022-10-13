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
package com.milaboratory.mixcr.postanalysis.downsampling;

import cc.redberry.pipe.InputPort;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.tag.TagCount;
import com.milaboratory.mixcr.basictypes.tag.TagTuple;
import com.milaboratory.mixcr.postanalysis.MappingFunction;
import com.milaboratory.mixcr.postanalysis.SetPreprocessor;
import com.milaboratory.mixcr.postanalysis.SetPreprocessorSetup;
import com.milaboratory.mixcr.postanalysis.SetPreprocessorStat;
import gnu.trove.iterator.TObjectDoubleIterator;
import gnu.trove.map.hash.TIntObjectHashMap;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.Well19937c;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class DownsamplingPreprocessorByTags implements SetPreprocessor<Clone> {
    final int tagLevel;
    public final DownsampleValueChooser downsampleValueChooser;
    final boolean dropOutliers;
    final String id;
    private final SetPreprocessorStat.Builder<Clone> stats;

    public DownsamplingPreprocessorByTags(int tagLevel,
                                          DownsampleValueChooser downsampleValueChooser,
                                          boolean dropOutliers,
                                          String id) {
        this.tagLevel = tagLevel;
        this.downsampleValueChooser = downsampleValueChooser;
        this.dropOutliers = dropOutliers;
        this.id = id;
        this.stats = new SetPreprocessorStat.Builder<>(id, clone -> clone.getTagCount().reduceToLevel(tagLevel).size());
    }

    private ComputeCountsStep setup = null;
    private int downsampling = -1;

    @Override
    public SetPreprocessorSetup<Clone> nextSetupStep() {
        if (setup == null)
            return setup = new ComputeCountsStep();

        return null;
    }

    @Override
    public MappingFunction<Clone> getMapper(int iDataset) {
        stats.clear(iDataset);

        if (downsampling == -1) {
            downsampling = (int) downsampleValueChooser.compute(
                    Arrays.stream(setup.tags).mapToLong(List::size).toArray());
        }

        if (downsampling > setup.tags[iDataset].size()) {
            if (dropOutliers) {
                stats.drop(iDataset);
                return t -> null;
            } else
                return t -> {
                    stats.asis(iDataset, t);
                    return t;
                };
        }

        // select N random elements using random shuffling
        RandomGenerator rnd = new Well19937c(downsampling);
        TagTupleAndIdx[] tagTuples = setup.tags[iDataset].toArray(new TagTupleAndIdx[0]);
        for (int n = 0; n < tagTuples.length; n++) {
            int i = rnd.nextInt(tagTuples.length);
            int j = rnd.nextInt(tagTuples.length);
            if (i == j)
                continue;

            TagTupleAndIdx c = tagTuples[i];
            tagTuples[i] = tagTuples[j];
            tagTuples[j] = c;
        }

        Map<Integer, HashSet<TagTuple>> clone2tuples = Arrays.stream(tagTuples, 0, downsampling)
                .collect(Collectors.toMap(
                        s -> s.cloneIndex,
                        s -> {
                            HashSet<TagTuple> set = new HashSet<>();
                            set.add(s.tagTuple);
                            return set;
                        },
                        (a, b) -> {
                            a.addAll(b);
                            return a;
                        }
                ));

        AtomicInteger idx = new AtomicInteger(0);
        return clone -> {
            stats.before(iDataset, clone);

            int cloneIndex = idx.getAndIncrement();
            HashSet<TagTuple> allowed = clone2tuples.get(cloneIndex);
            if (allowed == null)
                return null;

            TagCount tc = clone.getTagCount().reduceToLevel(tagLevel);
            TagCount newTc = tc.filter(t -> allowed.contains(t.prefix(tagLevel)));
            if (newTc.isNoTag())
                return null;

            Clone newClone = clone.setTagCount(newTc, true);
            stats.after(iDataset, newClone);
            return newClone;
        };
    }

    @Override
    public TIntObjectHashMap<List<SetPreprocessorStat>> getStat() {
        return stats.getStatMap();
    }

    @Override
    public String id() {
        return id;
    }

    class ComputeCountsStep implements SetPreprocessorSetup<Clone> {
        // store all tags for all clones
        ArrayList<TagTupleAndIdx>[] tags;

        boolean initialized = false;

        @Override
        public void initialize(int nDatasets) {
            if (initialized)
                throw new IllegalStateException();
            initialized = true;
            //noinspection unchecked
            tags = new ArrayList[nDatasets];
            for (int i = 0; i < nDatasets; i++) {
                tags[i] = new ArrayList<>();
            }
        }

        @Override
        public InputPort<Clone> consumer(int iDataset) {
            if (!initialized)
                throw new IllegalStateException();
            AtomicInteger idx = new AtomicInteger(0);
            return clone -> {
                int cloneIndex = idx.getAndIncrement();

                if (clone == null)
                    return;

                TagCount tagCount = clone.getTagCount().reduceToLevel(tagLevel);
                tags[iDataset].ensureCapacity(tags[iDataset].size() + tagCount.size());
                TObjectDoubleIterator<TagTuple> it = tagCount.iterator();
                while (it.hasNext()) {
                    it.advance();
                    tags[iDataset].add(new TagTupleAndIdx(cloneIndex, it.key()));
                }
            };
        }
    }

    private static final class TagTupleAndIdx {
        final int cloneIndex;
        final TagTuple tagTuple;

        public TagTupleAndIdx(int cloneIndex, TagTuple tagTuple) {
            this.cloneIndex = cloneIndex;
            this.tagTuple = tagTuple;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TagTupleAndIdx that = (TagTupleAndIdx) o;
            return cloneIndex == that.cloneIndex && Objects.equals(tagTuple, that.tagTuple);
        }

        @Override
        public int hashCode() {
            return Objects.hash(cloneIndex, tagTuple);
        }
    }
}
