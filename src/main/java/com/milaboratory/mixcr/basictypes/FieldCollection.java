package com.milaboratory.mixcr.basictypes;

import com.milaboratory.util.HashFunctions;

import java.util.Comparator;
import java.util.function.ToIntFunction;

public final class FieldCollection {
    public static final Comparator<VDJCAlignments> VDJCACloneIdComparator =
            Comparator.comparing(VDJCAlignments::getCloneIndex);

    public static final ToIntFunction<VDJCAlignments> VDJCACloneIdHash =
            a -> HashFunctions.JenkinWang32shift(a.getCloneIndex());
}
