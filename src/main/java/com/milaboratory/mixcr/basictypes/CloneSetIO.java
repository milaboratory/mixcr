/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 * 
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 * 
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.basictypes;

import io.repseq.core.VDJCLibraryRegistry;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import static com.milaboratory.mixcr.basictypes.IOUtil.*;

public final class CloneSetIO {
    public static CloneSet read(String file) throws IOException {
        return read(file, VDJCLibraryRegistry.getDefault());
    }

    public static CloneSet read(File file) throws IOException {
        return read(file, VDJCLibraryRegistry.getDefault());
    }

    public static CloneSet read(String file, VDJCLibraryRegistry libraryRegistry) throws IOException {
        return read(new File(file), libraryRegistry);
    }

    public static CloneSet read(File file, VDJCLibraryRegistry libraryRegistry) throws IOException {
        switch (Objects.requireNonNull(fileInfoExtractorInstance.getFileInfo(file)).fileType) {
            case MAGIC_CLNA:
                try (ClnAReader r = new ClnAReader(file.toPath(), libraryRegistry)) {
                    return r.readCloneSet();
                }
            case MAGIC_CLNS:
                try (ClnsReader r = new ClnsReader(file, libraryRegistry)) {
                    return r.getCloneSet();
                }
            default:
                throw new RuntimeException("Unsupported file type");
        }
    }

    public static CloneSet readClns(InputStream inputStream) {
        return readClns(inputStream, VDJCLibraryRegistry.getDefault());
    }

    public static CloneSet readClns(InputStream inputStream, VDJCLibraryRegistry libraryRegistry) {
        return new ClnsReader(inputStream, libraryRegistry).getCloneSet();
    }
}
