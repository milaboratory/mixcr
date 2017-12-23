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
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.util.CanReportProgress;
import com.milaboratory.util.Factory;
import com.milaboratory.util.HashFunctions;
import com.milaboratory.util.RandomUtil;
import gnu.trove.iterator.TObjectFloatIterator;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TObjectFloatHashMap;
import gnu.trove.procedure.TObjectProcedure;
import io.repseq.core.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static io.repseq.core.GeneFeature.*;

public final class CloneAssembler implements CanReportProgress, AutoCloseable {
    final CloneAssemblerParameters parameters;
    // Accumulators and generators (atomics)
    final AtomicLong successfullyAssembledAlignments = new AtomicLong(),
            mappedAlignments = new AtomicLong(),
            droppedAlignments = new AtomicLong(),
            totalAlignments = new AtomicLong();
    final AtomicInteger cloneIndexGenerator = new AtomicInteger();
    // Storage
    private final ConcurrentHashMap<ClonalSequence, CloneAccumulatorContainer> clones = new ConcurrentHashMap<>();
    private final List<CloneAccumulator> cloneList = new ArrayList<>();
    final AssemblerEventLogger globalLogger;
    private AssemblerEventLogger deferredAlignmentsLogger;
    private TIntIntHashMap idMapping;
    private volatile SequenceTreeMap<NucleotideSequence, ArrayList<CloneAccumulatorContainer>> mappingTree;
    private ArrayList<CloneAccumulator> clusteredClonesAccumulators;
    private volatile Clone[] realClones;
    private final HashMap<VDJCGeneId, VDJCGene> usedGenes = new HashMap<>();
    volatile CanReportProgress progressReporter;
    private CloneAssemblerListener listener;
    volatile boolean deferredExists = false;
    volatile boolean preClusteringDone = false;
    final TIntIntHashMap preClustered = new TIntIntHashMap();
    final EnumMap<GeneType, GeneFeature> featuresToAlign;

    public static final Factory<ArrayList<CloneAccumulatorContainer>> LIST_FACTORY = new Factory<ArrayList<CloneAccumulatorContainer>>() {
        @Override
        public ArrayList<CloneAccumulatorContainer> create() {
            return new ArrayList<>(1);
        }
    };

    public CloneAssembler(CloneAssemblerParameters parameters, boolean logAssemblerEvents, Collection<VDJCGene> genes, EnumMap<GeneType, GeneFeature> featuresToAlign) {
        if (!parameters.isComplete())
            throw new IllegalArgumentException("Not complete parameters");
        this.parameters = parameters.clone();
        this.featuresToAlign = featuresToAlign;
        if (!logAssemblerEvents && !parameters.isMappingEnabled())
            globalLogger = null;
        else
            globalLogger = new AssemblerEventLogger();
        for (VDJCGene gene : genes)
            usedGenes.put(gene.getId(), gene);
    }

