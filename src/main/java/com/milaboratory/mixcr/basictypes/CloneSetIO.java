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

import com.milaboratory.mixcr.basictypes.tag.TagsInfo;
import com.milaboratory.util.LambdaSemaphore;
import io.repseq.core.VDJCLibraryRegistry;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public final class CloneSetIO {

    public static CloneSet read(File file) throws IOException {
        return read(file, VDJCLibraryRegistry.getDefault());
    }

    public static CloneSet read(Path file) throws IOException {
        return read(file.toFile());
    }

    public static CloneSet read(Path file, VDJCLibraryRegistry libraryRegistry) throws IOException {
        return read(file.toFile(), libraryRegistry);
    }

    public static CloneSet read(File file, VDJCLibraryRegistry libraryRegistry) throws IOException {
        switch (IOUtil.extractFileType(file.toPath())) {
            case CLNA:
                try (ClnAReader r = new ClnAReader(file.toPath(), libraryRegistry, 1)) {
                    return r.readCloneSet();
                }
            case CLNS:
                try (ClnsReader r = new ClnsReader(file.toPath(), libraryRegistry)) {
                    return r.getCloneSet();
                }
            default:
                throw new RuntimeException("Unsupported file type");
        }
    }

    public static final int DEFAULT_READER_CONCURRENCY_LIMIT = 1;

    public static CloneReader mkReader(Path file, VDJCLibraryRegistry libraryRegistry) throws IOException {
        return mkReader(file, libraryRegistry, DEFAULT_READER_CONCURRENCY_LIMIT);
    }

    public static CloneReader mkReader(Path file, VDJCLibraryRegistry libraryRegistry, int concurrency) throws IOException {
        return mkReader(file, libraryRegistry, new LambdaSemaphore(concurrency));
    }

    public static CloneReader mkReader(Path file, VDJCLibraryRegistry libraryRegistry, LambdaSemaphore concurrency) throws IOException {
        switch (IOUtil.extractFileType(file)) {
            case CLNA:
                return new ClnAReader(file, libraryRegistry, concurrency);
            case CLNS:
                return new ClnsReader(file, libraryRegistry, concurrency);
            default:
                throw new RuntimeException("Unsupported file type");
        }
    }

    public static TagsInfo extractTagsInfo(Path file) {
        try (CloneReader reader = mkReader(file, VDJCLibraryRegistry.getDefault())) {
            return reader.getTagsInfo();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
