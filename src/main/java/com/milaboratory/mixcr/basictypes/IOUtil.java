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
import com.milaboratory.mixcr.reference.Allele;
import com.milaboratory.mixcr.reference.AlleleId;
import com.milaboratory.mixcr.reference.AlleleResolver;
import com.milaboratory.mixcr.reference.GeneFeature;
import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.PrimitivO;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class IOUtil {
    public static void writeAlleleReferences(PrimitivO output, List<Allele> alleles,
                                             HasFeatureToAlign featuresToAlign) {
        // Writing allele ids
        output.writeInt(alleles.size());
        for (Allele allele : alleles)
            output.writeObject(allele.getId());

        // Putting alleles references and feature sequences to be serialized/deserialized as references
        for (Allele allele : alleles) {
            output.putKnownReference(allele);
            // Also put sequences of certain gene features of alleles as known references if required
            if (featuresToAlign != null) {
                GeneFeature featureToAlign = featuresToAlign.getFeatureToAlign(allele.getGeneType());
                if (featureToAlign == null)
                    continue;
                NucleotideSequence featureSequence = allele.getFeature(featureToAlign);
                if (featureSequence == null)
                    continue;
                output.putKnownReference(allele.getFeature(featuresToAlign.getFeatureToAlign(allele.getGeneType())));
            }
        }
    }

    public static List<Allele> readAlleleReferences(PrimitivI input, AlleleResolver alleleResolver,
                                                    HasFeatureToAlign featuresToAlign) {
        // Reading allele ids
        int count = input.readInt();
        List<Allele> alleles = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            AlleleId id = input.readObject(AlleleId.class);
            Allele allele = alleleResolver.getAllele(id);
            if (allele == null)
                throw new RuntimeException("Allele not found: " + id);
            alleles.add(allele);
        }

        // Putting alleles references and feature sequences to be serialized/deserialized as references
        for (Allele allele : alleles) {
            input.putKnownReference(allele);
            // Also put sequences of certain gene features of alleles as known references if required
            if (featuresToAlign != null) {
                GeneFeature featureToAlign = featuresToAlign.getFeatureToAlign(allele.getGeneType());
                if (featureToAlign == null)
                    continue;
                NucleotideSequence featureSequence = allele.getFeature(featureToAlign);
                if (featureSequence == null)
                    continue;
                input.putKnownReference(featureSequence);
            }
        }

        return alleles;
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
}
