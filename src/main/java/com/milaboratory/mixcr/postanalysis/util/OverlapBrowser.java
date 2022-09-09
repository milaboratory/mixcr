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
package com.milaboratory.mixcr.postanalysis.util;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.CloneReader;
import com.milaboratory.mixcr.basictypes.CloneSetIO;
import com.milaboratory.mixcr.postanalysis.overlap.OverlapGroup;
import com.milaboratory.mixcr.util.OutputPortWithProgress;
import com.milaboratory.util.CanReportProgress;
import com.milaboratory.util.CanReportProgressAndStage;
import com.milaboratory.util.ProgressAndStage;
import io.repseq.core.Chains;
import io.repseq.core.GeneFeature;
import io.repseq.core.VDJCLibraryRegistry;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class OverlapBrowser implements CanReportProgressAndStage {
    final boolean onlyProductive;

    public OverlapBrowser(boolean onlyProductive) {
        this.onlyProductive = onlyProductive;
    }

    private final ProgressAndStage pas = new ProgressAndStage("");

    @Override
    public double getProgress() {
        return pas.getProgress();
    }

    @Override
    public boolean isFinished() {
        return pas.isFinished();
    }

    @Override
    public String getStage() {
        return pas.getStage();
    }


    /** Compute counts for each chain in each sample */
    public Map<Chains, double[]> computeCountsByChain(List<String> samples) {
        Map<Chains, double[]> counts = new HashMap<>();
        AtomicInteger sampleIndex = new AtomicInteger(0);
        pas.setStage("Calculating dataset counts");
        pas.delegate(new CanReportProgress() {
            @Override
            public double getProgress() {
                return 1.0 * sampleIndex.get() / samples.size();
            }

            @Override
            public boolean isFinished() {
                return sampleIndex.get() == samples.size();
            }
        });
        for (int i = 0; i < samples.size(); i++) {
            try (CloneReader reader = CloneSetIO.mkReader(Paths.get(samples.get(i)), VDJCLibraryRegistry.getDefault())) {
                for (Clone cl : CUtils.it(reader.readClones())) {
                    if (!includeClone(cl))
                        continue;
                    Chains chains = cl.commonTopChains();
                    counts.computeIfAbsent(chains, __ -> new double[samples.size()])[i] += cl.getCount();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            sampleIndex.set(i + 1);
        }
        return counts;
    }

    public OutputPort<Map<Chains, OverlapGroup<Clone>>> overlap(Map<Chains, double[]> counts,
                                                                OutputPortWithProgress<OverlapGroup<Clone>> port) {
        pas.setStage("Calculating overlap");
        pas.delegate(port);
        return () -> {
            while (true) {
                OverlapGroup<Clone> row = port.take();
                if (row == null)
                    return null;

                Map<Chains, OverlapGroup<Clone>> map = new HashMap<>();
                for (Chains ch : counts.keySet()) {
                    OverlapGroup<Clone> forChain = forChains(row, ch);
                    if (forChain == null)
                        continue;

                    if (onlyProductive)
                        forChain = filterProductive(forChain, true);

                    if (forChain == null)
                        continue;

                    double[] cs = counts.get(ch);
                    for (int i = 0; i < forChain.size(); i++)
                        for (Clone cl : forChain.getBySample(i))
                            cl.overrideFraction(cl.getCount() / cs[i]);

                    map.put(ch, forChain);
                }

                if (map.size() > 0)
                    return map;
            }
        };
    }

    private static OverlapGroup<Clone> filter(OverlapGroup<Clone> row, Predicate<Clone> criteria, boolean inPlace) {
        if (inPlace) {
            boolean empty = true;
            for (List<Clone> l : row) {
                l.removeIf(c -> !criteria.test(c));
                if (!l.isEmpty()) empty = false;
            }
            if (empty) return null;
            else return row;
        } else {
            boolean empty = true;
            List<List<Clone>> r = new ArrayList<>();
            for (List<Clone> l : row) {
                List<Clone> f = l.stream().filter(criteria).collect(Collectors.toList());
                r.add(f);
                if (!f.isEmpty()) empty = false;
            }
            if (empty) return null;
            else return new OverlapGroup<>(r);
        }
    }

    private boolean includeClone(Clone c) {
        return !onlyProductive || !(c.isOutOfFrameOrAbsent(GeneFeature.CDR3) || c.containsStopsOrAbsent(GeneFeature.CDR3));
    }

    private static boolean chainsMatch(Clone c, Chains chains) {
        return c.commonTopChains().equals(chains);
    }

    private static OverlapGroup<Clone> forChains(OverlapGroup<Clone> row, Chains chains) {
        return chains == null ? row : filter(row, c -> chainsMatch(c, chains), false);
    }

    private OverlapGroup<Clone> filterProductive(OverlapGroup<Clone> row, boolean inPlace) {
        return onlyProductive ? filter(row, this::includeClone, inPlace) : row;
    }
}
