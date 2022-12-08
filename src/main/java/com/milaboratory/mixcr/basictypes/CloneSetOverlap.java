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
package com.milaboratory.mixcr.basictypes;

import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.blocks.FilteringPort;
import cc.redberry.primitives.Filter;
import com.milaboratory.mixcr.util.OutputPortWithProgress;
import com.milaboratory.primitivio.PrimitivIOStateBuilder;
import com.milaboratory.util.TempFileManager;
import com.milaboratory.util.sorting.MergeStrategy;
import com.milaboratory.util.sorting.UnorderedMerger;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public final class CloneSetOverlap {
    private CloneSetOverlap() {
    }

    public static OutputPortWithProgress<List<List<Clone>>> overlap(
            List<? extends VDJCSProperties.VDJCSProperty<? super Clone>> by,
            List<? extends CloneReader> readers) {

        List<OutputPortWithProgress<Clone>> individualPorts = readers
                .stream()
                .map(r -> OutputPortWithProgress.wrap(r.numberOfClones(), r.readClones()))
                .collect(Collectors.toList());


        VDJCSProperties.CloneOrdering ordering = readers.get(0).ordering();
        boolean needSort = false;
        for (int i = 1; i < readers.size(); i++)
            if (!ordering.equals(readers.get(i).ordering())) {
                needSort = true;
                break;
            }

        if (!needSort && !MergeStrategy.calculateStrategy(ordering.getProperties(), by).usesStreamOrdering())
            needSort = true;

        Filter<Clone> cloneFilter = clone -> by.stream().allMatch(b -> b.get(clone) != null);
        if (needSort) {
            // HDD-offloading collator of alignments
            // Collate solely by cloneId (no sorting by mapping type, etc.);
            // less fields to sort by -> faster the procedure
            long memoryBudget =
                    Runtime.getRuntime().maxMemory() > 10_000_000_000L /* -Xmx10g */
                            ? Runtime.getRuntime().maxMemory() / 4L /* 1 Gb */
                            : 1 << 28 /* 256 Mb */;
            PrimitivIOStateBuilder stateBuilder = new PrimitivIOStateBuilder();
            IOUtil.registerGeneReferences(stateBuilder, readers.get(0).getUsedGenes(), readers.get(0).getHeader().getFeaturesToAlign());
            UnorderedMerger<Clone> merger = new UnorderedMerger<>(
                    Clone.class,
                    readers.stream()
                            .map(CloneReader::readClones)
                            .map(it -> new FilteringPort<>(it, cloneFilter))
                            .collect(Collectors.toList()),
                    by,
                    5,
                    TempFileManager.systemTempFolderDestination("mixcr.overlap."),
                    4, 6,
                    memoryBudget, 1 << 18 /* 256 Kb */, stateBuilder);
            merger.loadData();
            AtomicLong index = new AtomicLong(0);
            return new OutputPortWithProgress<List<List<Clone>>>() {
                @Override
                public long currentIndex() {
                    return index.get();
                }

                @Override
                public void finish() {

                }

                @Override
                public void close() {
                    merger.close();
                }

                @Override
                public List<List<Clone>> take() {
                    List<List<Clone>> t = merger.take();
                    if (t == null) {
                        return null;
                    }
                    index.incrementAndGet();
                    return t;
                }

                @Override
                public double getProgress() {
                    return merger.getProgress();
                }

                @Override
                public boolean isFinished() {
                    return merger.isFinished();
                }
            };
        } else {
            MergeStrategy<Clone> strategy = MergeStrategy.calculateStrategy(ordering.getProperties(), by);
            @SuppressWarnings("resource")
            OutputPort<List<List<Clone>>> joinedPort = strategy.join(individualPorts.
                    stream()
                    .map(it -> new FilteringPort<>(it, cloneFilter))
                    .collect(Collectors.toList()));
            AtomicLong index = new AtomicLong(0);
            AtomicBoolean isFinished = new AtomicBoolean(false);
            long totalClones = readers.stream().mapToLong(CloneReader::numberOfClones).sum();
            return new OutputPortWithProgress<List<List<Clone>>>() {
                @Override
                public long currentIndex() {
                    return index.get();
                }

                @Override
                public void finish() {

                }

                @Override
                public void close() {
                    joinedPort.close();
                }

                @Override
                public List<List<Clone>> take() {
                    List<List<Clone>> t = joinedPort.take();
                    if (t == null) {
                        isFinished.set(true);
                        return null;
                    }
                    index.incrementAndGet();
                    return t;
                }

                @Override
                public double getProgress() {
                    return 1.0 * individualPorts.stream().mapToLong(OutputPortWithProgress::currentIndex).sum() / totalClones;
                }

                @Override
                public boolean isFinished() {
                    return isFinished.get();
                }
            };
        }
    }
}
