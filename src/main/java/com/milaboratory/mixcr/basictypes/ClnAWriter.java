/*
 * Copyright (c) 2014-2017, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
 * commercial purposes should contact the Inventors using one of the following
 * email addresses: chudakovdm@mail.ru, chudakovdm@gmail.com
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
package com.milaboratory.mixcr.basictypes;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.OutputPortCloseable;
import cc.redberry.pipe.util.CountingOutputPort;
import com.milaboratory.mixcr.util.MiXCRVersionInfo;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.primitivio.PipeDataInputReader;
import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.PrimitivO;
import com.milaboratory.util.CanReportProgressAndStage;
import com.milaboratory.util.ObjectSerializer;
import com.milaboratory.util.Sorter;
import gnu.trove.list.array.TLongArrayList;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGene;
import org.apache.commons.io.output.CountingOutputStream;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Writer for CLNA file format.
 *
 * Usage: 1. Constructor (opens the output file, buffered) 2. writeClones() 3. sortAlignments() 4.
 * writeAlignmentsAndIndex() 5. close()
 */
public final class ClnAWriter implements AutoCloseable, CanReportProgressAndStage {
    static final String MAGIC_V1 = "MiXCR.CLNA.V01";
    static final String MAGIC = MAGIC_V1;
    static final int MAGIC_LENGTH = MAGIC.length();

    /**
     * Will be used for alignments pre-sorting
     */
    private final File tempFile;
    /**
     * Used to read current position in output file
     */
    private final CountingOutputStream outputStream;
    private final PrimitivO output;
    /**
     * Counter OP used to report progress during stage 2
     */
    private volatile CountingOutputPort<VDJCAlignments> toSorter;
    private volatile int numberOfClones = -1, numberOfClonesWritten = 0;
    private volatile OutputPortCloseable<VDJCAlignments> sortedAlignments = null;
    private volatile long numberOfAlignments = -1, numberOfAlignmentsWritten = 0;
    private volatile boolean clonesBlockFinished = false, finished = false;

    public ClnAWriter(String fileName) throws IOException {
        this(new File(fileName));
    }

    public ClnAWriter(File file) throws IOException {
        this.tempFile = new File(file.getAbsolutePath() + ".presorted");
        this.outputStream = new CountingOutputStream(new BufferedOutputStream(
                new FileOutputStream(file), 131072));
        this.outputStream.write(MAGIC.getBytes(StandardCharsets.US_ASCII));
        this.output = new PrimitivO(this.outputStream);
    }

    private long positionOfFirstClone = -1;

    private List<VDJCGene> usedGenes = null;
    private HasFeatureToAlign featureToAlign = null;

    /**
     * Step 1
     */
    public synchronized void writeClones(CloneSet cloneSet, VDJCAlignerParameters alignerParameters) {
        // Checking state
        if (clonesBlockFinished)
            throw new IllegalArgumentException("Clone block was already written.");

        // Saving VDJC gene list
        this.usedGenes = cloneSet.getUsedGenes();

        // Saving features to align
        this.featureToAlign = new CloneSetIO.GT2GFAdapter(cloneSet.alignedFeatures);

        // Writing number of clones ahead of any other content to make it available
        // in known file position (MAGIC_LENGTH)
        output.writeInt(cloneSet.size());

        // Writing version information
        output.writeUTF(MiXCRVersionInfo.get()
                .getVersionString(MiXCRVersionInfo.OutputType.ToFile));

        // Writing aligner parameters
        output.writeObject(alignerParameters);

        // Saving assembling features
        GeneFeature[] assemblingFeatures = cloneSet.getAssemblingFeatures();
        output.writeObject(assemblingFeatures);

        // Writing aligned gene features for each gene type
        IO.writeGT2GFMap(output, cloneSet.alignedFeatures);

        // These GeneFeature objects and corresponding nucleotide sequences from all
        // genes in analysis will be added to the set of known references of PrimitivO object
        // so that they will be serialized as 1-2 byte reference records (see PrimitivIO implementation)

        // During deserialization, the same procedure (in the same order) will be applied to
        // the PrimitivI object, so that correct singleton objects (GeneFeature objects and sequences) will be
        // deserialized from reference records
        IOUtil.writeAndRegisterGeneReferences(output, usedGenes, featureToAlign);

        // Saving stream position of the first clone object
        // this value will be written to the end of the file
        positionOfFirstClone = outputStream.getByteCount();

        // Saving number of clones
        numberOfClones = cloneSet.size();

        // Writing clones
        for (Clone clone : cloneSet) {
            output.writeObject(clone);
            ++numberOfClonesWritten;
        }

        // Setting flag telling other methods that clones block was written successfully
        clonesBlockFinished = true;
    }

    /**
     * Step 2
     */
    public synchronized void sortAlignments(OutputPort<VDJCAlignments> alignments,
                                            long numberOfAlignments) throws IOException {
        // Checking state
        if (!clonesBlockFinished)
            throw new IllegalStateException("Write clones before writing alignments.");
        if (sortedAlignments != null)
            throw new IllegalStateException("Alignments are already sorted.");

        // Saving number of alignments
        this.numberOfAlignments = numberOfAlignments;

        // Dirty heuristic to optimize trade-off between memory usage and number of random access places in file
        // to read from
        int chunkSize = (int) Math.min(Math.max(16384, numberOfAlignments / 8), 1048576);

        // Sorting alignments by cloneId and then by mapping type (core alignments will be written before all others)
        // and saving sorting output port
        sortedAlignments = Sorter.sort(
                toSorter = new CountingOutputPort<>(alignments),
                (o1, o2) -> {
                    int i = Integer.compare(o1.cloneIndex, o2.cloneIndex);
                    if (i != 0)
                        return i;
                    return Byte.compare(o1.mappingType, o2.mappingType);
                },
                chunkSize,
                new VDJCAlignmentsSerializer(usedGenes, featureToAlign),
                tempFile);
    }

