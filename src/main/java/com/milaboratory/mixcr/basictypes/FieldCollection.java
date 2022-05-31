package com.milaboratory.mixcr.basictypes;

import com.milaboratory.mixcr.assembler.preclone.PreClone;
import com.milaboratory.util.HashFunctions;

import java.util.Comparator;
import java.util.function.ToIntFunction;

public final class FieldCollection {
    public static final Comparator<VDJCAlignments> VDJCACloneIdComparator =
            Comparator.comparing(VDJCAlignments::getCloneIndex);

    public static final ToIntFunction<VDJCAlignments> VDJCACloneIdHash =
            a -> HashFunctions.Wang64to32shift(a.getCloneIndex());

    public static final Comparator<PreClone> PreCloneIdComparator =
            Comparator.comparing(PreClone::getId);

    public static final ToIntFunction<PreClone> PreCloneIdHash =
            a -> HashFunctions.Wang64to32shift(a.getId());
}
