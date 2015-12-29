/*
 * Copyright (c) 2014-2015, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
package com.milaboratory.mixcr.assembler;

import cc.redberry.pipe.OutputPortCloseable;
import cc.redberry.pipe.VoidProcessor;
import cc.redberry.primitives.Filter;
import com.milaboratory.core.Range;
import com.milaboratory.core.clustering.Cluster;
import com.milaboratory.core.clustering.Clustering;
import com.milaboratory.core.clustering.SequenceExtractor;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequenceQuality;
import com.milaboratory.core.tree.MutationGuide;
import com.milaboratory.core.tree.NeighborhoodIterator;
import com.milaboratory.core.tree.SequenceTreeMap;
import com.milaboratory.mixcr.basictypes.ClonalSequence;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.CloneSet;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.reference.Allele;
import com.milaboratory.mixcr.reference.AlleleId;
import com.milaboratory.mixcr.reference.GeneFeature;
import com.milaboratory.mixcr.reference.GeneType;
import com.milaboratory.util.CanReportProgress;
import com.milaboratory.util.Factory;
import com.milaboratory.util.RandomUtil;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.procedure.TObjectProcedure;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.milaboratory.mixcr.reference.GeneFeature.*;

public final class CloneAssembler implements CanReportProgress, AutoCloseable {
    final CloneAssemblerParameters parameters;
    // Accumulators and generators (atomics)
    final AtomicLong successfullyAssembledAlignments = new AtomicLong(),
            mappedAlignments = new AtomicLong(),
            droppedAlignments = new AtomicLong(),
            totalAlignments = new AtomicLong();
    final AtomicInteger cloneIndexGenerator = new AtomicInteger();
    // Storage
    private final ConcurrentHashMap<ClonalSequence, CloneAccumulator> clones = new ConcurrentHashMap<>();
    final AssemblerEventLogger globalLogger;
    private AssemblerEventLogger deferredAlignmentsLogger;
    private TIntIntHashMap idMapping;
    private volatile SequenceTreeMap<NucleotideSequence, ArrayList<CloneAccumulator>> mappingTree;
    private ArrayList<CloneAccumulator> clusteredClonesAccumulators;
    private volatile Clone[] realClones;
    private final HashMap<AlleleId, Allele> usedAlleles = new HashMap<>();
    volatile CanReportProgress progressReporter;
    private CloneAssemblerListener listener;
    volatile boolean deferredExists = false;

    public static final Factory<ArrayList<CloneAccumulator>> LIST_FACTORY = new Factory<ArrayList<CloneAccumulator>>() {
        @Override
        public ArrayList<CloneAccumulator> create() {
            return new ArrayList<>(1);
        }
    };

    public CloneAssembler(CloneAssemblerParameters parameters, boolean logAssemblerEvents, Collection<Allele> alleles) {
        this.parameters = parameters.clone();
        if (!logAssemblerEvents && !parameters.isMappingEnabled())
            globalLogger = null;
        else
            globalLogger = new AssemblerEventLogger();
        for (Allele allele : alleles)
            usedAlleles.put(allele.getId(), allele);
    }

    /* Initial Assembly Events */

    void onNewCloneCreated(CloneAccumulator accumulator) {
        if (listener != null)
            listener.onNewCloneCreated(accumulator);
    }

    void onFailedToExtractTarget(VDJCAlignments alignments) {
        if (listener != null)
            listener.onFailedToExtractTarget(alignments);
    }

    void onTooManyLowQualityPoints(VDJCAlignments alignments) {
        if (listener != null)
            listener.onTooManyLowQualityPoints(alignments);
    }

    void onAlignmentDeferred(VDJCAlignments alignments) {
        deferredExists = true;
        if (listener != null)
            listener.onAlignmentDeferred(alignments);
    }

    void onAlignmentAddedToClone(VDJCAlignments alignments, CloneAccumulator accumulator) {
        if (listener != null)
            listener.onAlignmentAddedToClone(alignments, accumulator);
    }

    /* Mapping Events */

    void onNoCandidateFoundForDefferedAlignment(VDJCAlignments alignments) {
        if (listener != null)
            listener.onNoCandidateFoundForDeferredAlignment(alignments);
    }

    void onDefferedAlignmentMappedToClone(VDJCAlignments alignments, CloneAccumulator accumulator) {
        if (listener != null)
            listener.onDeferredAlignmentMappedToClone(alignments, accumulator);
    }

    /* Clustering Events */

    void onClustered(CloneAccumulator majorClone, CloneAccumulator minorClone) {
        if (listener != null)
            listener.onClustered(majorClone, minorClone);
    }

    public void setListener(CloneAssemblerListener listener) {
        this.listener = listener;
    }

    private ClonalSequence extractClonalSequence(VDJCAlignments alignments) {
        final NSequenceWithQuality[] targets = new NSequenceWithQuality[parameters.assemblingFeatures.length];
        int totalLengt = 0;
        for (int i = 0; i < targets.length; ++i)
            if ((targets[i] = alignments.getFeature(parameters.assemblingFeatures[i])) == null)
                return null;
            else
                totalLengt += targets[i].size();
        if (totalLengt < parameters.minimalClonalSequenceLength)
            return null;
        return new ClonalSequence(targets);
    }

    private Range[] extractNRegions(ClonalSequence clonalSequence, VDJCAlignments alignments) {
        int count = 0;
        boolean dFound;
        ArrayList<Range> result = new ArrayList<>();
        Range range;
        int offset = 0;
        for (int i = 0; i < parameters.assemblingFeatures.length; ++i) {
            GeneFeature assemblingFeature = parameters.assemblingFeatures[i];
            if (!assemblingFeature.contains(VDJunction) && !assemblingFeature.contains(DJJunction))
                continue;
            dFound = false;

            range = alignments.getRelativeRange(assemblingFeature, VDJunction);
            if (range != null) {
                result.add(range.move(offset));
                dFound = true;
            }

            range = alignments.getRelativeRange(assemblingFeature, DJJunction);
            if (range != null) {
                result.add(range.move(offset));
                dFound = true;
            }

            if (!dFound) {
                range = alignments.getRelativeRange(assemblingFeature, VJJunction);
                if (range != null)
                    result.add(range.move(offset));
            }

            offset += clonalSequence.get(i).size();
        }
        return result.toArray(new Range[result.size()]);
    }

    public VoidProcessor<VDJCAlignments> getInitialAssembler() {
        return new InitialAssembler();
    }

    public boolean beginMapping() {
        if (!parameters.isMappingEnabled())
            throw new IllegalStateException("No mapping is needed for this parameters.");
        if (deferredAlignmentsLogger != null)
            throw new IllegalStateException();
        globalLogger.end(totalAlignments.get());
        if (!deferredExists)
            return false;
        deferredAlignmentsLogger = new AssemblerEventLogger();
        mappingTree = new SequenceTreeMap<>(NucleotideSequence.ALPHABET);
        for (CloneAccumulator accumulator : clones.values())
            mappingTree.createIfAbsent(accumulator.getSequence().getConcatenated().getSequence(), LIST_FACTORY).add(accumulator);
        return true;
    }

    public Filter<VDJCAlignments> getDeferredAlignmentsFilter() {
        return new DeferredAlignmentsFilter();
    }

    public VoidProcessor<VDJCAlignments> getDeferredAlignmentsMapper() {
        if (mappingTree == null)
            throw new IllegalStateException("Mapping tree not yet created.");
        return new DeferredAlignmentsMapper();
    }

    public void endMapping() {
        this.mappingTree = null;
        this.deferredAlignmentsLogger.end();
    }

    @Override
    public double getProgress() {
        if (progressReporter == null)//case!
            return 0.0;
        return progressReporter.getProgress();
    }

    @Override
    public boolean isFinished() {
        if (progressReporter == null)//case!
            return false;
        return progressReporter.isFinished();
    }

    public void runClustering() {
        if (clusteredClonesAccumulators != null)
            throw new IllegalStateException("Already clustered.");
        @SuppressWarnings("unchecked")
        Clustering clustering = new Clustering(clones.values(),
                new SequenceExtractor<CloneAccumulator, NucleotideSequence>() {
                    @Override
                    public NucleotideSequence getSequence(CloneAccumulator object) {
                        return object.getSequence().getConcatenated().getSequence();
                    }
                }, new CloneClusteringStrategy(parameters.getCloneClusteringParameters()));
        this.progressReporter = clustering;
        List<Cluster<CloneAccumulator>> clusters = clustering.performClustering();
        clusteredClonesAccumulators = new ArrayList<>(clusters.size());
        idMapping = new TIntIntHashMap(clones.size());
        for (int i = 0; i < clusters.size(); ++i) {
            final Cluster<CloneAccumulator> cluster = clusters.get(i);
            final CloneAccumulator head = cluster.getHead();
            idMapping.put(head.getCloneIndex(), i);
            head.setCloneIndex(i);
            final int k = ~i;
            cluster.processAllChildren(new TObjectProcedure<Cluster<CloneAccumulator>>() {
                @Override
                public boolean execute(Cluster<CloneAccumulator> object) {
                    onClustered(head, object.getHead());
                    if (parameters.isAddReadsCountOnClustering())
                        head.count += object.getHead().count;
                    idMapping.put(object.getHead().getCloneIndex(), k);
                    return true;
                }
            });
            clusteredClonesAccumulators.add(head);
        }
        this.progressReporter = null;
    }

    public void buildClones() {
        ClonesBuilder builder = new ClonesBuilder();
        progressReporter = builder;
        builder.buildClones();
        this.progressReporter = null;
    }

    @Override
    public void close() {
        if (globalLogger != null)
            globalLogger.close();
        if (deferredAlignmentsLogger != null)
            deferredAlignmentsLogger.close();
    }

    public CloneSet getCloneSet() {
        EnumMap<GeneType, GeneFeature> features = new EnumMap<>(GeneType.class);
        for (GeneType geneType : GeneType.values()) {
            GeneFeature gf = parameters.cloneFactoryParameters.getFeatureToAlign(geneType);
            if (gf != null)
                features.put(geneType, gf);
        }
        return new CloneSet(Arrays.asList(realClones), usedAlleles.values(), features, parameters.getAssemblingFeatures());
    }

    public OutputPortCloseable<ReadToCloneMapping> getAssembledReadsPort() {
        return new AssembledReadsPort(globalLogger.createEventsPort(), deferredAlignmentsLogger == null ? null : deferredAlignmentsLogger.createEventsPort(), idMapping);
    }

    private int numberOfBadPoints(ClonalSequence clonalSequence) {
        int badPoints = 0;
        for (NSequenceWithQuality p : clonalSequence) {
            SequenceQuality q = p.getQuality();
            for (int i = q.size() - 1; i >= 0; --i)
                if (q.value(i) <= parameters.getBadQualityThreshold())
                    ++badPoints;
        }
        return badPoints;
    }

    private final class InitialAssembler implements VoidProcessor<VDJCAlignments> {
        private void log(AssemblerEvent event) {
            if (globalLogger != null)
                globalLogger.newEvent(event);
        }

        @Override
        public void process(VDJCAlignments input) {
            totalAlignments.incrementAndGet();
            final ClonalSequence target = extractClonalSequence(input);
            if (target == null) {
                log(new AssemblerEvent(input.getAlignmentsIndex(), input.getReadId(), AssemblerEvent.DROPPED));
                droppedAlignments.incrementAndGet();
                onFailedToExtractTarget(input);
                return;
            }
            //Calculating number of bad points
            int badPoints = numberOfBadPoints(target);

            if (badPoints > target.getConcatenated().size() * parameters.getMaxBadPointsPercent()) {
                // Too many bad points (this read has too low quality in the regions of interest)
                log(new AssemblerEvent(input.getAlignmentsIndex(), input.getReadId(), AssemblerEvent.DROPPED));
                droppedAlignments.incrementAndGet();
                onTooManyLowQualityPoints(input);
                return;
            } else if (badPoints > 0) {
                // Has some number of bad points but not greater then maxBadPointsToMap
                log(new AssemblerEvent(input.getAlignmentsIndex(), input.getReadId(), AssemblerEvent.DEFERRED));
                onAlignmentDeferred(input);
                return;
            }
            //Getting or creating accumulator from map
            CloneAccumulator accumulator = clones.get(target);
            if (accumulator == null) {
                //Creating accumulator
                CloneAccumulator temp = new CloneAccumulator(target, extractNRegions(target, input));
                //Trying to put this new clone to map
                accumulator = clones.putIfAbsent(target, temp);
                //Assign cloneIndex for the newly created clone only if it was successfully put into map
                if (accumulator == null) {
                    //Executed only once for newly created clone
                    accumulator = temp;
                    accumulator.setCloneIndex(cloneIndexGenerator.getAndIncrement());
                    onNewCloneCreated(accumulator);
                }
                //accumulator variable contains correct clone from map
            }
            //Logging assembler events for subsequent index creation and mapping filtering
            log(new AssemblerEvent(input.getAlignmentsIndex(), input.getReadId(), accumulator.getCloneIndex()));
            //Incrementing corresponding counter
            successfullyAssembledAlignments.incrementAndGet();
            onAlignmentAddedToClone(input, accumulator);
            accumulator.accumulate(target, input, false);
        }
    }

    private final class DeferredAlignmentsFilter implements Filter<VDJCAlignments> {
        final Iterator<AssemblerEvent> events = globalLogger.events().iterator();

        @Override
        public boolean accept(VDJCAlignments alignment) {
            if (!events.hasNext())
                throw new IllegalArgumentException("This filter can not be used in concurrent " +
                        "environment. Perform pre-filtering in a single thread.");
            AssemblerEvent event = events.next();
            if (alignment.getAlignmentsIndex() != event.alignmentsIndex)
                throw new IllegalArgumentException("This filter can not be used in concurrent " +
                        "environment. Perform pre-filtering in a single thread.");
            if (event.cloneIndex != AssemblerEvent.DEFERRED) {
                deferredAlignmentsLogger.newEvent(new AssemblerEvent(event.alignmentsIndex, event.readId, AssemblerEvent.DROPPED));
                return false;
            }
            return true;
        }
    }

    private final class DeferredAlignmentsMapper implements VoidProcessor<VDJCAlignments> {
        final AssemblerUtils.MappingThresholdCalculator thresholdCalculator = parameters.getThresholdCalculator();

        @Override
        public void process(VDJCAlignments input) {
            final ClonalSequence clonalSequence = extractClonalSequence(input);

            int badPoints = numberOfBadPoints(clonalSequence);
            int threshold = thresholdCalculator.getThreshold(badPoints);

            NeighborhoodIterator<NucleotideSequence, ArrayList<CloneAccumulator>> iterator =
                    mappingTree.getNeighborhoodIterator(clonalSequence.getConcatenated().getSequence(),
                            threshold, 0, 0, threshold,
                            new DeferredAlignmentsMapperGuide(clonalSequence.getConcatenated().getQuality(),
                                    parameters.getBadQualityThreshold()));

            ArrayList<CloneAccumulator> candidates = new ArrayList<>();
            ArrayList<CloneAccumulator> assembledClones;

            int minMismatches = -1;
            long count = 0;
            while ((assembledClones = iterator.next()) != null)
                for (CloneAccumulator accumulator : assembledClones)
                    // Version of isCompatible without mutations is used here because
                    // ony substitutions possible in this place
                    if (clonalSequence.isCompatible(accumulator.getSequence())) {
                        if (minMismatches == -1)
                            minMismatches = iterator.getMismatches();
                        else if (minMismatches < iterator.getMismatches())
                            break;
                        candidates.add(accumulator);
                        count += accumulator.count;
                    }

            if (candidates.isEmpty()) {
                deferredAlignmentsLogger.newEvent(new AssemblerEvent(input.getAlignmentsIndex(), input.getReadId(),
                        AssemblerEvent.DROPPED));
                droppedAlignments.incrementAndGet();
                onNoCandidateFoundForDefferedAlignment(input);
                return;
            }

            count = (count == 1 ? 1 : RandomUtil.getThreadLocalRandomData().nextLong(1, count));
            CloneAccumulator accumulator = null;
            for (CloneAccumulator acc : candidates)
                if ((count -= acc.count) <= 0)
                    accumulator = acc;

            assert accumulator != null;

            mappedAlignments.incrementAndGet();
            successfullyAssembledAlignments.incrementAndGet();
            deferredAlignmentsLogger.newEvent(new AssemblerEvent(input.getAlignmentsIndex(),
                    input.getReadId(), minMismatches == 0 ?
                    accumulator.getCloneIndex() : -4 - accumulator.getCloneIndex()));
            onDefferedAlignmentMappedToClone(input, accumulator);
            accumulator.accumulate(clonalSequence, input, minMismatches > 0);
        }
    }

    private static final class DeferredAlignmentsMapperGuide implements MutationGuide<NucleotideSequence> {
        final SequenceQuality quality;
        final byte badQuality;

        private DeferredAlignmentsMapperGuide(SequenceQuality quality, byte badQuality) {
            this.quality = quality;
            this.badQuality = badQuality;
        }

        @Override
        public boolean allowMutation(NucleotideSequence reference, int position,
                                     byte type, byte to) {
            return type == 0 && (quality.value(position) <= badQuality);
        }
    }

    private final class ClonesBuilder implements CanReportProgress {
        final int sourceSize;
        volatile int progress;

        private ClonesBuilder() {
            this.sourceSize = clusteredClonesAccumulators != null ? clusteredClonesAccumulators.size() : clones.size();
        }

        @Override
        public double getProgress() {
            return (1.0 * progress) / sourceSize;
        }

        @Override
        public boolean isFinished() {
            return progress == sourceSize;
        }

        void buildClones() {
            CloneFactory cloneFactory =
                    new CloneFactory(parameters.getCloneFactoryParameters(),
                            parameters.getAssemblingFeatures(), usedAlleles);
            Collection<CloneAccumulator> source;
            if (clusteredClonesAccumulators != null)
                source = clusteredClonesAccumulators;
            else {
                idMapping = new TIntIntHashMap();
                //sort clones by count (if not yet sorted by clustering)
                CloneAccumulator[] sourceArray = clones.values().toArray(new CloneAccumulator[clones.size()]);
                Arrays.sort(sourceArray, new Comparator<CloneAccumulator>() {
                    @Override
                    public int compare(CloneAccumulator o1, CloneAccumulator o2) {
                        return Long.compare(o2.count, o1.count);
                    }
                });
                for (int i = 0; i < sourceArray.length; i++) {
                    idMapping.put(sourceArray[i].getCloneIndex(), i);
                    sourceArray[i].setCloneIndex(i);
                }
                source = Arrays.asList(sourceArray);
            }
            realClones = new Clone[source.size()];
            int i = 0;
            Iterator<CloneAccumulator> iterator = source.iterator();
            while (iterator.hasNext()) {
                CloneAccumulator accumulator = iterator.next();
                int cloneIndex = accumulator.getCloneIndex();
                assert realClones[cloneIndex] == null;
                realClones[cloneIndex] = cloneFactory.create(cloneIndex, accumulator);
                this.progress = ++i;
            }
        }
    }
}
