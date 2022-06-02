package com.milaboratory.mixcr.assembler.preclone;

import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.util.OutputPortWithProgress;
import io.repseq.core.GeneFeature;

import java.util.concurrent.atomic.AtomicLong;

public interface PreCloneReader {
    /** Creates streamed pre-clone reader. */
    OutputPortWithProgress<PreClone> readPreClones();

    /**
     * Creates streamed alignments reader.
     * Must output at least one alignment assigned for each of the pre-clones returned by a pre-clone reader.
     * Alignments must be ordered the same way as pre-clones.
     * Must output all unassigned alignments before all assigned.
     */
    OutputPortWithProgress<VDJCAlignments> readAlignments();

    static PreCloneReader fromAlignments(VDJCAlignmentsReader alignmentsReader, GeneFeature[] geneFeatures) {
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
                    public void close() {
                        alignmentReader.close();
                    }

                    @Override
                    public PreClone take() {
                        VDJCAlignments al = alignmentReader.take();
                        if (al == null)
                            return null;
                        return PreClone.fromAlignment(al.getAlignmentsIndex(), al, geneFeatures);
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
                VDJCAlignmentsReader.SecondaryReader reader = alignmentsReader.createRawSecondaryReader();
                return new OutputPortWithProgress<VDJCAlignments>() {
                    @Override
                    public long currentIndex() {
                        return reader.currentIndex();
                    }

                    @Override
                    public void close() {
                        reader.close();
                    }

                    @Override
                    public VDJCAlignments take() {
                        synchronized (sync) {
                            VDJCAlignments al;
                            //noinspection StatementWithEmptyBody
                            while ((al = reader.take()) != null && !alignmentPredicate(al)) ;
                            if (al == null)
                                return null;
                            long id = idGenerator.getAndIncrement();
                            return al.withCloneIndexAndMappingType(id, (byte) 0).setAlignmentsIndex(id);
                        }
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
        };
    }
}