    public CloneAssembler(CloneAssemblerParameters parameters, boolean logAssemblerEvents, Collection<VDJCGene> genes, VDJCAlignerParameters alignerParameters) {
        this(parameters.clone().updateFrom(alignerParameters), logAssemblerEvents, genes, alignerParameters.getFeaturesToAlignMap());
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

    void onDeferredAlignmentMappedToClone(VDJCAlignments alignments, CloneAccumulator accumulator) {
        if (listener != null)
            listener.onDeferredAlignmentMappedToClone(alignments, accumulator);
    }

    /* Clustering Events */

    void onPreClustered(CloneAccumulator majorClone, CloneAccumulator minorClone) {
        if (listener != null)
            listener.onPreClustered(majorClone, minorClone);
    }

    void onClustered(CloneAccumulator majorClone, CloneAccumulator minorClone) {
        if (listener != null)
            listener.onClustered(majorClone, minorClone, parameters.isAddReadsCountOnClustering());
    }

    /* Filtering events */

    void onCloneDropped(CloneAccumulator acc) {
        if (listener != null)
            listener.onCloneDropped(acc);
    }

    public void setListener(CloneAssemblerListener listener) {
        this.listener = listener;
    }

    private ClonalSequence extractClonalSequence(VDJCAlignments alignments) {
        final NSequenceWithQuality[] targets = new NSequenceWithQuality[parameters.assemblingFeatures.length];
        int totalLength = 0;
        for (int i = 0; i < targets.length; ++i)
            if ((targets[i] = alignments.getFeature(parameters.assemblingFeatures[i])) == null)
                return null;
            else
                totalLength += targets[i].size();
        if (totalLength < parameters.minimalClonalSequenceLength)
            return null;
        return new ClonalSequence(targets);
    }

    public VoidProcessor<VDJCAlignments> getInitialAssembler() {
        return new InitialAssembler();
    }

    public boolean beginMapping() {
        if (globalLogger != null)
            globalLogger.end(totalAlignments.get());
        if (!parameters.isMappingEnabled())
            return false;

        if (deferredAlignmentsLogger != null)
            throw new IllegalStateException();

        if (!deferredExists)
            return false;
        deferredAlignmentsLogger = new AssemblerEventLogger();
        mappingTree = new SequenceTreeMap<>(NucleotideSequence.ALPHABET);
        for (CloneAccumulatorContainer container : clones.values()) {
            for (CloneAccumulator accumulator : container.accumulators.values())
                accumulator.onBeforeMapping();
            mappingTree.createIfAbsent(container.getSequence().getConcatenated().getSequence(), LIST_FACTORY).add(container);
        }
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

    public void preClustering() {
        for (CloneAccumulatorContainer c : clones.values())
            cloneList.addAll(c.build());
        preClusteringDone = true;
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
        if (!preClusteringDone)
            throw new IllegalStateException("No preclustering done.");

        @SuppressWarnings("unchecked")
        Clustering clustering = new Clustering(cloneList,
                new SequenceExtractor<CloneAccumulator, NucleotideSequence>() {
                    @Override
                    public NucleotideSequence getSequence(CloneAccumulator object) {
                        return object.getSequence().getConcatenated().getSequence();
                    }
                }, new CloneClusteringStrategy(parameters.getCloneClusteringParameters(),
                this));
        this.progressReporter = clustering;
        List<Cluster<CloneAccumulator>> clusters = clustering.performClustering();
        clusteredClonesAccumulators = new ArrayList<>(clusters.size());
        idMapping = new TIntIntHashMap(cloneList.size());
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
                        head.mergeCounts(object.getHead());
                    idMapping.put(object.getHead().getCloneIndex(), k);
                    return true;
                }
            });
            clusteredClonesAccumulators.add(head);
        }
        this.progressReporter = null;
    }

    public long getAlignmentsCount() {
        return totalAlignments.get();
    }

    public void buildClones() {
        if (!preClusteringDone)
            throw new IllegalStateException("No preclustering done.");
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
            GeneFeature gf = featuresToAlign.get(geneType);
            if (gf != null)
                features.put(geneType, gf);
        }
        return new CloneSet(Arrays.asList(realClones), usedGenes.values(), features, parameters.getAssemblingFeatures());
    }

    public OutputPortCloseable<ReadToCloneMapping> getAssembledReadsPort() {
        return new AssembledReadsPort(globalLogger.createEventsPort(), deferredAlignmentsLogger == null ? null : deferredAlignmentsLogger.createEventsPort(), idMapping, preClustered);
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
                log(new AssemblerEvent(input.getAlignmentsIndex(), AssemblerEvent.DROPPED));
                droppedAlignments.incrementAndGet();
                onFailedToExtractTarget(input);
                return;
            }
            //Calculating number of bad points
            int badPoints = numberOfBadPoints(target);

            if (badPoints > target.getConcatenated().size() * parameters.getMaxBadPointsPercent()) {
                // Too many bad points (this read has too low quality in the regions of interest)
                log(new AssemblerEvent(input.getAlignmentsIndex(), AssemblerEvent.DROPPED));
                droppedAlignments.incrementAndGet();
                onTooManyLowQualityPoints(input);
                return;
            }

            if (badPoints > 0) {
                // Has some number of bad points but not greater then maxBadPointsToMap
                log(new AssemblerEvent(input.getAlignmentsIndex(), AssemblerEvent.DEFERRED));
                onAlignmentDeferred(input);
                return;
            }

            //Getting or creating accumulator from map
            CloneAccumulatorContainer container = clones.get(target);
            if (container == null) {
                //Creating accumulator
                CloneAccumulatorContainer temp = new CloneAccumulatorContainer();
                //Trying to put this new clone to map
                container = clones.putIfAbsent(target, temp);
                //Assign cloneIndex for the newly created clone only if it was successfully put into map
                if (container == null) {
                    //Executed only once for newly created clone
                    container = temp;
                }
                //accumulator variable contains correct clone from map
            }

            CloneAccumulator acc = container.accumulate(target, input, false);
            //Logging assembler events for subsequent index creation and mapping filtering
            log(new AssemblerEvent(input.getAlignmentsIndex(), acc.getCloneIndex()));
            //Incrementing corresponding counter
            successfullyAssembledAlignments.incrementAndGet();
            onAlignmentAddedToClone(input, acc);
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
                deferredAlignmentsLogger.newEvent(new AssemblerEvent(event.alignmentsIndex, AssemblerEvent.DROPPED));
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

            RandomUtil.reseedThreadLocal(HashFunctions.JenkinWang64shift(Arrays.hashCode(input.getReadIds())));

            int badPoints = numberOfBadPoints(clonalSequence);
            int threshold = thresholdCalculator.getThreshold(badPoints);

            NeighborhoodIterator<NucleotideSequence, ArrayList<CloneAccumulatorContainer>> iterator =
                    mappingTree.getNeighborhoodIterator(clonalSequence.getConcatenated().getSequence(),
                            threshold, 0, 0, threshold,
                            new DeferredAlignmentsMapperGuide(clonalSequence.getConcatenated().getQuality(),
                                    parameters.getBadQualityThreshold()));

            ArrayList<CloneAccumulator> candidates = new ArrayList<>();
            ArrayList<CloneAccumulatorContainer> assembledClones;

            int minMismatches = -1;
            long count = 0;
            while ((assembledClones = iterator.next()) != null)
                for (CloneAccumulatorContainer container : assembledClones) {
                    // Version of isCompatible without mutations is used here because
                    // ony substitutions possible in this place
                    CloneAccumulator acc = container.accumulators.get(extractSignature(input));
                    if (acc != null && clonalSequence.isCompatible(acc.getSequence())) {
                        if (minMismatches == -1)
                            minMismatches = iterator.getMismatches();
                        else if (minMismatches < iterator.getMismatches())
                            break;
                        candidates.add(acc);
                        count += acc.getInitialCoreCount();
                    }
                }

            if (candidates.isEmpty()) {
                deferredAlignmentsLogger.newEvent(new AssemblerEvent(input.getAlignmentsIndex(),
                        AssemblerEvent.DROPPED));
                droppedAlignments.incrementAndGet();
                onNoCandidateFoundForDefferedAlignment(input);
                return;
            }

            count = (count == 1 ? 1 : RandomUtil.getThreadLocalRandomData().nextLong(1, count));
            CloneAccumulator accumulator = null;
            for (CloneAccumulator acc : candidates)
                if ((count -= acc.getInitialCoreCount()) <= 0)
                    accumulator = acc;

            assert accumulator != null;

            mappedAlignments.incrementAndGet();
            successfullyAssembledAlignments.incrementAndGet();
            deferredAlignmentsLogger.newEvent(new AssemblerEvent(input.getAlignmentsIndex(),
                    minMismatches == 0 ? accumulator.getCloneIndex() : -4 - accumulator.getCloneIndex()));

            if (minMismatches > 0) {
                // Mapped
                onDeferredAlignmentMappedToClone(input, accumulator);
                accumulator.accumulate(clonalSequence, input, true);
            } else {
                // Added to clone as normal alignment,
                // because sequence exactly equals to clonal sequence
                onAlignmentAddedToClone(input, accumulator);
                accumulator.accumulate(clonalSequence, input, false);
            }
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
                            parameters.getAssemblingFeatures(), usedGenes, featuresToAlign);
            Collection<CloneAccumulator> source;
            if (clusteredClonesAccumulators != null)
                source = clusteredClonesAccumulators;
            else {
                idMapping = new TIntIntHashMap();
                //sort clones by count (if not yet sorted by clustering)
                CloneAccumulator[] sourceArray = cloneList.toArray(new CloneAccumulator[cloneList.size()]);
                Arrays.sort(sourceArray, CLONE_ACCUMULATOR_COMPARATOR);
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

    /**
     * Container for Clone Accumulators with the same clonal sequence but different V/J/C genes.
     */
    public final class CloneAccumulatorContainer {
        final HashMap<VJCSignature, CloneAccumulator> accumulators = new HashMap<>();

        synchronized CloneAccumulator accumulate(ClonalSequence sequence, VDJCAlignments alignments, boolean mapped) {
            VJCSignature vjcSignature = extractSignature(alignments);
            CloneAccumulator acc = accumulators.get(vjcSignature);
            if (acc == null) {
                acc = new CloneAccumulator(sequence, extractNRegions(sequence, alignments),
                        parameters.getQualityAggregationType());
                accumulators.put(vjcSignature, acc);
                acc.setCloneIndex(cloneIndexGenerator.incrementAndGet());
                onNewCloneCreated(acc);
            }
            acc.accumulate(sequence, alignments, mapped);
            return acc;
        }

        /**
         * Preforms pre-clustering and returns final list of clonotypes.
         */
        public List<CloneAccumulator> build() {
            CloneAccumulator[] accs = accumulators.values().toArray(new CloneAccumulator[accumulators.size()]);
            for (CloneAccumulator acc : accs)
                acc.calculateScores(parameters.cloneFactoryParameters);
            Arrays.sort(accs, CLONE_ACCUMULATOR_COMPARATOR);
            int deleted = 0;
            for (int i = 0; i < accs.length - 1; i++) {
                // null marks clustered clonotypes
                if (accs[i] == null)
                    continue;

                // Top V, J and C genes of the major clonotype
                VJCSignature vjcSignature = extractSignature(accs[i]);
                long countThreshold = (long) (accs[i].getCount() * parameters.maximalPreClusteringRatio);
                for (int j = i + 1; j < accs.length; j++)
                    // Clustering j'th clone to i'th
                    if (accs[j] != null && accs[j].getCount() <= countThreshold &&
                            vjcSignature.matchHits(accs[j])) {
                        accs[i].mergeCounts(accs[j]);
                        onPreClustered(accs[i], accs[j]);
                        preClustered.put(accs[j].getCloneIndex(), accs[i].getCloneIndex());
                        accs[j] = null;
                        ++deleted;
                    }
            }

            // Filtering low quality clonotypes
            List<CloneAccumulator> result = new ArrayList<>(accs.length - deleted);

            out:
            for (CloneAccumulator acc : accs) {
                // null marks clustered clonotypes
                if (acc == null)
                    continue;

                acc.rebuildClonalSequence();

                if (acc.getSequence().getConcatenated().getQuality().minValue() <
                        parameters.minimalQuality) {
                    onCloneDropped(acc);
                    continue out;
                }

                result.add(acc);
            }

            return result;
        }

        public ClonalSequence getSequence() {
            return accumulators.values().iterator().next().getSequence();
        }

        private Range[] extractNRegions(ClonalSequence clonalSequence, VDJCAlignments alignments) {
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
    }

    VJCSignature extractSignature(VDJCAlignments alignments) {
        return new VJCSignature(
                parameters.getSeparateByV() ? getGeneId(alignments, GeneType.Variable) : DO_NOT_CHECK,
                parameters.getSeparateByJ() ? getGeneId(alignments, GeneType.Joining) : DO_NOT_CHECK,
                parameters.getSeparateByC() ? getGeneId(alignments, GeneType.Constant) : DO_NOT_CHECK
        );
    }

    VJCSignature extractSignature(CloneAccumulator alignments) {
        return new VJCSignature(
                parameters.getSeparateByV() ? getGeneId(alignments, GeneType.Variable) : DO_NOT_CHECK,
                parameters.getSeparateByJ() ? getGeneId(alignments, GeneType.Joining) : DO_NOT_CHECK,
                parameters.getSeparateByC() ? getGeneId(alignments, GeneType.Constant) : DO_NOT_CHECK
        );
    }

    /**
     * Special marker GeneID used to make matchHits procedure to ignore V, J or C genes during matchHits procedure
     */
    private static final VDJCGeneId DO_NOT_CHECK = new VDJCGeneId(new VDJCLibraryId("NO_LIBRARY", 0), "DO_NOT_CHECK");

    static final class VJCSignature {
        final VDJCGeneId vGene, jGene, cGene;

        /**
         * null for absent hits, DO_NOT_CHECK to ignore corresponding gene
         */
        VJCSignature(VDJCGeneId vGene, VDJCGeneId jGene, VDJCGeneId cGene) {
            this.vGene = vGene;
            this.jGene = jGene;
            this.cGene = cGene;
        }

        boolean matchHits(CloneAccumulator acc) {
            TObjectFloatHashMap<VDJCGeneId> minor;

            if (vGene != DO_NOT_CHECK) {
                minor = acc.geneScores.get(GeneType.Variable);
                if (vGene == null && (minor != null && !minor.isEmpty()))
                    return false;
                if (vGene != null && minor != null && !minor.containsKey(vGene))
                    return false;
            }

            if (jGene != DO_NOT_CHECK) {
                minor = acc.geneScores.get(GeneType.Joining);
                if (jGene == null && (minor != null && !minor.isEmpty()))
                    return false;
                if (jGene != null && minor != null && !minor.containsKey(jGene))
                    return false;
            }

            if (cGene != DO_NOT_CHECK) {
                minor = acc.geneScores.get(GeneType.Constant);
                if (cGene == null && (minor != null && !minor.isEmpty()))
                    return false;
                if (cGene != null && minor != null && !minor.containsKey(cGene))
                    return false;
            }

            return true;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            VJCSignature that = (VJCSignature) o;

            if (vGene != null ? !vGene.equals(that.vGene) : that.vGene != null) return false;
            if (jGene != null ? !jGene.equals(that.jGene) : that.jGene != null) return false;
            return !(cGene != null ? !cGene.equals(that.cGene) : that.cGene != null);

        }

        @Override
        public int hashCode() {
            int result = vGene != null ? vGene.hashCode() : 0;
            result = 31 * result + (jGene != null ? jGene.hashCode() : 0);
            result = 31 * result + (cGene != null ? cGene.hashCode() : 0);
            return result;
        }
    }

    static VDJCGeneId getGeneId(VDJCAlignments alignments, GeneType type) {
        VDJCHit hit = alignments.getBestHit(type);
        return hit == null ? null : hit.getGene().getId();
    }

    static VDJCGeneId getGeneId(CloneAccumulator acc, GeneType type) {
        TObjectFloatHashMap<VDJCGeneId> aScores = acc.geneScores.get(type);
        if (aScores == null || aScores.isEmpty())
            return null;
        VDJCGeneId id = null;
        float maxScore = Float.MIN_VALUE;
        TObjectFloatIterator<VDJCGeneId> it = aScores.iterator();
        while (it.hasNext()) {
            it.advance();
            if (maxScore < it.value()) {
                maxScore = it.value();
                id = it.key();
            }
        }
        return id;
    }

    static final Comparator<CloneAccumulator> CLONE_ACCUMULATOR_COMPARATOR = new Comparator<CloneAccumulator>() {
        @Override
        public int compare(CloneAccumulator o1, CloneAccumulator o2) {
            int c;

            if ((c = Long.compare(o2.getCount(), o1.getCount())) != 0)
                return c;

            if ((c = compareByBestHists(o1, o2, GeneType.Variable)) != 0)
                return c;

            if ((c = compareByBestHists(o1, o2, GeneType.Joining)) != 0)
                return c;

            if ((c = compareByBestHists(o1, o2, GeneType.Constant)) != 0)
                return c;

            return 0;
        }
    };

    public static int compareByBestHists(CloneAccumulator o1, CloneAccumulator o2, GeneType geneType) {
        VDJCGeneId a1 = o1.getBestGene(geneType);
        VDJCGeneId a2 = o2.getBestGene(geneType);

        if (a1 == null && a2 == null)
            return 0;

        if (a1 == null)
            return -1;

        if (a2 == null)
            return 1;

        return a1.compareTo(a2);
    }
}
