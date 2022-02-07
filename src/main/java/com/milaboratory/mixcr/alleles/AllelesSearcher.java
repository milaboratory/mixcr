package com.milaboratory.mixcr.alleles;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPortCloseable;
import cc.redberry.pipe.blocks.FilteringPort;
import cc.redberry.pipe.util.FlatteningOutputPort;
import com.milaboratory.core.alignment.AlignmentScoring;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.alleles.CommonMutationsSearcher.CloneDescription;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.CloneReader;
import com.milaboratory.mixcr.basictypes.IOUtil;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.util.Cluster;
import com.milaboratory.mixcr.util.ExceptionUtil;
import com.milaboratory.primitivio.PrimitivIOStateBuilder;
import com.milaboratory.util.sorting.HashSorter;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

        return new SortedClonotypes(
                sorterSupplier.apply(Variable).port(new FlatteningOutputPort<>(CUtils.asOutputPort(
                        datasets.stream().map(this::readClonesWithNonProductiveFilter).collect(Collectors.toList()))
                )),
                sorterSupplier.apply(Joining).port(new FlatteningOutputPort<>(CUtils.asOutputPort(
                        datasets.stream().map(this::readClonesWithNonProductiveFilter).collect(Collectors.toList()))
                ))
        );
    }

    private OutputPortCloseable<Clone> readClonesWithNonProductiveFilter(CloneReader dataset) {
        // filter non-productive clonotypes
        if (parameters.productiveOnly) {
            // todo CDR3?
            return new FilteringPort<>(dataset.readClones(),
                    c -> !c.containsStops(CDR3) && !c.isOutOfFrame(CDR3));
        } else {
            return dataset.readClones();
        }
    }

    public OutputPortCloseable<Cluster<Clone>> buildClusters(OutputPortCloseable<Clone> sortedClones, GeneType geneType) {
        Comparator<Clone> comparator = Comparator.comparing(c -> c.getBestHit(geneType).getGene().getId().getName());

        // todo do not copy cluster
        final List<Clone> cluster = new ArrayList<>();

        // group by similar V/J/C genes
        return new OutputPortCloseable<Cluster<Clone>>() {
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
    }

    public List<Allele> findAlleles(Cluster<Clone> clusterByTheSameGene, GeneType geneType) {
        if (clusterByTheSameGene.cluster.isEmpty()) {
            throw new IllegalArgumentException();
        }
        GeneType complimentaryGene = complimentaryGene(geneType);
        List<CloneDescription> cloneDescriptors = clusterByTheSameGene.cluster.stream()
                .map(clone -> new CloneDescription(
                        () -> Arrays.stream(clone.getBestHit(geneType).getAlignments())
                                .flatMapToInt(it -> IntStream.of(it.getAbsoluteMutations().getRAWMutations())),
                        clone.getNFeature(GeneFeature.CDR3).size(),
                        clone.getBestHit(complimentaryGene).getGene().getGeneName()
                ))
                .collect(Collectors.toList());

        VDJCHit bestHit = clusterByTheSameGene.cluster.get(0).getBestHit(geneType);
        CommonMutationsSearcher commonMutationsSearcher = new CommonMutationsSearcher(
                parameters.minPartOfClonesToDeterminateAllele,
                parameters.maxPenaltyByAlleleMutation,
                scoring(geneType),
                bestHit.getAlignment(0).getSequence1()
        );

        return commonMutationsSearcher.findAlleles(cloneDescriptors).stream()
                .map(alleleMutationsFromGermline -> new Allele(
                        bestHit.getGene().getId(),
                        alleleMutationsFromGermline
                ))
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

    public static class SortedClonotypes {
        private final OutputPortCloseable<Clone> sortedByV;
        private final OutputPortCloseable<Clone> sortedByJ;

        public SortedClonotypes(OutputPortCloseable<Clone> sortedByV, OutputPortCloseable<Clone> sortedByJ) {
            this.sortedByV = sortedByV;
            this.sortedByJ = sortedByJ;
        }

        public OutputPortCloseable<Clone> getSortedByV() {
            return sortedByV;
        }

        public OutputPortCloseable<Clone> getSortedByJ() {
            return sortedByJ;
        }
    }
}
