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

import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.CloneSet;
import com.milaboratory.mixcr.basictypes.CloneSetIO;
import com.milaboratory.mixcr.export.InfoWriter;
import com.milaboratory.mixcr.reference.LociLibraryManager;
import com.milaboratory.util.CanReportProgressAndStage;
import com.milaboratory.util.SmartProgressReporter;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

public class ActionExportClones extends ActionExport {
    public ActionExportClones() {
        super(new ActionExportParameters(Clone.class));
    }

    @Override
    public void go0() throws Exception {
        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(parameters.inputFile), 65536);
             InfoWriter<Clone> writer = new InfoWriter<>(parameters.outputFile)) {
            CloneSet set = CloneSetIO.read(inputStream, LociLibraryManager.getDefault());
            writer.attachInfoProviders((List) parameters.exporters);
            ExportClones exportClones = new ExportClones(set, writer);
            SmartProgressReporter.startProgressReport(exportClones);
            exportClones.run();
            writer.close();
        }
    }

    @Override
    public String command() {
        return "exportClones";
    }

    private static final class ExportClones implements CanReportProgressAndStage {
        final CloneSet clones;
        final InfoWriter<Clone> writer;
        final int size;
        volatile int current = 0;
        final static String stage = "Exporting clones";

        private ExportClones(CloneSet clones, InfoWriter<Clone> writer) {
            this.clones = clones;
            this.writer = writer;
            this.size = clones.size();
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
                writer.put(clone);
                ++current;
            }
        }
    }
}
