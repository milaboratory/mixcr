/*
 * Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
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
