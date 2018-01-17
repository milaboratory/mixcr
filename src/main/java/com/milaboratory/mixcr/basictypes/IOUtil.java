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

import com.milaboratory.core.io.CompressionType;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.PrimitivO;
import io.repseq.core.GeneFeature;
import io.repseq.core.VDJCGene;
import io.repseq.core.VDJCGeneId;
import io.repseq.core.VDJCLibraryRegistry;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class IOUtil {
    public static void writeAndRegisterGeneReferences(PrimitivO output, List<VDJCGene> genes,
                                                      HasFeatureToAlign featuresToAlign) {
        // Writing gene ids
        output.writeInt(genes.size());
        for (VDJCGene gene : genes)
            output.writeObject(gene.getId());

        registerGeneReferences(output, genes, featuresToAlign);
    }

    public static void registerGeneReferences(PrimitivO output, List<VDJCGene> genes,
                                              HasFeatureToAlign featuresToAlign) {
        // Putting genes references and feature sequences to be serialized/deserialized as references
        for (VDJCGene gene : genes) {
            output.putKnownReference(gene);
            // Also put sequences of certain gene features of genes as known references if required
            if (featuresToAlign != null) {
                GeneFeature featureToAlign = featuresToAlign.getFeatureToAlign(gene.getGeneType());
                if (featureToAlign == null)
                    continue;
                NucleotideSequence featureSequence = gene.getFeature(featureToAlign);
                if (featureSequence == null)
                    continue;
                output.putKnownReference(gene.getFeature(featuresToAlign.getFeatureToAlign(gene.getGeneType())));
            }
        }
    }

    public static List<VDJCGene> readGeneReferences(PrimitivI input, VDJCLibraryRegistry registry) {
        // Reading gene ids
        int count = input.readInt();
        List<VDJCGene> genes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            VDJCGeneId id = input.readObject(VDJCGeneId.class);
            VDJCGene gene = registry.getGene(id);
            if (gene == null)
                throw new RuntimeException("Gene not found: " + id);
            genes.add(gene);
        }

        return genes;
    }

    public static List<VDJCGene> readAndRegisterGeneReferences(PrimitivI input, VDJCLibraryRegistry registry,
                                                               HasFeatureToAlign featuresToAlign) {
        List<VDJCGene> genes = readGeneReferences(input, registry);
        registerGeneReferences(input, genes, featuresToAlign);
        return genes;
    }

    public static void registerGeneReferences(PrimitivI input, List<VDJCGene> genes,
                                              HasFeatureToAlign featuresToAlign) {
        // Putting genes references and feature sequences to be serialized/deserialized as references
        for (VDJCGene gene : genes) {
            input.putKnownReference(gene);
            // Also put sequences of certain gene features of genes as known references if required
            if (featuresToAlign != null) {
                GeneFeature featureToAlign = featuresToAlign.getFeatureToAlign(gene.getGeneType());
                if (featureToAlign == null)
                    continue;
                NucleotideSequence featureSequence = gene.getFeature(featureToAlign);
                if (featureSequence == null)
                    continue;
                input.putKnownReference(featureSequence);
            }
        }
    }

    public static InputStream createIS(String file) throws IOException {
        return createIS(CompressionType.detectCompressionType(file), new FileInputStream(file));
    }

    public static InputStream createIS(File file) throws IOException {
        return createIS(CompressionType.detectCompressionType(file), new FileInputStream(file));
    }

    public static InputStream createIS(CompressionType ct, InputStream is) throws IOException {
        if (ct == CompressionType.None)
            return new BufferedInputStream(is, 65536);
        else return ct.createInputStream(is, 65536);
    }

    public static OutputStream createOS(String file) throws IOException {
        return createOS(CompressionType.detectCompressionType(file), new FileOutputStream(file));
    }

    public static OutputStream createOS(File file) throws IOException {
        return createOS(CompressionType.detectCompressionType(file), new FileOutputStream(file));
    }

    public static OutputStream createOS(CompressionType ct, OutputStream os) throws IOException {
        if (ct == CompressionType.None)
            return new BufferedOutputStream(os, 65536);
        else return ct.createOutputStream(os, 65536);
    }

    public enum MiXCRFileType {
        Clns, ClnA, VDJCA
    }

    public static MiXCRFileType detectFilType(File file) {
        try {
            try (FileInputStream reader = new FileInputStream(file)) {
                byte[] data = new byte[10];
                reader.read(data);
                String magic = new String(data, StandardCharsets.US_ASCII);
                switch (magic) {
                    case "MiXCR.VDJC": return MiXCRFileType.VDJCA;
                    case "MiXCR.CLNS": return MiXCRFileType.Clns;
                    case "MiXCR.CLNA": return MiXCRFileType.ClnA;
                    default:
                        throw new IllegalArgumentException("Not a MiXCR file");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
