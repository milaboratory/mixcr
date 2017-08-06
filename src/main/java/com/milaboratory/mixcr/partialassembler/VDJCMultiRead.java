package com.milaboratory.mixcr.partialassembler;

import com.milaboratory.core.io.sequence.MultiRead;
import com.milaboratory.core.io.sequence.SingleRead;
import com.milaboratory.core.io.sequence.SingleReadImpl;

import java.util.List;

/**
 * @author Dmitry Bolotin
 * @author Stanislav Poslavsky
 */
public class VDJCMultiRead extends MultiRead {
    //final EnumSet<GeneType>[] expectedGeneTypes;

    public VDJCMultiRead(SingleRead... data) {
        super(data.clone());
    }

    @SuppressWarnings("unchecked")
    public VDJCMultiRead(long readId, List<AlignedTarget> targets) {
        super(extractReads(readId, targets));
        //this.expectedGeneTypes = new EnumSet[targets.size()];
        //for (int i = 0; i < expectedGeneTypes.length; i++)
        //    expectedGeneTypes[i] = targets.get(i).getExpectedGenes();
    }

    public static SingleReadImpl[] extractReads(long readId, List<AlignedTarget> targets) {
        final SingleReadImpl[] reads = new SingleReadImpl[targets.size()];
        for (int i = 0; i < reads.length; i++)
            reads[i] = new SingleReadImpl(readId, targets.get(i).getTarget(), targets.get(i).getDescription());
        return reads;
    }
}
