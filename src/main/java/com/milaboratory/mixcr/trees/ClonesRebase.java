package com.milaboratory.mixcr.trees;

import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.Aligner;
import com.milaboratory.core.alignment.AlignmentScoring;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequenceBuilder;
import com.milaboratory.mixcr.util.RangeInfo;

class ClonesRebase {
    private final NucleotideSequence VSequence1;
    private final AlignmentScoring<NucleotideSequence> VScoring;
    private final AlignmentScoring<NucleotideSequence> NDNScoring;
    private final NucleotideSequence JSequence1;
    private final AlignmentScoring<NucleotideSequence> JScoring;

    ClonesRebase(
            NucleotideSequence VSequence1,
            AlignmentScoring<NucleotideSequence> VScoring,
            AlignmentScoring<NucleotideSequence> NDNScoring,
            NucleotideSequence JSequence1,
            AlignmentScoring<NucleotideSequence> JScoring
    ) {
        this.VSequence1 = VSequence1;
        this.VScoring = VScoring;
        this.JSequence1 = JSequence1;
        this.NDNScoring = NDNScoring;
        this.JScoring = JScoring;
    }

    CloneWithMutationsFromReconstructedRoot rebaseClone(RootInfo rootInfo, MutationsFromVJGermline mutationsFromVJGermline, CloneWrapper cloneWrapper) {
        Range NDNRangeInKnownNDN = NDNRangeInKnownNDN(mutationsFromVJGermline, rootInfo.getVRangeInCDR3(), rootInfo.getJRangeInCDR3());

        MutationsWithRange VMutationsInCDR3WithoutNDN = mutationsFromVJGermline.getVMutationsInCDR3WithoutNDN();
        Range wasVRangeInCDR3 = mutationsFromVJGermline.getVMutationsInCDR3WithoutNDN().getRangeInfo().getRange();
        Range VRange = new Range(wasVRangeInCDR3.getLower(), rootInfo.getVRangeInCDR3().getUpper());
        //can skip empty VRange because we will not include first mutations (empty range always will mutate to empty range)
        if (!VRange.isEmpty()) {
            Range VMutationsWithinNDNRange = mutationsFromVJGermline.getKnownVMutationsWithinNDN().getSecond()
                    .intersection(VRange);
            VMutationsWithinNDNRange = VMutationsWithinNDNRange != null ? VMutationsWithinNDNRange
                    : new Range(wasVRangeInCDR3.getUpper(), wasVRangeInCDR3.getUpper());
            int lengthDelta = 0;
            if (!VMutationsWithinNDNRange.isEmpty()) {
                MutationsWithRange VMutationsToAdd = new MutationsWithRange(
                        VSequence1,
                        mutationsFromVJGermline.getKnownVMutationsWithinNDN().getFirst(),
                        new RangeInfo(VMutationsWithinNDNRange, false)
                );
                VMutationsInCDR3WithoutNDN = VMutationsInCDR3WithoutNDN.combineWithTheSameMutationsRight(VMutationsToAdd);
                lengthDelta += VMutationsToAdd.lengthDelta();
            }
            Range rangeToAlign = new Range(VMutationsWithinNDNRange.getUpper(), rootInfo.getVRangeInCDR3().getUpper());
            if (!rangeToAlign.isEmpty() && !rangeToAlign.isReverse()) {
                Mutations<NucleotideSequence> absoluteMutations = Aligner.alignGlobal(
                        VScoring,
                        VSequence1,
                        mutationsFromVJGermline.getKnownNDN(),
                        rangeToAlign.getLower(),
                        rangeToAlign.length(),
                        VMutationsWithinNDNRange.length() + lengthDelta,
                        rangeToAlign.length()
                ).getAbsoluteMutations();
                VMutationsInCDR3WithoutNDN = VMutationsInCDR3WithoutNDN.combineWithMutationsToTheRight(absoluteMutations, rangeToAlign);
            }
            NDNRangeInKnownNDN = new Range(NDNRangeInKnownNDN.getLower() + lengthDelta, NDNRangeInKnownNDN.getUpper());
        }

        MutationsWithRange JMutationsInCDR3WithoutNDN = mutationsFromVJGermline.getJMutationsInCDR3WithoutNDN();
        Range wasJRangeInCDR3 = mutationsFromVJGermline.getJMutationsInCDR3WithoutNDN().getRangeInfo().getRange();
        Range JRange = new Range(rootInfo.getJRangeInCDR3().getLower(), wasJRangeInCDR3.getLower());
        Range JMutationsWithinNDNRange = mutationsFromVJGermline.getKnownJMutationsWithinNDN().getSecond()
                .intersection(JRange);
        JMutationsWithinNDNRange = JMutationsWithinNDNRange != null ? JMutationsWithinNDNRange
                : new Range(wasJRangeInCDR3.getLower(), wasJRangeInCDR3.getLower());
        int lengthDelta = 0;
        MutationsWithRange JMutationsToAdd = new MutationsWithRange(
                JSequence1,
                mutationsFromVJGermline.getKnownJMutationsWithinNDN().getFirst(),
                new RangeInfo(JMutationsWithinNDNRange, true)
        );
        JMutationsInCDR3WithoutNDN = JMutationsInCDR3WithoutNDN.combineWithTheSameMutationsLeft(JMutationsToAdd);
        lengthDelta += JMutationsToAdd.lengthDelta();
        Range rangeToAlign = new Range(rootInfo.getJRangeInCDR3().getLower(), JMutationsWithinNDNRange.getLower());
        if (!rangeToAlign.isEmpty() && !rangeToAlign.isReverse()) {
            Mutations<NucleotideSequence> absoluteMutations = Aligner.alignGlobal(
                    JScoring,
                    JSequence1,
                    mutationsFromVJGermline.getKnownNDN(),
                    rangeToAlign.getLower(),
                    rangeToAlign.length(),
                    NDNRangeInKnownNDN.getUpper() - lengthDelta,
                    rangeToAlign.length()
            ).getAbsoluteMutations();
            JMutationsInCDR3WithoutNDN = JMutationsInCDR3WithoutNDN.combineWithMutationsToTheLeft(absoluteMutations, rangeToAlign);
        }
        NDNRangeInKnownNDN = new Range(NDNRangeInKnownNDN.getLower(), NDNRangeInKnownNDN.getUpper() - lengthDelta);
        MutationsDescription mutations = new MutationsDescription(
                mutationsFromVJGermline.getVMutationsWithoutCDR3(),
                VMutationsInCDR3WithoutNDN,
                NDNMutations(rootInfo.getReconstructedNDN(), mutationsFromVJGermline.getKnownNDN().getRange(NDNRangeInKnownNDN)),
                JMutationsInCDR3WithoutNDN,
                mutationsFromVJGermline.getJMutationsWithoutCDR3()
        );
        return new CloneWithMutationsFromReconstructedRoot(mutations, mutationsFromVJGermline, cloneWrapper);
    }

