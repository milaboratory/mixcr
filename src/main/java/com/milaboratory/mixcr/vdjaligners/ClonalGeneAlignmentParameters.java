package com.milaboratory.mixcr.vdjaligners;

import com.milaboratory.core.alignment.AlignmentScoring;
import com.milaboratory.core.sequence.NucleotideSequence;

/**
 * Created by poslavsky on 01/03/2017.
 */
public interface ClonalGeneAlignmentParameters {
    float getRelativeMinScore();

    AlignmentScoring<NucleotideSequence> getScoring();

    ClonalGeneAlignmentParameters clone();
}
