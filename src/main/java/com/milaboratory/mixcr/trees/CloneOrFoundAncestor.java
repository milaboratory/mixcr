package com.milaboratory.mixcr.trees;

import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.mutations.MutationsUtil;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.TranslationParameters;
import io.repseq.core.GeneType;

import java.math.BigDecimal;
import java.util.function.Function;

public abstract class CloneOrFoundAncestor {
    private final int id;
    private final MutationsDescription fromGermlineToThis;
    private final MutationsDescription fromGermlineToReconstructedRoot;
    private final MutationsDescription fromReconstructedRootToThis;
    private final MutationsDescription fromGermlineToParent;
    private final MutationsDescription fromParentToThis;
    private final BigDecimal distanceFromReconstructedRoot;
    private final BigDecimal distanceFromGermline;

    protected CloneOrFoundAncestor(int id, MutationsDescription fromGermlineToThis, MutationsDescription fromGermlineToReconstructedRoot, MutationsDescription fromGermlineToParent, BigDecimal distanceFromReconstructedRoot, BigDecimal distanceFromGermline) {
        this.id = id;
        this.fromGermlineToThis = fromGermlineToThis;
        this.fromGermlineToReconstructedRoot = fromGermlineToReconstructedRoot;
        this.fromGermlineToParent = fromGermlineToParent;
        if (fromGermlineToParent != null) {
            this.fromReconstructedRootToThis = MutationsUtils.mutationsBetween(fromGermlineToReconstructedRoot, fromGermlineToThis);
            this.fromParentToThis = MutationsUtils.mutationsBetween(fromGermlineToParent, fromGermlineToThis);
        } else {
            this.fromReconstructedRootToThis = null;
            this.fromParentToThis = null;
        }
        this.distanceFromReconstructedRoot = distanceFromReconstructedRoot;
        this.distanceFromGermline = distanceFromGermline;
    }

    public int getId() {
        return id;
    }

    public NucleotideSequence getCDR3() {
        return fromGermlineToThis.getVMutationsInCDR3WithoutNDN().buildSequence()
                .concatenate(fromGermlineToThis.getKnownNDN().buildSequence())
                .concatenate(fromGermlineToThis.getJMutationsInCDR3WithoutNDN().buildSequence());
    }

    public Mutations<NucleotideSequence> CDR3_VMutations(Base base) {
        return getMutations(base, MutationsDescription::getVMutationsInCDR3WithoutNDN);
    }

    public MutationsUtil.MutationNt2AADescriptor[] CDR3_AA_VMutations(Base base) {
        return getAAMutations(base, MutationsDescription::getVMutationsInCDR3WithoutNDN, null, TranslationParameters.FromLeftWithIncompleteCodon);
    }

    public Mutations<NucleotideSequence> CDR3_JMutations(Base base) {
        return getMutations(base, MutationsDescription::getJMutationsInCDR3WithoutNDN);
    }

    public MutationsUtil.MutationNt2AADescriptor[] CDR3_AA_JMutations(Base base) {
        return getAAMutations(base, MutationsDescription::getJMutationsInCDR3WithoutNDN, null, TranslationParameters.FromRightWithIncompleteCodon);
    }


    public Mutations<NucleotideSequence> CDR3_NDNMutations(Base base) {
        return getMutations(base, MutationsDescription::getKnownNDN);
    }

    private Mutations<NucleotideSequence> getMutations(Base base, Function<MutationsDescription, MutationsWithRange> supplier) {
        switch (base) {
            case FromGermline:
                return supplier.apply(fromGermlineToThis).mutationsForRange();
            case FromParent:
                if (fromGermlineToParent == null) {
                    return null;
                }
                return supplier.apply(fromGermlineToParent).mutationsForRange().invert().combineWith(supplier.apply(fromGermlineToThis).mutationsForRange());
            case FromReconstructedRoot:
                if (fromGermlineToParent == null) {
                    return null;
                }
                return supplier.apply(fromGermlineToReconstructedRoot).mutationsForRange().invert().combineWith(supplier.apply(fromGermlineToThis).mutationsForRange());
            default:
                throw new IllegalArgumentException();
        }
    }

    private MutationsUtil.MutationNt2AADescriptor[] getAAMutations(Base base, Function<MutationsDescription, MutationsWithRange> supplier, Integer nucleotidesLeft, TranslationParameters translationParameters) {
        MutationsDescription subject = fromBaseToThis(base);
        if (subject == null) {
            return null;
        }
        var mutations = supplier.apply(subject);
        if (nucleotidesLeft != null && mutations.buildSequence().size() % 3 != nucleotidesLeft) {
            return null;
        }
        return MutationsUtil.nt2aaDetailed(
                mutations.getSequence1().getRange(mutations.getRangeInfo().getRange()),
                mutations.mutationsForRange().move(-mutations.getRangeInfo().getRange().getLower()),
                translationParameters,
                3
        );
    }

    private MutationsDescription fromBaseToThis(Base base) {
        switch (base) {
            case FromGermline:
                return fromGermlineToThis;
            case FromParent:
                return fromParentToThis;
            case FromReconstructedRoot:
                return fromReconstructedRootToThis;
            default:
                throw new IllegalArgumentException();
        }
    }

    public abstract Integer getCloneId();

    public abstract Double getCount();

    public abstract String getCGeneName();

    public BigDecimal getDistanceFromReconstructedRoot() {
        return distanceFromReconstructedRoot;
    }

    public BigDecimal getDistanceFromGermline() {
        return distanceFromGermline;
    }

    public enum Base {
        FromGermline,
        FromParent,
        FromReconstructedRoot
    }

    static class CloneInfo extends CloneOrFoundAncestor {
        private final CloneWrapper cloneWrapper;

        CloneInfo(CloneWrapper cloneWrapper, int id, MutationsDescription mutationsFromRoot, MutationsDescription fromGermlineToReconstructedRoot, MutationsDescription fromGermlineToParent, BigDecimal distanceFromReconstructedRoot, BigDecimal distanceFromGermline) {
            super(id, mutationsFromRoot, fromGermlineToReconstructedRoot, fromGermlineToParent, distanceFromReconstructedRoot, distanceFromGermline);
            this.cloneWrapper = cloneWrapper;
        }

        @Override
        public Integer getCloneId() {
            return cloneWrapper.clone.getId();
        }

        @Override
        public Double getCount() {
            return cloneWrapper.clone.getCount();
        }

        @Override
        public String getCGeneName() {
            var bestHit = cloneWrapper.clone.getBestHit(GeneType.Constant);
            if (bestHit == null) {
                return null;
            }
            return bestHit.getGene().getName();
        }
    }

    static class AncestorInfo extends CloneOrFoundAncestor {
        AncestorInfo(int id, MutationsDescription mutationsFromRoot, MutationsDescription fromGermlineToReconstructedRoot, MutationsDescription fromGermlineToParent, BigDecimal distanceFromReconstructedRoot, BigDecimal distanceFromGermline) {
            super(id, mutationsFromRoot, fromGermlineToReconstructedRoot, fromGermlineToParent, distanceFromReconstructedRoot, distanceFromGermline);
        }

        @Override
        public Integer getCloneId() {
            return null;
        }

        @Override
        public Double getCount() {
            return null;
        }

        @Override
        public String getCGeneName() {
            return null;
        }
    }
}
