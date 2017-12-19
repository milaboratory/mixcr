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
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.SerializersManager;
import com.milaboratory.util.CanReportProgress;
import com.milaboratory.util.CountingInputStream;
import gnu.trove.list.array.TLongArrayList;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGene;
import io.repseq.core.VDJCLibraryRegistry;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter.*;

public final class VDJCAlignmentsReader implements OutputPortCloseable<VDJCAlignments>, CanReportProgress {
    private static final int DEFAULT_BUFFER_SIZE = 1048576; // 1 MB
    VDJCAlignerParameters parameters;
    List<VDJCGene> usedGenes;
    final PrimitivI input;
    final VDJCLibraryRegistry vdjcRegistry;
    String versionInfo;
    String magic;
    long numberOfReads = -1;
    boolean closed = false;
    long counter = 0;
    final long size;
    final CountingInputStream countingInputStream;
    final CountingInputStream indexingStream;
    volatile TLongArrayList index = null;

    public VDJCAlignmentsReader(String fileName) throws IOException {
        this(new File(fileName), VDJCLibraryRegistry.getDefault());
    }

    public VDJCAlignmentsReader(String fileName, VDJCLibraryRegistry vdjcRegistry) throws IOException {
        this(new File(fileName), vdjcRegistry);
    }

    public VDJCAlignmentsReader(File file) throws IOException {
        this(file, VDJCLibraryRegistry.getDefault());
    }

    public VDJCAlignmentsReader(File file, VDJCLibraryRegistry vdjcRegistry) throws IOException {
        CompressionType ct = CompressionType.detectCompressionType(file);
        this.countingInputStream = new CountingInputStream(new FileInputStream(file));
        if (ct == CompressionType.None)
            this.input = new PrimitivI(indexingStream = new CountingInputStream(
                    new BufferedInputStream(countingInputStream, DEFAULT_BUFFER_SIZE)));
        else {
            this.input = new PrimitivI(ct.createInputStream(countingInputStream, DEFAULT_BUFFER_SIZE));
            indexingStream = null;
        }
        this.vdjcRegistry = vdjcRegistry;
        this.size = file.length();
    }

    public VDJCAlignmentsReader(InputStream input) {
        this(input, VDJCLibraryRegistry.getDefault(), 0);
    }

    public VDJCAlignmentsReader(InputStream input, VDJCLibraryRegistry vdjcRegistry) {
        this(input, vdjcRegistry, 0);
    }

    public VDJCAlignmentsReader(InputStream input, long size) {
        this(input, VDJCLibraryRegistry.getDefault(), size);
    }

    public VDJCAlignmentsReader(InputStream input, VDJCLibraryRegistry vdjcRegistry, long size) {
        this.input = new PrimitivI(indexingStream = countingInputStream =
                new CountingInputStream(input));
        this.vdjcRegistry = vdjcRegistry;
        this.size = size;
    }

    public VDJCAlignmentsReader(DataInput input, VDJCLibraryRegistry vdjcRegistry) {
        this.input = new PrimitivI(input);
        this.vdjcRegistry = vdjcRegistry;
        this.countingInputStream = null;
        this.indexingStream = null;
        this.size = 0;
    }

    public void setIndexer(TLongArrayList index) {
        if (indexingStream == null)
            throw new IllegalStateException("Can't index compressed file.");
        this.index = index;
    }

    public void init() {
        init(null);
    }

    void init(Map<GeneFeature, GeneFeature> geneFeatureRefs) {
        if (usedGenes != null)
            return;

        assert MAGIC_BYTES.length == MAGIC_LENGTH;
        byte[] magic = new byte[MAGIC_LENGTH];
        input.readFully(magic);
        String magicString = new String(magic);
        this.magic = magicString;

        SerializersManager serializersManager = input.getSerializersManager();
        switch (magicString) {
            case MAGIC_V9:
                serializersManager.registerCustomSerializer(VDJCAlignments.class, new IO.VDJCAlignmentsSerializer21());
            case MAGIC:
                break;
            default:
                throw new RuntimeException("Unsupported file format; .vdjca file of version " + new String(magic) + " while you are running MiXCR " + MAGIC);
        }

        versionInfo = input.readUTF();

        parameters = input.readObject(VDJCAlignerParameters.class);

        this.usedGenes = IOUtil.readAndRegisterGeneReferences(input, vdjcRegistry, parameters);

        // Registering links to features to align
        for (GeneType gt : GeneType.VDJC_REFERENCE) {
            GeneFeature featureParams = parameters.getFeatureToAlign(gt);
            GeneFeature featureDeserialized = input.readObject(GeneFeature.class);
            if (!Objects.equals(featureDeserialized, featureParams))
                throw new RuntimeException("Wrong format.");

            // Find corresponding reference
            if (geneFeatureRefs != null) {
                featureParams = geneFeatureRefs.get(featureParams);
                if (featureParams == null)
                    throw new RuntimeException("Absent record for " + featureDeserialized + " in geneFeatureRefs map.");
            }

//            parameters.getGeneAlignerParameters(gt).setGeneFeatureToAlign(featureParams);

            if (featureDeserialized != null)
                input.putKnownReference(featureParams);
        }
    }

    public synchronized VDJCAlignerParameters getParameters() {
        init();
        return parameters;
    }

    public synchronized List<VDJCGene> getUsedGenes() {
        init();
        return usedGenes;
    }

    /**
     * Returns information about version of MiXCR which produced this file.
     *
     * @return information about version of MiXCR which produced this file
     */
    public String getVersionInfo() {
        return versionInfo;
    }

    /**
     * Returns magic bytes of this file.
     *
     * @return magic bytes of this file
     */
    public String getMagic() {
        return magic;
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
        return closed || (countingInputStream != null && countingInputStream.getBytesRead() == size);
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

        if (index != null)
            index.add(indexingStream.getBytesRead());

        VDJCAlignments alignments = input.readObject(VDJCAlignments.class);

        if (alignments == null) {
            if (index != null)
                index.removeAt(index.size() - 1);
            close(true);
        } else
            alignments.setAlignmentsIndex(counter++);

        return alignments;
    }

    /**
     * Produce reader that uses the same reference for geneFeatures.
     *
     * @param reader     target reader
     * @param parameters parameters to take reference from
     */
    public static void initGeneFeatureReferencesFrom(VDJCAlignmentsReader reader, VDJCAlignerParameters parameters) {
        Map<GeneFeature, GeneFeature> featureRefs = new HashMap<>();
        for (GeneType gt : GeneType.VDJC_REFERENCE) {
            GeneFeature f = parameters.getFeatureToAlign(gt);
            featureRefs.put(f, f);
        }
        reader.init(featureRefs);
    }

    /**
     * Produce reader that uses the same reference for geneFeatures.
     *
     * @param reader       target reader
     * @param sourceReader reader to take reference from
     */
    public static void initGeneFeatureReferencesFrom(VDJCAlignmentsReader reader, VDJCAlignmentsReader sourceReader) {
        initGeneFeatureReferencesFrom(reader, sourceReader.getParameters());
    }
}
