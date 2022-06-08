package com.milaboratory.mixcr.basictypes;

import com.milaboratory.primitivio.annotations.Serializable;
import io.repseq.core.VDJCGeneId;

import java.util.Objects;

@Serializable(by = IO.GeneAndScoreSerializer.class)
public final class GeneAndScore implements Comparable<GeneAndScore> {
    public final VDJCGeneId geneId;
    public final float score;

    public GeneAndScore(VDJCGeneId geneId, float score) {
        Objects.requireNonNull(geneId);
        this.geneId = geneId;
        this.score = score;
    }

    @Override
    public int compareTo(GeneAndScore o) {
        int c;

        if ((c = Float.compare(o.score, score)) != 0) // from high to low score (reversed order)
            return c;

        return geneId.compareTo(o.geneId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GeneAndScore that = (GeneAndScore) o;
        return Float.compare(that.score, score) == 0 && geneId.equals(that.geneId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(geneId, score);
    }

    @Override
    public String toString() {
        return "" + geneId.getName() + '(' + score + ')';
    }
}
