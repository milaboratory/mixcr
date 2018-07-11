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
    static PipelineConfiguration fromFileOrNull(String fileName) {
        try {
            return fromFile(fileName);
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Read pipeline configuration from file or throw exception
     */
    static PipelineConfiguration fromFile(String fileName) {
        try {
            switch (IOUtil.detectFilType(fileName)) {
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
