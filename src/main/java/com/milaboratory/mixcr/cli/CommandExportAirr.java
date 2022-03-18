package com.milaboratory.mixcr.cli;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPortCloseable;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.mixcr.export.AirrVDJCObjectWrapper;
import com.milaboratory.mixcr.export.FieldExtractor;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCLibraryRegistry;

import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.milaboratory.mixcr.basictypes.IOUtil.*;
import static com.milaboratory.mixcr.export.AirrColumns.*;
import static picocli.CommandLine.*;

@Command(name = "exportAirr",
        separator = " ",
        description = "Exports a clns, clna or vdjca file to Airr formatted tsv file.")
public class CommandExportAirr extends ACommandMiXCR {
    @Option(description = "Target id (use -1 to export from the target containing CDR3).",
            names = {"-t", "--target"})
    public int targetId = -1;

    @Parameters(index = "0", description = "input_file.[vdjca|clna|clns]")
    public String in;
    @Parameters(index = "1", description = "output.tsv")
    public String out;

    private IOUtil.MiXCRFileInfo info0 = null;

    public String getType() {
        if (info0 == null)
            info0 = (IOUtil.MiXCRFileInfo) fileInfoExtractorInstance.getFileInfo(in);
        return info0.fileType;
    }

    private List<FieldExtractor<AirrVDJCObjectWrapper>> CommonExtractors() {
        List<FieldExtractor<AirrVDJCObjectWrapper>> ret = new ArrayList<>(Arrays.asList(
                new Sequence(targetId),
                new RevComp(),
                new Productive(),

                new VDJCCalls(GeneType.Variable),
                new VDJCCalls(GeneType.Diversity),
                new VDJCCalls(GeneType.Joining),
                new VDJCCalls(GeneType.Constant),

                new SequenceAlignment(targetId),
                new GermlineAlignment(targetId),

                new CompleteVDJ(targetId),

                new NFeature(GeneFeature.CDR3, "junction"),
                new AAFeature(GeneFeature.CDR3, "junction_aa"),

                new NFeature(GeneFeature.CDR1, "cdr1"),
                new AAFeature(GeneFeature.CDR1, "cdr1_aa"),

                new NFeature(GeneFeature.CDR2, "cdr2"),
                new AAFeature(GeneFeature.CDR2, "cdr2_aa"),

                new NFeature(GeneFeature.ShortCDR3, "cdr3"),
                new AAFeature(GeneFeature.ShortCDR3, "cdr3_aa"),

                new NFeature(GeneFeature.FR1, "fwr1"),
                new AAFeature(GeneFeature.FR1, "fwr1_aa"),

                new NFeature(GeneFeature.FR2, "fwr2"),
                new AAFeature(GeneFeature.FR2, "fwr2_aa"),

                new NFeature(GeneFeature.FR3, "fwr3"),
                new AAFeature(GeneFeature.FR3, "fwr3_aa"),

                new NFeature(GeneFeature.FR4, "fwr4"),
                new AAFeature(GeneFeature.FR4, "fwr4_aa"),

                new AlignmentScoring(targetId, GeneType.Variable),
                new AlignmentCigar(targetId, GeneType.Variable),

                new AlignmentScoring(targetId, GeneType.Diversity),
                new AlignmentCigar(targetId, GeneType.Diversity),

                new AlignmentScoring(targetId, GeneType.Joining),
                new AlignmentCigar(targetId, GeneType.Joining),

                new AlignmentScoring(targetId, GeneType.Constant),
                new AlignmentCigar(targetId, GeneType.Constant)
        ));

        for (GeneType gt : GeneType.VDJC_REFERENCE)
            for (boolean start : new boolean[]{true, false})
                for (boolean germline : new boolean[]{true, false})
                    ret.add(new SequenceAlignmentBoundary(targetId, gt, start, germline));

        for (GeneType gt : GeneType.VDJC_REFERENCE)
            for (boolean start : new boolean[]{true, false})
                ret.add(new AirrAlignmentBoundary(targetId, gt, start));

        return ret;
    }

    private List<FieldExtractor<AirrVDJCObjectWrapper>> CloneExtractors() {
        List<FieldExtractor<AirrVDJCObjectWrapper>> ret = new ArrayList<>();
        ret.add(new CloneId());
        ret.addAll(CommonExtractors());
        ret.add(new CloneCount());
        return ret;
    }

    private List<FieldExtractor<AirrVDJCObjectWrapper>> AlignmentsExtractors() {
        List<FieldExtractor<AirrVDJCObjectWrapper>> ret = new ArrayList<>();
        ret.add(new AlignmentId());
        ret.addAll(CommonExtractors());
        return ret;
    }

    @Override
    public void run0() throws Exception {
        List<FieldExtractor<AirrVDJCObjectWrapper>> extractors;
        AutoCloseable closeable;
        OutputPortCloseable<VDJCObject> port;

        Path inPath = Paths.get(in);
        VDJCLibraryRegistry libraryRegistry = VDJCLibraryRegistry.getDefault();

        switch (getType()) {
            case MAGIC_CLNA:
                extractors = CloneExtractors();
                ClnAReader clnaReader = new ClnAReader(inPath, libraryRegistry, 4);
                //noinspection unchecked,rawtypes
                port = (OutputPortCloseable) clnaReader.readClones();
                closeable = clnaReader;
                break;
            case MAGIC_CLNS:
                extractors = CloneExtractors();
                ClnsReader clnsReader = new ClnsReader(inPath, libraryRegistry);
                //noinspection unchecked,rawtypes
                port = (OutputPortCloseable) clnsReader.readClones();
                closeable = clnsReader;
                break;
            case MAGIC_VDJC:
                extractors = AlignmentsExtractors();
                VDJCAlignmentsReader alignmentsReader = new VDJCAlignmentsReader(inPath, libraryRegistry);
                //noinspection unchecked,rawtypes
                port = (OutputPortCloseable) alignmentsReader;
                closeable = alignmentsReader;
                break;
            default:
                throwValidationException("Unexpected file type.");
                return;
        }

        try (PrintStream output = new PrintStream(out);
             AutoCloseable c = closeable; OutputPortCloseable<VDJCObject> p = port) {
            boolean first = true;

            for (FieldExtractor<AirrVDJCObjectWrapper> extractor : extractors) {
                if (!first)
                    output.print("\t");
                first = false;
                output.print(extractor.getHeader());
            }
            output.println();

            for (VDJCObject obj : CUtils.it(port)) {
                first = true;

                AirrVDJCObjectWrapper wrapper = new AirrVDJCObjectWrapper(obj);

                for (FieldExtractor<AirrVDJCObjectWrapper> extractor : extractors) {
                    if (!first)
                        output.print("\t");
                    first = false;
                    output.print(extractor.extractValue(wrapper));
                }
                output.println();
            }
        }
    }
}
