package com.milaboratory.mixcr.cli;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPortCloseable;
import cc.redberry.pipe.util.CountingOutputPort;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.mixcr.export.AirrVDJCObjectWrapper;
import com.milaboratory.mixcr.export.FieldExtractor;
import com.milaboratory.util.CanReportProgress;
import com.milaboratory.util.SmartProgressReporter;
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
import static io.repseq.core.GeneFeature.*;
import static io.repseq.core.GeneType.*;
import static io.repseq.core.ReferencePoint.*;
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

    @SuppressWarnings("UnnecessaryLocalVariable")
    private List<FieldExtractor<AirrVDJCObjectWrapper>> CommonExtractors() {
        ComplexReferencePoint vnpEnd = new Leftmost(VEndTrimmed, VEnd);
        ComplexReferencePoint dnpBegin = new Rightmost(DBegin, DBeginTrimmed);
        ComplexReferencePoint dnpEnd = new Leftmost(DEnd, DEndTrimmed);
        ComplexReferencePoint jnpBegin = new Rightmost(JBegin, JBeginTrimmed);

        ComplexReferencePoint np1Begin = vnpEnd;
        ComplexReferencePoint np1End = new Leftmost(dnpBegin, jnpBegin);

        ComplexReferencePoint np2Begin = dnpEnd;
        ComplexReferencePoint np2End = jnpBegin;

        List<FieldExtractor<AirrVDJCObjectWrapper>> ret = new ArrayList<>(Arrays.asList(
                new Sequence(targetId),
                new RevComp(),
                new Productive(),

                new VDJCCalls(Variable),
                new VDJCCalls(Diversity),
                new VDJCCalls(Joining),
                new VDJCCalls(Constant),

                new SequenceAlignment(targetId),
                new GermlineAlignment(targetId),

                new CompleteVDJ(targetId),

                new NFeature(CDR3, "junction"),
                new AAFeature(CDR3, "junction_aa"),

                new NFeature(targetId, np1Begin, np1End, "np1"),
                new NFeature(targetId, np2Begin, np2End, "np2"),

                new NFeature(CDR1, "cdr1"),
                new AAFeature(CDR1, "cdr1_aa"),

                new NFeature(CDR2, "cdr2"),
                new AAFeature(CDR2, "cdr2_aa"),

                new NFeature(ShortCDR3, "cdr3"),
                new AAFeature(ShortCDR3, "cdr3_aa"),

                new NFeature(FR1, "fwr1"),
                new AAFeature(FR1, "fwr1_aa"),

                new NFeature(FR2, "fwr2"),
                new AAFeature(FR2, "fwr2_aa"),

                new NFeature(FR3, "fwr3"),
                new AAFeature(FR3, "fwr3_aa"),

                new NFeature(FR4, "fwr4"),
                new AAFeature(FR4, "fwr4_aa"),

                new AlignmentScoring(targetId, Variable),
                new AlignmentCigar(targetId, Variable),

                new AlignmentScoring(targetId, Diversity),
                new AlignmentCigar(targetId, Diversity),

                new AlignmentScoring(targetId, Joining),
                new AlignmentCigar(targetId, Joining),

                new AlignmentScoring(targetId, Constant),
                new AlignmentCigar(targetId, Constant),

                new NFeatureLength(CDR3, "junction_length"),
                new NFeatureLength(targetId, np1Begin, np1End, "np1_length"),
                new NFeatureLength(targetId, np2Begin, np2End, "np2_length")
        ));

        for (GeneType gt : VDJC_REFERENCE)
            for (boolean start : new boolean[]{true, false})
                for (boolean germline : new boolean[]{true, false})
                    ret.add(new SequenceAlignmentBoundary(targetId, gt, start, germline));

        for (GeneType gt : VDJC_REFERENCE)
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
        CountingOutputPort cPort;
        CanReportProgress progressReporter = null;

        switch (getType()) {
            case MAGIC_CLNA:
                extractors = CloneExtractors();
                ClnAReader clnaReader = new ClnAReader(inPath, libraryRegistry, 4);
                //noinspection unchecked,rawtypes
                cPort = new CountingOutputPort<>((OutputPortCloseable) clnaReader.readClones());
                port = cPort;
                closeable = clnaReader;
                progressReporter = SmartProgressReporter.extractProgress(cPort, clnaReader.numberOfClones());
                break;
            case MAGIC_CLNS:
                extractors = CloneExtractors();
                ClnsReader clnsReader = new ClnsReader(inPath, libraryRegistry);

                // I know, still writing airr is much slower...
                int maxCount = 0;
                try (OutputPortCloseable<Clone> p = clnsReader.readClones()) {
                    for (Clone ignore : CUtils.it(p))
                        ++maxCount;
                }

                //noinspection unchecked,rawtypes
                cPort = new CountingOutputPort<>((OutputPortCloseable) clnsReader.readClones());
                port = cPort;
                closeable = clnsReader;
                progressReporter = SmartProgressReporter.extractProgress(cPort, maxCount);
                break;
            case MAGIC_VDJC:
                extractors = AlignmentsExtractors();
                VDJCAlignmentsReader alignmentsReader = new VDJCAlignmentsReader(inPath, libraryRegistry);
                //noinspection unchecked,rawtypes
                port = (OutputPortCloseable) alignmentsReader;
                closeable = alignmentsReader;
                progressReporter = alignmentsReader;
                break;
            default:
                throwValidationException("Unexpected file type.");
                return;
        }

        SmartProgressReporter.startProgressReport("Exporting to AIRR format", progressReporter);

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
