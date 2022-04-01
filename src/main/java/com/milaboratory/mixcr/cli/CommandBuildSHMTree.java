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

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.util.CountingOutputPort;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.milaboratory.core.mutations.MutationsUtil;
import com.milaboratory.core.sequence.AminoAcidSequence;
import com.milaboratory.mixcr.basictypes.CloneReader;
import com.milaboratory.mixcr.basictypes.CloneSetIO;
import com.milaboratory.mixcr.trees.*;
import com.milaboratory.mixcr.util.Cluster;
import com.milaboratory.mixcr.util.ExceptionUtil;
import com.milaboratory.mixcr.util.XSV;
import com.milaboratory.util.SmartProgressReporter;
import io.repseq.core.VDJCLibraryRegistry;
import org.apache.commons.io.FilenameUtils;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.milaboratory.mixcr.trees.CloneOrFoundAncestor.Base.*;

@CommandLine.Command(name = CommandBuildSHMTree.BUILD_SHM_TREE_COMMAND_NAME,
        sortOptions = false,
        separator = " ",
        description = "Builds SHM trees.")
public class CommandBuildSHMTree extends ACommandWithOutputMiXCR {
    static final String BUILD_SHM_TREE_COMMAND_NAME = "shm_tree";

    private static final Map<String, Function<Tree.NodeWithParent<CloneOrFoundAncestor>, Object>> columnsThatDependOnNode = ImmutableMap.<String, Function<Tree.NodeWithParent<CloneOrFoundAncestor>, Object>>builder()
            .put("id", it -> it.getNode().getContent().getId())
            .put("parentId", it -> it.getParent() == null ? null : it.getParent().getContent().getId())
            .put("cloneId", it -> it.getNode().getContent().getCloneId())
            .put("count", it -> it.getNode().getContent().getCount())
            .put("distanceFromGermline", it -> it.getNode().getContent().getDistanceFromGermline())
            .put("distanceFromReconstructedRoot", it -> it.getNode().getContent().getDistanceFromReconstructedRoot())
            .put("distanceFromParent", Tree.NodeWithParent::getDistance)
            .put("CDR3", it -> it.getNode().getContent().getCDR3())
            .put("CDR3_AA", it -> {
                var CDR3 = it.getNode().getContent().getCDR3();
                if (CDR3.size() % 3 == 0) {
                    return AminoAcidSequence.translate(CDR3);
                } else {
                    return "";
                }
            })
            .put("CDR3_VMutations_FromGermline", it -> it.getNode().getContent().CDR3_VMutations(FromGermline))
            .put("CDR3_VMutations_FromParent", it -> it.getNode().getContent().CDR3_VMutations(FromParent))
            .put("CDR3_VMutations_FromRoot", it -> it.getNode().getContent().CDR3_VMutations(FromReconstructedRoot))
            .put("CDR3_AA_VMutations_FromGermline", it -> toString(it.getNode().getContent().CDR3_AA_VMutations(FromGermline)))
            .put("CDR3_AA_VMutations_FromParent", it -> toString(it.getNode().getContent().CDR3_AA_VMutations(FromParent)))
            .put("CDR3_AA_VMutations_FromRoot", it -> toString(it.getNode().getContent().CDR3_AA_VMutations(FromReconstructedRoot)))
            .put("CDR3_JMutations_FromGermline", it -> it.getNode().getContent().CDR3_JMutations(FromGermline))
            .put("CDR3_JMutations_FromParent", it -> it.getNode().getContent().CDR3_JMutations(FromParent))
            .put("CDR3_JMutations_FromRoot", it -> it.getNode().getContent().CDR3_JMutations(FromReconstructedRoot))
            .put("CDR3_AA_JMutations_FromGermline", it -> toString(it.getNode().getContent().CDR3_AA_JMutations(FromGermline)))
            .put("CDR3_AA_JMutations_FromParent", it -> toString(it.getNode().getContent().CDR3_AA_JMutations(FromParent)))
            .put("CDR3_AA_JMutations_FromRoot", it -> toString(it.getNode().getContent().CDR3_AA_JMutations(FromReconstructedRoot)))
            .put("CDR3_NDN_FromGermline", it -> it.getNode().getContent().CDR3_NDNMutations(FromGermline))
            .put("CDR3_NDN_FromParent", it -> it.getNode().getContent().CDR3_NDNMutations(FromParent))
            .put("CDR3_NDN_FromRoot", it -> it.getNode().getContent().CDR3_NDNMutations(FromReconstructedRoot))
            .put("CGene", it -> it.getNode().getContent().getCGeneName())
            .build();

