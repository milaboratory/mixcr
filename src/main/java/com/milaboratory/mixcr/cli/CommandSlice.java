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
import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.util.FlatteningOutputPort;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.mixcr.util.Concurrency;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.set.hash.TLongHashSet;
import io.repseq.core.VDJCLibraryRegistry;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static com.milaboratory.mixcr.cli.CommandSlice.SLICE_COMMAND_NAME;
import static com.milaboratory.util.TempFileManager.smartTempDestination;

@Command(name = SLICE_COMMAND_NAME,
        sortOptions = true,
        separator = " ",
        description = "Slice ClnA file.")
public class CommandSlice extends MiXCRCommand {
    static final String SLICE_COMMAND_NAME = "slice";

    @Parameters(description = "data.[vdjca|clns|clna]", index = "0")
    public String in;

    @Parameters(description = "data_sliced", index = "1")
    public String out;

    @Option(description = "List of read (for .vdjca) / clone (for .clns/.clna) ids to export.",
            names = {"-i", "--id"})
    public List<Long> ids = new ArrayList<>();

    @Override
    protected List<String> getInputFiles() {
        return Collections.singletonList(in);
    }

    @Override
    protected List<String> getOutputFiles() {
        return Collections.singletonList(out);
    }

    @Override
    public void run0() throws Exception {
        Collections.sort(ids);

        switch (IOUtil.extractFileType(Paths.get(in))) {
            case VDJCA:
                sliceVDJCA();
                break;
            case CLNS:
                throwValidationException("Operation is not yet supported for Clns files.");
                break;
            case CLNA:
                sliceClnA();
                break;
            default:
                throwValidationException("Not supported file type.");
        }
    }

    void sliceVDJCA() throws Exception {
        TLongHashSet set = new TLongHashSet(ids);

        try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(in);
             VDJCAlignmentsWriter writer = new VDJCAlignmentsWriter(out)) {
            writer.header(reader);
            for (VDJCAlignments alignments : CUtils.it(reader)) {
                if (set.removeAll(alignments.getReadIds()))
                    writer.write(alignments);
                if (set.isEmpty())
                    break;
            }
            writer.writeFooter(reader.reports(), null);
        }
    }

    void sliceClnA() throws Exception {
        try (ClnAReader reader = new ClnAReader(in, VDJCLibraryRegistry.getDefault(), Concurrency.noMoreThan(4));
             ClnAWriter writer = new ClnAWriter(out,
                     smartTempDestination(out, "", false))) {

            // Getting full clone set
            CloneSet cloneSet = reader.readCloneSet();

            // old clone id -> new clone id
            TIntIntHashMap idMapping = new TIntIntHashMap();

            long newNumberOfAlignments = 0;

            // Creating new cloneset
            List<Clone> clones = new ArrayList<>();
            int i = 0;
            List<OutputPort<VDJCAlignments>> allAlignmentsList = new ArrayList<>();
            for (Long cloneId_ : ids) {
                int cloneId = (int) ((long) cloneId_);
                newNumberOfAlignments += reader.numberOfAlignmentsInClone(cloneId);
                Clone clone = cloneSet.get(cloneId);
                idMapping.put(clone.getId(), i);
                clones.add(clone.setId(i).resetParentCloneSet());
                OutputPort<VDJCAlignments> als = reader.readAlignmentsOfClone(cloneId);
                final int ii = i;
                allAlignmentsList.add(() -> {
                    VDJCAlignments al = als.take();
                    if (al == null)
                        return null;
                    return al.withCloneIndex(ii);
                });
                i++;
            }

            CloneSet newCloneSet = new CloneSet(clones, cloneSet.getUsedGenes(), cloneSet.getInfo(), cloneSet.getOrdering());

            OutputPort<VDJCAlignments> allAlignmentsPortRaw = new FlatteningOutputPort<>(CUtils.asOutputPort(allAlignmentsList));
            AtomicLong idGen = new AtomicLong();
            OutputPort<VDJCAlignments> allAlignmentsPort = () -> {
                VDJCAlignments al = allAlignmentsPortRaw.take();
                if (al == null)
                    return null;
                return al.setAlignmentsIndex(idGen.getAndIncrement());
            };

            writer.writeClones(newCloneSet);

            writer.collateAlignments(allAlignmentsPort, newNumberOfAlignments);

            writer.writeFooter(reader.reports(), null);
            writer.writeAlignmentsAndIndex();
        }
    }
}
