package com.milaboratory.mixcr.trees;

import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.AffineGapAlignmentScoring;
import com.milaboratory.core.alignment.Aligner;
import com.milaboratory.core.alignment.AlignmentScoring;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;

class ClonesRebase {
    private final NucleotideSequence VSequence1;
    private final NucleotideSequence JSequence1;
    private final AlignmentScoring<NucleotideSequence> NDNScoring;

    ClonesRebase(NucleotideSequence VSequence1, NucleotideSequence JSequence1, AlignmentScoring<NucleotideSequence> NDNScoring) {
        this.VSequence1 = VSequence1;
        this.JSequence1 = JSequence1;
        this.NDNScoring = NDNScoring;
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
                        AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
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
                    AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
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

    //TODO test with randomized test
    MutationsDescription rebaseMutations(
            MutationsDescription originalRoot,
            RootInfo originalRootInfo,
            RootInfo rebaseTo
    ) {
        throw new UnsupportedOperationException();
//        NucleotideSequence originalKnownNDN = originalRoot.getKnownNDN().buildSequence();
//        List<MutationsWithRange> VMutationsWithoutNDN;
//        if (originalRootInfo.getVRangeInCDR3().length() < rebaseTo.getVRangeInCDR3().length()) {
//            VMutationsWithoutNDN = new ArrayList<>(originalRoot.getVMutationsWithoutNDN());
//            Range difference = new Range(originalRootInfo.getVRangeInCDR3().getUpper(), rebaseTo.getVRangeInCDR3().getUpper());
//
//            Mutations<NucleotideSequence> absoluteMutations = Aligner.alignGlobal(
//                    AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
//                    VSequence1,
//                    originalKnownNDN,
//                    difference.getLower(),
//                    difference.length(),
//                    0,
//                    difference.length()
//            ).getAbsoluteMutations();
//            VMutationsWithoutNDN.add(new MutationsWithRange(
//                    VSequence1,
//                    absoluteMutations,
//                    difference,
//                    true, true
//            ));
//        } else if (originalRootInfo.getVRangeInCDR3().length() == rebaseTo.getVRangeInCDR3().length()) {
//            VMutationsWithoutNDN = originalRoot.getVMutationsWithoutNDN();
//        } else {
//            VMutationsWithoutNDN = originalRoot.getVMutationsWithoutNDN().stream()
//                    .flatMap(mutations -> {
//                        Range intersection = mutations.getSequence1Range().intersection(new Range(0, rebaseTo.getVRangeInCDR3().getUpper()));
//                        if (intersection == null) {
//                            return Stream.empty();
//                        } else {
//                            boolean includeLastInserts;
//                            if (intersection.getUpper() == mutations.getSequence1Range().getUpper()) {
//                                includeLastInserts = mutations.isIncludeLastMutations();
//                            } else {
//                                includeLastInserts = true;
//                            }
//                            return Stream.of(new MutationsWithRange(
//                                    mutations.getSequence1(),
//                                    mutations.getMutations(),
//                                    intersection,
//                                    true,
//                                    includeLastInserts
//                            ));
//                        }
//                    })
//                    .collect(Collectors.toList());
//        }
//        List<MutationsWithRange> JMutationsWithoutNDN;
//        if (originalRootInfo.getJRangeInCDR3().length() < rebaseTo.getJRangeInCDR3().length()) {
//            JMutationsWithoutNDN = new ArrayList<>(originalRoot.getJMutationsWithoutNDN());
//            Range difference = new Range(rebaseTo.getJRangeInCDR3().getLower(), originalRootInfo.getJRangeInCDR3().getLower());
//
//            Mutations<NucleotideSequence> absoluteMutations = Aligner.alignGlobal(
//                    AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
//                    JSequence1,
//                    originalKnownNDN,
//                    difference.getLower(),
//                    difference.length(),
//                    originalKnownNDN.size() - difference.length(),
//                    difference.length()
//            ).getAbsoluteMutations();
//            JMutationsWithoutNDN.add(0, new MutationsWithRange(
//                    JSequence1,
//                    absoluteMutations,
//                    difference,
//                    true,
//                    true
//            ));
//        } else if (originalRootInfo.getJRangeInCDR3().length() == rebaseTo.getJRangeInCDR3().length()) {
//            JMutationsWithoutNDN = originalRoot.getJMutationsWithoutNDN();
//        } else {
//            JMutationsWithoutNDN = originalRoot.getJMutationsWithoutNDN().stream()
//                    .flatMap(mutations -> {
//                        Range intersection = mutations.getSequence1Range().intersection(new Range(rebaseTo.getJRangeInCDR3().getLower(), JSequence1.size()));
//                        if (intersection == null) {
//                            return Stream.empty();
//                        } else {
//                            boolean includeLastInserts;
//                            if (intersection.getUpper() == mutations.getSequence1Range().getUpper()) {
//                                includeLastInserts = mutations.isIncludeLastMutations();
//                            } else {
//                                includeLastInserts = true;
//                            }
//                            return Stream.of(new MutationsWithRange(
//                                    mutations.getSequence1(),
//                                    mutations.getMutations(),
//                                    intersection,
//                                    true,
//                                    includeLastInserts
//                            ));
//                        }
//                    })
//                    .collect(Collectors.toList());
//        }
//
//        SequenceBuilder<NucleotideSequence> knownNDNBuilder = NucleotideSequence.ALPHABET.createBuilder();
//        if (originalRootInfo.getVRangeInCDR3().length() > rebaseTo.getVRangeInCDR3().length()) {
//            Range rangeToAdd = new Range(rebaseTo.getVRangeInCDR3().getUpper(), originalRootInfo.getVRangeInCDR3().getUpper());
//            originalRoot.getVMutationsWithoutNDN().stream()
//                    .map(mutations -> {
//                        Range intersection = mutations.getSequence1Range().intersection(rangeToAdd);
//                        if (intersection == null) {
//                            return Optional.<NucleotideSequence>empty();
//                        } else {
//                            return Optional.of(
//                                    new MutationsWithRange(
//                                            mutations.getSequence1(),
//                                            mutations.getMutations(),
//                                            intersection,
//                                            true,
//                                            true
//                                    ).buildSequence()
//                            );
//                        }
//                    })
//                    .flatMap(Java9Util::stream)
//                    .forEach(knownNDNBuilder::append);
//        }
//
//        knownNDNBuilder.append(originalKnownNDN.getRange(
//                Math.max(0, rebaseTo.getVRangeInCDR3().length() - originalRootInfo.getVRangeInCDR3().length()),
//                originalKnownNDN.size() - Math.max(0, rebaseTo.getJRangeInCDR3().length() - originalRootInfo.getJRangeInCDR3().length())
//        ));
//
//        if (originalRootInfo.getJRangeInCDR3().length() > rebaseTo.getJRangeInCDR3().length()) {
//            Range rangeToAdd = new Range(originalRootInfo.getJRangeInCDR3().getLower(), rebaseTo.getJRangeInCDR3().getLower());
//            originalRoot.getJMutationsWithoutNDN().stream()
//                    .map(mutations -> {
//                        Range intersection = mutations.getSequence1Range().intersection(rangeToAdd);
//                        if (intersection == null) {
//                            return Optional.<NucleotideSequence>empty();
//                        } else {
//                            return Optional.of(
//                                    new MutationsWithRange(
//                                            mutations.getSequence1(),
//                                            mutations.getMutations(),
//                                            intersection,
//                                            true,
//                                            true
//                                    ).buildSequence()
//                            );
//                        }
//                    })
//                    .flatMap(Java9Util::stream)
//                    .forEach(knownNDNBuilder::append);
//        }
//
//        NucleotideSequence rebasedKnownNDN = knownNDNBuilder
//                .createAndDestroy();
//        MutationsDescription result = new MutationsDescription(
//                VMutationsWithoutNDN,
//                NDNMutations(rebaseTo.getReconstructedNDN(), rebasedKnownNDN),
//                JMutationsWithoutNDN
//        );
//        //TODO remove after testing on more data
////        if (result.getVMutationsWithoutNDN().stream().mapToInt(it -> it.getSequence1Range().getUpper()).max().getAsInt() != rebaseTo.getVRangeInCDR3().getUpper()) {
////            throw new IllegalArgumentException();
////        }
////        if (result.getJMutationsWithoutNDN().stream().mapToInt(it -> it.getSequence1Range().getLower()).min().getAsInt() != rebaseTo.getJRangeInCDR3().getLower()) {
////            throw new IllegalArgumentException();
////        }
////        AncestorInfo resultAncestorInfo = ancestorInfoBuilder.buildAncestorInfo(result);
////        AncestorInfo originalAncestorInfo = ancestorInfoBuilder.buildAncestorInfo(originalRoot);
////        if (!resultAncestorInfo.getSequence().equals(originalAncestorInfo.getSequence())) {
////            throw new IllegalArgumentException();
////        }
////        if (!resultAncestorInfo.getSequence().getRange(resultAncestorInfo.getCDR3Begin(), resultAncestorInfo.getCDR3End())
////                .equals(originalAncestorInfo.getSequence().getRange(originalAncestorInfo.getCDR3Begin(), originalAncestorInfo.getCDR3End()))) {
////            throw new IllegalArgumentException();
////        }
//        return result;
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
