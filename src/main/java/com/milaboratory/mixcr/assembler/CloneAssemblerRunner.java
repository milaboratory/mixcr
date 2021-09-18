/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 * 
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 * 
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.assembler;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPortCloseable;
import cc.redberry.pipe.blocks.FilteringPort;
import com.milaboratory.mixcr.basictypes.CloneSet;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.util.CanReportProgress;
import com.milaboratory.util.CanReportProgressAndStage;

public class CloneAssemblerRunner implements CanReportProgressAndStage {
    final AlignmentsProvider alignmentsProvider;
    final CloneAssembler assembler;
    final int threads;
    volatile String stage = "Initialization";
    volatile CanReportProgress innerProgress;
    volatile VDJCAlignmentsReader alignmentReader = null;
    volatile boolean isFinished = false;

    public CloneAssemblerRunner(AlignmentsProvider alignmentsProvider, CloneAssembler assembler, int threads) {
        this.alignmentsProvider = alignmentsProvider;
        this.assembler = assembler;
        this.threads = Math.min(threads, Runtime.getRuntime().availableProcessors());
    }

    @Override
    public String getStage() {
        return stage;
    }

    @Override
    public double getProgress() {
        if (innerProgress == null)
            return Double.NaN;
        return innerProgress.getProgress();
    }

    @Override
    public boolean isFinished() {
        return isFinished;
    }

    public void run() {
        //run initial assembler
        try (OutputPortCloseable<VDJCAlignments> alignmentsPort = alignmentsProvider.create()) {
            if (alignmentsPort instanceof VDJCAlignmentsReaderWrapper.OP)
                alignmentReader = ((VDJCAlignmentsReaderWrapper.OP) alignmentsPort).reader;
            synchronized (this) {
                stage = "Assembling initial clonotypes";
                if (alignmentsPort instanceof CanReportProgress)
                    innerProgress = (CanReportProgress) alignmentsPort;
            }
            try {
                CUtils.processAllInParallel(CUtils.buffered(alignmentsPort, 128), assembler.getInitialAssembler(), threads);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            alignmentReader = null;
        }
        // run mapping if required
        if (assembler.beginMapping()) {
            synchronized (this) {
                stage = "Preparing for mapping of low quality reads";
                innerProgress = null;
            }
            try (OutputPortCloseable<VDJCAlignments> alignmentsPort = alignmentsProvider.create()) {
                if (alignmentsPort instanceof VDJCAlignmentsReaderWrapper.OP)
                    alignmentReader = ((VDJCAlignmentsReaderWrapper.OP) alignmentsPort).reader;
                synchronized (this) {
                    stage = "Mapping low quality reads";
                    if (alignmentsPort instanceof CanReportProgress)
                        innerProgress = (CanReportProgress) alignmentsPort;
                }
                try {
                    CUtils.processAllInParallel(CUtils.buffered(
                            new FilteringPort<>(alignmentsPort,
                                    assembler.getDeferredAlignmentsFilter()), 128),
                            assembler.getDeferredAlignmentsMapper(), threads);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            alignmentReader = null;
            assembler.endMapping();
        }
        assembler.preClustering();
        //run clustering
        if (assembler.parameters.isClusteringEnabled()) {
            synchronized (this) {
                stage = "Clustering";
                innerProgress = assembler;
            }
            assembler.runClustering();
        }
        //build clones
        synchronized (this) {
            stage = "Building clones";
            innerProgress = assembler;
        }
        assembler.buildClones();
        isFinished = true;
    }

    public int getQueueSize() {
        if (alignmentReader == null)
            return -1;
        return alignmentReader.getQueueSize();
    }

    public CloneSet getCloneSet(VDJCAlignerParameters alignerParameters) {
        return assembler.getCloneSet(alignerParameters);
    }
}
