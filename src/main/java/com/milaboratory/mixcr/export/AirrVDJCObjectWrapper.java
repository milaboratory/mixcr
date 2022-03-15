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