    public static final class VDJCAlignmentsSerializer implements ObjectSerializer<VDJCAlignments> {
        private final List<VDJCGene> genes;
        private final HasFeatureToAlign featureToAlign;

        public VDJCAlignmentsSerializer(List<VDJCGene> genes, HasFeatureToAlign featureToAlign) {
            this.genes = genes;
            this.featureToAlign = featureToAlign;
        }

        @Override
        public void write(Collection<VDJCAlignments> data, OutputStream stream) {
            PrimitivO primitivO = new PrimitivO(stream);
            // Initializing PrimitivO object (see big comment block in writeClones(...) method
            IOUtil.registerGeneReferences(primitivO, genes, featureToAlign);
            for (VDJCAlignments alignments : data) {
                // Checking that alignments has the same alignedFeature as was in cloneSet
                assert Arrays.stream(GeneType.values())
                        .allMatch(gt -> Optional
                                .ofNullable(alignments.getBestHit(gt))
                                .map(VDJCHit::getAlignedFeature)
                                .map(f -> f.equals(featureToAlign.getFeatureToAlign(gt)))
                                .orElse(true));
                // Writing alignment
                primitivO.writeObject(alignments);
            }
            // Writing null in the end of the stream to detect end of block during deserialization
            primitivO.writeObject(null);
        }

        @Override
        public OutputPort<VDJCAlignments> read(InputStream stream) {
            PrimitivI primitivI = new PrimitivI(stream);
            IOUtil.registerGeneReferences(primitivI, genes, featureToAlign);
            // Will end on null object, that was added to the stream
            return new PipeDataInputReader<>(VDJCAlignments.class, primitivI);
        }
    }

    /**
     * Step 3
     */
    public synchronized void writeAlignmentsAndIndex() {
        // Checking state
        if (sortedAlignments == null)
            throw new IllegalStateException("Call sortAlignments before this method.");
        if (finished)
            throw new IllegalStateException("Writer already closed.");

        // Indices that will be written below all alignments
        TLongArrayList aBlockOffset = new TLongArrayList();
        TLongArrayList aBlockCount = new TLongArrayList();

        // Position of alignments with cloneIndex = -1 (not aligned alignments)
        aBlockOffset.add(outputStream.getByteCount());

        long previousAlsCount = 0;
        int currentCloneIndex = -1;

        // Writing alignments and writing indices
        for (VDJCAlignments alignments : CUtils.it(sortedAlignments)) {
            if (currentCloneIndex != alignments.cloneIndex) {
                ++currentCloneIndex;
                if (currentCloneIndex != alignments.cloneIndex)
                    throw new IllegalArgumentException("No alignments for clone number " + currentCloneIndex);
                if (alignments.cloneIndex >= numberOfClones)
                    throw new IllegalArgumentException("Out of range clone Index in alignment: " + currentCloneIndex);
                aBlockOffset.add(outputStream.getByteCount());
                aBlockCount.add(numberOfAlignmentsWritten - previousAlsCount);
                previousAlsCount = numberOfAlignmentsWritten;
            }
            output.writeObject(alignments);
            ++numberOfAlignmentsWritten;
        }
        // Closing sorted output port, this will delete presorted file
        sortedAlignments.close();
        // Writing count of alignments in the last block
        aBlockCount.add(numberOfAlignmentsWritten - previousAlsCount);

        // Writing position of last alignments block end
        aBlockOffset.add(outputStream.getByteCount());
        // To make counts index the same length as aBlockOffset
        aBlockCount.add(0);

        // Saving index offset in file to write in the end of stream
        long indexBeginOffset = outputStream.getByteCount();
        long previousValue = 0;

        // Writing both indices
        for (int i = 0; i < aBlockOffset.size(); i++) {
            long iValue = aBlockOffset.get(i);
            // Writing offset index using deltas to save space
            // (smaller values are represented by less number of bytes in VarLong representation)
            output.writeVarLong(iValue - previousValue);
            previousValue = iValue;

            output.writeVarLong(aBlockCount.get(i));
        }

        // Writing two key positions in a file
        // This values will be using during deserialization to find certain blocks
        output.writeLong(positionOfFirstClone);
        output.writeLong(indexBeginOffset);

        // Setting finished flag (will stop progress reporting)
        finished = true;
    }

    @Override
    public double getProgress() {
        if (!clonesBlockFinished)
            if (numberOfClones == -1)
                return Double.NaN;
            else
                return 1.0 * numberOfClonesWritten / numberOfClones;
        else if (sortedAlignments == null) {
            if (toSorter == null)
                return Double.NaN;
            else
                return 1.0 * toSorter.getCount() / numberOfAlignments;
        } else
            return 1.0 * numberOfAlignmentsWritten / numberOfAlignments;

    }

    @Override
    public String getStage() {
        if (!clonesBlockFinished)
            if (numberOfClones == -1)
                return "Initialization";
            else
                return "Writing clones";
        else if (sortedAlignments == null) {
            if (toSorter == null)
                return "Preparing for sorting";
            else
                return "Sorting alignments";
        } else
            return "Writing alignments";
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public void close() {
        finished = true;
        output.close();
    }
}
