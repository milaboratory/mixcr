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
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.mixcr.assembler.preclone.FilePreCloneReader;
import com.milaboratory.mixcr.assembler.preclone.PreClone;
import com.milaboratory.mixcr.assembler.preclone.PreCloneAssemblerParameters;
import com.milaboratory.mixcr.assembler.preclone.PreCloneAssemblerRunner;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.basictypes.tag.TagInfo;
import com.milaboratory.mixcr.basictypes.tag.TagTuple;
import com.milaboratory.mixcr.basictypes.tag.TagType;
import com.milaboratory.mixcr.basictypes.tag.TagValue;
import com.milaboratory.mixcr.util.OutputPortWithProgress;
import com.milaboratory.util.*;
import gnu.trove.iterator.TObjectDoubleIterator;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.*;

import static picocli.CommandLine.*;

@Command(name = "itestAssemblePreClones",
        separator = " ",
        hidden = true)
public class ITestCommandAssemblePreClones extends AbstractMiXCRCommand {
    @Parameters(arity = "4", description = "input_file output_file output_clones output_alignments")
    public List<String> files;

    @Option(description = "Overlap sequences on the cell level instead of UMIs for tagged data with molecular and cell barcodes",
            names = {"--cell-level"})
    public boolean cellLevel = false;

    @Option(description = "Use system temp folder for temporary files, the output folder will be used if this option is omitted.",
            names = {"--use-system-temp"})
    public boolean useSystemTemp = false;

    @Option(names = "-P", description = "Overrides default pre-clone assembler parameter values.")
    private Map<String, String> preCloneAssemblerOverrides = new HashMap<>();

    @Override
    protected List<String> getInputFiles() {
        return files.subList(0, 1);
    }

    @Override
    protected List<String> getOutputFiles() {
        return files.subList(1, 3);
    }

