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
package com.milaboratory.mixcr.basictypes;

import com.milaboratory.cli.BinaryFileInfo;
import com.milaboratory.cli.PipelineConfiguration;
import com.milaboratory.cli.PipelineConfigurationReader;
import io.repseq.core.VDJCLibraryRegistry;

import java.nio.file.Paths;

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
                    try (ClnsReader reader = new ClnsReader(Paths.get(fileName), VDJCLibraryRegistry.getDefault())) {
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
