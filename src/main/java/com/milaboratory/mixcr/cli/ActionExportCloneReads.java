package com.milaboratory.mixcr.cli;

import cc.redberry.pipe.CUtils;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.milaboratory.cli.Action;
import com.milaboratory.cli.ActionHelper;
import com.milaboratory.cli.ActionParameters;
import com.milaboratory.core.io.sequence.PairedRead;
import com.milaboratory.core.io.sequence.SequenceRead;
import com.milaboratory.core.io.sequence.SequenceWriter;
import com.milaboratory.core.io.sequence.SingleReadImpl;
import com.milaboratory.core.io.sequence.fasta.FastaSequenceWriterWrapper;
import com.milaboratory.core.io.sequence.fastq.PairedFastqWriter;
import com.milaboratory.core.io.sequence.fastq.SingleFastqWriter;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.mixcr.assembler.ReadToCloneMapping;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.reference.LociLibraryManager;
import gnu.trove.map.hash.TIntObjectHashMap;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableSet;

/**
 * @author Dmitry Bolotin
 * @author Stanislav Poslavsky
 */
public final class ActionExportCloneReads implements Action {
    private final ExtractCloneParameters parameters = new ExtractCloneParameters();

    @Override
    public String command() {
        return "exportReadsForClones";
    }

    @Override
    public ActionParameters params() {
        return parameters;
    }

    @Override
    public void go(ActionHelper helper) throws Exception {
        if (!originalReadsPresent()) {
            final String msg = "Error: original reads was not saved in the .vdjca file: re-run align with '-g' option.";
            throw new IllegalArgumentException(msg);
        }

        DB db;
        try {
            db = DBMaker.newFileDB(new File(parameters.getMapDBFile()))
                    .transactionDisable()
                    .make();
        } catch (Exception | Error e) {
            final String msg = "Error: corrupted or malformed index file.";
            throw new IllegalArgumentException(msg, e);
        }

        int[] cloneIds = parameters.getCloneIds();
        if (cloneIds.length == 1) {//byClones
            NavigableSet<ReadToCloneMapping> byClones = db.getTreeSet(ActionAssemble.MAPDB_SORTED_BY_CLONE);
            writeSingle(byClones, cloneIds[0]);
        } else {
            NavigableSet<ReadToCloneMapping> byAls = db.getTreeSet(ActionAssemble.MAPDB_SORTED_BY_ALIGNMENT);
            writeMany(byAls, cloneIds);
        }

        db.close();
    }

    private boolean originalReadsPresent() throws IOException {
        try (VDJCAlignmentsReader reader
                     = new VDJCAlignmentsReader(parameters.getVDJCAFile(), LociLibraryManager.getDefault())) {
            VDJCAlignments test = reader.take();
            return test == null || test.getOriginalSequences() != null;
        }
    }

