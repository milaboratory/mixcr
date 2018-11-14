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
