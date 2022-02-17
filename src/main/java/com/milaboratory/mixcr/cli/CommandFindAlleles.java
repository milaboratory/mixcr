/*
 * Copyright (c) 2014-2019, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
 * commercial purposes should contact MiLaboratory LLC, which owns exclusive
 * rights for distribution of this program for commercial purposes, using the
 * following email address: licensing@milaboratory.com.
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
package com.milaboratory.mixcr.cli;

import cc.redberry.pipe.OutputPortCloseable;
import com.google.common.collect.Lists;
import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.alignment.AlignmentScoring;
import com.milaboratory.core.alignment.AlignmentUtils;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.alleles.AllelesSearcher;
import com.milaboratory.mixcr.alleles.FindAllelesParameters;
import com.milaboratory.mixcr.alleles.FindAllelesParametersPresets;
import com.milaboratory.mixcr.assembler.CloneFactory;
import com.milaboratory.mixcr.assembler.CloneFactoryParameters;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.mixcr.util.Cluster;
import com.milaboratory.mixcr.util.ExceptionUtil;
import com.milaboratory.util.GlobalObjectMappers;
import gnu.trove.map.hash.TObjectFloatHashMap;
import io.repseq.core.*;
import io.repseq.dto.VDJCGeneData;
import io.repseq.dto.VDJCLibraryData;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.math3.util.Pair;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.repseq.core.GeneType.*;

@CommandLine.Command(name = CommandFindAlleles.FIND_ALLELES_COMMAND_NAME,
        sortOptions = false,
        separator = " ",
        description = "Find allele variants in clns.")
public class CommandFindAlleles extends ACommandWithOutputMiXCR {
    static final String FIND_ALLELES_COMMAND_NAME = "find_alleles";

    @CommandLine.Parameters(
            arity = "2..*",
            description = "input_file.clns [input_file2.clns ....] output_template.clns\n" +
                    "output_template may contain {file_name} and {file_dir_path},\n" +
                    "outputs for 'input_file.clns input_file2.clns /output/folder/{file_name}_with_alleles.clns' will be /output/folder/input_file_with_alleles.clns and /output/folder/input_file2_with_alleles.clns,\n" +
                    "outputs for '/some/folder1/input_file.clns /some/folder2/input_file2.clns {file_dir_path}/{file_name}_with_alleles.clns' will be /seme/folder1/input_file_with_alleles.clns and /some/folder2/input_file2_with_alleles.clns\n" +
                    "Resulted outputs must be uniq"
    )
    private List<String> inOut = new ArrayList<>();

    @Override
    public List<String> getInputFiles() {
        return inOut.subList(0, inOut.size() - 1);
    }

    @Override
    protected List<String> getOutputFiles() {
        List<String> outputs = outputClnsFiles();
        if (libraryOutput != null) {
            if (!libraryOutput.endsWith(".json")) {
                throwValidationException("Exported library must be json: " + libraryOutput);
            }
            outputs.add(libraryOutput);
        }
        return outputs;
    }

    private List<String> outputClnsFiles() {
        String template = inOut.get(inOut.size() - 1);
        if (!template.endsWith(".clns")) {
            throwValidationException("Wrong template: command produces only clns " + template);
        }
        List<String> clnsFiles = getInputFiles().stream()
                .map(it -> Paths.get(it).toAbsolutePath())
                .map(path -> template
                        .replaceAll("\\{file_name}", FilenameUtils.removeExtension(path.getFileName().toString()))
                        .replaceAll("\\{file_dir_path}", path.getParent().toString())
                )
                .collect(Collectors.toList());
        if (clnsFiles.stream().distinct().count() < clnsFiles.size()) {
            throwValidationException("Output clns files are not uniq: " + clnsFiles);
        }
        return clnsFiles;
    }

    @CommandLine.Option(description = "Find alleles parameters preset.",
            names = {"--export-library"})
    public String libraryOutput = null;

    @CommandLine.Option(description = "Find alleles parameters preset.",
            names = {"-p", "--preset"})
    public String findAllelesParametersName = "default";

    private FindAllelesParameters findAllelesParameters = null;

    private void ensureParametersInitialized() {
        if (findAllelesParameters != null)
            return;

        findAllelesParameters = FindAllelesParametersPresets.getByName(findAllelesParametersName);
        if (findAllelesParameters == null)
            throwValidationException("Unknown parameters: " + findAllelesParametersName);
    }

    //TODO report
    @Override
    public void run0() throws Exception {
        ensureParametersInitialized();
        VDJCLibraryRegistry libraryRegistry = VDJCLibraryRegistry.getDefault();
        List<CloneReader> cloneReaders = getInputFiles().stream()
                .map(ExceptionUtil.wrap(path -> CloneSetIO.mkReader(Paths.get(path), libraryRegistry)))
                .collect(Collectors.toList());
        if (cloneReaders.size() == 0) {
            throw new IllegalArgumentException("there is no files to process");
        }
        if (cloneReaders.stream().map(CloneReader::getAssemblerParameters).distinct().count() != 1) {
            throw new IllegalArgumentException("input files must have the same assembler parameters");
        }

        AllelesSearcher allelesSearcher = new AllelesSearcher(findAllelesParameters, cloneReaders);
        AllelesSearcher.SortedClonotypes sortedClonotypes = allelesSearcher.sortClonotypes();

        Map<String, List<VDJCGeneData>> alleles = Stream.concat(
                        buildAlleles(allelesSearcher, sortedClonotypes, Variable).stream(),
                        buildAlleles(allelesSearcher, sortedClonotypes, Joining).stream()
                )
                .collect(Collectors.toMap(Pair::getKey, Pair::getValue));

        Map<String, VDJCGeneData> usedGenes = collectUsedGenes(cloneReaders, alleles);

        registerNotProcessedVJ(alleles, usedGenes);

        VDJCLibrary resultLibrary = buildLibrary(libraryRegistry, cloneReaders, usedGenes);

        if (libraryOutput != null) {
            GlobalObjectMappers.ONE_LINE.writeValue(new File(libraryOutput), resultLibrary.getData());
        }

        writeResultClnsFiles(cloneReaders, alleles, resultLibrary);
    }

    private void writeResultClnsFiles(List<CloneReader> cloneReaders, Map<String, List<VDJCGeneData>> alleles, VDJCLibrary resultLibrary) throws IOException {
        Map<String, List<VDJCGeneId>> allelesMapping = alleles.entrySet().stream()
                .map(e -> Pair.create(
                        e.getKey(),
                        e.getValue().stream().map(it -> resultLibrary.get(it.getName()).getId()).collect(Collectors.toList())
                ))
                .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
        for (int i = 0; i < cloneReaders.size(); i++) {
            CloneReader cloneReader = cloneReaders.get(i);
            String outputFile = outputClnsFiles().get(i);

            List<Clone> mapperClones = rebuildClones(resultLibrary, allelesMapping, cloneReader);
            CloneSet cloneSet = new CloneSet(
                    mapperClones,
                    resultLibrary.getGenes(),
                    cloneReader.getAlignerParameters(),
                    cloneReader.getAssemblerParameters(),
                    cloneReader.ordering()
            );
            try (ClnsWriter clnsWriter = new ClnsWriter(outputFile)) {
                clnsWriter.writeCloneSet(cloneReader.getPipelineConfiguration(), cloneSet, Collections.singletonList(resultLibrary));
            }
        }
    }

    private VDJCLibrary buildLibrary(VDJCLibraryRegistry libraryRegistry, List<CloneReader> cloneReaders, Map<String, VDJCGeneData> usedGenes) {
        VDJCLibrary originalLibrary = anyClone(cloneReaders).getBestHit(Variable).getGene().getParentLibrary();
        VDJCLibrary resultLibrary = new VDJCLibrary(
                new VDJCLibraryData(originalLibrary.getData(), new ArrayList<>(usedGenes.values())),
                originalLibrary.getName() + "_with_found_alleles",
                libraryRegistry,
                null
        );
        usedGenes.values().forEach(it -> VDJCLibrary.addGene(resultLibrary, it));
        return resultLibrary;
    }

    private void registerNotProcessedVJ(Map<String, List<VDJCGeneData>> alleles, Map<String, VDJCGeneData> usedGenes) {
        usedGenes.forEach((name, geneData) -> {
            if (geneData.getGeneType() == Joining || geneData.getGeneType() == Variable) {
                //if gene wasn't processed in alleles search, then register it as a single allele
                if (!alleles.containsKey(name)) {
                    alleles.put(
                            geneData.getName(),
                            Collections.singletonList(geneData)
                    );
                }
            }
        });
    }

    private Map<String, VDJCGeneData> collectUsedGenes(List<CloneReader> cloneReaders, Map<String, List<VDJCGeneData>> alleles) {
        Map<String, VDJCGeneData> usedGenes = new HashMap<>();
        alleles.values().stream()
                .flatMap(Collection::stream)
                .forEach(it -> usedGenes.put(it.getName(), it));
        for (CloneReader cloneReader : cloneReaders) {
            try (OutputPortCloseable<Clone> port = cloneReader.readClones()) {
                Clone clone;
                while ((clone = port.take()) != null) {
                    for (GeneType gt : VDJC_REFERENCE) {
                        for (VDJCHit hit : clone.getHits(gt)) {
                            String geneName = hit.getGene().getName();
                            if (!alleles.containsKey(geneName) && !usedGenes.containsKey(geneName)) {
                                usedGenes.put(geneName, hit.getGene().getData());
                            }
                        }
                    }
                }
            }
        }
        return usedGenes;
    }

    private List<Clone> rebuildClones(VDJCLibrary resultLibrary, Map<String, List<VDJCGeneId>> allelesMapping, CloneReader cloneReader) {
        CloneFactoryParameters cloneFactoryParameters = cloneReader.getAssemblerParameters().getCloneFactoryParameters();
        CloneFactory cloneFactory = new CloneFactory(
                cloneFactoryParameters,
                cloneReader.getAssemblerParameters().getAssemblingFeatures(),
                resultLibrary.getGenes(),
                cloneReader.getAlignerParameters().getFeaturesToAlignMap()
        );

        List<Clone> mapperClones = new ArrayList<>();
        try (OutputPortCloseable<Clone> port = cloneReader.readClones()) {
            Clone clone;
            while ((clone = port.take()) != null) {
                EnumMap<GeneType, TObjectFloatHashMap<VDJCGeneId>> originalGeneScores = new EnumMap<>(GeneType.class);
                //copy D and C
                for (GeneType gt : Lists.newArrayList(Diversity, Constant)) {
                    TObjectFloatHashMap<VDJCGeneId> scores = new TObjectFloatHashMap<>();
                    for (VDJCHit hit : clone.getHits(gt)) {
                        VDJCGeneId mappedGeneId = new VDJCGeneId(resultLibrary.getLibraryId(), hit.getGene().getName());
                        scores.put(mappedGeneId, hit.getScore());
                    }
                    originalGeneScores.put(gt, scores);
                }

                for (GeneType gt : Lists.newArrayList(Variable, Joining)) {
                    TObjectFloatHashMap<VDJCGeneId> scores = new TObjectFloatHashMap<>();
                    for (VDJCHit hit : clone.getHits(gt)) {
                        for (VDJCGeneId foundAlleleId : allelesMapping.get(hit.getGene().getName())) {
                            if (!foundAlleleId.getName().equals(hit.getGene().getName())) {
                                float scoreDelta = scoreDelta(
                                        resultLibrary.get(foundAlleleId.getName()),
                                        cloneFactoryParameters.getVJCParameters(gt).getScoring(),
                                        hit.getAlignments()
                                );
                                scores.put(foundAlleleId, hit.getScore() + scoreDelta);
                            } else {
                                scores.put(foundAlleleId, hit.getScore());
                            }
                        }
                    }
                    originalGeneScores.put(gt, scores);
                }

                mapperClones.add(cloneFactory.create(
                        clone.getId(),
                        clone.getCount(),
                        originalGeneScores,
                        clone.getTagCounter(),
                        clone.getTargets()
                ));
            }
        }
        return mapperClones;
    }

    private List<Pair<String, List<VDJCGeneData>>> buildAlleles(AllelesSearcher allelesSearcher, AllelesSearcher.SortedClonotypes sortedClonotypes, GeneType geneType) {
        List<Pair<String, List<VDJCGeneData>>> result = new ArrayList<>();
        OutputPortCloseable<Cluster<Clone>> clusters = allelesSearcher.buildClusters(sortedClonotypes.getSortedBy(geneType), geneType);
        Cluster<Clone> cluster;
        while ((cluster = clusters.take()) != null) {
            String geneId = cluster.cluster.get(0).getBestHit(geneType).getGene().getName();
            List<VDJCGeneData> resultGenes = allelesSearcher.allelesGeneData(cluster, geneType);
            result.add(Pair.create(geneId, resultGenes));
        }
        return result;
    }

    private float scoreDelta(VDJCGene foundAllele, AlignmentScoring<NucleotideSequence> scoring, Alignment<NucleotideSequence>[] alignments) {
        float scoreDelta;
        scoreDelta = 0.0f;
        //recalculate score for every alignment based on found allele
        for (Alignment<NucleotideSequence> alignment : alignments) {
            Mutations<NucleotideSequence> alleleMutations = foundAllele.getData().getBaseSequence().getMutations();
            if (alleleMutations != null) {
                Range seq1RangeAfterAlleleMutations = new Range(
                        alleleMutations.convertToSeq2Position(alignment.getSequence1Range().getLower()),
                        alleleMutations.convertToSeq2Position(alignment.getSequence1Range().getUpper())
                );
                Mutations<NucleotideSequence> mutationsFromAllele = alignment.getAbsoluteMutations().invert().combineWith(alleleMutations).invert();
                int recalculatedScore = AlignmentUtils.calculateScore(
                        alleleMutations.mutate(alignment.getSequence1()),
                        seq1RangeAfterAlleleMutations,
                        mutationsFromAllele.extractAbsoluteMutationsForRange(seq1RangeAfterAlleleMutations),
                        scoring
                );
                scoreDelta += recalculatedScore - alignment.getScore();
            }
        }
        return scoreDelta;
    }


    private Clone anyClone(List<CloneReader> cloneReaders) {
        try (OutputPortCloseable<Clone> port = cloneReaders.get(0).readClones()) {
            return port.take();
        }
    }
}
