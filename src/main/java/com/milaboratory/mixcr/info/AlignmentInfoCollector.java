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
package com.milaboratory.mixcr.info;

import com.milaboratory.mixcr.basictypes.VDJCAlignments;

import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * Created by dbolotin on 04/08/15.
 */
public interface AlignmentInfoCollector {
    void writeResult(PrintStream writer);

    void put(VDJCAlignments alignments);

    void end();
}
