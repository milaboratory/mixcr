
package com.milaboratory.mixcr.trees;

import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.Aligner;
import com.milaboratory.core.alignment.LinearGapAlignmentScoring;
import com.milaboratory.core.mutations.Mutation;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.mutations.MutationsBuilder;
import com.milaboratory.core.sequence.NucleotideSequence;

import java.util.Random;

class MutationsGenerator {
    static Mutations<NucleotideSequence> generateMutations(NucleotideSequence parent, Random random) {
        return generateMutations(parent, random, new Range(0, parent.size()));
    }

    static Mutations<NucleotideSequence> generateMutations(NucleotideSequence parent, Random random, Range range) {
        MutationsBuilder<NucleotideSequence> result = new MutationsBuilder<>(NucleotideSequence.ALPHABET);

        byte[] parentChars = parent.getSequence().asArray();
        for (int i = range.getFrom(); i < range.getTo() - 1; i++) {
            int count = random.nextInt(20);
            switch (count) {
                case 0:
                    int insertionsCount = random.nextInt(3);
                    for (int i1 = 0; i1 < insertionsCount; i1++) {
                        result.append(Mutation.createInsertion(i, (byte) random.nextInt(4)));
                    }
                    break;
                case 1:
                    result.append(Mutation.createDeletion(i, parentChars[i]));
                    break;
                case 2:
                case 3:
                case 4:
                    byte replaceWith = (byte) random.nextInt(4);
                    if (parentChars[i] != replaceWith) {
                        result.append(Mutation.createSubstitution(i, parentChars[i], replaceWith));
                    }
                    break;
            }
        }

        NucleotideSequence child = result.createAndDestroy().mutate(parent);
        return Aligner.alignGlobal(
                LinearGapAlignmentScoring.getNucleotideBLASTScoring(),
                parent,
                child,
                range.getLower(), parent.size() - range.getLower(),
                range.getLower(), child.size() - range.getLower()
        ).getAbsoluteMutations();
    }
}
