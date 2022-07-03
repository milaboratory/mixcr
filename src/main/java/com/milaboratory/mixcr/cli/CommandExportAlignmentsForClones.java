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
package com.milaboratory.mixcr.cli;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPortCloseable;
import com.milaboratory.mixcr.basictypes.ClnAReader;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter;
import com.milaboratory.mixcr.util.Concurrency;
import io.repseq.core.VDJCLibraryRegistry;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 */
@Command(name = "exportAlignmentsForClones",
        sortOptions = true,
        separator = " ",
        description = "Export alignments for particular clones from \"clones & alignments\" (*.clna) file.")
public class CommandExportAlignmentsForClones extends MiXCRCommand {
    static final String EXPORT_ALIGNMENTS_FOR_CLONES_COMMAND_NAME = "exportAlignmentsForClones";

    @Parameters(index = "0", description = "clones.clna")
    public String in;

    @Parameters(index = "1", description = "alignments.vdjca")
    public String out = null;

    @Option(names = "--id", description = "[cloneId1 [cloneId2 [cloneId3]]]", arity = "0..*")
    public List<Integer> ids = new ArrayList<>();

    @Override
    protected List<String> getInputFiles() {
        return Collections.singletonList(in);
    }

    @Override
    protected List<String> getOutputFiles() {
        return Collections.singletonList(out);
    }

    public int[] getCloneIds() {
        return ids.stream().mapToInt(Integer::intValue).sorted().toArray();
    }

    @Override
    public void run0() throws Exception {
        try (ClnAReader clna = new ClnAReader(in, VDJCLibraryRegistry.getDefault(), Concurrency.noMoreThan(4));
             VDJCAlignmentsWriter writer = new VDJCAlignmentsWriter(out)) {
            writer.header(clna.getAlignerParameters(), clna.getUsedGenes(), clna.getTagsInfo());

            long count = 0;
            if (getCloneIds().length == 0)
                for (VDJCAlignments al : CUtils.it(clna.readAllAlignments())) {
                    if (al.getCloneIndex() == -1)
                        continue;
                    writer.write(al);
                    ++count;
                }
            else
                for (int id : getCloneIds()) {
                    OutputPortCloseable<VDJCAlignments> reader = clna.readAlignmentsOfClone(id);
                    VDJCAlignments al;
                    while ((al = reader.take()) != null) {
                        writer.write(al);
                        ++count;
                    }
                }

            writer.setNumberOfProcessedReads(count);
        }
    }
}