package com.milaboratory.mixcr.basictypes;

import io.repseq.core.VDJCLibraryRegistry;

/**
 *
 */
public interface PipelineConfigurationReader {
    PipelineConfiguration getPipelineConfiguration();

    static PipelineConfiguration fromFile(String fileName) {
        try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(fileName)) {
            return reader.getPipelineConfiguration();
        } catch (Throwable ignored) {}

        try (ClnsReader reader = new ClnsReader(fileName, VDJCLibraryRegistry.getDefault())) {
            return reader.getPipelineConfiguration();
        } catch (Throwable ignored) {}

        try (ClnAReader reader = new ClnAReader(fileName, VDJCLibraryRegistry.getDefault())) {
            return reader.getPipelineConfiguration();
        } catch (Throwable ignored) {}

        throw new RuntimeException("not a MiXCR binary file: " + fileName);
    }
}