    private static String toString(MutationsUtil.MutationNt2AADescriptor[] array) {
        if (array == null) {
            return "";
        }
        return Arrays.stream(array).map(Object::toString).collect(Collectors.joining());
    }

    @CommandLine.Parameters(
            arity = "2..*",
            description = "input_file.clns [input_file2.clns ....] output_files.zip"
    )
    private List<String> inOut = new ArrayList<>();

    @Override
    public List<String> getInputFiles() {
        return inOut.subList(0, inOut.size() - 1);
    }

    @Override
    protected List<String> getOutputFiles() {
        return inOut.subList(inOut.size() - 1, inOut.size());
    }

    private List<String> getClnsFiles() {
        return getInputFiles();
    }

    private String getOutputZipPath() {
        return inOut.get(inOut.size() - 1);
    }

    @CommandLine.Option(description = "SHM tree builder parameters preset.",
            names = {"-p", "--preset"})
    public String shmTreeBuilderParametersName = "default";

    @CommandLine.Option(names = {"-r", "--report"}, description = "Report file path")
    public String report = null;

    @CommandLine.Option(names = {"-rp", "--report-pdf"}, description = "Pdf report file path")
    public String reportPdf = null;

    @CommandLine.Option(description = "Path to directory to store debug info",
            names = {"-d", "--debug"})
    public String debugDirectoryPath = null;

    public Path debugDirectory = null;

    private SHMTreeBuilderParameters shmTreeBuilderParameters = null;

    private void ensureParametersInitialized() throws IOException {
        if (shmTreeBuilderParameters != null)
            return;

        shmTreeBuilderParameters = SHMTreeBuilderParametersPresets.getByName(shmTreeBuilderParametersName);
        if (shmTreeBuilderParameters == null)
            throwValidationException("Unknown parameters: " + shmTreeBuilderParametersName);
        if (debugDirectory == null) {
            if (debugDirectoryPath == null) {
                debugDirectory = Files.createTempDirectory("debug");
            } else {
                debugDirectory = Paths.get(debugDirectoryPath);
            }
        }
        debugDirectory.toFile().mkdirs();
    }

    @Override
    public void validate() {
        super.validate();
        if (report == null)
            warn("NOTE: report file is not specified, using " + getReport() + " to write report.");
    }

    public String getReport() {
        return Objects.requireNonNullElseGet(report, () -> FilenameUtils.removeExtension(getOutputZipPath()) + ".report");
    }

