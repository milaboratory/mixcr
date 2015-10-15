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
package com.milaboratory.mixcr.cli;

import cc.redberry.primitives.Filter;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.milaboratory.core.sequence.AminoAcidSequence;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.CloneSet;
import com.milaboratory.mixcr.basictypes.CloneSetIO;
import com.milaboratory.mixcr.basictypes.IOUtil;
import com.milaboratory.mixcr.export.InfoWriter;
import com.milaboratory.mixcr.reference.GeneFeature;
import com.milaboratory.mixcr.reference.LociLibraryManager;
import com.milaboratory.util.CanReportProgressAndStage;
import com.milaboratory.util.SmartProgressReporter;

import java.io.InputStream;
import java.util.List;

public class ActionExportClones extends ActionExport {
    public ActionExportClones() {
        super(new CloneExportParameters(), Clone.class);
    }

    @Override
    public void go0() throws Exception {
        CloneExportParameters parameters = (CloneExportParameters) this.parameters;
        try (InputStream inputStream = IOUtil.createIS(parameters.getInputFile());
             InfoWriter<Clone> writer = new InfoWriter<>(parameters.getOutputFile())) {
            CloneSet set = CloneSetIO.read(inputStream, LociLibraryManager.getDefault());
            if (parameters.filterOutOfFrames || parameters.filterStops)
                set = CloneSet.transform(set, new CFilter(parameters.filterOutOfFrames, parameters.filterStops));
            writer.attachInfoProviders((List) parameters.exporters);
            ExportClones exportClones = new ExportClones(set, writer, parameters.limit);
            if (!parameters.printToStdout())
                SmartProgressReporter.startProgressReport(exportClones);
            exportClones.run();
            writer.close();
        }
    }

    @Override
    public String command() {
        return "exportClones";
    }

    private static final class CFilter implements Filter<Clone> {
        final boolean filterOutOfFrames, filterStopCodons;

        public CFilter(boolean filterOutOfFrames, boolean filterStopCodons) {
            this.filterOutOfFrames = filterOutOfFrames;
            this.filterStopCodons = filterStopCodons;
        }

        @Override
        public boolean accept(Clone clone) {
            if (filterOutOfFrames) {
                NSequenceWithQuality cdr3 = clone.getFeature(GeneFeature.CDR3);
                if (cdr3 == null || cdr3.size() % 3 != 0)
                    return false;
            }
            if (filterStopCodons)
                for (int i = 0; i < clone.numberOfTargets(); i++)
                    if (AminoAcidSequence.translateFromCenter(clone.getTarget(i).getSequence()).containStops())
                        return false;
            return true;
        }
    }

    @Parameters(commandDescription = "Export clones to tab-delimited text file", optionPrefixes = "-")
    public static final class ExportClones implements CanReportProgressAndStage {
        final CloneSet clones;
        final InfoWriter<Clone> writer;
        final long size;
        volatile long current = 0;
        final static String stage = "Exporting clones";
        final long limit;

        private ExportClones(CloneSet clones, InfoWriter<Clone> writer, long limit) {
            this.clones = clones;
            this.writer = writer;
            this.size = clones.size();
            this.limit = limit;
        }

        @Override
        public String getStage() {
            return stage;
        }

        @Override
        public double getProgress() {
            return (1.0 * current) / size;
        }

        @Override
        public boolean isFinished() {
            return current == size;
        }

        void run() {
            for (Clone clone : clones.getClones()) {
                if (current == limit)
                    break;
                writer.put(clone);
                ++current;
            }
        }
    }

    public static class CloneExportParameters extends ActionExportParameters {
        @Parameter(description = "Exclude out of frames (fractions will be recalculated)",
                names = {"-o", "--filter-out-of-frames"})
        public Boolean filterOutOfFrames = false;
        @Parameter(description = "Exclude sequences containing stop codons (fractions will be recalculated)",
                names = {"-t", "--filter-stops"})
        public Boolean filterStops = false;
    }
}
