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

import com.milaboratory.mixcr.reference.Allele;
import com.milaboratory.mixcr.reference.AlleleResolver;
import com.milaboratory.mixcr.reference.GeneFeature;
import com.milaboratory.mixcr.reference.GeneType;
import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.PrimitivO;
import com.milaboratory.util.CanReportProgressAndStage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;

public final class CloneSetIO {
    static final String MAGIC = "MiXCR.CLNS.V01";
    static final int MAGIC_LENGTH = 14;
    static final byte[] MAGIC_BYTES = MAGIC.getBytes(StandardCharsets.US_ASCII);

    public static class CloneSetWriter implements CanReportProgressAndStage {
        final String stage = "Writing clones";
        final PrimitivO output;
        final CloneSet cloneSet;
        final int size;
        volatile int current;

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
            output.write(MAGIC_BYTES);
            output.writeObject(cloneSet.getAssemblingFeatures());
            IO.writeGT2GFMap(output, cloneSet.alignedFeatures);
            IOUtil.writeAlleleReferences(output, cloneSet.getUsedAlleles(), new GT2GFAdapter(cloneSet.alignedFeatures));

            output.writeInt(cloneSet.getClones().size());

            for (Clone clone : cloneSet) {
                output.writeObject(clone);
                ++current;
            }
        }
    }

    public static void read(CloneSet cloneSet, File file) throws IOException {
        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(file), 32768)) {
            write(cloneSet, os);
        }
    }

    public static void read(CloneSet cloneSet, String fileName) throws IOException {
        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(fileName), 32768)) {
            write(cloneSet, os);
        }
    }

    public static void write(CloneSet cloneSet, OutputStream outputStream) {
        PrimitivO output = new PrimitivO(outputStream);

        // Writing magic bytes
        output.write(MAGIC_BYTES);
        output.writeObject(cloneSet.getAssemblingFeatures());
        IO.writeGT2GFMap(output, cloneSet.alignedFeatures);
        IOUtil.writeAlleleReferences(output, cloneSet.getUsedAlleles(), new GT2GFAdapter(cloneSet.alignedFeatures));

        output.writeInt(cloneSet.getClones().size());

        for (Clone clone : cloneSet)
            output.writeObject(clone);
    }

    public static CloneSet read(String fileName, AlleleResolver alleleResolver) throws IOException {
        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(fileName), 32768)) {
            return read(inputStream, alleleResolver);
        }
    }

    public static CloneSet read(File file, AlleleResolver alleleResolver) throws IOException {
        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(file), 32768)) {
            return read(inputStream, alleleResolver);
        }
    }

    public static CloneSet read(InputStream inputStream, AlleleResolver alleleResolver) {
        PrimitivI input = new PrimitivI(inputStream);

        byte[] magicBytes = new byte[MAGIC_LENGTH];
        input.readFully(magicBytes);

        if (!Arrays.equals(magicBytes, MAGIC_BYTES))
            throw new RuntimeException("Wrong file format.");

        GeneFeature[] assemblingFeatures = input.readObject(GeneFeature[].class);
        EnumMap<GeneType, GeneFeature> alignedFeatures = IO.readGF2GTMap(input);
        List<Allele> alleles = IOUtil.readAlleleReferences(input, alleleResolver, new GT2GFAdapter(alignedFeatures));
        int count = input.readInt();
        List<Clone> clones = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            clones.add(input.readObject(Clone.class));

        return new CloneSet(clones, alleles, alignedFeatures, assemblingFeatures);
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
