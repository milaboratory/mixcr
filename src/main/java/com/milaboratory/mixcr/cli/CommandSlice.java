/*
 * Copyright (c) 2014-2019, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
 * (here and after addressed as Inventors)
 * All Rights Reserved
 *
 * Permission to use, copy, modify and distribute any part of this program for
 * educational, research and non-profit purposes, by non-profit institutions
 * only, without fee, and without a written agreement is hereby granted,
 * provided that the above copyright notice, this paragraph and the following
 * three paragraphs appear in all copies.
 *
 * Those desiring to incorporate this work into commercial products or use for
 * commercial purposes should contact MiLaboratory LLC, which owns exclusive
 * rights for distribution of this program for commercial purposes, using the
 * following email address: licensing@milaboratory.com.
 *
 * IN NO EVENT SHALL THE INVENTORS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
 * SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
 * ARISING OUT OF THE USE OF THIS SOFTWARE, EVEN IF THE INVENTORS HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * THE SOFTWARE PROVIDED HEREIN IS ON AN "AS IS" BASIS, AND THE INVENTORS HAS
 * NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR
 * MODIFICATIONS. THE INVENTORS MAKES NO REPRESENTATIONS AND EXTENDS NO
 * WARRANTIES OF ANY KIND, EITHER IMPLIED OR EXPRESS, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY OR FITNESS FOR A
 * PARTICULAR PURPOSE, OR THAT THE USE OF THE SOFTWARE WILL NOT INFRINGE ANY
 * PATENT, TRADEMARK OR OTHER RIGHTS.
 */
package com.milaboratory.mixcr.cli;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.util.FlatteningOutputPort;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.milaboratory.cli.ActionConfiguration;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.mixcr.util.Concurrency;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.set.hash.TLongHashSet;
import io.repseq.core.VDJCLibraryRegistry;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static com.milaboratory.mixcr.basictypes.IOUtil.*;
import static com.milaboratory.mixcr.cli.CommandSlice.SLICE_COMMAND_NAME;
import static com.milaboratory.util.TempFileManager.smartTempDestination;

@Command(name = SLICE_COMMAND_NAME,
        sortOptions = true,
        separator = " ",
        description = "Slice ClnA file.")
public class CommandSlice extends ACommandWithSmartOverwriteWithSingleInputMiXCR {
    static final String SLICE_COMMAND_NAME = "slice";

    @Option(description = "List of read (for .vdjca) / clone (for .clns/.clna) ids to export.",
            names = {"-i", "--id"})
    public List<Long> ids = new ArrayList<>();

    @Override
    public ActionConfiguration getConfiguration() {
        return new SliceConfiguration(ids.stream().mapToLong(Long::longValue).toArray());
    }

    @Override
    public void run1() throws Exception {
        Collections.sort(ids);

        switch (getInputFileInfo().fileType) {
            case MAGIC_VDJC:
                sliceVDJCA();
                break;
            case MAGIC_CLNS:
                throwValidationException("Operation is not yet supported for Clns files.");
                break;
            case MAGIC_CLNA:
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
            writer.header(reader, getFullPipelineConfiguration(), reader.getTagsInfo());
            for (VDJCAlignments alignments : CUtils.it(reader)) {
                if (set.removeAll(alignments.getReadIds()))
                    writer.write(alignments);
                if (set.isEmpty())
                    break;
            }
        }
    }

    void sliceClnA() throws Exception {
        try (ClnAReader reader = new ClnAReader(in, VDJCLibraryRegistry.getDefault(), Concurrency.noMoreThan(4));
             ClnAWriter writer = new ClnAWriter(getFullPipelineConfiguration(), out,
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
                    return al.updateCloneIndex(ii);
                });
                i++;
            }

            CloneSet newCloneSet = new CloneSet(clones, cloneSet.getUsedGenes(), cloneSet.getAlignmentParameters(),
                    cloneSet.getAssemblerParameters(), cloneSet.getTagsInfo(), cloneSet.getOrdering());

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

            writer.writeAlignmentsAndIndex();
        }
    }

    @JsonAutoDetect(
            fieldVisibility = JsonAutoDetect.Visibility.ANY,
            isGetterVisibility = JsonAutoDetect.Visibility.NONE,
            getterVisibility = JsonAutoDetect.Visibility.NONE)
    @JsonTypeInfo(
            use = JsonTypeInfo.Id.CLASS,
            include = JsonTypeInfo.As.PROPERTY,
            property = "type")
    public static class SliceConfiguration implements ActionConfiguration {
        final long[] ids;

        @JsonCreator
        public SliceConfiguration(@JsonProperty("ids") long[] ids) {
            this.ids = ids;
            Arrays.sort(ids);
        }

        @Override
        public String actionName() {
            return SLICE_COMMAND_NAME;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SliceConfiguration that = (SliceConfiguration) o;
            return Arrays.equals(ids, that.ids);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(ids);
        }
    }
}
