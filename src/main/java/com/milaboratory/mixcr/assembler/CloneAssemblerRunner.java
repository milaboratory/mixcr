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
import cc.redberry.pipe.blocks.FilteringPort;
import com.milaboratory.mixcr.assembler.preclone.PreClone;
import com.milaboratory.mixcr.assembler.preclone.PreCloneReader;
import com.milaboratory.mixcr.basictypes.CloneSet;
import com.milaboratory.mixcr.basictypes.MiXCRFooter;
import com.milaboratory.mixcr.basictypes.MiXCRHeader;
import com.milaboratory.util.CanReportProgressAndStage;
import com.milaboratory.util.OutputPortWithProgress;
import com.milaboratory.util.ProgressAndStage;

public class CloneAssemblerRunner implements CanReportProgressAndStage {
    final PreCloneReader preCloneReader;
    final CloneAssembler assembler;
    final ProgressAndStage ps = new ProgressAndStage("Initialization");

    public CloneAssemblerRunner(PreCloneReader preCloneReader, CloneAssembler assembler) {
        this.preCloneReader = preCloneReader;
        this.assembler = assembler;
    }

    @Override
    public String getStage() {
        return ps.getStage();
    }

    @Override
    public double getProgress() {
        return ps.getProgress();
    }

    @Override
    public boolean isFinished() {
        return ps.isFinished();
    }

    public void run() {
        // run initial assembler
        try (OutputPortWithProgress<PreClone> preClones = preCloneReader.readPreClones()) {
            ps.setStage("Assembling initial clonotypes");
            ps.delegate(preClones);
            try {
                CUtils.processAll(CUtils.buffered(preClones, 128),
                        assembler.getInitialAssembler());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        // run mapping if required
        if (assembler.beginMapping()) {
            ps.unDelegate();
            ps.setStage("Preparing for mapping of low quality reads");
            try (OutputPortWithProgress<PreClone> preClones = preCloneReader.readPreClones()) {
                ps.delegate("Mapping low quality reads", preClones);
                try {
                    CUtils.processAll(CUtils.buffered(
                                    new FilteringPort<>(preClones,
                                            assembler.getDeferredAlignmentsFilter()), 128),
                            assembler.getDeferredAlignmentsMapper());
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            assembler.endMapping();
        }

        ps.unDelegate();
        ps.setStage("Pre-clustering");
        assembler.preClustering();

        // run clustering
        if (assembler.parameters.isClusteringEnabled()) {
            ps.delegate("Clustering", assembler);
            assembler.runClustering();
        }
        // build clones
        ps.delegate("Building clones", assembler);
        assembler.buildClones();
        ps.finish();
    }

    public CloneSet getCloneSet(MiXCRHeader header, MiXCRFooter footer) {
        return assembler.getCloneSet(header, footer);
    }
}
