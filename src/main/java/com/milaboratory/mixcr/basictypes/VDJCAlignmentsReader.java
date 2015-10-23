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
package com.milaboratory.mixcr.basictypes;

import cc.redberry.pipe.OutputPortCloseable;
import com.milaboratory.core.io.CompressionType;
import com.milaboratory.mixcr.reference.Allele;
import com.milaboratory.mixcr.reference.AlleleResolver;
import com.milaboratory.mixcr.reference.CompatibilityIO;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.util.CanReportProgress;
import com.milaboratory.util.CountingInputStream;

import java.io.*;
import java.util.List;

import static com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter.*;

public class VDJCAlignmentsReader implements OutputPortCloseable<VDJCAlignments>, CanReportProgress {
    VDJCAlignerParameters parameters;
    List<Allele> usedAlleles;
    final PrimitivI input;
    final AlleleResolver alleleResolver;
    String versionInfo;
    long numberOfReads = -1;
    boolean closed = false;
    long counter = 0;
    final long size;
    final CountingInputStream countingInputStream;

    public VDJCAlignmentsReader(String fileName, AlleleResolver alleleResolver) throws IOException {
        this(new File(fileName), alleleResolver);
    }

    public VDJCAlignmentsReader(File file, AlleleResolver alleleResolver) throws IOException {
        CompressionType ct = CompressionType.detectCompressionType(file);
        this.countingInputStream = new CountingInputStream(new FileInputStream(file));
        if (ct == CompressionType.None)
            this.input = new PrimitivI(new BufferedInputStream(countingInputStream, 65536));
        else
            this.input = new PrimitivI(ct.createInputStream(countingInputStream, 65536));
        this.alleleResolver = alleleResolver;
        this.size = file.length();
    }

    public VDJCAlignmentsReader(InputStream input, AlleleResolver alleleResolver) {
        this(input, alleleResolver, 0);
    }

    public VDJCAlignmentsReader(InputStream input, AlleleResolver alleleResolver, long size) {
        this.input = new PrimitivI(countingInputStream = new CountingInputStream(input));
        this.alleleResolver = alleleResolver;
        this.size = size;
    }

    public void init() {
        if (usedAlleles != null)
            return;

        assert MAGIC_BYTES.length == MAGIC_LENGTH;
        byte[] magic = new byte[MAGIC_LENGTH];
        input.readFully(magic);
        String magicString = new String(magic);
        switch (magicString) {
            case MAGIC_V3:
                CompatibilityIO.registerV3Serializers(input.getSerializersManager());
                break;
            case MAGIC_V4:
            case MAGIC:
                break;
            default:
                throw new RuntimeException("Unsupported file format; .vdjca file of version " + new String(magic) + " while you are running MiXCR " + MAGIC);
        }

        if (magicString.compareTo(MAGIC_V5) >= 0)
            versionInfo = input.readUTF();

        parameters = input.readObject(VDJCAlignerParameters.class);

        this.usedAlleles = IOUtil.readAlleleReferences(input, alleleResolver, parameters);
    }

    public synchronized VDJCAlignerParameters getParameters() {
        init();
        return parameters;
    }

    public synchronized List<Allele> getUsedAlleles() {
        init();
        return usedAlleles;
    }

    /**
     * Returns information about version of MiXCR which produced this file.
     *
     * @return information about version of MiXCR which produced this file
     */
    public String getVersionInfo() {
        return versionInfo;
    }

    public long getNumberOfReads() {
        return numberOfReads;
    }

    @Override
    public double getProgress() {
        if (size == 0)
            return Double.NaN;
        return (1.0 * countingInputStream.getBytesRead()) / size;
    }

    @Override
    public boolean isFinished() {
        return countingInputStream.getBytesRead() == size || closed;
    }

    @Override
    public synchronized void close() {
        close(false);
    }

    private void close(boolean onEnd) {
        if (closed)
            return;

        try {
            // If all alignments are read
            // footer with number of reads processed to produce this
            // file can be read form the stream.
            if (onEnd)
                numberOfReads = input.readLong();
            input.close();
        } finally {
            closed = true;
        }
    }

    @Override
    public synchronized VDJCAlignments take() {
        if (closed)
            return null;

        init();

        VDJCAlignments alignments = input.readObject(VDJCAlignments.class);

        if (alignments == null)
            close(true);
        else
            alignments.setAlignmentsIndex(counter++);

        return alignments;
    }
}
