package com.milaboratory.mixcr.alleles;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.OutputPortCloseable;
import cc.redberry.pipe.blocks.FilteringPort;
import cc.redberry.pipe.util.FlatteningOutputPort;
import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.AlignmentScoring;
import com.milaboratory.core.mutations.Mutation;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.mutations.MutationsBuilder;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.alleles.CommonMutationsSearcher.CloneDescription;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.CloneReader;
import com.milaboratory.mixcr.basictypes.IOUtil;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.util.ClonesAlignmentRanges;
import com.milaboratory.mixcr.util.Cluster;
import com.milaboratory.mixcr.util.ExceptionUtil;
import com.milaboratory.primitivio.PrimitivIOStateBuilder;
import com.milaboratory.util.sorting.HashSorter;
import io.repseq.core.*;
import io.repseq.dto.VDJCGeneData;

import java.nio.file.Files;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.milaboratory.core.mutations.Mutations.EMPTY_NUCLEOTIDE_MUTATIONS;
import static io.repseq.core.GeneFeature.CDR3;
import static io.repseq.core.GeneType.Joining;
import static io.repseq.core.GeneType.Variable;

public class AllelesSearcher {
    private final FindAllelesParameters parameters;
    private final List<CloneReader> datasets;
    private final AlignmentScoring<NucleotideSequence> VScoring;
    private final AlignmentScoring<NucleotideSequence> JScoring;

    public AllelesSearcher(FindAllelesParameters parameters, List<CloneReader> datasets) {
        this.parameters = parameters;
        this.datasets = datasets;
        VScoring = datasets.get(0).getAssemblerParameters().getCloneFactoryParameters().getVParameters().getScoring();
        JScoring = datasets.get(0).getAssemblerParameters().getCloneFactoryParameters().getJParameters().getScoring();
    }

    public SortedClonotypes sortClonotypes() {
        // todo pre-build state, fill with references if possible
        PrimitivIOStateBuilder stateBuilder = new PrimitivIOStateBuilder();

        datasets.forEach(dataset -> IOUtil.registerGeneReferences(stateBuilder, dataset.getGenes(), dataset.getAlignerParameters()));


        // todo check memory budget
        // HDD-offloading collator of alignments
        // Collate solely by cloneId (no sorting by mapping type, etc.);
        // less fields to sort by -> faster the procedure
        long memoryBudget =
                Runtime.getRuntime().maxMemory() > 10_000_000_000L /* -Xmx10g */
                        ? Runtime.getRuntime().maxMemory() / 4L /* 1 Gb */
                        : 1 << 28 /* 256 Mb */;


        Function<GeneType, HashSorter<Clone>> sorterSupplier = ExceptionUtil.wrap(geneType -> {
            // todo move constants to parameters
            // creating sorter instance
            return new HashSorter<>(Clone.class,
                    clone -> clone.getBestHit(geneType).getGene().getId().getName().hashCode(),
                    Comparator.comparing(clone -> clone.getBestHit(geneType).getGene().getId().getName()),
                    5,
                    Files.createTempFile("alleles.searcher", "hash.sorter"),
                    8,
                    8,
                    stateBuilder.getOState(),
                    stateBuilder.getIState(),
                    memoryBudget,
                    1 << 18 /* 256 Kb */
            );
        });

        Supplier<OutputPort<Clone>> clonesSupplier = () -> new FlatteningOutputPort<>(
                CUtils.asOutputPort(datasets.stream()
                        .map(CloneReader::readClones)
                        .map(this::readClonesWithNonProductiveFilter)
                        .map(this::readClonesWithCountThreshold)
                        .collect(Collectors.toList())
                )
        );

        return new SortedClonotypes(
                sorterSupplier.apply(Variable).port(clonesSupplier.get()),
                sorterSupplier.apply(Joining).port(clonesSupplier.get())
        );
    }

    private OutputPort<Clone> readClonesWithNonProductiveFilter(OutputPort<Clone> port) {
        // filter non-productive clonotypes
        if (parameters.productiveOnly) {
            // todo CDR3?
            return new FilteringPort<>(port, c -> !c.containsStops(CDR3) && !c.isOutOfFrame(CDR3));
        } else {
            return port;
        }
    }

    private OutputPort<Clone> readClonesWithCountThreshold(OutputPort<Clone> port) {
        return new FilteringPort<>(port, c -> c.getCount() >= parameters.filterClonesWithCountLessThan);
    }

