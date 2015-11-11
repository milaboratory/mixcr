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
package com.milaboratory.mixcr.reference.builder;

import com.milaboratory.core.io.sequence.fasta.FastaReader;
import com.milaboratory.core.sequence.AminoAcidSequence;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.util.StringUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by dbolotin on 25/10/15.
 */
public class LocusBuilderTest {
    @Test
    public void test1() throws Exception {
        FastaReader reader = new FastaReader("/Volumes/Data/Projects/MiLaboratory/tmp/result/human_IGHV.fasta", null);
        FastaReader.RawFastaRecord rec;
        int[] refpoints = {0, 78, 114, 165, 195, 309, -1};
        List<AminoAcidSequence[]> seqs = new ArrayList<>();
        int[] lens = new int[refpoints.length];
        while ((rec = reader.takeRawRecord()) != null) {
            //System.out.println(rec.sequence);
            StringWithMapping swm = StringWithMapping.removeSymbol(rec.sequence, '.');
            NucleotideSequence seq = new NucleotideSequence(swm.getModifiedString());
            if (seq.containsWildcards()) {
                //System.out.println("Skipped: " + rec.description);
                continue;
            }
            StringBuilder coordString = new StringBuilder();
            int next = 0;
            //System.out.println(AminoAcidSequence.translate(seq, 0));

            AminoAcidSequence[] parts = new AminoAcidSequence[refpoints.length];
            for (int i = 0; i < refpoints.length - 1; ++i) {
                parts[i] = tr(swm, refpoints[i], refpoints[i + 1]);
                if (parts[i] != null)
                    lens[i] = Math.max(lens[i], parts[i].size());
            }
            seqs.add(parts);
        }

        for (AminoAcidSequence[] seq : seqs) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < refpoints.length; i++) {
                int len = 0;
                if (seq[i] != null) {
                    sb.append(seq[i]);
                    len = seq[i].size();
                }
                sb.append(StringUtil.spaces(lens[i] - len + 2));
            }
            System.out.println(sb);
        }
    }

    private static AminoAcidSequence tr(StringWithMapping sm, int from, int to) {
        String str = sm.getModifiedString();
        int sFrom = sm.convertPosition(from);
        int sTo = to == -1 ? str.length() : sm.convertPosition(to);
        if (sTo == -1 || sFrom == -1)
            return null;
        return AminoAcidSequence.translateFromLeft(new NucleotideSequence(
                str.substring(sFrom, sTo)));
    }
}