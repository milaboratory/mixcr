/*
 * Copyright (c) 2014-2015, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
 * (here and after addressed as Inventors)
 * All Rights Reserved
 *
 * Permission to use, copy, modify and distribute any part of this program for
 * educational, research and non-profit purposes, by non-profit institutions
 * only, without fee, and without a written agreement is hereby granted,
 * provided that the above copyright notice, this paragraph and the following
 * three paragraphs appear in all copies.
 *
 * Those desiring to incorporate this work into commercial products or use for
 * commercial purposes should contact the Inventors using one of the following
 * email addresses: chudakovdm@mail.ru, chudakovdm@gmail.com
 *
 * IN NO EVENT SHALL THE INVENTORS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
 * SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
 * ARISING OUT OF THE USE OF THIS SOFTWARE, EVEN IF THE INVENTORS HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * THE SOFTWARE PROVIDED HEREIN IS ON AN "AS IS" BASIS, AND THE INVENTORS HAS
 * NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR
 * MODIFICATIONS. THE INVENTORS MAKES NO REPRESENTATIONS AND EXTENDS NO
 * WARRANTIES OF ANY KIND, EITHER IMPLIED OR EXPRESS, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY OR FITNESS FOR A
 * PARTICULAR PURPOSE, OR THAT THE USE OF THE SOFTWARE WILL NOT INFRINGE ANY
 * PATENT, TRADEMARK OR OTHER RIGHTS.
 */
package com.milaboratory.mixcr.assembler;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPortCloseable;
import cc.redberry.pipe.blocks.FilteringPort;
import com.milaboratory.mixcr.basictypes.CloneSet;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.util.CanReportProgress;
import com.milaboratory.util.CanReportProgressAndStage;

public class CloneAssemblerRunner implements CanReportProgressAndStage {
    final AlignmentsProvider alignmentsProvider;
    final CloneAssembler assembler;
    final int threads;
    volatile String stage = "Initialization";
    volatile CanReportProgress innerProgress;
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
        }
        // run mapping if required
        if (assembler.beginMapping()) {
            synchronized (this) {
                stage = "Preparing for mapping of low quality reads";
                innerProgress = null;
            }
            try (OutputPortCloseable<VDJCAlignments> alignmentsPort = alignmentsProvider.create()) {
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

    public CloneSet getCloneSet(VDJCAlignerParameters alignerParameters) {
        return assembler.getCloneSet(alignerParameters);
    }
}