    MutationsDescription rebaseMutations(
            MutationsDescription originalNode,
            RootInfo originalRoot,
            RootInfo rebaseTo
    ) {
        NucleotideSequence originalKnownNDN = originalNode.getKnownNDN().buildSequence();
        MutationsWithRange VMutationsInCDR3WithoutNDN;
        if (originalRoot.getVRangeInCDR3().length() < rebaseTo.getVRangeInCDR3().length()) {
            Range difference = new Range(originalRoot.getVRangeInCDR3().getUpper(), rebaseTo.getVRangeInCDR3().getUpper());
            VMutationsInCDR3WithoutNDN = originalNode.getVMutationsInCDR3WithoutNDN()
                    .combineWithMutationsToTheRight(
                            Aligner.alignGlobal(
                                    VScoring,
                                    VSequence1,
                                    originalKnownNDN,
                                    difference.getLower(),
                                    difference.length(),
                                    0,
                                    difference.length()
                            ).getAbsoluteMutations(),
                            difference
                    );
        } else if (rebaseTo.getVRangeInCDR3().length() < originalRoot.getVRangeInCDR3().length()) {
            VMutationsInCDR3WithoutNDN = new MutationsWithRange(
                    VSequence1,
                    originalNode.getVMutationsInCDR3WithoutNDN().getMutations(),
                    new RangeInfo(
                            rebaseTo.getVRangeInCDR3(),
                            false
                    )
            );
        } else {
            VMutationsInCDR3WithoutNDN = originalNode.getVMutationsInCDR3WithoutNDN();
        }

        SequenceBuilder<NucleotideSequence> knownNDNBuilder = NucleotideSequence.ALPHABET.createBuilder();

        if (rebaseTo.getVRangeInCDR3().length() < originalRoot.getVRangeInCDR3().length()) {
            knownNDNBuilder.append(MutationsUtils.buildSequence(
                    VSequence1,
                    originalNode.getVMutationsInCDR3WithoutNDN().getMutations(),
                    new RangeInfo(
                            new Range(rebaseTo.getVRangeInCDR3().getUpper(), originalRoot.getVRangeInCDR3().getUpper()),
                            false
                    )
            ));
        }

        knownNDNBuilder.append(originalKnownNDN.getRange(
                Math.max(0, rebaseTo.getVRangeInCDR3().length() - originalRoot.getVRangeInCDR3().length()),
                originalKnownNDN.size() - Math.max(0, rebaseTo.getJRangeInCDR3().length() - originalRoot.getJRangeInCDR3().length())
        ));

        if (rebaseTo.getJRangeInCDR3().length() < originalRoot.getJRangeInCDR3().length()) {
            knownNDNBuilder.append(MutationsUtils.buildSequence(
                    JSequence1,
                    originalNode.getJMutationsInCDR3WithoutNDN().getMutations(),
                    new RangeInfo(
                            new Range(originalRoot.getJRangeInCDR3().getLower(), rebaseTo.getJRangeInCDR3().getLower()),
                            false
                    )
            ));
        }

        NucleotideSequence rebasedKnownNDN = knownNDNBuilder.createAndDestroy();

        MutationsWithRange JMutationsInCDR3WithoutNDN;
        if (originalRoot.getJRangeInCDR3().length() < rebaseTo.getJRangeInCDR3().length()) {
            Range difference = new Range(rebaseTo.getJRangeInCDR3().getLower(), originalRoot.getJRangeInCDR3().getLower());
            JMutationsInCDR3WithoutNDN = originalNode.getJMutationsInCDR3WithoutNDN()
                    .combineWithMutationsToTheLeft(
                            Aligner.alignGlobal(
                                    JScoring,
                                    JSequence1,
                                    originalKnownNDN,
                                    difference.getLower(),
                                    difference.length(),
                                    originalKnownNDN.size() - difference.length(),
                                    difference.length()
                            ).getAbsoluteMutations(),
                            difference
                    );
        } else if (rebaseTo.getJRangeInCDR3().length() < originalRoot.getJRangeInCDR3().length()) {
            JMutationsInCDR3WithoutNDN = new MutationsWithRange(
                    JSequence1,
                    originalNode.getJMutationsInCDR3WithoutNDN().getMutations(),
                    new RangeInfo(
                            rebaseTo.getJRangeInCDR3(),
                            false
                    )
            );
        } else {
            JMutationsInCDR3WithoutNDN = originalNode.getJMutationsInCDR3WithoutNDN();
        }


        return new MutationsDescription(
                originalNode.getVMutationsWithoutCDR3(),
                VMutationsInCDR3WithoutNDN,
                NDNMutations(rebaseTo.getReconstructedNDN(), rebasedKnownNDN),
                JMutationsInCDR3WithoutNDN,
                originalNode.getJMutationsWithoutCDR3()
        );
    }


    static Range NDNRangeInKnownNDN(MutationsFromVJGermline mutations, Range VRangeInCDR3, Range JRangeInCDR3) {
        return new Range(
                VRangeInCDR3.length() - mutations.getVMutationsInCDR3WithoutNDN().getRangeInfo().getRange().length(),
                mutations.getKnownNDN().size() - (JRangeInCDR3.length() - mutations.getJMutationsInCDR3WithoutNDN().getRangeInfo().getRange().length())
        );
    }

    MutationsWithRange NDNMutations(NucleotideSequence first, NucleotideSequence second) {
        return new MutationsWithRange(
                first,
                Aligner.alignGlobal(
                        NDNScoring,
                        first,
                        second
                ).getAbsoluteMutations(),
                new RangeInfo(new Range(0, first.size()), true)
        );
    }
}
