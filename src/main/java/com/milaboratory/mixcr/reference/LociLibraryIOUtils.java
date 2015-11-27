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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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
}
