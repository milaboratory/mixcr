/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 *
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 *
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
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
