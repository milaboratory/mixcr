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
        try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(fileName)) {
            return reader.getPipelineConfiguration();
        } catch (Throwable ignored) {}

        try (ClnsReader reader = new ClnsReader(fileName, VDJCLibraryRegistry.getDefault())) {
            return reader.getPipelineConfiguration();
        } catch (Throwable ignored) {}

        try (ClnAReader reader = new ClnAReader(fileName, VDJCLibraryRegistry.getDefault())) {
            return reader.getPipelineConfiguration();
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Read pipeline configuration from file or throw exception
     */
    static PipelineConfiguration fromFile(String fileName) {
        PipelineConfiguration f = fromFileOrNull(fileName);
        if (f != null)
            return f;
        throw new RuntimeException("not a MiXCR binary file: " + fileName);
    }
}
