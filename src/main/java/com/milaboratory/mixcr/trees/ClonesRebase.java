package com.milaboratory.mixcr.trees;

import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.AffineGapAlignmentScoring;
import com.milaboratory.core.alignment.Aligner;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequenceBuilder;
import com.milaboratory.mixcr.util.Java9Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.milaboratory.core.mutations.Mutations.EMPTY_NUCLEOTIDE_MUTATIONS;

class ClonesRebase {
    private final NucleotideSequence VSequence1;
    private final NucleotideSequence JSequence1;

    ClonesRebase(NucleotideSequence vSequence1, NucleotideSequence jSequence1) {
        VSequence1 = vSequence1;
        JSequence1 = jSequence1;
    }

    CloneWithMutationsFromReconstructedRoot rebaseClone(RootInfo rootInfo, MutationsFromVJGermline mutationsFromVJGermline, CloneWrapper cloneWrapper) {
        Range NDNRangeInKnownNDN = NDNRangeInKnownNDN(mutationsFromVJGermline, rootInfo.getVRangeInCDR3(), rootInfo.getJRangeInCDR3());

        List<MutationsWithRange> VMutationsWithoutNDN = new ArrayList<>(mutationsFromVJGermline.getVMutationsWithoutNDN());
        Range VRange = new Range(mutationsFromVJGermline.getVRangeInCDR3().getLower(), rootInfo.getVRangeInCDR3().getUpper());
        if (!VRange.isEmpty()) {
            Range VMutationsWithinNDNRange = mutationsFromVJGermline.getKnownVMutationsWithinNDN().getSecond()
                    .intersection(VRange);
            VMutationsWithinNDNRange = VMutationsWithinNDNRange != null ? VMutationsWithinNDNRange
                    : new Range(mutationsFromVJGermline.getVRangeInCDR3().getUpper(), mutationsFromVJGermline.getVRangeInCDR3().getUpper());
            int lengthDelta = 0;
            if (!VMutationsWithinNDNRange.isEmpty()) {
                MutationsWithRange VMutationsToAdd = new MutationsWithRange(
                        VSequence1,
                        EMPTY_NUCLEOTIDE_MUTATIONS,
                        mutationsFromVJGermline.getKnownVMutationsWithinNDN().getFirst(),
                        VMutationsWithinNDNRange,
                        false,
                        true
                );
                VMutationsWithoutNDN.add(VMutationsToAdd);
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
                VMutationsWithoutNDN.add(new MutationsWithRange(
                        VSequence1,
                        EMPTY_NUCLEOTIDE_MUTATIONS,
                        absoluteMutations,
                        rangeToAlign,
                        true,
                        true
                ));
            }
            NDNRangeInKnownNDN = new Range(NDNRangeInKnownNDN.getLower() + lengthDelta, NDNRangeInKnownNDN.getUpper());
        }

        List<MutationsWithRange> JMutationsWithoutNDN = new ArrayList<>(mutationsFromVJGermline.getJMutationsWithoutNDN());
        Range JRange = new Range(rootInfo.getJRangeInCDR3().getLower(), mutationsFromVJGermline.getJRangeInCDR3().getLower());
        if (!JRange.isEmpty()) {
            Range JMutationsWithinNDNRange = mutationsFromVJGermline.getKnownJMutationsWithinNDN().getSecond()
                    .intersection(JRange);
            JMutationsWithinNDNRange = JMutationsWithinNDNRange != null ? JMutationsWithinNDNRange
                    : new Range(mutationsFromVJGermline.getJRangeInCDR3().getLower(), mutationsFromVJGermline.getJRangeInCDR3().getLower());
            int lengthDelta = 0;
            if (!JMutationsWithinNDNRange.isEmpty()) {
                MutationsWithRange JMutationsToAdd = new MutationsWithRange(
                        JSequence1,
                        EMPTY_NUCLEOTIDE_MUTATIONS,
                        mutationsFromVJGermline.getKnownJMutationsWithinNDN().getFirst(),
                        JMutationsWithinNDNRange,
                        true,
                        false
                );
                JMutationsWithoutNDN.add(0, JMutationsToAdd);
                lengthDelta += JMutationsToAdd.lengthDelta();
            }
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
                JMutationsWithoutNDN.add(0, new MutationsWithRange(
                        JSequence1,
                        EMPTY_NUCLEOTIDE_MUTATIONS,
                        absoluteMutations,
                        rangeToAlign,
                        true,
                        true
                ));
            }
            NDNRangeInKnownNDN = new Range(NDNRangeInKnownNDN.getLower(), NDNRangeInKnownNDN.getUpper() - lengthDelta);
        }
        MutationsDescription mutations = new MutationsDescription(
                VMutationsWithoutNDN,
                mutations(rootInfo.getReconstructedNDN(), mutationsFromVJGermline.getKnownNDN().getRange(NDNRangeInKnownNDN)),
                JMutationsWithoutNDN
        );
        return new CloneWithMutationsFromReconstructedRoot(mutations, mutationsFromVJGermline, cloneWrapper);
    }

    //TODO test with randomized test
    MutationsDescription rebaseMutations(
            MutationsDescription originalRoot,
            RootInfo originalRootInfo,
            RootInfo rebaseTo
    ) {
        NucleotideSequence originalKnownNDN = originalRoot.getKnownNDN().buildSequence();
        List<MutationsWithRange> VMutationsWithoutNDN;
        if (originalRootInfo.getVRangeInCDR3().length() < rebaseTo.getVRangeInCDR3().length()) {
            VMutationsWithoutNDN = new ArrayList<>(originalRoot.getVMutationsWithoutNDN());
            Range difference = new Range(originalRootInfo.getVRangeInCDR3().getUpper(), rebaseTo.getVRangeInCDR3().getUpper());

            Mutations<NucleotideSequence> absoluteMutations = Aligner.alignGlobal(
                    AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                    VSequence1,
                    originalKnownNDN,
                    difference.getLower(),
                    difference.length(),
                    0,
                    difference.length()
            ).getAbsoluteMutations();
            VMutationsWithoutNDN.add(new MutationsWithRange(
                    VSequence1,
                    EMPTY_NUCLEOTIDE_MUTATIONS,
                    absoluteMutations,
                    difference,
                    true, true
            ));
        } else if (originalRootInfo.getVRangeInCDR3().length() == rebaseTo.getVRangeInCDR3().length()) {
            VMutationsWithoutNDN = originalRoot.getVMutationsWithoutNDN();
        } else {
            VMutationsWithoutNDN = originalRoot.getVMutationsWithoutNDN().stream()
                    .flatMap(mutations -> {
                        Range intersection = mutations.getSequence1Range().intersection(new Range(0, rebaseTo.getVRangeInCDR3().getUpper()));
                        if (intersection == null) {
                            return Stream.empty();
                        } else {
                            boolean includeLastInserts;
                            if (intersection.getUpper() == mutations.getSequence1Range().getUpper()) {
                                includeLastInserts = mutations.isIncludeLastMutations();
                            } else {
                                includeLastInserts = true;
                            }
                            return Stream.of(new MutationsWithRange(
                                    mutations.getSequence1(),
                                    mutations.getFromBaseToParent(),
                                    mutations.getFromParentToThis(),
                                    intersection,
                                    true, includeLastInserts
                            ));
                        }
                    })
                    .collect(Collectors.toList());
        }
        List<MutationsWithRange> JMutationsWithoutNDN;
        if (originalRootInfo.getJRangeInCDR3().length() < rebaseTo.getJRangeInCDR3().length()) {
            JMutationsWithoutNDN = new ArrayList<>(originalRoot.getJMutationsWithoutNDN());
            Range difference = new Range(rebaseTo.getJRangeInCDR3().getLower(), originalRootInfo.getJRangeInCDR3().getLower());

            Mutations<NucleotideSequence> absoluteMutations = Aligner.alignGlobal(
                    AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                    JSequence1,
                    originalKnownNDN,
                    difference.getLower(),
                    difference.length(),
                    originalKnownNDN.size() - difference.length(),
                    difference.length()
            ).getAbsoluteMutations();
            JMutationsWithoutNDN.add(0, new MutationsWithRange(
                    JSequence1,
                    EMPTY_NUCLEOTIDE_MUTATIONS,
                    absoluteMutations,
                    difference,
                    true, true
            ));
        } else if (originalRootInfo.getJRangeInCDR3().length() == rebaseTo.getJRangeInCDR3().length()) {
            JMutationsWithoutNDN = originalRoot.getJMutationsWithoutNDN();
        } else {
            JMutationsWithoutNDN = originalRoot.getJMutationsWithoutNDN().stream()
                    .flatMap(mutations -> {
                        Range intersection = mutations.getSequence1Range().intersection(new Range(rebaseTo.getJRangeInCDR3().getLower(), JSequence1.size()));
                        if (intersection == null) {
                            return Stream.empty();
                        } else {
                            boolean includeLastInserts;
                            if (intersection.getUpper() == mutations.getSequence1Range().getUpper()) {
                                includeLastInserts = mutations.isIncludeLastMutations();
                            } else {
                                includeLastInserts = true;
                            }
                            return Stream.of(new MutationsWithRange(
                                    mutations.getSequence1(),
                                    mutations.getFromBaseToParent(),
                                    mutations.getFromParentToThis(),
                                    intersection,
                                    true, includeLastInserts
                            ));
                        }
                    })
                    .collect(Collectors.toList());
        }

        SequenceBuilder<NucleotideSequence> knownNDNBuilder = NucleotideSequence.ALPHABET.createBuilder();
        if (originalRootInfo.getVRangeInCDR3().length() > rebaseTo.getVRangeInCDR3().length()) {
            Range rangeToAdd = new Range(rebaseTo.getVRangeInCDR3().getUpper(), originalRootInfo.getVRangeInCDR3().getUpper());
            originalRoot.getVMutationsWithoutNDN().stream()
                    .map(mutations -> {
                        Range intersection = mutations.getSequence1Range().intersection(rangeToAdd);
                        if (intersection == null) {
                            return Optional.<NucleotideSequence>empty();
                        } else {
                            return Optional.of(
                                    new MutationsWithRange(
                                            mutations.getSequence1(),
                                            mutations.getFromBaseToParent(),
                                            mutations.getFromParentToThis(),
                                            intersection,
                                            true, true
                                    ).buildSequence()
                            );
                        }
                    })
                    .flatMap(Java9Util::stream)
                    .forEach(knownNDNBuilder::append);
        }

        knownNDNBuilder.append(originalKnownNDN.getRange(
                Math.max(0, rebaseTo.getVRangeInCDR3().length() - originalRootInfo.getVRangeInCDR3().length()),
                originalKnownNDN.size() - Math.max(0, rebaseTo.getJRangeInCDR3().length() - originalRootInfo.getJRangeInCDR3().length())
        ));

        if (originalRootInfo.getJRangeInCDR3().length() > rebaseTo.getJRangeInCDR3().length()) {
            Range rangeToAdd = new Range(originalRootInfo.getJRangeInCDR3().getLower(), rebaseTo.getJRangeInCDR3().getLower());
            originalRoot.getJMutationsWithoutNDN().stream()
                    .map(mutations -> {
                        Range intersection = mutations.getSequence1Range().intersection(rangeToAdd);
                        if (intersection == null) {
                            return Optional.<NucleotideSequence>empty();
                        } else {
                            return Optional.of(
                                    new MutationsWithRange(
                                            mutations.getSequence1(),
                                            mutations.getFromBaseToParent(),
                                            mutations.getFromParentToThis(),
                                            intersection,
                                            true, true
                                    ).buildSequence()
                            );
                        }
                    })
                    .flatMap(Java9Util::stream)
                    .forEach(knownNDNBuilder::append);
        }

        NucleotideSequence rebasedKnownNDN = knownNDNBuilder
                .createAndDestroy();
        MutationsDescription result = new MutationsDescription(
                VMutationsWithoutNDN,
                mutations(rebaseTo.getReconstructedNDN(), rebasedKnownNDN),
                JMutationsWithoutNDN
        );
        //TODO remove after testing on more data
//        if (result.getVMutationsWithoutNDN().stream().mapToInt(it -> it.getSequence1Range().getUpper()).max().getAsInt() != rebaseTo.getVRangeInCDR3().getUpper()) {
//            throw new IllegalArgumentException();
//        }
//        if (result.getJMutationsWithoutNDN().stream().mapToInt(it -> it.getSequence1Range().getLower()).min().getAsInt() != rebaseTo.getJRangeInCDR3().getLower()) {
//            throw new IllegalArgumentException();
//        }
//        AncestorInfo resultAncestorInfo = ancestorInfoBuilder.buildAncestorInfo(result);
//        AncestorInfo originalAncestorInfo = ancestorInfoBuilder.buildAncestorInfo(originalRoot);
//        if (!resultAncestorInfo.getSequence().equals(originalAncestorInfo.getSequence())) {
//            throw new IllegalArgumentException();
//        }
//        if (!resultAncestorInfo.getSequence().getRange(resultAncestorInfo.getCDR3Begin(), resultAncestorInfo.getCDR3End())
//                .equals(originalAncestorInfo.getSequence().getRange(originalAncestorInfo.getCDR3Begin(), originalAncestorInfo.getCDR3End()))) {
//            throw new IllegalArgumentException();
//        }
        return result;
    }


    static Range NDNRangeInKnownNDN(MutationsFromVJGermline mutations, Range VRangeInCDR3, Range JRangeInCDR3) {
        return new Range(
                VRangeInCDR3.length() - mutations.getVRangeInCDR3().length(),
                mutations.getKnownNDN().size() - (JRangeInCDR3.length() - mutations.getJRangeInCDR3().length())
        );
    }

    static MutationsWithRange mutations(NucleotideSequence first, NucleotideSequence second) {
        return new MutationsWithRange(
                first,
                EMPTY_NUCLEOTIDE_MUTATIONS,
                Aligner.alignGlobal(
                        AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                        first,
                        second
                ).getAbsoluteMutations(),
                new Range(0, first.size()),
                true,
                true
        );
    }
}
