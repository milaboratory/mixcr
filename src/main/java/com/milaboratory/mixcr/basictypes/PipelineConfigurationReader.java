package com.milaboratory.mixcr.basictypes;

import io.repseq.core.VDJCLibraryRegistry;

/**
 *
 */
public interface PipelineConfigurationReader {
    PipelineConfiguration getPipelineConfiguration();

    /**
     * Read pipeline configuration from file or return null
     */
    static PipelineConfiguration fromFileOrNull(String fileName, IOUtil.MiXCRFileInfo fileInfo) {
        if (!fileInfo.valid)
            return null;
        try {
            return fromFile(fileName, fileInfo);
        } catch (Throwable ignored) {}
        return null;
    }

    static PipelineConfiguration fromFile(String fileName) {
        IOUtil.MiXCRFileInfo fileInfo = IOUtil.getFileInfo(fileName);
        if (!fileInfo.valid)
            throw new RuntimeException("File " + fileName + " corrupted.");
        return fromFile(fileName, fileInfo);
    }

    /**
     * Read pipeline configuration from file or throw exception
     */
    static PipelineConfiguration fromFile(String fileName, IOUtil.MiXCRFileInfo fileInfo) {
        try {
            switch (fileInfo.fileType) {
                case VDJCA:
                    try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(fileName)) {
                        return reader.getPipelineConfiguration();
                    }
                case Clns:
                    try (ClnsReader reader = new ClnsReader(fileName, VDJCLibraryRegistry.getDefault())) {
                        return reader.getPipelineConfiguration();
                    }
                case ClnA:
                    try (ClnAReader reader = new ClnAReader(fileName, VDJCLibraryRegistry.getDefault())) {
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
