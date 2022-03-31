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
import com.milaboratory.core.sequence.AminoAcidSequence;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.CloneReader;
import com.milaboratory.mixcr.basictypes.CloneSetIO;
import com.milaboratory.mixcr.trees.*;
import com.milaboratory.mixcr.trees.TreeBuilderByAncestors.ObservedOrReconstructed;
import com.milaboratory.mixcr.util.Cluster;
import com.milaboratory.mixcr.util.ExceptionUtil;
import com.milaboratory.mixcr.util.XSV;
import com.milaboratory.util.SmartProgressReporter;
import io.repseq.core.GeneFeature;
import io.repseq.core.VDJCLibraryRegistry;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.math3.util.Pair;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.repseq.core.GeneFeature.CDR3;
import static io.repseq.core.ReferencePoint.*;

@CommandLine.Command(name = CommandBuildSHMTree.BUILD_SHM_TREE_COMMAND_NAME,
        sortOptions = false,
        separator = " ",
        description = "Builds SHM trees.")
public class CommandBuildSHMTree extends ACommandWithOutputMiXCR {
    static final String BUILD_SHM_TREE_COMMAND_NAME = "shm_tree";

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
        if (report != null) {
            return report;
        } else {
            return FilenameUtils.removeExtension(getOutputPath()) + ".report";
        }
    }

    public String getOutputPath() {
        return getOutputFiles().get(0);
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

        var columnsThatDependOnNode = ImmutableMap.<String, Function<Tree.NodeWithParent<ObservedOrReconstructed<CloneWrapper, AncestorInfo>>, Object>>builder()
                .put("id", it -> it.getNode().getContent().getId())
                .put("parentId", it -> it.getParent() == null ? null : it.getParent().getContent().getId())
                .put("cloneId", it -> it.getNode().getContent().convert(cw -> cw.clone.getId(), __ -> null))
                .put("CDR3", CommandBuildSHMTree::getCDR3)
                .put("CDR3_AA", it -> {
                    var CDR3 = getCDR3(it);
                    if (CDR3.size() % 3 == 0) {
                        return AminoAcidSequence.translate(CDR3);
                    } else {
                        return "";
                    }
                })
                .put("sequence", nodeWithParent -> nodeWithParent.getNode().getContent().convert(
                        it -> it.clone.getTarget(0).getSequence(),
                        AncestorInfo::getSequence
                ))
                .build();

        var columnsThatDependOnTree = ImmutableMap.<String, Function<TreeWithMeta, Object>>builder()
                .put("treeId", it -> it.getTreeId().encode())
                .build();

        var nodesTableFile = outputDirInTmp.toPath().resolve("nodes.csv").toFile();
        nodesTableFile.createNewFile();
        var nodesTable = new PrintStream(nodesTableFile);

        var allColumnNames = ImmutableSet.<String>builder()
                .addAll(columnsThatDependOnNode.keySet())
                .addAll(columnsThatDependOnTree.keySet())
                .build();
        XSV.writeXSVHeaders(nodesTable, allColumnNames, ";");

        for (Cluster<CloneWrapper> cluster : CUtils.it(shmTreeBuilder.buildClusters(sortedClones))) {
            for (TreeWithMeta tree : shmTreeBuilder.getResult(cluster, previousStepDebug.treesAfterDecisionsWriter)) {
                var columnsBuilder = ImmutableMap.<String, Function<Tree.NodeWithParent<ObservedOrReconstructed<CloneWrapper, AncestorInfo>>, Object>>builder()
                        .putAll(columnsThatDependOnNode);
                columnsThatDependOnTree.forEach((key, function) -> columnsBuilder.put(key, __ -> function.apply(tree)));
                var columns = columnsBuilder.build();

                var nodes = tree.getTree()
                        .allNodes()
                        .collect(Collectors.toList());
                XSV.writeXSVBody(nodesTable, nodes, columns, ";");
            }
        }

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

    private static NucleotideSequence getCDR3(Tree.NodeWithParent<ObservedOrReconstructed<CloneWrapper, AncestorInfo>> nodeWithParent) {
        return nodeWithParent.getNode().getContent().convert(
                cw -> cw.getFeature(CDR3).getSequence(),
                ancestor -> ancestor.getSequence().getRange(ancestor.getCDR3Begin(), ancestor.getCDR3End())
        );
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

    private NucleotideSequence getSequence(ObservedOrReconstructed<CloneWrapper, AncestorInfo> content) {
        return content.convert(
                cloneWrapper -> cloneWrapper.clone.getTarget(0).getSequence(),
                AncestorInfo::getSequence
        );
    }

    private String md5(NucleotideSequence sequence) {
        return ExceptionUtil.wrap(() -> {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            for (int i = 0; i < sequence.size(); i++) {
                md5.update(sequence.codeAt(i));
            }
            return new String(Base64.getEncoder().encode(md5.digest()));
        }).get();
    }

    private Pair<String, NucleotideSequence> idPair(ObservedOrReconstructed<CloneWrapper, AncestorInfo> content) {
        return Pair.create(
                content.convert(
                        cloneWrapper -> String.valueOf(cloneWrapper.clone.getId()),
                        seq -> "?"
                ),
                getSequence(content)
        );
    }

    private NucleotideSequence getCDR3(ObservedOrReconstructed<CloneWrapper, AncestorInfo> content) {
        return content.convert(
                cloneWrapper -> cloneWrapper.clone.getNFeature(GeneFeature.CDR3),
                ancestorInfo -> ancestorInfo.getSequence().getRange(ancestorInfo.getCDR3Begin(), ancestorInfo.getCDR3End())
        );
    }

    private NucleotideSequence getV(Tree.Node<ObservedOrReconstructed<CloneWrapper, AncestorInfo>> node, RootInfo rootInfo) {
        return node.getContent().convert(
                cloneWrapper -> cloneWrapper.clone.getNFeature(new GeneFeature(FR1End, CDR3Begin))
                        .concatenate(cloneWrapper.clone.getNFeature(CDR3).getRange(0, rootInfo.getVRangeInCDR3().length())),
                ancestorInfo -> ancestorInfo.getSequence().getRange(0, ancestorInfo.getCDR3Begin() + rootInfo.getVRangeInCDR3().length())
        );
    }

    private NucleotideSequence getJ(Tree.Node<ObservedOrReconstructed<CloneWrapper, AncestorInfo>> node, RootInfo rootInfo) {
        return node.getContent().convert(
                cloneWrapper -> {
                    NucleotideSequence CDR3 = cloneWrapper.clone.getNFeature(GeneFeature.CDR3);
                    return CDR3.getRange(CDR3.size() - rootInfo.getJRangeInCDR3().length(), CDR3.size())
                            .concatenate(cloneWrapper.clone.getNFeature(new GeneFeature(CDR3End, FR4End)));
                },
                ancestorInfo -> ancestorInfo.getSequence().getRange(ancestorInfo.getCDR3End() - rootInfo.getJRangeInCDR3().length(), ancestorInfo.getSequence().size())
        );
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
