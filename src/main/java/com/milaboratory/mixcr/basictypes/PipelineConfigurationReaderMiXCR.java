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
package com.milaboratory.mixcr.basictypes;

import com.milaboratory.cli.BinaryFileInfo;
import com.milaboratory.cli.PipelineConfiguration;
import com.milaboratory.cli.PipelineConfigurationReader;
import io.repseq.core.VDJCLibraryRegistry;

import static com.milaboratory.mixcr.basictypes.IOUtil.*;

/**
 *
 */
public class PipelineConfigurationReaderMiXCR implements PipelineConfigurationReader {
    public static final PipelineConfigurationReaderMiXCR pipelineConfigurationReaderInstance =
            new PipelineConfigurationReaderMiXCR();

    protected PipelineConfigurationReaderMiXCR() {}

    @Override
    public PipelineConfiguration fromFileOrNull(String fileName, BinaryFileInfo fileInfo) {
        return sFromFileOrNull(fileName, fileInfo);
    }

    @Override
    public PipelineConfiguration fromFile(String fileName) {
        return sFromFile(fileName);
    }

    @Override
    public PipelineConfiguration fromFile(String fileName, BinaryFileInfo fileInfo) {
        return sFromFile(fileName, fileInfo);
    }

    /**
     * Read pipeline configuration from file or return null
     */
    public static PipelineConfiguration sFromFileOrNull(String fileName, BinaryFileInfo fileInfo) {
        if (fileInfo == null)
            return null;
        if (!fileInfo.valid)
            return null;
        try {
            return sFromFile(fileName, fileInfo);
        } catch (Throwable ignored) {}
        return null;
    }

    public static PipelineConfiguration sFromFile(String fileName) {
        BinaryFileInfo fileInfo = fileInfoExtractorInstance.getFileInfo(fileName);
        if (!fileInfo.valid)
            throw new RuntimeException("File " + fileName + " corrupted.");
        return sFromFile(fileName, fileInfo);
    }

    /**
     * Read pipeline configuration from file or throw exception
     */
    public static PipelineConfiguration sFromFile(String fileName, BinaryFileInfo fileInfo) {
        try {
            switch (fileInfo.fileType) {
                case MAGIC_VDJC:
                    try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(fileName)) {
                        return reader.getPipelineConfiguration();
                    }
                case MAGIC_CLNS:
                    try (ClnsReader reader = new ClnsReader(fileName, VDJCLibraryRegistry.getDefault())) {
                        return reader.getPipelineConfiguration();
                    }
                case MAGIC_CLNA:
                    try (ClnAReader reader = new ClnAReader(fileName, VDJCLibraryRegistry.getDefault(), 1)) {
                        return reader.getPipelineConfiguration();
                    }
                default:
                    throw new RuntimeException("Not a MiXCR file");
            }
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
