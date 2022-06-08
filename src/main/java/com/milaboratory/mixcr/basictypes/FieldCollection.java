package com.milaboratory.mixcr.basictypes;

import com.milaboratory.mixcr.assembler.preclone.PreCloneImpl;
import com.milaboratory.util.HashFunctions;

import java.util.Comparator;
import java.util.function.ToIntFunction;

public final class FieldCollection {
    public static final Comparator<VDJCAlignments> VDJCACloneIdComparator =
            Comparator.comparing(VDJCAlignments::getCloneIndex);

    public static final ToIntFunction<VDJCAlignments> VDJCACloneIdHash =
            a -> HashFunctions.Wang64to32shift(a.getCloneIndex());

    public static final Comparator<PreCloneImpl> PreCloneIdComparator =
            Comparator.comparing(PreCloneImpl::getIndex);

    public static final ToIntFunction<PreCloneImpl> PreCloneIdHash =
            a -> HashFunctions.Wang64to32shift(a.getIndex());
}