    public void writeMany(NavigableSet<ReadToCloneMapping> byAlignments, int[] clonIds)
            throws Exception {
        TIntObjectHashMap<SequenceWriter> writers = new TIntObjectHashMap<>(clonIds.length);
        for (int cloneId : clonIds)
            writers.put(cloneId, null);

        try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(parameters.getVDJCAFile(),
                LociLibraryManager.getDefault())) {

            Iterator<ReadToCloneMapping> mappingIterator = byAlignments.iterator();
            Iterator<VDJCAlignments> vdjcaIterator = new CUtils.OPIterator<>(reader);

            for (; mappingIterator.hasNext() && vdjcaIterator.hasNext(); ) {
                //mapping = mappingIterator.next();
                ReadToCloneMapping mapping = mappingIterator.next();
                if (!writers.containsKey(mapping.getCloneIndex()))
                    continue;
                VDJCAlignments vdjca = vdjcaIterator.next();
                while (vdjca.getAlignmentsIndex() < mapping.getAlignmentsId()
                        && vdjcaIterator.hasNext())
                    vdjca = vdjcaIterator.next();

                assert vdjca.getAlignmentsIndex() == mapping.getAlignmentsId();

                SequenceWriter writer = writers.get(mapping.getCloneIndex());
                if (writer == null)
                    writers.put(mapping.getCloneIndex(), writer = createWriter(vdjca.getOriginalSequences().length == 2,
                            createFileName(parameters.getOutputFileName(), mapping.getCloneIndex())));
                writer.write(createRead(vdjca.getOriginalSequences(), vdjca.getDescriptions()));
            }

            for (SequenceWriter writer : writers.valueCollection())
                if (writer != null)
                    writer.close();
        }
    }


    public void writeSingle(NavigableSet<ReadToCloneMapping> byClones, int cloneId)
            throws Exception {
        NavigableSet<ReadToCloneMapping> selected = byClones.subSet(
                new ReadToCloneMapping(0, 0, cloneId, false, false, false, false), true,
                new ReadToCloneMapping(Long.MAX_VALUE, 0, cloneId, false, false, false, false), true);

        if (selected.isEmpty())
            return;
        try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(parameters.getVDJCAFile(),
                LociLibraryManager.getDefault())) {

            Iterator<ReadToCloneMapping> mappingIterator = selected.iterator();
            Iterator<VDJCAlignments> vdjcaIterator = new CUtils.OPIterator<>(reader);

            SequenceWriter writer = null;
            for (; mappingIterator.hasNext() && vdjcaIterator.hasNext(); ) {
                //mapping = mappingIterator.next();
                VDJCAlignments vdjca = vdjcaIterator.next();
                ReadToCloneMapping mapping = mappingIterator.next();
                while (vdjca.getAlignmentsIndex() < mapping.getAlignmentsId()
                        && vdjcaIterator.hasNext())
                    vdjca = vdjcaIterator.next();

                if (vdjca.getAlignmentsIndex() != mapping.getAlignmentsId())
                    continue;

                if (writer == null)
                    writer = createWriter(vdjca.getOriginalSequences().length == 2,
                            createFileName(parameters.getOutputFileName(), cloneId));
                writer.write(createRead(vdjca.getOriginalSequences(), vdjca.getDescriptions()));
            }
            if (writer != null)
                writer.close();
        }
    }

    private static String createFileName(String fileName, int id) {
        if (fileName.contains(".fast"))
            fileName = fileName.replace(".fast", "_cln" + id + ".fast");
        else fileName += id;
        return fileName;
    }

    private static SequenceRead createRead(NSequenceWithQuality[] nseqs, String[] descr) {
        if (nseqs.length == 1)
            return new SingleReadImpl(-1, nseqs[0], descr[0]);
        else {
            String descr1, descr2;
            if (descr == null)
                descr1 = descr2 = "";
            else if (descr.length == 1)
                descr1 = descr2 = descr[0];
            else {
                descr1 = descr[0];
                descr2 = descr[1];
            }

            return new PairedRead(
                    new SingleReadImpl(-1, nseqs[0], descr1),
                    new SingleReadImpl(-1, nseqs[1], descr2));
        }
    }

    private static SequenceWriter createWriter(boolean paired, String fileName)
            throws Exception {
        String[] split = fileName.split("\\.");
        String ext = split[split.length - 1];
        boolean gz = ext.equals("gz");
        if (gz)
            ext = split[split.length - 2];
        if (ext.equals("fasta")) {
            if (paired)
                throw new IllegalArgumentException("Fasta does not support paired reads.");
            return new FastaSequenceWriterWrapper(fileName);
        } else if (ext.equals("fastq")) {
            if (paired) {
                String fileName1 = fileName.replace(".fastq", "_R1.fastq");
                String fileName2 = fileName.replace(".fastq", "_R2.fastq");
                return new PairedFastqWriter(fileName1, fileName2);
            } else return new SingleFastqWriter(fileName);
        }

        if (paired)
            return new PairedFastqWriter(fileName + "_R1.fastq.gz", fileName + "_R2.fastq.gz");
        else return new SingleFastqWriter(fileName + ".fastq.gz");
    }

    @Parameters(commandDescription = "Export reads for particular clones.")
    public static final class ExtractCloneParameters extends ActionParameters {
        @Parameter(description = "mappingFile vdjcaFile clone1 [clone2] [clone3] ... output")
        public List<String> parameters;

        public String getMapDBFile() {
            return parameters.get(0);
        }

        public String getVDJCAFile() {
            return parameters.get(1);
        }

        public int[] getCloneIds() {
            int[] cloneIds = new int[parameters.size() - 3];
            for (int i = 2; i < parameters.size() - 1; ++i)
                cloneIds[i - 2] = Integer.valueOf(parameters.get(i));
            return cloneIds;
        }

        public String getOutputFileName() {
            return parameters.get(parameters.size() - 1);
        }

        @Override
        public void validate() {
            if (parameters.size() < 4)
                throw new ParameterException("Required parameters missed.");
            super.validate();
        }
    }
}