    @Override
    public void run0() throws Exception {
        PreCloneAssemblerParameters params = PreCloneAssemblerParameters.getDefaultParameters(cellLevel);

        if (!preCloneAssemblerOverrides.isEmpty()) {
            params = JsonOverrider.override(params,
                    PreCloneAssemblerParameters.class,
                    preCloneAssemblerOverrides);
            if (params == null)
                throwValidationException("Failed to override some pre-clone assembler parameters: " + preCloneAssemblerOverrides);
        }

        long totalAlignments;
        TempFileDest tmp = TempFileManager.smartTempDestination(files.get(1), "", useSystemTemp);
        int cdr3Hash = 0;
        try (VDJCAlignmentsReader alignmentsReader = new VDJCAlignmentsReader(files.get(0))) {
            totalAlignments = alignmentsReader.getNumberOfAlignments();
            PreCloneAssemblerRunner assemblerRunner = new PreCloneAssemblerRunner(
                    alignmentsReader,
                    cellLevel ? TagType.Cell : TagType.Molecule,
                    new GeneFeature[]{GeneFeature.CDR3},
                    params,
                    Paths.get(files.get(1)),
                    tmp);
            SmartProgressReporter.startProgressReport(assemblerRunner);
            assemblerRunner.run();
            assemblerRunner.getReport().buildReport().writeReport(ReportHelper.STDOUT);

            Set<TagTuple> tagTuples = new HashSet<>();
            TagTuple prevTagKey = null;
            for (VDJCAlignments al : CUtils.it(alignmentsReader.readAlignments())) {
                cdr3Hash += Objects.hashCode(al.getFeature(GeneFeature.CDR3));
                TagTuple tagKey = al.getTagCount().asKeyPrefixOrError(alignmentsReader.getTagsInfo().getSortingLevel());
                if (!tagKey.equals(prevTagKey)) {
                    if (!tagTuples.add(tagKey))
                        throwExecutionException("broken sorting: " + tagKey);
                    prevTagKey = tagKey;
                }
            }
        }

        try (FilePreCloneReader reader = new FilePreCloneReader(Paths.get(files.get(1)))) {
            long totalClones = reader.getNumberOfClones();

            // Checking and exporting alignments

            long numberOfAlignmentsCheck = 0;
            try (PrintStream ps = new PrintStream(new BufferedOutputStream(new FileOutputStream(files.get(2)), 1 << 20))) {
                ps.print("alignmentId\t");
                for (TagInfo ti : reader.getTagsInfo())
                    ps.print(ti.getName() + "\t");
                ps.println("readIndex\tcloneId\tcdr3\tcdr3_qual\tbestV\tbestJ");
                OutputPortWithProgress<VDJCAlignments> aReader = reader.readAlignments();
                SmartProgressReporter.startProgressReport("Exporting alignments", aReader);
                for (VDJCAlignments al : CUtils.it(aReader)) {
                    cdr3Hash -= Objects.hashCode(al.getFeature(GeneFeature.CDR3));
                    numberOfAlignmentsCheck++;

                    TObjectDoubleIterator<TagTuple> it = al.getTagCount().iterator();
                    while (it.hasNext()) {
                        it.advance();
                        ps.print(numberOfAlignmentsCheck + "\t");
                        for (TagValue tv : it.key())
                            ps.print(tv.toString() + "\t");
                        ps.print(al.getMinReadId() + "\t" +
                                al.getCloneIndex() + "\t");
                        NSequenceWithQuality cdr3 = al.getFeature(GeneFeature.CDR3);
                        if (cdr3 != null)
                            ps.print(cdr3.getSequence());
                        ps.print("\t");
                        if (cdr3 != null)
                            ps.print(cdr3.getQuality());
                        ps.print("\t");
                        if (al.getBestHit(GeneType.Variable) != null)
                            ps.print(al.getBestHit(GeneType.Variable).getGene().getName());
                        ps.print("\t");
                        if (al.getBestHit(GeneType.Joining) != null)
                            ps.print(al.getBestHit(GeneType.Joining).getGene().getName());
                        ps.println();
                    }
                }
            }

            if (cdr3Hash != 0)
                throwExecutionException("inconsistent alignment composition between initial file and pre-clone container");

            if (numberOfAlignmentsCheck != totalAlignments) {
                throwExecutionException("numberOfAlignmentsCheck != totalAlignments (" +
                        numberOfAlignmentsCheck + " != " + totalAlignments + ")");
            }

            for (VDJCAlignments al : CUtils.it(reader.readAssignedAlignments()))
                numberOfAlignmentsCheck--;

            for (VDJCAlignments al : CUtils.it(reader.readUnassignedAlignments()))
                numberOfAlignmentsCheck--;

            if (numberOfAlignmentsCheck != 0)
                throwExecutionException("numberOfAlignmentsCheck != 0 (" +
                        numberOfAlignmentsCheck + " != 0)");

            // Checking and exporting clones

            long numberOfClonesCheck = 0;
            try (PrintStream ps = new PrintStream(new BufferedOutputStream(new FileOutputStream(files.get(3)), 1 << 20))) {
                ps.print("cloneId\t");
                for (TagInfo ti : reader.getTagsInfo())
                    ps.print(ti.getName() + "\t");
                ps.println("count\tcount_full\tcdr3\tbestV\tbestJ");
                OutputPortWithProgress<PreClone> cReader = reader.readPreClones();
                SmartProgressReporter.startProgressReport("Exporting clones", cReader);
                for (PreClone c : CUtils.it(cReader)) {
                    TObjectDoubleIterator<TagTuple> it = c.getCoreTagCount().iterator();
                    while (it.hasNext()) {
                        it.advance();
                        // if (!tagTuples.add(it.key()))
                        //     throwExecutionException("duplicate clone tag tuple: " + it.key());
                        ps.print(c.getIndex() + "\t");
                        for (TagValue tv : it.key())
                            ps.print(tv.toString() + "\t");
                        ps.print(it.value() + "\t");
                        ps.print(c.getFullTagCount().get(it.key()) + "\t");
                        ps.println(c.getClonalSequence()[0].getSequence() + "\t" +
                                c.getBestGene(GeneType.Variable).getName() + "\t" +
                                c.getBestGene(GeneType.Joining).getName());
                    }
                    numberOfClonesCheck++;
                }
            }

            if (numberOfClonesCheck != totalClones)
                throwExecutionException("numberOfClonesCheck != totalClones (" +
                        numberOfClonesCheck + " != " + totalClones + ")");
        }
    }
}
