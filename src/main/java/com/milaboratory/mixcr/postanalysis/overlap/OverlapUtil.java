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

import cc.redberry.pipe.OutputPortCloseable;
import cc.redberry.pipe.util.SimpleProcessorWrapper;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.mixcr.util.OutputPortWithProgress;
import com.milaboratory.util.LambdaSemaphore;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGene;
import io.repseq.core.VDJCLibraryRegistry;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

public final class OverlapUtil {
    private OverlapUtil() {
    }

    public static OverlapDataset<Clone> overlap(
            List<String> datasetIds,
            List<? extends VDJCSProperties.VDJCSProperty<? super Clone>> by,
            List<? extends CloneReader> readers) {
        return new OverlapDataset<Clone>(datasetIds) {
            @Override
            public OutputPortWithProgress<OverlapGroup<Clone>> mkElementsPort() {
                OutputPortWithProgress<List<List<Clone>>> port = CloneSetOverlap.overlap(by, readers);
                SimpleProcessorWrapper<List<List<Clone>>, OverlapGroup<Clone>> processor = new SimpleProcessorWrapper<>(port, OverlapGroup::new);
                return new OutputPortWithProgress<OverlapGroup<Clone>>() {
                    @Override
                    public long currentIndex() {
                        return port.currentIndex();
                    }

                    @Override
                    public void finish() {

                    }

                    @Override
                    public void close() {
                        processor.close();
                    }

                    @Override
                    public OverlapGroup<Clone> take() {
                        return processor.take();
                    }

                    @Override
                    public double getProgress() {
                        return port.getProgress();
                    }

                    @Override
                    public boolean isFinished() {
                        return port.isFinished();
                    }
                };
            }
        };
    }

    public static OverlapDataset<Clone> overlap(
            List<String> samples,
            Predicate<Clone> filter,
            List<? extends VDJCSProperties.VDJCSProperty<? super Clone>> by
    ) {
        // Limits concurrency across all readers
        LambdaSemaphore concurrencyLimiter = new LambdaSemaphore(32);
        List<CloneReader> readers = samples
                .stream()
                .map(s -> {
                    try {
                        return mkCheckedReader(
                                Paths.get(s).toAbsolutePath(),
                                filter,
                                concurrencyLimiter);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toList());

        return OverlapUtil.overlap(
                samples.stream().map(OverlapUtil::getSampleId).collect(toList()),
                by,
                readers);
    }

    /** Get sample id from file name */
    static String getSampleId(String file) {
        return Paths.get(file).getFileName().toString();
    }

    public static CloneReader mkCheckedReader(Path path,
                                              LambdaSemaphore concurrencyLimiter) throws IOException {
        return mkCheckedReader(path, __ -> true, concurrencyLimiter);
    }

    public static CloneReader mkCheckedReader(Path path,
                                              Predicate<Clone> filter,
                                              LambdaSemaphore concurrencyLimiter) throws IOException {
        CloneReader inner = CloneSetIO.mkReader(
                path,
                VDJCLibraryRegistry.getDefault(),
                concurrencyLimiter);
        return new CloneReader() {
            @Override
            public VDJCSProperties.CloneOrdering ordering() {
                return inner.ordering();
            }

            @Override
            public OutputPortCloseable<Clone> readClones() {
                OutputPortCloseable<Clone> in = inner.readClones();
                return new OutputPortCloseable<Clone>() {
                    @Override
                    public void close() {
                        in.close();
                    }

                    @Override
                    public Clone take() {
                        while (true) {
                            Clone t = in.take();
                            if (t == null)
                                return null;
                            if (t.getFeature(GeneFeature.CDR3) == null)
                                continue;
                            if (!filter.test(t))
                                continue;
                            return t;
                        }
                    }
                };
            }

            @Override
            public void close() throws Exception {
                inner.close();
            }

            @Override
            public MiXCRHeader getHeader() {
                return inner.getHeader();
            }

            @Override
            public int numberOfClones() {
                return inner.numberOfClones();
            }

            @Override
            public List<VDJCGene> getUsedGenes() {
                return inner.getUsedGenes();
            }

            @Override
            public MiXCRFooter getFooter() {
                return inner.getFooter();
            }
        };
    }

    public static final class OverlapCriteria {
        public final GeneFeature feature;
        public final boolean isAA;
        public final boolean withV;
        public final boolean withJ;

        public OverlapCriteria(GeneFeature feature, boolean isAA, boolean withV, boolean withJ) {
            this.feature = feature;
            this.isAA = isAA;
            this.withV = withV;
            this.withJ = withJ;
        }

        public List<VDJCSProperties.VDJCSProperty<VDJCObject>> ordering() {
            List<GeneType> geneTypes = new ArrayList<>();
            if (withV)
                geneTypes.add(GeneType.Variable);
            if (withJ)
                geneTypes.add(GeneType.Joining);
            if (isAA)
                return VDJCSProperties.orderingByAminoAcid(new GeneFeature[]{feature}, geneTypes.toArray(new GeneType[0]));
            else
                return VDJCSProperties.orderingByNucleotide(new GeneFeature[]{feature}, geneTypes.toArray(new GeneType[0]));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OverlapCriteria that = (OverlapCriteria) o;
            return isAA == that.isAA && withV == that.withV && withJ == that.withJ && Objects.equals(feature, that.feature);
        }

        @Override
        public int hashCode() {
            return Objects.hash(feature, isAA, withV, withJ);
        }
    }

    public static OverlapCriteria parseCriteria(String overlapCriteria) {
        String[] parts = overlapCriteria.toLowerCase().split("\\|");
        if (parts.length < 2)
            throw new IllegalArgumentException("Illegal criteria input: " + overlapCriteria);
        GeneFeature feature = GeneFeature.parse(parts[0]);
        if (!parts[1].equals("aa") && !parts[1].equals("nt"))
            throw new IllegalArgumentException("Illegal criteria input: " + overlapCriteria);
        boolean isAA = parts[1].equals("aa");
        boolean withV = false, withJ = false;
        if (parts.length > 2)
            if (!parts[2].equals("v"))
                throw new IllegalArgumentException("Illegal criteria input: " + overlapCriteria);
            else
                withV = true;
        if (parts.length > 3)
            if (!parts[3].equals("j"))
                throw new IllegalArgumentException("Illegal criteria input: " + overlapCriteria);
            else
                withJ = true;

        return new OverlapCriteria(feature, isAA, withV, withJ);
    }
}