    @Override
    public void run0() throws Exception {
        ensureParametersInitialized();
        List<CloneReader> cloneReaders = getClnsFiles().stream()
                .map(ExceptionUtil.wrap(path -> CloneSetIO.mkReader(Paths.get(path), VDJCLibraryRegistry.getDefault())))
                .collect(Collectors.toList());
        if (cloneReaders.size() == 0) {
            throw new IllegalArgumentException("there is no files to process");
        }
        if (cloneReaders.stream().map(CloneReader::getAssemblerParameters).distinct().count() != 1) {
            throw new IllegalArgumentException("input files must have the same assembler parameters");
        }
        SHMTreeBuilder shmTreeBuilder = new SHMTreeBuilder(
                shmTreeBuilderParameters,
                new ClusteringCriteria.DefaultClusteringCriteria(),
                cloneReaders
        );
        int cloneWrappersCount = shmTreeBuilder.cloneWrappersCount();

        BuildSHMTreeReport report = new BuildSHMTreeReport();
        int stepsCount = shmTreeBuilderParameters.stepsOrder.size() + 1;

        CountingOutputPort<CloneWrapper> sortedClones = new CountingOutputPort<>(shmTreeBuilder.sortedClones());
        int stepNumber = 1;
        String stepDescription = "Step " + stepNumber + "/" + stepsCount + ", " + BuildSHMTreeStep.BuildingInitialTrees.forPrint;
        SmartProgressReporter.startProgressReport(stepDescription, SmartProgressReporter.extractProgress(sortedClones, cloneWrappersCount));
        Debug currentStepDebug = createDebug(stepNumber);
        for (Cluster<CloneWrapper> cluster : CUtils.it(shmTreeBuilder.buildClusters(sortedClones))) {
            shmTreeBuilder.zeroStep(cluster, currentStepDebug.treesBeforeDecisionsWriter);
        }
        int clonesWasAddedOnInit = shmTreeBuilder.makeDecisions();
        //TODO check that all trees has minimum common mutations in VJ
        report.onStepEnd(BuildSHMTreeStep.BuildingInitialTrees, clonesWasAddedOnInit, shmTreeBuilder.treesCount());

        Debug previousStepDebug = currentStepDebug;
        for (BuildSHMTreeStep step : shmTreeBuilderParameters.stepsOrder) {
            stepNumber++;
            currentStepDebug = createDebug(stepNumber);
            int treesCountBefore = shmTreeBuilder.treesCount();
            sortedClones = new CountingOutputPort<>(shmTreeBuilder.sortedClones());
            stepDescription = "Step " + stepNumber + "/" + stepsCount + ", " + step.forPrint;
            SmartProgressReporter.startProgressReport(stepDescription, SmartProgressReporter.extractProgress(sortedClones, cloneWrappersCount));
            for (Cluster<CloneWrapper> cluster : CUtils.it(shmTreeBuilder.buildClusters(sortedClones))) {
                shmTreeBuilder.applyStep(
                        cluster,
                        step,
                        previousStepDebug.treesAfterDecisionsWriter,
                        currentStepDebug.treesBeforeDecisionsWriter
                );
            }
            int clonesWasAdded = shmTreeBuilder.makeDecisions();

            report.onStepEnd(step, clonesWasAdded, shmTreeBuilder.treesCount() - treesCountBefore);
            previousStepDebug = currentStepDebug;
        }

        sortedClones = new CountingOutputPort<>(shmTreeBuilder.sortedClones());
        SmartProgressReporter.startProgressReport("Building results", SmartProgressReporter.extractProgress(sortedClones, cloneWrappersCount));
        var outputDirInTmp = Files.createTempDirectory("tree_outputs").toFile();
        outputDirInTmp.deleteOnExit();

        var columnsThatDependOnTree = ImmutableMap.<String, Function<TreeWithMeta, Object>>builder()
                .put("treeId", it -> it.getTreeId().encode())
                .put("VGene", it -> it.getRootInfo().getVJBase().VGeneName)
                .put("JGene", it -> it.getRootInfo().getVJBase().JGeneName)
                .build();

        var nodesTableFile = outputDirInTmp.toPath().resolve("nodes.tsv").toFile();
        nodesTableFile.createNewFile();
        var nodesTable = new PrintStream(nodesTableFile);

        var allColumnNames = ImmutableSet.<String>builder()
                .addAll(columnsThatDependOnNode.keySet())
                .addAll(columnsThatDependOnTree.keySet())
                .build();
        XSV.writeXSVHeaders(nodesTable, allColumnNames, "\t");

        var printer = new NewickTreePrinter<CloneOrFoundAncestor>(it -> Integer.toString(it.getContent().getId()), true, false);

        for (Cluster<CloneWrapper> cluster : CUtils.it(shmTreeBuilder.buildClusters(sortedClones))) {
            for (TreeWithMeta treeWithMeta : shmTreeBuilder.getResult(cluster, previousStepDebug.treesAfterDecisionsWriter)) {
                var columnsBuilder = ImmutableMap.<String, Function<Tree.NodeWithParent<CloneOrFoundAncestor>, Object>>builder()
                        .putAll(columnsThatDependOnNode);
                columnsThatDependOnTree.forEach((key, function) -> columnsBuilder.put(key, __ -> function.apply(treeWithMeta)));
                var columns = columnsBuilder.build();

                var nodes = treeWithMeta.getTree()
                        .allNodes()
                        .collect(Collectors.toList());
                XSV.writeXSVBody(nodesTable, nodes, columns, "\t");

                var treeFile = outputDirInTmp.toPath().resolve(treeWithMeta.getTreeId().encode() + ".tree").toFile();
                Files.writeString(treeFile.toPath(), printer.print(treeWithMeta.getTree()));
            }
        }

        zip(outputDirInTmp.toPath(), Path.of(getOutputZipPath()));

        for (int i = 0; i <= shmTreeBuilderParameters.stepsOrder.size(); i++) {
            stepNumber = i + 1;
            File treesBeforeDecisions = debugFile(stepNumber, Debug.BEFORE_DECISIONS_SUFFIX);
            File treesAfterDecisions = debugFile(stepNumber, Debug.AFTER_DECISIONS_SUFFIX);
            report.addStatsForStep(i, treesBeforeDecisions, treesAfterDecisions);
        }

        System.out.println("============= Report ==============");
        Util.writeReportToStdout(report);
        Util.writeJsonReport(getReport(), report);
        if (reportPdf != null) {
            report.writePdfReport(Paths.get(reportPdf));
        }
    }

