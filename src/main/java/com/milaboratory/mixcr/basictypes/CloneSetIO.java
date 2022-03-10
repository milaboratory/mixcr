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
import com.milaboratory.util.LambdaSemaphore;
import io.repseq.core.VDJCLibraryRegistry;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
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
        BinaryFileInfo fileInfo = fileInfoExtractorInstance.getFileInfo(file);
        if (fileInfo == null)
            throw new RuntimeException("Unsupported file type");
        switch (Objects.requireNonNull(fileInfo).fileType) {
            case MAGIC_CLNA:
                try (ClnAReader r = new ClnAReader(file.toPath(), libraryRegistry, 1)) {
                    return r.readCloneSet();
                }
            case MAGIC_CLNS:
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
        switch (Objects.requireNonNull(fileInfoExtractorInstance.getFileInfo(file.toFile())).fileType) {
            case MAGIC_CLNA:
                return new ClnAReader(file, libraryRegistry, concurrency);
            case MAGIC_CLNS:
                return new ClnsReader(file, libraryRegistry, concurrency);
            default:
                throw new RuntimeException("Unsupported file type");
        }
    }

}
