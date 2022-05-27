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
import io.repseq.core.Chains.NamedChains;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCLibraryRegistry;

import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class OverlapBrowser implements CanReportProgressAndStage {
    final List<NamedChains> chains;
    final boolean onlyProductive;

    public OverlapBrowser(List<NamedChains> chains, boolean onlyProductive) {
        this.chains = chains;
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


    public Map<NamedChains, double[]> computeCounts(List<String> samples) {
        Map<NamedChains, double[]> counts = new HashMap<>();
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
                    if (!isProductive(cl))
                        continue;
                    for (NamedChains ch : chains) {
                        if (!hasChains(cl, ch))
                            continue;
                        counts.computeIfAbsent(ch, __ -> new double[samples.size()])[i] += cl.getCount();
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            sampleIndex.set(i + 1);
        }
        return counts;
    }

    public OutputPort<Map<NamedChains, OverlapGroup<Clone>>>
    overlap(Map<NamedChains, double[]> counts,
            OutputPortWithProgress<OverlapGroup<Clone>> port) {
        pas.setStage("Calculating overlap");
        pas.delegate(port);
        return () -> {
            while (true) {
                OverlapGroup<Clone> row = port.take();
                if (row == null)
                    return null;

                Map<NamedChains, OverlapGroup<Clone>> map = new HashMap<>();
                for (NamedChains ch : chains) {
                    OverlapGroup<Clone> forChain = forChains(row, ch);
                    if (forChain == null)
                        continue;

                    if (counts != null) {
                        double[] cs = counts.get(ch);
                        for (int i = 0; i < forChain.size(); i++)
                            for (Clone cl : forChain.getBySample(i))
                                cl.overrideFraction(cl.getCount() / cs[i]);
                    }

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
                if (!l.isEmpty())
                    empty = false;
            }
            if (empty)
                return null;
            else
                return row;
        } else {
            boolean empty = true;
            List<List<Clone>> r = new ArrayList<>();
            for (List<Clone> l : row) {
                List<Clone> f = l.stream().filter(criteria).collect(Collectors.toList());
                r.add(f);
                if (!f.isEmpty())
                    empty = false;
            }
            if (empty)
                return null;
            else
                return new OverlapGroup<>(r);
        }
    }

    private static boolean isProductive(Clone c) {
        return !c.isOutOfFrame(GeneFeature.CDR3) && !c.containsStops(GeneFeature.CDR3);
    }

    private static boolean hasChains(Clone c, NamedChains chains) {
        return chains == Chains.ALL_NAMED || Arrays.stream(GeneType.VJC_REFERENCE).anyMatch(gt -> {
            Chains clChains = c.getAllChains(gt);
            if (clChains == null)
                return false;
            return clChains.intersects(chains.chains);
        });
    }

    private static OverlapGroup<Clone> forChains(OverlapGroup<Clone> row, NamedChains chains) {
        return chains == Chains.ALL_NAMED ? row : filter(row, c -> hasChains(c, chains), false);
    }

    private OverlapGroup<Clone> filterProductive(OverlapGroup<Clone> row, boolean inPlace) {
        return onlyProductive ? filter(row, OverlapBrowser::isProductive, inPlace) : row;
    }
}
