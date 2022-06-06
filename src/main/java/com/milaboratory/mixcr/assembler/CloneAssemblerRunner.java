/*
 * Copyright (c) 2014-2019, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
 * commercial purposes should contact MiLaboratory LLC, which owns exclusive
 * rights for distribution of this program for commercial purposes, using the
 * following email address: licensing@milaboratory.com.
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
import cc.redberry.pipe.blocks.FilteringPort;
import com.milaboratory.mixcr.assembler.preclone.PreClone;
import com.milaboratory.mixcr.assembler.preclone.PreCloneReader;
import com.milaboratory.mixcr.basictypes.CloneSet;
import com.milaboratory.mixcr.basictypes.tag.TagsInfo;
import com.milaboratory.mixcr.util.OutputPortWithProgress;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.util.CanReportProgressAndStage;
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

    public CloneSet getCloneSet(VDJCAlignerParameters alignerParameters, TagsInfo tagsInfo) {
        return assembler.getCloneSet(alignerParameters, tagsInfo);
    }
}
