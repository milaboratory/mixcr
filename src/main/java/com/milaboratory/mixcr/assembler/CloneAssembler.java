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
package com.milaboratory.mixcr.assembler;

import cc.redberry.pipe.OutputPortCloseable;
import cc.redberry.pipe.VoidProcessor;
import cc.redberry.primitives.Filter;
import com.milaboratory.core.Range;
import com.milaboratory.core.clustering.Cluster;
import com.milaboratory.core.clustering.Clustering;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequenceQuality;
import com.milaboratory.core.tree.MutationGuide;
import com.milaboratory.core.tree.NeighborhoodIterator;
import com.milaboratory.core.tree.SequenceTreeMap;
import com.milaboratory.mixcr.assembler.preclone.PreClone;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.util.CanReportProgress;
import com.milaboratory.util.Factory;
import com.milaboratory.util.HashFunctions;
import com.milaboratory.util.RandomUtil;
import gnu.trove.iterator.TIntIntIterator;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGene;
import io.repseq.core.VDJCGeneId;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static io.repseq.core.GeneFeature.*;

/**
 * Clone assembly steps:
 *
 * - Initial clone assembly:
 * Iteration over alignments to assemble clonotypes into {@link CloneAccumulatorContainer} (groups of clonotypes with the same
 * clonal sequence). Each {@link CloneAccumulatorContainer} consists of a map {@link VJCSignature} -> {@link CloneAccumulator}.
 * {@link CloneAccumulatorContainer} may be populated with several {@link CloneAccumulator} if one of the
 * {@link CloneAssemblerParameters#separateByV} / J / C is true, otherwise each {@link CloneAccumulatorContainer}
 * contains exactly one {@link CloneAccumulator}.
 * Alignments having nucleotides with quality scores lower then the threshold, are deferred for processing in the mapping step,
 * by saving their ids into special on-disk (to save memory) log structure, that will be used on the mapping step to pick only
 * alignments, skipped on this step.
 * Initial clone assembly is performed by pushing clonotypes into {@link InitialAssembler}.
 *
 * - Mapping low quality reads:
 * Second iteration over alignments, only alignments deferred on the initial assemble step are taken into processing here.
 * Clonal sequence are mapped with the algorithm implemented in {@link DeferredAlignmentsMapper}.
 *
 * - Pre-clustering. This step performs "clustering" between clonotypes with the same clonal sequence (clonotypes inside
 * the same {@link CloneAccumulatorContainer}). To reduce artificial diversity due to the mis-identification of V/J/C genes,
 * both because of experimental artifacts and alignment errors. This step do nothing if
 *
 * - Clustering. Grouping of clonotypes with similar clonal sequences, and high ratio between their counts, to eliminate the
 * artificial diversity.
 */
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
    /**
     * Mapping between initial clonotype id (one that was written to globalLogger) and final clonotype id,
     * to be used in alignment-to-clone mapping tracking
     *
     * FinalCloneId -> OldCloneId or bitwise negated OldCloneId of the head clonotype to which the clonotype was clustered to
     */
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

    public CloneAssembler(CloneAssemblerParameters parameters,
                          boolean logAssemblerEvents,
                          Collection<VDJCGene> genes,
                          EnumMap<GeneType, GeneFeature> featuresToAlign) {
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

    void onTooShortClonalSequence(PreClone preClone) {
        if (listener != null)
            listener.onTooShortClonalSequence(preClone);
    }

    void onTooManyLowQualityPoints(PreClone preClone) {
        if (listener != null)
            listener.onTooManyLowQualityPoints(preClone);
    }

    void onAlignmentDeferred(PreClone preClone) {
        deferredExists = true;
        if (listener != null)
            listener.onAlignmentDeferred(preClone);
    }

    void onAlignmentAddedToClone(PreClone preClone, CloneAccumulator accumulator) {
        if (listener != null)
            listener.onAlignmentAddedToClone(preClone, accumulator);
    }

    /* Mapping Events */

    void onNoCandidateFoundForDefferedAlignment(PreClone preClone) {
        if (listener != null)
            listener.onNoCandidateFoundForDeferredAlignment(preClone);
    }

    void onDeferredAlignmentMappedToClone(PreClone preClone, CloneAccumulator accumulator) {
        if (listener != null)
            listener.onDeferredAlignmentMappedToClone(preClone, accumulator);
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

    private ClonalSequence extractClonalSequence(PreClone preClone) {
        NSequenceWithQuality[] clonalSequence = preClone.getClonalSequence();
        int totalLength = 0;
        for (NSequenceWithQuality s : clonalSequence) totalLength += s.size();
        if (totalLength < parameters.minimalClonalSequenceLength)
            return null;
        return new ClonalSequence(clonalSequence);
    }

    public VoidProcessor<PreClone> getInitialAssembler() {
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

    public Filter<PreClone> getDeferredAlignmentsFilter() {
        return new DeferredAlignmentsFilter();
    }

    public VoidProcessor<PreClone> getDeferredAlignmentsMapper() {
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
            throw new IllegalStateException("No pre-clustering is done.");

        Clustering<CloneAccumulator, NucleotideSequence> clustering = new Clustering<>(cloneList,
                object -> object.getSequence().getConcatenated().getSequence(),
                new CloneClusteringStrategy(parameters.getCloneClusteringParameters(), this));

        this.progressReporter = clustering;
        List<Cluster<CloneAccumulator>> clusters = clustering.performClustering();
        clusteredClonesAccumulators = new ArrayList<>(clusters.size());
        idMapping = new TIntIntHashMap(cloneList.size());
        for (int i = 0; i < clusters.size(); ++i) {
            final Cluster<CloneAccumulator> cluster = clusters.get(i);
            final CloneAccumulator head = cluster.getHead();
            idMapping.put(head.getCloneIndex(), i);
            // i - new index of head clone
            head.setCloneIndex(i);
            // k - index to be set for all child clonotypes
            final int k = ~i;
            cluster.processAllChildren(object -> {
                onClustered(head, object.getHead());
                if (parameters.isAddReadsCountOnClustering())
                    head.mergeCounts(object.getHead());
                idMapping.put(object.getHead().getCloneIndex(), k);
                return true;
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
            throw new IllegalStateException("No pre-clustering is done.");
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

    /** Adds another layer to idMapping */
    private void addIdMapping(TIntIntHashMap newIdMapping, boolean assertAllMatch) {
        if (idMapping == null)
            idMapping = newIdMapping;
        else {
            for (TIntIntIterator it = idMapping.iterator(); it.hasNext(); ) {
                it.advance();
                int val = it.value();
                if (val >= 0) { // "renaming" normal clonotypes
                    if (newIdMapping.containsKey(val))
                        it.setValue(newIdMapping.get(val));
                    else if (assertAllMatch)
                        throw new IllegalStateException("Assertion error.");
                    else
                        it.remove();
                } else { // "renaming" clustered clonotypes
                    if (newIdMapping.containsKey(~val))
                        it.setValue(~newIdMapping.get(~val));
                    else if (assertAllMatch)
                        throw new IllegalStateException("Assertion error.");
                    else
                        it.remove();
                }
            }
        }
    }

    public CloneSet getCloneSet(MiXCRMetaInfo info) {
        return new CloneSet(
                Arrays.asList(realClones),
                usedGenes.values(),
                info
                        .withAssemblerParameters(parameters)
                        .withAllClonesCutBy(parameters.assemblingFeatures),
                new VDJCSProperties.CloneOrdering(new VDJCSProperties.CloneCount())
        );
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

    private final class InitialAssembler implements VoidProcessor<PreClone> {
        private void log(AssemblerEvent event) {
            if (globalLogger != null)
                globalLogger.newEvent(event);
        }

        @Override
        public void process(PreClone input) {
            totalAlignments.incrementAndGet();
            final ClonalSequence target = extractClonalSequence(input);

            if (target == null) {
                log(new AssemblerEvent(input.getIndex(), AssemblerEvent.DROPPED));
                droppedAlignments.incrementAndGet();
                onTooShortClonalSequence(input);
                return;
            }

            // Calculating number of bad points
            int badPoints = numberOfBadPoints(target);

            if (badPoints > target.getConcatenated().size() * parameters.getMaxBadPointsPercent()) {
                // Too many bad points (this read has too low quality in the regions of interest)
                log(new AssemblerEvent(input.getIndex(), AssemblerEvent.DROPPED));
                droppedAlignments.incrementAndGet();
                onTooManyLowQualityPoints(input);
                return;
            }

            if (badPoints > 0) {
                // Has number of bad points but not greater then maxBadPointsToMap
                log(new AssemblerEvent(input.getIndex(), AssemblerEvent.DEFERRED));
                onAlignmentDeferred(input);
                return;
            }

            // Getting or creating accumulator container for a given sequence
            CloneAccumulatorContainer container = clones.computeIfAbsent(target, t -> new CloneAccumulatorContainer());
            // Preforming alignment accumulation
            CloneAccumulator acc = container.accumulate(target, input, false);
            // Logging assembler events for subsequent index creation and mapping filtering
            log(new AssemblerEvent(input.getIndex(), acc.getCloneIndex()));
            // Incrementing corresponding counter
            successfullyAssembledAlignments.incrementAndGet();
            onAlignmentAddedToClone(input, acc);
        }
    }

    private final class DeferredAlignmentsFilter implements Filter<PreClone> {
        final Iterator<AssemblerEvent> events = globalLogger.events().iterator();

        @Override
        public boolean accept(PreClone alignment) {
            if (!events.hasNext())
                throw new IllegalArgumentException("This filter can not be used in concurrent " +
                        "environment. Perform pre-filtering in a single thread.");
            AssemblerEvent event = events.next();
            if (alignment.getIndex() != event.preCloneIndex)
                throw new IllegalArgumentException("This filter can not be used in concurrent " +
                        "environment. Perform pre-filtering in a single thread.");
            if (event.cloneIndex != AssemblerEvent.DEFERRED) {
                deferredAlignmentsLogger.newEvent(new AssemblerEvent(event.preCloneIndex, AssemblerEvent.DROPPED));
                return false;
            }
            return true;
        }
    }

    private final class DeferredAlignmentsMapper implements VoidProcessor<PreClone> {
        final AssemblerUtils.MappingThresholdCalculator thresholdCalculator = parameters.getThresholdCalculator();

        @Override
        public void process(PreClone input) {
            final ClonalSequence clonalSequence = extractClonalSequence(input);

            // The sequence was deferred on the initial step, so it must contain clonal sequence
            assert clonalSequence != null;

            // Seeding random generator to make ambiguous mappings below reproducible
            RandomUtil.reseedThreadLocal(HashFunctions.JenkinWang64shift(input.getIndex()));

            int badPoints = numberOfBadPoints(clonalSequence);
            // Implements the algorithm to control the number of possible matching sequences
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
                    CloneAccumulator acc = container.accumulators.get(extractSignature(input));
                    // Version of isCompatible without mutations is used here because
                    // ony substitutions possible in this place
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
                deferredAlignmentsLogger.newEvent(new AssemblerEvent(input.getIndex(),
                        AssemblerEvent.DROPPED));
                droppedAlignments.incrementAndGet();
                onNoCandidateFoundForDefferedAlignment(input);
                return;
            }

            // Selecting a random clone if mapping is ambiguous
            count = (count == 1 ? 1 : RandomUtil.getThreadLocalRandomData().nextLong(1, count));
            CloneAccumulator accumulator = null;
            for (CloneAccumulator acc : candidates)
                if ((count -= acc.getInitialCoreCount()) <= 0)
                    accumulator = acc;

            assert accumulator != null;

            mappedAlignments.incrementAndGet();
            successfullyAssembledAlignments.incrementAndGet();
            deferredAlignmentsLogger.newEvent(new AssemblerEvent(input.getIndex(),
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
            this.sourceSize = clusteredClonesAccumulators != null ? clusteredClonesAccumulators.size() : cloneList.size();
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
            Collection<CloneAccumulator> source;
            if (clusteredClonesAccumulators != null &&
                    // addReadsCountOnClustering=true may change clone counts
                    // This fixes #468
                    // If AddReadsCountOnClustering is enabled resorting will be performed for the dataset
                    !parameters.isAddReadsCountOnClustering())
                source = clusteredClonesAccumulators;
            else {
                TIntIntHashMap newIdMapping = new TIntIntHashMap();
                //sort clones by count (if not yet sorted by clustering)
                CloneAccumulator[] sourceArray = clusteredClonesAccumulators == null
                        ? cloneList.toArray(new CloneAccumulator[cloneList.size()])
                        : clusteredClonesAccumulators.toArray(new CloneAccumulator[clusteredClonesAccumulators.size()]);
                Arrays.sort(sourceArray, CLONE_ACCUMULATOR_COMPARATOR);
                for (int i = 0; i < sourceArray.length; i++) {
                    newIdMapping.put(sourceArray[i].getCloneIndex(), i);
                    sourceArray[i].setCloneIndex(i);
                }
                addIdMapping(newIdMapping, true);
                source = Arrays.asList(sourceArray);
            }

            CloneFactory cloneFactory =
                    new CloneFactory(parameters.getCloneFactoryParameters(),
                            parameters.getAssemblingFeatures(), usedGenes, featuresToAlign);

            TIntIntHashMap finalIdMapping = new TIntIntHashMap();
            List<Clone> finalClones = new ArrayList<>(source.size());
            Iterator<CloneAccumulator> iterator = source.iterator();
            int i = 0;
            while (iterator.hasNext()) {
                CloneAccumulator accumulator = iterator.next();

                int oldCloneId = accumulator.getCloneIndex();
                int newId = finalClones.size();

                Clone realClone = cloneFactory.create(newId, accumulator);
                if (!fineFilteringPredicate(realClone, accumulator))
                    continue;

                finalIdMapping.put(oldCloneId, newId);
                finalClones.add(realClone);

                this.progress = ++i;
            }
            addIdMapping(finalIdMapping, false);

            realClones = finalClones.toArray(new Clone[0]);
        }
    }

    public boolean fineFilteringPredicate(Clone clone, CloneAccumulator accumulator) {
        for (GeneFeature af : parameters.assemblingFeatures)
            if (clone.getFeature(af) == null)
                return false;

        return true;
    }

    /**
     * Container for Clone Accumulators with the same clonal sequence but different V/J/C genes.
     */
    public final class CloneAccumulatorContainer {
        final HashMap<VJCSignature, CloneAccumulator> accumulators = new HashMap<>();

        synchronized CloneAccumulator accumulate(ClonalSequence sequence, PreClone preClone, boolean mapped) {
            VJCSignature vjcSignature = extractSignature(preClone);
            CloneAccumulator acc = accumulators.get(vjcSignature);
            if (acc == null) {
                acc = new CloneAccumulator(sequence, extractNRegions(sequence, preClone),
                        parameters.getQualityAggregationType());
                accumulators.put(vjcSignature, acc);
                acc.setCloneIndex(cloneIndexGenerator.incrementAndGet());
                onNewCloneCreated(acc);
            }
            acc.accumulate(sequence, preClone, mapped);
            return acc;
        }

        /**
         * Preforms pre-clustering and returns final list of clonotypes.
         */
        public List<CloneAccumulator> build() {
            CloneAccumulator[] accs = accumulators.values().toArray(new CloneAccumulator[0]);
            for (CloneAccumulator acc : accs)
                acc.aggregateGeneInfo(parameters.cloneFactoryParameters);

            // Stores list of clonotypes clustered into specific clonotype
            final TIntObjectHashMap<TIntArrayList> reversePreClustered = new TIntObjectHashMap<>();

            Arrays.sort(accs, CLONE_ACCUMULATOR_COMPARATOR);
            double minCountForMaxScore = accs[0].getCount() / parameters.preClusteringCountFilteringRatio;
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

                        TIntArrayList mappedClones = reversePreClustered.get(accs[i].getCloneIndex());
                        if (mappedClones == null)
                            reversePreClustered.put(accs[i].getCloneIndex(), mappedClones = new TIntArrayList());
                        mappedClones.add(accs[j].getCloneIndex());

                        accs[j] = null;
                        ++deleted;
                    }
            }

            Consumer<CloneAccumulator> dropped = cloneAccumulator -> {
                if (preClustered.containsKey(cloneAccumulator.getCloneIndex()))
                    preClustered.put(cloneAccumulator.getCloneIndex(), -1);
                TIntArrayList subClones = reversePreClustered.get(cloneAccumulator.getCloneIndex());
                if (subClones != null) {
                    TIntIterator iterator = subClones.iterator();
                    while (iterator.hasNext())
                        preClustered.put(iterator.next(), -1);
                }
                onCloneDropped(cloneAccumulator);
            };

            // Score filtering step

            // Calculation

            float[] maxScores = new float[2];
            for (CloneAccumulator acc : accs) {
                if (acc == null)
                    continue;
                if (acc.getCount() < minCountForMaxScore)
                    continue;
                for (int i = 0; i < 2; i++) {  // Only for V and J
                    GeneType gt = GeneType.VJC_REFERENCE[i];
                    maxScores[i] =
                            parameters.getSeparateBy(gt)
                                    ? Math.max(maxScores[i], acc.getBestScore(gt))
                                    : 0;
                }
            }

            for (int i = 0; i < 2; i++) // Only for V and J
                maxScores[i] /= parameters.preClusteringScoreFilteringRatio;

            // Filtering low score clonotypes
            for (int i = 0; i < accs.length - 1; i++) {
                // null marks clustered clonotypes
                if (accs[i] == null)
                    continue;

                for (int j = 0; j < 2; j++) { // Only for V and J
                    if (accs[i].getBestGene(GeneType.VJC_REFERENCE[j]) != null &&
                            accs[i].getBestScore(GeneType.VJC_REFERENCE[j]) < maxScores[j]) {
                        dropped.accept(accs[i]);
                        accs[i] = null;
                        ++deleted;
                        break;
                    }
                }
            }

            // Filtering low quality clonotypes (has nothing to do with clustering)
            List<CloneAccumulator> result = new ArrayList<>(accs.length - deleted);

            for (CloneAccumulator acc : accs) {
                // null marks clustered clonotypes
                if (acc == null)
                    continue;

                acc.rebuildClonalSequence();

                if (acc.getSequence().getConcatenated().getQuality().minValue() < parameters.minimalQuality) {
                    dropped.accept(acc);
                    continue;
                }

                result.add(acc);
            }

            assert result.size() == accs.length - deleted;

            return result;
        }

        public ClonalSequence getSequence() {
            return accumulators.values().iterator().next().getSequence();
        }

        private Range[] extractNRegions(ClonalSequence clonalSequence, PreClone preClone) {
            boolean dFound;
            ArrayList<Range> result = new ArrayList<>();
            Range range;
            int offset = 0;
            for (int csIdx = 0; csIdx < parameters.assemblingFeatures.length; ++csIdx) {
                GeneFeature assemblingFeature = parameters.assemblingFeatures[csIdx];
                if (!assemblingFeature.contains(VDJunction) && !assemblingFeature.contains(DJJunction))
                    continue;
                dFound = false;

                range = preClone.getRange(csIdx, VDJunction);
                if (range != null) {
                    result.add(range.move(offset));
                    dFound = true;
                }

                range = preClone.getRange(csIdx, DJJunction);
                if (range != null) {
                    result.add(range.move(offset));
                    dFound = true;
                }

                if (!dFound) {
                    range = preClone.getRange(csIdx, VJJunction);
                    if (range != null)
                        result.add(range.move(offset));
                }

                offset += clonalSequence.get(csIdx).size();
            }
            return result.toArray(new Range[0]);
        }
    }

    VJCSignature extractSignature(PreClone preClone) {
        return new VJCSignature(
                parameters.getSeparateByV() ? preClone.getBestGene(GeneType.Variable) : VJCSignature.DO_NOT_CHECK,
                parameters.getSeparateByJ() ? preClone.getBestGene(GeneType.Joining) : VJCSignature.DO_NOT_CHECK,
                parameters.getSeparateByC() ? preClone.getBestGene(GeneType.Constant) : VJCSignature.DO_NOT_CHECK
        );
    }

    VJCSignature extractSignature(CloneAccumulator cloneAccumulator) {
        return new VJCSignature(
                parameters.getSeparateByV() ? cloneAccumulator.getBestGene(GeneType.Variable) : VJCSignature.DO_NOT_CHECK,
                parameters.getSeparateByJ() ? cloneAccumulator.getBestGene(GeneType.Joining) : VJCSignature.DO_NOT_CHECK,
                parameters.getSeparateByC() ? cloneAccumulator.getBestGene(GeneType.Constant) : VJCSignature.DO_NOT_CHECK
        );
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

            if ((c = o1.getSequence().compareTo(o2.getSequence())) != 0)
                return c;

            return 0;
        }
    };

    private static int compareByBestHists(CloneAccumulator o1, CloneAccumulator o2, GeneType geneType) {
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
