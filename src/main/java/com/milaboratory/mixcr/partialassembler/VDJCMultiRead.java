package com.milaboratory.mixcr.partialassembler;

import com.milaboratory.core.io.sequence.MultiRead;
import com.milaboratory.core.io.sequence.SingleRead;
import com.milaboratory.mixcr.reference.GeneType;

import java.util.EnumSet;

/**
 * @author Dmitry Bolotin
 * @author Stanislav Poslavsky
 */
public class VDJCMultiRead extends MultiRead {
    final EnumSet<GeneType>[] expectedGeneTypes;

    public VDJCMultiRead(SingleRead[] data, EnumSet<GeneType>[] expectedGeneTypes) {
        super(data);
        this.expectedGeneTypes = expectedGeneTypes;
    }
}
