package com.milaboratory.mixcr.assembler.preclone;

import com.milaboratory.mixcr.assembler.AlignmentsProvider;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.util.OutputPortWithProgress;
import io.repseq.core.GeneFeature;

import java.util.concurrent.atomic.AtomicLong;

public interface PreCloneReader extends AutoCloseable {
    /** Creates streamed pre-clone reader. */
    OutputPortWithProgress<PreClone> readPreClones();

    /**
     * Creates streamed alignments reader.
     * Must output at least one alignment assigned for each of the pre-clones returned by a pre-clone reader.
     * Alignments must be ordered the same way as pre-clones.
     * Must output all unassigned alignments before all assigned.
     */
    OutputPortWithProgress<VDJCAlignments> readAlignments();

    /** Total number of reads (not alignments). Used for reporting. */
    long getTotalNumberOfReads();

    /**
     * Returns a PreCloneReader view of a given VDJCAlignmentsReader representing a set of pre-clones that are formed as
     * a one-to-one image of alignments that completely covers provided set of gene features
     */
    static PreCloneReader fromAlignments(AlignmentsProvider alignmentsReader, GeneFeature[] geneFeatures) {
        return new PreCloneReader() {
            private boolean alignmentPredicate(VDJCAlignments al) {
                for (GeneFeature geneFeature : geneFeatures)
                    if (!al.isAvailable(geneFeature))
                        return false;
                return true;
            }

            @Override
            public OutputPortWithProgress<PreClone> readPreClones() {
                //noinspection resource
                OutputPortWithProgress<VDJCAlignments> alignmentReader = readAlignments();
                return new OutputPortWithProgress<PreClone>() {
                    @Override
                    public long currentIndex() {
                        return alignmentReader.currentIndex();
                    }

                    @Override
                    public PreClone take() {
                        VDJCAlignments al;
                        //noinspection StatementWithEmptyBody
                        while ((al = alignmentReader.take()) != null && al.getCloneIndex() == -1) ;
                        if (al == null)
                            return null;
                        return PreClone.fromAlignment(al.getCloneIndex(), al, geneFeatures);
                    }

                    @Override
                    public void close() {
                        alignmentReader.close();
                    }

                    @Override
                    public double getProgress() {
                        return alignmentReader.getProgress();
                    }

                    @Override
                    public boolean isFinished() {
                        return alignmentReader.isFinished();
                    }
                };
            }

            @Override
            public OutputPortWithProgress<VDJCAlignments> readAlignments() {
                Object sync = new Object();
                AtomicLong idGenerator = new AtomicLong();
                // noinspection resource
                OutputPortWithProgress<VDJCAlignments> reader = alignmentsReader.readAlignments();
                return new OutputPortWithProgress<VDJCAlignments>() {
                    @Override
                    public long currentIndex() {
                        return reader.currentIndex();
                    }

                    @Override
                    public VDJCAlignments take() {
                        synchronized (sync) {
                            VDJCAlignments al = reader.take();
                            if (al == null)
                                return null;


                            if(!alignmentPredicate(al))
                                al = al.withCloneIndexAndMappingType(idGenerator.getAndIncrement(), (byte) 0)
                                        .setAlignmentsIndex(al.getAlignmentsIndex());

                            return al;
                        }
                    }

                    @Override
                    public void close() {
                        reader.close();
                    }

                    @Override
                    public double getProgress() {
                        return reader.getProgress();
                    }

                    @Override
                    public boolean isFinished() {
                        return reader.isFinished();
                    }
                };
            }

            @Override
            public long getTotalNumberOfReads() {
                return alignmentsReader.getNumberOfReads();
            }

            @Override
            public void close() {
                alignmentsReader.close();
            }
        };
    }
}