    public OutputPort<Cluster<Clone>> buildClusters(OutputPortCloseable<Clone> sortedClones, GeneType geneType) {
        Comparator<Clone> comparator = Comparator.comparing(c -> c.getBestHit(geneType).getGene().getId().getName());

        // todo do not copy cluster
        final List<Clone> cluster = new ArrayList<>();

        // group by similar V/J/C genes
        var result = new OutputPortCloseable<Cluster<Clone>>() {
            @Override
            public void close() {
                sortedClones.close();
            }

            @Override
            public Cluster<Clone> take() {
                Clone clone;
                while ((clone = sortedClones.take()) != null) {
                    if (cluster.isEmpty()) {
                        cluster.add(clone);
                        continue;
                    }

                    Clone lastAdded = cluster.get(cluster.size() - 1);
                    if (comparator.compare(lastAdded, clone) == 0)
                        cluster.add(clone);
                    else {
                        ArrayList<Clone> copy = new ArrayList<>(cluster);

                        // new cluster
                        cluster.clear();
                        cluster.add(clone);

                        return new Cluster<>(copy);
                    }
                }
                return null;
            }
        };
        return CUtils.wrapSynchronized(result);
    }

    private List<Allele> findAlleles(Cluster<Clone> clusterByTheSameGene, GeneType geneType) {
        if (clusterByTheSameGene.cluster.isEmpty()) {
            throw new IllegalArgumentException();
        }
        ClonesAlignmentRanges commonAlignmentRanges = ClonesAlignmentRanges.commonAlignmentRanges(
                clusterByTheSameGene.cluster,
                parameters.minPortionOfClonesForCommonAlignmentRanges,
                geneType,
                it -> it.getBestHit(geneType)
        );

        VDJCHit bestHit = clusterByTheSameGene.cluster.get(0).getBestHit(geneType);
        GeneType complimentaryGene = complimentaryGene(geneType);
        List<CloneDescription> cloneDescriptors = clusterByTheSameGene.cluster.stream()
                .filter(commonAlignmentRanges::containsClone)
                .map(clone -> new CloneDescription(
                        () -> Arrays.stream(clone.getBestHit(geneType).getAlignments())
                                .flatMapToInt(it -> IntStream.of(it.getAbsoluteMutations().getRAWMutations()))
                                .filter(commonAlignmentRanges::containsMutation),
                        clone.getNFeature(GeneFeature.CDR3).size(),
                        clone.getBestHit(complimentaryGene).getGene().getGeneName()
                ))
                .collect(Collectors.toList());

        CommonMutationsSearcher commonMutationsSearcher = new CommonMutationsSearcher(
                parameters,
                scoring(geneType),
                bestHit.getAlignment(0).getSequence1()
        );

        //TODO search for mutations in CDR3
        // iterate over positions in CDR3 and align every clone to germline
        // get mutations of every clone as proposals.
        // Align every clone against every proposal. Choose proposal with maximum sum of score.
        // Calculate sum of score fine on a letter in a sliding window.
        // If it decreasing more than constant in left and right parts of a window, than stop (decide what choose as an end).
        // May be size of a window depends on clones count
        //
        // What to do with P segment? May be use previous decisions as germline or generate more proposals based on mirroring
        //
        // Why it will works: on the end of a gene we will get chaotic nucleotides, otherwise few clones will have
        // mutation that will not correspond with others in this position.
        // So if it is an allele mutation score will decrease slightly and dramatically otherwise.
        // Sliding window will allow to make decisions even on small count of clones (voting will be on 'count of clones' * 'window size')
        return commonMutationsSearcher.findAlleles(cloneDescriptors).stream()
                .map(alleleMutationsFromGermline -> {
                    if (alleleMutationsFromGermline.equals(EMPTY_NUCLEOTIDE_MUTATIONS)) {
                        return new Allele(
                                bestHit.getGene(),
                                EMPTY_NUCLEOTIDE_MUTATIONS,
                                bestHit.getAlignedFeature(),
                                Collections.emptyList()
                        );
                    } else {
                        MutationsBuilder<NucleotideSequence> mutationsOnBaseLine = new MutationsBuilder<>(NucleotideSequence.ALPHABET);
                        for (int i = 0; i < alleleMutationsFromGermline.size(); i++) {
                            int mutation = alleleMutationsFromGermline.getMutation(i);
                            int convertedPosition = 0;
                            for (Range alignmentRange : commonAlignmentRanges.getCommonRanges()) {
                                if (alignmentRange.contains(Mutation.getPosition(mutation))) {
                                    convertedPosition += Mutation.getPosition(mutation) - alignmentRange.getLower();
                                    break;
                                } else {
                                    convertedPosition += alignmentRange.length();
                                }
                            }
                            mutationsOnBaseLine.append(Mutation.createMutation(
                                    Mutation.getType(mutation),
                                    convertedPosition,
                                    Mutation.getFrom(mutation),
                                    Mutation.getTo(mutation)
                            ));
                        }
                        return new Allele(
                                bestHit.getGene(),
                                alleleMutationsFromGermline,
                                bestHit.getAlignedFeature(),
                                commonAlignmentRanges.getCommonRanges()
                        );
                    }
                })
                .collect(Collectors.toList());
    }

