/*
 * Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
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
