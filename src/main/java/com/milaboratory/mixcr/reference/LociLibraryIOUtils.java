/*
 * Copyright (c) 2014-2015, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
 * commercial purposes should contact the Inventors using one of the following
 * email addresses: chudakovdm@mail.ru, chudakovdm@gmail.com
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
package com.milaboratory.mixcr.reference;

import com.milaboratory.core.sequence.NucleotideSequence;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class LociLibraryIOUtils {
    private LociLibraryIOUtils() {
    }

    public static void writeReferenceGeneFeature(OutputStream stream, GeneFeature geneFeature) throws IOException {
        for (GeneFeature.ReferenceRange referenceRange : geneFeature)
            if (!referenceRange.begin.isBasicPoint() || !referenceRange.end.isBasicPoint())
                throw new IllegalArgumentException("Supports only pure reference gene features.");
        if (geneFeature.size() >= 0x100)
            throw new IllegalArgumentException();
        stream.write(geneFeature.size());
        for (GeneFeature.ReferenceRange referenceRange : geneFeature) {
            stream.write(referenceRange.begin.basicPoint.index);
            stream.write(referenceRange.end.basicPoint.index);
        }
    }

    public static GeneFeature readReferenceGeneFeature(InputStream stream) throws IOException {
        int size = stream.read();
        if (size < 0)
            throw new IOException("Wrong format.");
        GeneFeature.ReferenceRange[] rr = new GeneFeature.ReferenceRange[size];
        for (int i = 0; i < size; i++) {
            int begin = stream.read();
            if (begin < 0)
                throw new IOException("Wrong format.");
            int end = stream.read();
            if (end < 0)
                throw new IOException("Wrong format.");
            rr[i] = new GeneFeature.ReferenceRange(new ReferencePoint(BasicReferencePoint.getByIndex(begin)),
                    new ReferencePoint(BasicReferencePoint.getByIndex(end)));
        }
        return new GeneFeature(rr, true);
    }

    public static void filterLociLibrary(final File file, final LociLibraryFilter filter) throws IOException {
        final List<RangeToRemove> ranges = new ArrayList<>();
        class Listener extends LociLibraryReaderListener {
            RangeToRemove currentRange = null;

            @Override
            public void magic(long from, long to) {
                r(from, to, filter.magic());
            }

            @Override
            public void meta(long from, long to, String key, String value) {
                r(from, to, filter.meta(key, value));
            }

            @Override
            public void speciesName(long from, long to, int taxonId, String name) {
                r(from, to, filter.speciesName(taxonId, name));
            }

            @Override
            public void sequencePart(long from, long to, int seqFrom, NucleotideSequence seq) {
                r(from, to, filter.sequencePart(seqFrom, seq));
            }

            @Override
            public void beginLocus(long from, long to, LocusContainer container) {
                r(from, to, filter.beginLocus(container));
            }

            @Override
            public void endLocus(long from, long to, LocusContainer container) {
                r(from, to, filter.endLocus(container));
            }

            @Override
            public void allele(long from, long to, Allele allele) {
                r(from, to, filter.allele(allele));
            }

            void finish() {
                r(0, 0, true);
            }

            void r(long from, long to, boolean result) {
                if (!result) {
                    if (currentRange == null)
                        currentRange = new RangeToRemove(from, to);
                    else
                        currentRange.to = to;
                } else if (currentRange != null) {
                    ranges.add(currentRange);
                    currentRange = null;
                }
            }
        }

        // Collecting ranges to remove
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
            Listener listener = new Listener();
            LociLibraryReader reader = new LociLibraryReader(bis, false).setListener(listener);
            reader.checkMagic();
            reader.readToEnd();
            listener.finish();
        }

        Path path = file.toPath();
        Path old = path.resolveSibling(path.getFileName() + ".old");
        Files.move(path, old, StandardCopyOption.REPLACE_EXISTING);

        try (InputStream is = new FileInputStream(old.toFile());
             OutputStream os = new FileOutputStream(path.toFile())) {
            byte[] buffer = new byte[1024];
            long streamPointer = 0;
            int rangePointer = 0;
            boolean skip = false;
            while (true) {
                int toRead = buffer.length;
                if (rangePointer < ranges.size()) {
                    toRead = Math.min((int) (ranges.get(rangePointer).boundary(skip) - streamPointer),
                            toRead);
                }
                if (toRead == 0) {
                    if (!skip)
                        skip = true;
                    else {
                        ++rangePointer;
                        skip = false;
                    }
                    continue;
                }
                int read = is.read(buffer, 0, toRead);

                if (read > 0 && !skip)
                    os.write(buffer, 0, read);

                if (read < toRead)
                    break;

                streamPointer += read;
            }
        }

        // Check
        LociLibraryReader.read(path.toFile(), false);

        // Remove old file
        Files.delete(old);
    }

    private static final class RangeToRemove {
        long from, to;

        public RangeToRemove(long from, long to) {
            this.from = from;
            this.to = to;
        }

        public long boundary(boolean isSkip) {
            return isSkip ? to : from;
        }
    }

    public abstract static class LociLibraryFilter {
        public boolean magic() {
            return true;
        }

        public boolean meta(String key, String value) {
            return true;
        }

        public boolean speciesName(int taxonId, String name) {
            return true;
        }

        public boolean sequencePart(int seqFrom, NucleotideSequence seq) {
            return true;
        }

        public boolean beginLocus(LocusContainer container) {
            return true;
        }

        public boolean endLocus(LocusContainer container) {
            return true;
        }

        public boolean allele(Allele allele) {
            return true;
        }
    }
}
