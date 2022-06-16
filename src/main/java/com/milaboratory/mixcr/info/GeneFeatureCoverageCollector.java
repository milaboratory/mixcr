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
package com.milaboratory.mixcr.info;

import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.util.ReportHelper;
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
                "\t" + ReportHelper.PERCENT_FORMAT.format(100.0 * covered.get() / total.get()) + "%");
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
