// package com.milaboratory.mixcr.cli;
//
// import cc.redberry.pipe.CUtils;
// import com.beust.jcommander.Parameter;
// import com.beust.jcommander.ParameterException;
// import com.beust.jcommander.Parameters;
// import com.milaboratory.cli.Action;
// import com.milaboratory.cli.ActionHelper;
// import com.milaboratory.cli.ActionParameters;
// import com.milaboratory.core.io.sequence.SequenceRead;
// import com.milaboratory.core.io.sequence.SequenceWriter;
// import com.milaboratory.core.io.sequence.fasta.FastaSequenceWriterWrapper;
// import com.milaboratory.core.io.sequence.fastq.PairedFastqWriter;
// import com.milaboratory.core.io.sequence.fastq.SingleFastqWriter;
// import com.milaboratory.mixcr.assembler.ReadToCloneMapping;
// import com.milaboratory.mixcr.basictypes.VDJCAlignments;
// import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
// import gnu.trove.map.hash.TIntObjectHashMap;
//
// import java.io.IOException;
// import java.util.Iterator;
// import java.util.List;
//
// /**
//  * @author Dmitry Bolotin
//  * @author Stanislav Poslavsky
//  */
// public final class ActionExportCloneReads implements Action {
//     private final ExtractCloneParameters parameters = new ExtractCloneParameters();
//
//     @Override
//     public String command() {
//         return "exportReadsForClones";
//     }
//
//     @Override
//     public ActionParameters params() {
//         return parameters;
//     }
//
//     @Override
//     public void go(ActionHelper helper) throws Exception {
//         if (!originalReadsPresent()) {
//             final String msg = "Error: original reads was not saved in the .vdjca file: re-run align with '-g' option.";
//             throw new IllegalArgumentException(msg);
//         }
//
//         try (AlignmentsToClonesMappingContainer index = AlignmentsToClonesMappingContainer.open(parameters.getIndexFile())) {
//             int[] cloneIds = parameters.getCloneIds();
//             if (cloneIds.length == 1) //byClones
//                 writeSingle(index, cloneIds[0]);
//             else
//                 writeMany(index, cloneIds);
//         }
//     }
//
//     private boolean originalReadsPresent() throws IOException {
//         try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(parameters.getAlignmentsFile())) {
//             VDJCAlignments test = reader.take();
//             return test == null || test.getOriginalReads() != null;
//         }
//     }
//
//     public void writeMany(AlignmentsToClonesMappingContainer index, int[] cloneIds)
//             throws Exception {
//         TIntObjectHashMap<SequenceWriter> writers = new TIntObjectHashMap<>(cloneIds.length);
//         for (int cloneId : cloneIds)
//             writers.put(cloneId, null);
//
//         try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(parameters.getAlignmentsFile())) {
//             Iterator<ReadToCloneMapping> mappingIterator = CUtils.it(index.createPortByClones()).iterator();
//             Iterator<VDJCAlignments> vdjcaIterator = new CUtils.OPIterator<>(reader);
//
//             for (; mappingIterator.hasNext() && vdjcaIterator.hasNext(); ) {
//                 //mapping = mappingIterator.next();
//                 ReadToCloneMapping mapping = mappingIterator.next();
//                 if (!writers.containsKey(mapping.getCloneIndex()))
//                     continue;
//                 VDJCAlignments vdjca = vdjcaIterator.next();
//                 while (vdjca.getAlignmentsIndex() < mapping.getAlignmentsId()
//                         && vdjcaIterator.hasNext())
//                     vdjca = vdjcaIterator.next();
//
//                 assert vdjca.getAlignmentsIndex() == mapping.getAlignmentsId();
//
//                 SequenceWriter writer = writers.get(mapping.getCloneIndex());
//                 List<SequenceRead> reads = vdjca.getOriginalReads();
//                 if (writer == null)
//                     writers.put(mapping.getCloneIndex(), writer = createWriter(reads.get(0).numberOfReads() == 2,
//                             createFileName(parameters.getOutputFileName(), mapping.getCloneIndex())));
//                 for (SequenceRead r : reads)
//                     writer.write(r);
//             }
//
//             for (SequenceWriter writer : writers.valueCollection())
//                 if (writer != null)
//                     writer.close();
//         }
//     }
//
//
//     public void writeSingle(AlignmentsToClonesMappingContainer index, int cloneId)
//             throws Exception {
//         try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(parameters.getAlignmentsFile())) {
//             Iterator<ReadToCloneMapping> mappingIterator = CUtils.it(index.createPortForClone(cloneId)).iterator();
//             Iterator<VDJCAlignments> vdjcaIterator = new CUtils.OPIterator<>(reader);
//
//             SequenceWriter writer = null;
//             for (; mappingIterator.hasNext() && vdjcaIterator.hasNext(); ) {
//                 //mapping = mappingIterator.next();
//                 VDJCAlignments vdjca = vdjcaIterator.next();
//                 ReadToCloneMapping mapping = mappingIterator.next();
//                 while (vdjca.getAlignmentsIndex() < mapping.getAlignmentsId()
//                         && vdjcaIterator.hasNext())
//                     vdjca = vdjcaIterator.next();
//
//                 if (vdjca.getAlignmentsIndex() != mapping.getAlignmentsId())
//                     continue;
//
//                 List<SequenceRead> reads = vdjca.getOriginalReads();
//                 if (writer == null)
//                     writer = createWriter(reads.get(0).numberOfReads() == 2,
//                             createFileName(parameters.getOutputFileName(), cloneId));
//
//                 for (SequenceRead read : reads)
//                     writer.write(read);
//             }
//             if (writer != null)
//                 writer.close();
//         }
//     }
//
//     private static String createFileName(String fileName, int id) {
//         if (fileName.contains(".fast"))
//             fileName = fileName.replace(".fast", "_cln" + id + ".fast");
//         else fileName += id;
//         return fileName;
//     }
//
//     private static SequenceWriter createWriter(boolean paired, String fileName)
//             throws Exception {
//         String[] split = fileName.split("\\.");
//         String ext = split[split.length - 1];
//         boolean gz = ext.equals("gz");
//         if (gz)
//             ext = split[split.length - 2];
//         if (ext.equals("fasta")) {
//             if (paired)
//                 throw new IllegalArgumentException("Fasta does not support paired reads.");
//             return new FastaSequenceWriterWrapper(fileName);
//         } else if (ext.equals("fastq")) {
//             if (paired) {
//                 String fileName1 = fileName.replace(".fastq", "_R1.fastq");
//                 String fileName2 = fileName.replace(".fastq", "_R2.fastq");
//                 return new PairedFastqWriter(fileName1, fileName2);
//             } else return new SingleFastqWriter(fileName);
//         }
//
//         if (paired)
//             return new PairedFastqWriter(fileName + "_R1.fastq.gz", fileName + "_R2.fastq.gz");
//         else return new SingleFastqWriter(fileName + ".fastq.gz");
//     }
//
//     @Parameters(commandDescription = "Export reads for particular clones.")
//     public static final class ExtractCloneParameters extends ActionParameters {
//         @Parameter(description = "mappingFile vdjcaFile clone1 [clone2] [clone3] ... output")
//         public List<String> parameters;
//
//         public String getIndexFile() {
//             return parameters.get(0);
//         }
//
//         public String getAlignmentsFile() {
//             return parameters.get(1);
//         }
//
//         public int[] getCloneIds() {
//             int[] cloneIds = new int[parameters.size() - 3];
//             for (int i = 2; i < parameters.size() - 1; ++i)
//                 cloneIds[i - 2] = Integer.valueOf(parameters.get(i));
//             return cloneIds;
//         }
//
//         public String getOutputFileName() {
//             return parameters.get(parameters.size() - 1);
//         }
//
//         @Override
//         public void validate() {
//             if (parameters.size() < 4)
//                 throw new ParameterException("Required parameters missing.");
//             super.validate();
//         }
//     }
// }
