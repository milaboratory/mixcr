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
package com.milaboratory.mixcr.vdjaligners;

import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import io.repseq.core.GeneFeature;
import io.repseq.core.VDJCGene;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

final class PreVDJCHit implements Comparable<PreVDJCHit> {
    final int id;
    final Alignment<NucleotideSequence> alignment;

    PreVDJCHit(int id, Alignment<NucleotideSequence> alignment) {
        this.id = id;
        this.alignment = alignment;
    }

    static VDJCHit[] convert(List<VDJCGene> genes, GeneFeature feature,
                             List<PreVDJCHit> preHits) {
        return convert(genes, feature, preHits, 0, 1);
    }

    static VDJCHit[] convert(List<VDJCGene> genes, GeneFeature feature,
                             List<PreVDJCHit> preHits, int indexOfTargets,
                             int numberOfTargets) {
        VDJCHit[] hits = new VDJCHit[preHits.size()];
        for (int i = 0; i < preHits.size(); i++) {
            PreVDJCHit h = preHits.get(i);
            Alignment<NucleotideSequence>[] alignments = new Alignment[numberOfTargets];
            alignments[indexOfTargets] = h.alignment;
            hits[i] = new VDJCHit(genes.get(h.id), alignments, feature);
        }
        return hits;
    }

    static VDJCHit[] combine(final List<VDJCGene> genes, final GeneFeature feature, final PreVDJCHit[][] hits) {
        for (int i = 0; i < hits.length; i++)
            Arrays.sort(hits[i]);
        ArrayList<VDJCHit> result = new ArrayList<>();
        final int[] pointers = new int[hits.length];
        Alignment<NucleotideSequence>[] alignments;
        int i, minId;
        while (true) {
            minId = Integer.MAX_VALUE;
            for (i = 0; i < pointers.length; ++i)
                if (pointers[i] < hits[i].length && minId > hits[i][pointers[i]].id)
                    minId = hits[i][pointers[i]].id;

            if (minId == Integer.MAX_VALUE)
                break;

            alignments = new Alignment[hits.length];
            for (i = 0; i < pointers.length; ++i)
                if (pointers[i] < hits[i].length && minId == hits[i][pointers[i]].id) {
                    alignments[i] = hits[i][pointers[i]].alignment;
                    ++pointers[i];
                }

            result.add(new VDJCHit(genes.get(minId), alignments, feature));
        }
        VDJCHit[] vdjcHits = result.toArray(new VDJCHit[result.size()]);
        Arrays.sort(vdjcHits);
        return vdjcHits;
    }

    @Override
    public int compareTo(PreVDJCHit o) {
        return Integer.compare(id, o.id);
    }

    public static final Comparator<PreVDJCHit> SCORE_COMPARATOR = new Comparator<PreVDJCHit>() {
        @Override
        public int compare(PreVDJCHit o1, PreVDJCHit o2) {
            return Float.compare(o2.alignment.getScore(), o1.alignment.getScore());
        }
    };
}
