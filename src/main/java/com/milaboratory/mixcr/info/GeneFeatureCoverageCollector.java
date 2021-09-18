/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 *
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 *
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.info;

import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.cli.Util;
import io.repseq.core.GeneFeature;

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by dbolotin on 04/08/15.
 */
public class GeneFeatureCoverageCollector implements AlignmentInfoCollector {
    final AtomicLong total = new AtomicLong(),
            covered = new AtomicLong();
    final GeneFeature feature;

    public GeneFeatureCoverageCollector(GeneFeature feature) {
        this.feature = feature;
    }

    @Override
    public void writeResult(PrintStream writer) {
        writer.println("" + GeneFeature.encode(feature) + "\t" + covered.get() +
                "\t" + Util.PERCENT_FORMAT.format(100.0 * covered.get() / total.get()) + "%");
    }

    @Override
    public void put(VDJCAlignments alignments) {
        total.incrementAndGet();
        if (alignments.getFeature(feature) != null)
            covered.incrementAndGet();
    }

    @Override
    public void end() {
    }
}
