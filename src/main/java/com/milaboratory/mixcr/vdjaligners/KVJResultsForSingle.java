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

import com.milaboratory.core.alignment.KAlignmentHit;
import com.milaboratory.core.alignment.KAlignmentResult;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.reference.Allele;
import com.milaboratory.mixcr.reference.GeneFeature;

import java.util.List;

final class KVJResultsForSingle {
    public final KAlignmentResult vResult, jResult;
    public final boolean isRC;
    public KAlignmentHit[] vHits, jHits;

    public KVJResultsForSingle(KAlignmentResult vResult, KAlignmentResult jResult, boolean isRC) {
        this.vResult = vResult;
        this.jResult = jResult;
        this.isRC = isRC;
    }

    private static KAlignmentHit[] extractHits(float minScore, KAlignmentResult result, int maxHits) {
        int count = 0;
        for (KAlignmentHit hit : result.getHits())
            if (hit.getAlignment().getScore() > minScore) {
                if (++count >= maxHits)
                    break;
            } else
                break;

        KAlignmentHit[] res = new KAlignmentHit[count];
        for (int i = 0; i < count; ++i)
            res[i] = result.getHits().get(i);

        return res;
    }

    public void calculateHits(float minTotalScore, int maxHits) {
        this.vHits = extractHits(minTotalScore - jResult.getBestHit().getAlignment().getScore(), vResult, maxHits);
        this.jHits = extractHits(minTotalScore - vResult.getBestHit().getAlignment().getScore(), jResult, maxHits);
    }

    public boolean isEmpty() {
        return (vResult == null || !vResult.hasHits()) &&
                (jResult == null || !jResult.hasHits());
    }

    public boolean isFull() {
        return vResult != null && jResult != null &&
                vResult.hasHits() && jResult.hasHits();
    }

    public boolean hasKVHits() {
        return vResult != null && vResult.hasHits();
    }

    public boolean hasKJHits() {
        return jResult != null && jResult.hasHits();
    }

    public boolean hasVJHits() {
        return vHits != null && vHits.length > 0 &&
                jHits != null && jHits.length > 0;
    }

    public VDJCHit[] getVHits(List<Allele> alleles, GeneFeature feature) {
        return createHits(vHits, alleles, feature);
    }

    public VDJCHit[] getJHits(List<Allele> alleles, GeneFeature feature) {
        return createHits(jHits, alleles, feature);
    }

    public static VDJCHit[] createHits(KAlignmentHit[] kHits, List<Allele> alleles, GeneFeature feature) {
        VDJCHit[] hits = new VDJCHit[kHits.length];
        for (int i = 0; i < kHits.length; i++)
            hits[i] = new VDJCHit(alleles.get(kHits[i].getId()), kHits[i].getAlignment(), feature);
        return hits;
    }

    public static VDJCHit[] createHits(List<KAlignmentHit> kHits, List<Allele> alleles, GeneFeature feature) {
        VDJCHit[] hits = new VDJCHit[kHits.size()];
        for (int i = 0; i < kHits.size(); i++)
            hits[i] = new VDJCHit(alleles.get(kHits.get(i).getId()), kHits.get(i).getAlignment(), feature);
        return hits;
    }

    public float getTotalTopScore() {
        float score = 0.0f;
        if (vResult != null && vResult.hasHits())
            score += vResult.getBestHit().getAlignment().getScore();
        if (jResult != null && jResult.hasHits())
            score += jResult.getBestHit().getAlignment().getScore();
        return score;
    }
}
