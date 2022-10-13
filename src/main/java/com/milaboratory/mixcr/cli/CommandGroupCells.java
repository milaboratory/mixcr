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
package com.milaboratory.mixcr.cli;

// /**
//  *
//  */
// @Command(
//         description = "Group clones for single cell data (e.g. alpha-beta / heavy-light chain pairing).")
// public class CommandGroupCells extends ACommandWithSmartOverwriteWithSingleInputMiXCR {
//     public static final String GROUP_CLONE_COMMAND_NAME = "groupCells";
//     @Option(description = "Cell barcode group name",
//             names = {"-t", "--tag"})
//     public int tagIndex;
//
//     @Option(description = CommonDescriptions.REPORT,
//             names = {"-r", "--report"})
//     public String reportFile;
//
//     @Option(description = CommonDescriptions.JSON_REPORT,
//             names = {"-j", "--json-report"})
//     public String jsonReportFile;
//
//     @Option(description = "Generate separate file with group mapping for cell barcodes",
//             names = {"--tag-mapping"})
//     public String tagMappingFile;
//
//     @Option(description = "Minimal fraction of reads in clone that were not assigned to any group and will be separated into 'unassigned' clone (default is 0).",
//             names = {"--minimal-ungrouped-fraction"})
//     public double minimalUngroupedFraction = 0;
//
//     @Option(names = {"-O"}, description = "Overrides default aligner parameter values")
//     public Map<String, String> overrides = new HashMap<>();
//
//     @Override
//     public GroupingPipelineConfiguration getConfiguration() {
//         return new GroupingPipelineConfiguration(getParameters(), minimalUngroupedFraction);
//     }
//
//     private DropletCloneGraphReport report;
//     private DropletCloneGraphParameters parameters;
//
//     DropletCloneGraphParameters getParameters() {
//         if (parameters != null)
//             return parameters;
//
//         parameters = DropletCloneGraphParameters.getDefault();
//         if (!overrides.isEmpty()) {
//             // Perform parameters overriding
//             parameters = JsonOverrider.override(parameters, DropletCloneGraphParameters.class, overrides);
//             if (parameters == null)
//                 throwValidationException("Failed to override some parameter: " + overrides);
//         }
//         return parameters;
//     }
//
//     @Override
//     public void run1() throws Exception {
//         String in = this.in;
//
//         report = new DropletCloneGraphReport();
//
//         switch (Objects.requireNonNull(fileInfoExtractorInstance.getFileInfo(in)).fileType) {
//             case MAGIC_CLNS:
//                 runClns();
//                 break;
//             case MAGIC_CLNA:
//                 runClna();
//                 break;
//             default:
//                 throwExecutionException("Illegal input format");
//         }
//
//         // Writing report to stout
//         Util.writeReportToStdout(report);
//
//         if (reportFile != null)
//             Util.writeReport(reportFile, report);
//
//         if (jsonReportFile != null)
//             Util.writeJsonReport(jsonReportFile, report);
//     }
//
//     private static final class CloneIdWithTag {
//         public final String tag;
//         public final int cloneId;
//
//         public CloneIdWithTag(String tag, int cloneId) {
//             this.tag = tag;
//             this.cloneId = cloneId;
//         }
//
//         @Override
//         public boolean equals(Object o) {
//             if (this == o) return true;
//             if (o == null || getClass() != o.getClass()) return false;
//             CloneIdWithTag that = (CloneIdWithTag) o;
//             return cloneId == that.cloneId &&
//                     Objects.equals(tag, that.tag);
//         }
//
//         @Override
//         public int hashCode() {
//             return Objects.hash(tag, cloneId);
//         }
//
//         @Override
//         public String toString() {
//             return cloneId + " " + tag;
//         }
//     }
//
//     private void runClns() throws Exception {
//         CloneSet cloneSet = CloneSetIO.read(in);
//         CloneSet resultCloneSet = doClustering(cloneSet, null);
//         try (ClnsWriter writer = new ClnsWriter(out)) {
//             writer.writeCloneSet(getFullPipelineConfiguration(), resultCloneSet);
//         }
//     }
//
//     private void runClna() throws Exception {
//         try (ClnAReader reader = new ClnAReader(in, VDJCLibraryRegistry.getDefault(), 3);
//              ClnAWriter writer = new ClnAWriter(getFullPipelineConfiguration(), out)) {
//
//             CloneSet cloneSet = reader.readCloneSet();
//
//             TObjectIntHashMap<CloneIdWithTag> idMapping = new TObjectIntHashMap<>(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, -1);
//             CloneSet resultCloneSet = doClustering(cloneSet, idMapping);
//
//             SmartProgressReporter.startProgressReport(writer);
//
//             writer.writeClones(resultCloneSet);
//
//             OutputPortCloseable<VDJCAlignments> alignments = reader.readAllAlignments();
//             writer.collateAlignments(() -> {
//                 VDJCAlignments als = alignments.take();
//                 if (als == null)
//                     return null;
//                 TagTuple tt = als.getTagCounter().singleOrNull();
//                 if (tt == null)
//                     return als.updateCloneIndex(-1);
//                 int newCloneIndex = idMapping.get(new CloneIdWithTag(tt.tags[tagIndex], als.getCloneIndex()));
//                 if (newCloneIndex == -1)
//                     return als.updateCloneIndex(-1);
//
//                 return als.updateCloneIndex(newCloneIndex);
//             }, reader.numberOfAlignments());
//
//             writer.writeAlignmentsAndIndex();
//         }
//     }
//
//     private CloneSet doClustering(CloneSet cloneSet, TObjectIntHashMap<CloneIdWithTag> idMapping) {
//         report.before(cloneSet);
//
//         ProgressAndStage progressAndStage = new ProgressAndStage("Grouping");
//         SmartProgressReporter.startProgressReport(progressAndStage);
//         List<CloneGroup> groups = DropletCloneGraph.calculateGroups(DropletCloneGraph.calculateTuples(cloneSet, tagIndex), parameters, report, progressAndStage);
//
//         if (tagMappingFile != null)
//             try (PrintStream os = new PrintStream(tagMappingFile)) {
//                 os.println("GroupId\tTag\tTotalReads\tTotalUMIs\tNumberOfClones");
//                 for (CloneGroup group : groups)
//                     for (String tag : group.groupTags) {
//                         os.print(group.groupId);
//                         os.print('\t');
//                         os.print(tag);
//                         os.print('\t');
//                         os.print(group.reads);
//                         os.print('\t');
//                         os.print(group.umis);
//                         os.print('\t');
//                         os.print(group.groupClones.size());
//                         os.println();
//                     }
//             } catch (FileNotFoundException e) {
//                 throw new RuntimeException(e);
//             }
//
//         TIntObjectHashMap<List<CloneGroup>> groupsByClones = new TIntObjectHashMap<>();
//         for (CloneGroup group : groups) {
//             group.groupClones.forEach(i -> {
//                 ArrayList<CloneGroup> list = new ArrayList<>();
//                 List<CloneGroup> val = groupsByClones.putIfAbsent(i, list);
//                 if (val != null) val.add(group);
//                 else list.add(group);
//                 return true;
//             });
//         }
//
//         List<Clone> resultingClones = new ArrayList<>();
//
//         final AtomicInteger cloneId = new AtomicInteger();
//         Consumer<Clone> addClone = clone -> {
//             if (clone.getGroup() < 0)
//                 report.onUngroupedClone(clone);
//             else
//                 report.onGroupedClone(clone);
//
//             int oldCloneId = clone.getId();
//             int newCloneId = cloneId.getAndIncrement();
//
//             if (idMapping != null)
//                 for (TagTuple key : clone.getTagCounter().keys()) {
//                     int put = idMapping.put(new CloneIdWithTag(key.tags[tagIndex], oldCloneId), newCloneId);
//                     assert put == -1 || put == newCloneId;
//                 }
//
//             resultingClones.add(clone.setId(newCloneId));
//         };
//
//         for (Clone clone : cloneSet) {
//             List<CloneGroup> cloneGroups = groupsByClones.get(clone.getId());
//             if (cloneGroups == null) {
//                 addClone.accept(clone.setGroup(-1 - clone.getId()));
//                 continue;
//             }
//
//             report.onCloneBeforeSplit(clone);
//
//             List<TagCounter> splitCounters = new ArrayList<>();
//             double sumTotal = 0;
//             for (CloneGroup cloneGroup : cloneGroups) {
//                 TagCounter filtered = clone.getTagCounter().filter(p -> cloneGroup.groupTags.contains(p.tags[tagIndex]));
//                 sumTotal += filtered.sum();
//                 splitCounters.add(filtered);
//             }
//
//             boolean includeUngrouped = (1 - sumTotal / clone.getTagCounter().sum()) > minimalUngroupedFraction;
//             TagCounter ungroupedCounter = null;
//             if (includeUngrouped) {
//                 ungroupedCounter = clone.getTagCounter().filter(p -> cloneGroups.stream().noneMatch(g -> g.groupTags.contains(p.tags[tagIndex])));
//                 sumTotal += ungroupedCounter.sum();
//                 assert Math.abs(sumTotal - clone.getTagCounter().sum()) < 0.1;
//             }
//
//             // TODO if cloneGroups.size() == 1, ungroupedCounter can be added to the splitCounters[0]
//
//             for (int i = 0; i < splitCounters.size(); ++i) {
//                 TagCounter tc = splitCounters.get(i);
//                 addClone.accept(clone
//                         .setTagCounts(tc)
//                         .setGroup(cloneGroups.get(i).groupId) // same index
//                         .setCount(clone.getCount() * tc.sum() / sumTotal));
//             }
//
//             if (includeUngrouped && ungroupedCounter.size() != 0)
//                 addClone.accept(clone
//                         .setTagCounts(ungroupedCounter)
//                         .setGroup(-1 - clone.getId())
//                         .setCount(clone.getCount() * ungroupedCounter.sum() / sumTotal));
//         }
//
//         return new CloneSet(resultingClones,
//                 cloneSet.getUsedGenes(),
//                 cloneSet.getAlignmentParameters(),
//                 cloneSet.getAssemblerParameters(),
//                 cloneSet.getOrdering());
//     }
//
//     @JsonAutoDetect(
//             fieldVisibility = JsonAutoDetect.Visibility.ANY,
//             isGetterVisibility = JsonAutoDetect.Visibility.NONE,
//             getterVisibility = JsonAutoDetect.Visibility.NONE)
//     @JsonTypeInfo(
//             use = JsonTypeInfo.Id.CLASS,
//             include = JsonTypeInfo.As.PROPERTY,
//             property = "type")
//     private static final class GroupingPipelineConfiguration
//             implements ActionConfiguration<GroupingPipelineConfiguration> {
//         @JsonProperty("parameters")
//         final DropletCloneGraphParameters parameters;
//         @JsonProperty("minimalUngroupedFraction")
//         final double minimalUngroupedFraction;
//
//         @JsonCreator
//         public GroupingPipelineConfiguration(@JsonProperty("parameters") DropletCloneGraphParameters parameters,
//                                              @JsonProperty("minimalUngroupedFraction") double minimalUngroupedFraction) {
//             this.parameters = parameters;
//             this.minimalUngroupedFraction = minimalUngroupedFraction;
//         }
//
//         @Override
//         public boolean equals(Object o) {
//             if (this == o) return true;
//             if (o == null || getClass() != o.getClass()) return false;
//             GroupingPipelineConfiguration that = (GroupingPipelineConfiguration) o;
//             return Double.compare(that.minimalUngroupedFraction, minimalUngroupedFraction) == 0 &&
//                     Objects.equals(parameters, that.parameters);
//         }
//
//         @Override
//         public int hashCode() {
//             return Objects.hash(parameters, minimalUngroupedFraction);
//         }
//
//         @Override
//         public String actionName() {
//             return GROUP_CLONE_COMMAND_NAME;
//         }
//     }
// }
