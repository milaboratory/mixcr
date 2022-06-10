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
package com.milaboratory.mixcr.export;

import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCObject;

public final class AirrVDJCObjectWrapper {
    public final VDJCObject object;
    private int bestTarget = -1;
    private int alignmentTarget = -2;
    private boolean alignmentVDJ = false;
    private AirrUtil.AirrAlignment alignment;

    public AirrVDJCObjectWrapper(VDJCObject object) {
        this.object = object;
    }

    public Clone asClone() {
        return (Clone) object;
    }

    public VDJCAlignments asAlignment() {
        return (VDJCAlignments) object;
    }

    public int getBestTarget() {
        if (bestTarget != -1)
            return bestTarget;
        bestTarget = AirrUtil.bestTarget(object);
        return bestTarget;
    }

    public AirrUtil.AirrAlignment getAirrAlignment(int target, boolean vdj) {
        if (alignmentTarget == target && alignmentVDJ == vdj)
            return alignment;
        alignmentTarget = target;
        alignmentVDJ = vdj;
        alignment = AirrUtil.calculateAirrAlignment(object, target, vdj);
        return alignment;
    }
}