    private static void zip(Path sourceDir, Path destination) throws IOException {
        try (ZipOutputStream zs = new ZipOutputStream(Files.newOutputStream(destination))) {
            Files.walk(sourceDir)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        ZipEntry zipEntry = new ZipEntry(sourceDir.relativize(path).toString());
                        try {
                            zs.putNextEntry(zipEntry);
                            Files.copy(path, zs);
                            zs.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    private Debug createDebug(int stepNumber) throws IOException {
        return new Debug(
                prepareDebugFile(stepNumber, Debug.BEFORE_DECISIONS_SUFFIX),
                prepareDebugFile(stepNumber, Debug.AFTER_DECISIONS_SUFFIX)
        );
    }

    private PrintStream prepareDebugFile(int stepNumber, String suffix) throws IOException {
        File debugFile = debugFile(stepNumber, suffix);
        debugFile.delete();
        debugFile.createNewFile();
        PrintStream debugWriter = new PrintStream(debugFile);
        XSV.writeXSVHeaders(debugWriter, DebugInfo.COLUMNS_FOR_XSV.keySet(), ";");
        return debugWriter;
    }

    private File debugFile(int stepNumber, String suffix) {
        return debugDirectory.resolve("step_" + stepNumber + "_" + suffix + ".csv").toFile();
    }

    public static class Debug {
        private static final String BEFORE_DECISIONS_SUFFIX = "before_decisions";
        private static final String AFTER_DECISIONS_SUFFIX = "after_decisions";

        private final PrintStream treesBeforeDecisionsWriter;
        private final PrintStream treesAfterDecisionsWriter;

        private Debug(PrintStream treesBeforeDecisionsWriter, PrintStream treesAfterDecisionsWriter) {
            this.treesBeforeDecisionsWriter = treesBeforeDecisionsWriter;
            this.treesAfterDecisionsWriter = treesAfterDecisionsWriter;
        }
    }
}