    private GeneType complimentaryGene(GeneType geneType) {
        switch (geneType) {
            case Variable:
                return Joining;
            case Joining:
                return Variable;
            default:
                throw new IllegalArgumentException();
        }
    }

    private AlignmentScoring<NucleotideSequence> scoring(GeneType geneType) {
        switch (geneType) {
            case Variable:
                return VScoring;
            case Joining:
                return JScoring;
            default:
                throw new IllegalArgumentException();
        }
    }

    public List<VDJCGeneData> allelesGeneData(Cluster<Clone> cluster, GeneType geneType) {
        return findAlleles(cluster, geneType)
                .stream()
                .map(allele -> {
                    if (!allele.mutations.equals(EMPTY_NUCLEOTIDE_MUTATIONS)) {
                        return buildGene(allele);
                    } else {
                        return allele.gene.getData();
                    }
                })
                .sorted(Comparator.comparing(VDJCGeneData::getName))
                .collect(Collectors.toList());
    }

    private VDJCGeneData buildGene(Allele allele) {
        return new VDJCGeneData(
                new BaseSequence(
                        allele.gene.getData().getBaseSequence().getOrigin(),
                        allele.gene.getPartitioning().getRanges(allele.alignedFeature),
                        allele.mutations
                ),
                generateGeneName(allele),
                allele.gene.getData().getGeneType(),
                allele.gene.getData().isFunctional(),
                allele.gene.getData().getChains(),
                metaForGeneratedGene(allele),
                recalculatedAnchorPoints(allele)
        );
    }

    private String generateGeneName(Allele allele) {
        return allele.gene.getName() + "-M" + allele.mutations.size() + "-" + allele.mutations.hashCode();
    }

    private SortedMap<String, SortedSet<String>> metaForGeneratedGene(Allele allele) {
        SortedMap<String, SortedSet<String>> meta = new TreeMap<>(allele.gene.getData().getMeta());
        meta.put(
                "alleleMutationsReliableRanges",
                allele.knownRanges.stream()
                        .map(Range::toString)
                        .collect(Collectors.toCollection(TreeSet::new))
        );
        return meta;
    }

    private TreeMap<ReferencePoint, Long> recalculatedAnchorPoints(Allele allele) {
        ReferencePoints mappedReferencePoints = allele.gene.getPartitioning()
                .getRelativeReferencePoints(allele.alignedFeature)
                .applyMutations(allele.mutations);
        return IntStream.range(0, mappedReferencePoints.pointsCount())
                .mapToObj(mappedReferencePoints::referencePointFromIndex)
                .collect(Collectors.toMap(
                        Function.identity(),
                        it -> (long) mappedReferencePoints.getPosition(it),
                        (Long a, Long b) -> {
                            throw new IllegalArgumentException();
                        },
                        TreeMap::new
                ));
    }

    public static class SortedClonotypes {
        private final OutputPortCloseable<Clone> sortedByV;
        private final OutputPortCloseable<Clone> sortedByJ;

        public SortedClonotypes(OutputPortCloseable<Clone> sortedByV, OutputPortCloseable<Clone> sortedByJ) {
            this.sortedByV = sortedByV;
            this.sortedByJ = sortedByJ;
        }

        public OutputPortCloseable<Clone> getSortedBy(GeneType geneType) {
            switch (geneType) {
                case Variable:
                    return sortedByV;
                case Joining:
                    return sortedByJ;
                default:
                    throw new IllegalArgumentException();
            }
        }
    }

    private static class Allele {
        private final VDJCGene gene;
        private final Mutations<NucleotideSequence> mutations;
        private final GeneFeature alignedFeature;
        private final List<Range> knownRanges;

        public Allele(VDJCGene gene, Mutations<NucleotideSequence> mutations, GeneFeature alignedFeature, List<Range> knownRanges) {
            this.gene = gene;
            this.mutations = mutations;
            this.alignedFeature = alignedFeature;
            this.knownRanges = knownRanges;
        }

        @Override
        public String toString() {
            return "Allele{" +
                    "id=" + gene +
                    ", mutations=" + mutations +
                    '}';
        }
    }

}
