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
import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.PrimitivO;
import com.milaboratory.util.CanReportProgressAndStage;
import io.repseq.core.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public final class CloneSetIO {
    static final String MAGIC_V5 = "MiXCR.CLNS.V05";
    static final String MAGIC = MAGIC_V5;
    static final int MAGIC_LENGTH = 14;
    static final byte[] MAGIC_BYTES = MAGIC.getBytes(StandardCharsets.US_ASCII);

    public static class CloneSetWriter implements CanReportProgressAndStage, Closeable {
        final String stage = "Writing clones";
        final PrimitivO output;
        final CloneSet cloneSet;
        final int size;
        volatile int current;

        public CloneSetWriter(CloneSet cloneSet, String fileName) throws IOException {
            this(cloneSet, new File(fileName));
        }

        public CloneSetWriter(CloneSet cloneSet, File file) throws IOException {
            this(cloneSet, IOUtil.createOS(file));
        }

        public CloneSetWriter(CloneSet cloneSet, OutputStream outputStream) {
            this.output = new PrimitivO(outputStream);
            this.cloneSet = cloneSet;
            this.size = cloneSet.size();
        }

        @Override
        public String getStage() {
            return stage;
        }

        @Override
        public double getProgress() {
            return (1.0 * current) / size;
        }

        @Override
        public boolean isFinished() {
            return current == size;
        }

        public void write() {
            // Registering custom serializer
            output.getSerializersManager().registerCustomSerializer(GeneFeature.class, new GeneFeatureSerializer(true));

            // Writing magic bytes
            output.write(MAGIC_BYTES);

            // Writing version information
            output.writeUTF(
                    MiXCRVersionInfo.get().getVersionString(
                            MiXCRVersionInfo.OutputType.ToFile));

            GeneFeature[] assemblingFeatures = cloneSet.getAssemblingFeatures();
            output.writeObject(assemblingFeatures);
            IO.writeGT2GFMap(output, cloneSet.alignedFeatures);

            IOUtil.writeAndRegisterGeneReferences(output, cloneSet.getUsedGenes(), new GT2GFAdapter(cloneSet.alignedFeatures));

            output.writeInt(cloneSet.getClones().size());

            for (Clone clone : cloneSet) {
                output.writeObject(clone);
                ++current;
            }
        }

        @Override
        public void close() {
            output.close();
        }
    }

    public static void write(CloneSet cloneSet, File file) throws IOException {
        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(file), 32768)) {
            write(cloneSet, os);
        }
    }

    public static void write(CloneSet cloneSet, String fileName) throws IOException {
        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(fileName), 32768)) {
            write(cloneSet, os);
        }
    }

    public static void write(CloneSet cloneSet, OutputStream outputStream) {
        try (CloneSetWriter writer = new CloneSetWriter(cloneSet, outputStream)) {
            writer.write();
        }
    }

    public static CloneSet read(String fileName) throws IOException {
        return read(new File(fileName), VDJCLibraryRegistry.getDefault());
    }

    public static CloneSet read(String fileName, VDJCLibraryRegistry libraryRegistry) throws IOException {
        return read(new File(fileName), libraryRegistry);
    }

    public static CloneSet read(File file) throws IOException {
        try (InputStream inputStream = IOUtil.createIS(file)) {
            return read(inputStream);
        }
    }

    public static CloneSet read(File file, VDJCLibraryRegistry libraryRegistry) throws IOException {
        try (InputStream inputStream = IOUtil.createIS(file)) {
            return read(inputStream, libraryRegistry);
        }
    }

    public static CloneSet read(InputStream inputStream) {
        return read(inputStream, VDJCLibraryRegistry.getDefault());
    }

    public static CloneSet read(InputStream inputStream, VDJCLibraryRegistry libraryRegistry) {
        PrimitivI input = new PrimitivI(inputStream);

        // Registering custom serializer
        input.getSerializersManager().registerCustomSerializer(GeneFeature.class, new GeneFeatureSerializer(true));

        byte[] magicBytes = new byte[MAGIC_LENGTH];
        input.readFully(magicBytes);

        String magicString = new String(magicBytes);

        //SerializersManager serializersManager = input.getSerializersManager();

        switch (magicString) {
            case MAGIC:
                break;
            default:
                throw new RuntimeException("Unsupported file format; .clns file of version " + magicString +
                        " while you are running MiXCR " + MAGIC);
        }

        String versionInfo = input.readUTF();

        GeneFeature[] assemblingFeatures = input.readObject(GeneFeature[].class);
        EnumMap<GeneType, GeneFeature> alignedFeatures = IO.readGF2GTMap(input);
        List<VDJCGene> genes = IOUtil.readAndRegisterGeneReferences(input, libraryRegistry, new GT2GFAdapter(alignedFeatures));

        int count = input.readInt();
        List<Clone> clones = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            clones.add(input.readObject(Clone.class));

        CloneSet cloneSet = new CloneSet(clones, genes, alignedFeatures, assemblingFeatures);
        cloneSet.versionInfo = versionInfo;

        return cloneSet;
    }

    public static class GT2GFAdapter implements HasFeatureToAlign {
        public final EnumMap<GeneType, GeneFeature> map;

        public GT2GFAdapter(EnumMap<GeneType, GeneFeature> map) {
            this.map = map;
        }

        @Override
        public GeneFeature getFeatureToAlign(GeneType geneType) {
            return map.get(geneType);
        }
    }
}
