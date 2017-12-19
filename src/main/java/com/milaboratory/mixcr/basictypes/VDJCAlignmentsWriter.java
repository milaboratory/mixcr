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

import com.milaboratory.mixcr.util.MiXCRVersionInfo;
import com.milaboratory.mixcr.vdjaligners.VDJCAligner;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.primitivio.PrimitivO;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGene;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class VDJCAlignmentsWriter implements VDJCAlignmentsWriterI {
    static final String MAGIC_V3 = "MiXCR.VDJC.V03";
    static final String MAGIC_V4 = "MiXCR.VDJC.V04";
    static final String MAGIC_V5 = "MiXCR.VDJC.V05";
    static final String MAGIC_V6 = "MiXCR.VDJC.V06";
    static final String MAGIC_V7 = "MiXCR.VDJC.V07";
    static final String MAGIC_V8 = "MiXCR.VDJC.V08";
    static final String MAGIC_V9 = "MiXCR.VDJC.V09";
    static final String MAGIC_V10 = "MiXCR.VDJC.V10";
    static final String MAGIC = MAGIC_V10;
    static final int MAGIC_LENGTH = 14;
    static final byte[] MAGIC_BYTES = MAGIC.getBytes(StandardCharsets.US_ASCII);
    final PrimitivO output;
    long numberOfProcessedReads = -1;
    boolean header = false, closed = false;

    public VDJCAlignmentsWriter(String fileName) throws IOException {
        this(new File(fileName));
    }

    public VDJCAlignmentsWriter(File file) throws IOException {
        this(IOUtil.createOS(file));
    }

    public VDJCAlignmentsWriter(OutputStream output) {
        this.output = new PrimitivO(output);
    }

    @Override
    public void setNumberOfProcessedReads(long numberOfProcessedReads) {
        this.numberOfProcessedReads = numberOfProcessedReads;
    }

    public void header(VDJCAligner aligner) {
        header(aligner.getParameters(), aligner.getUsedGenes());
    }

    @Override
    public void header(VDJCAlignerParameters parameters, List<VDJCGene> genes) {
        if (parameters == null || genes == null)
            throw new IllegalArgumentException();

        if (header)
            throw new IllegalStateException();

        // Writing magic bytes
        assert MAGIC_BYTES.length == MAGIC_LENGTH;
        output.write(MAGIC_BYTES);

        // Writing version information
        output.writeUTF(
                MiXCRVersionInfo.get().getVersionString(
                        MiXCRVersionInfo.OutputType.ToFile));

        // Writing parameters
        output.writeObject(parameters);

        IOUtil.writeAndRegisterGeneReferences(output, genes, parameters);

        // Registering links to features to align
        for (GeneType gt : GeneType.VDJC_REFERENCE) {
            GeneFeature feature = parameters.getFeatureToAlign(gt);
            output.writeObject(feature);
            if (feature != null)
                output.putKnownReference(feature);
        }

        header = true;
    }

    @Override
    public void write(VDJCAlignments alignment) {
        if (!header)
            throw new IllegalStateException();

        if (alignment == null)
            throw new NullPointerException();

        output.writeObject(alignment);
    }

    @Override
    public void close() {
        if (!closed) {
            output.writeObject(null);
            output.writeLong(numberOfProcessedReads);
            output.close();
            closed = true;
        }
    }
}
