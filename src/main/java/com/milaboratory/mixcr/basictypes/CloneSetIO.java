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

import io.repseq.reference.Allele;
import io.repseq.reference.AlleleResolver;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import com.milaboratory.mixcr.util.VersionInfoProvider;
import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.PrimitivO;
import com.milaboratory.primitivio.SerializersManager;
import com.milaboratory.util.CanReportProgressAndStage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import static com.milaboratory.mixcr.basictypes.CompatibilityIO.registerV6Serializers;

public final class CloneSetIO {
    static final String MAGIC_V2 = "MiXCR.CLNS.V02";
    static final String MAGIC_V3 = "MiXCR.CLNS.V03";
    static final String MAGIC_V4 = "MiXCR.CLNS.V04";
    static final String MAGIC = MAGIC_V4;
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
            // Writing magic bytes
            output.write(MAGIC_BYTES);

            // Writing version information
            output.writeUTF(
                    VersionInfoProvider.getVersionString(
                            VersionInfoProvider.OutputType.ToFile));

            output.writeObject(cloneSet.getAssemblingFeatures());
            IO.writeGT2GFMap(output, cloneSet.alignedFeatures);
            IOUtil.writeGeneReferences(output, cloneSet.getUsedAlleles(), new GT2GFAdapter(cloneSet.alignedFeatures));

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
        try(CloneSetWriter writer = new CloneSetWriter(cloneSet, outputStream)){
            writer.write();
        }
    }

    public static CloneSet read(String fileName, AlleleResolver alleleResolver) throws IOException {
        return read(new File(fileName), alleleResolver);
    }

    public static CloneSet read(File file, AlleleResolver alleleResolver) throws IOException {
        try (InputStream inputStream = IOUtil.createIS(file)) {
            return read(inputStream, alleleResolver);
        }
    }

    public static CloneSet read(InputStream inputStream, AlleleResolver alleleResolver) {
        PrimitivI input = new PrimitivI(inputStream);

        byte[] magicBytes = new byte[MAGIC_LENGTH];
        input.readFully(magicBytes);

        String magicString = new String(magicBytes);

        SerializersManager serializersManager = input.getSerializersManager();
        switch (magicString) {
            case MAGIC_V2:
            case MAGIC_V3:
                registerV6Serializers(serializersManager);
                break;
            case MAGIC:
                break;
            default:
                throw new RuntimeException("Unsupported file format; .clns file of version " + magicString + " while you are running MiXCR " + MAGIC);
        }

        String versionInfo = null;
        if (magicString.compareTo(MAGIC_V3) >= 0)
            versionInfo = input.readUTF();

        GeneFeature[] assemblingFeatures = input.readObject(GeneFeature[].class);
        EnumMap<GeneType, GeneFeature> alignedFeatures = IO.readGF2GTMap(input);
        List<Allele> alleles = IOUtil.readAlleleReferences(input, alleleResolver, new GT2GFAdapter(alignedFeatures));
        int count = input.readInt();
        List<Clone> clones = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            clones.add(input.readObject(Clone.class));

        CloneSet cloneSet = new CloneSet(clones, alleles, alignedFeatures, assemblingFeatures);
        cloneSet.versionInfo = versionInfo;

        return cloneSet;
    }

    private static class GT2GFAdapter implements HasFeatureToAlign {
        final EnumMap<GeneType, GeneFeature> map;

        private GT2GFAdapter(EnumMap<GeneType, GeneFeature> map) {
            this.map = map;
        }

        @Override
        public GeneFeature getFeatureToAlign(GeneType geneType) {
            return map.get(geneType);
        }
    }
}
