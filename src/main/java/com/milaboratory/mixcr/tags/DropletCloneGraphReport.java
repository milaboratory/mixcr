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
package com.milaboratory.mixcr.tags;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.util.concurrent.AtomicDouble;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.CloneSet;
import com.milaboratory.util.Report;
import com.milaboratory.util.ReportHelper;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

// public class DropletCloneGraphReport implements Report {
//     final AtomicInteger
//             clonesBefore = new AtomicInteger(),
//             connectedComponents = new AtomicInteger(),
//             maxClonesInConnectedComponents = new AtomicInteger(),
//             maxTagsInConnectedComponents = new AtomicInteger(),
//             numberOfGroups = new AtomicInteger(),
//             maxTagsClique = new AtomicInteger(),
//             maxTagsCliqueDelta = new AtomicInteger(),
//             sumTagsCliqueDelta = new AtomicInteger(),
//             maxClonesPerGroup = new AtomicInteger(),
//             sumClonesPerGroup = new AtomicInteger(),
//             maxTagsPerGroup = new AtomicInteger(),
//             sumTagsPerGroup = new AtomicInteger(),
//             ungroupedClones = new AtomicInteger(),
//             groupedClonesBefore = new AtomicInteger(),
//             groupedClonesAfter = new AtomicInteger();
//
//     final AtomicLong
//             maxUMIsInConnectedComponents = new AtomicLong(),
//             sumUMIs = new AtomicLong();
//
//     final AtomicDouble
//             readsBefore = new AtomicDouble(),
//             readsInUngroupedClones = new AtomicDouble(),
//             readsInGroupedClones = new AtomicDouble(),
//             maxReadsInConnectedComponents = new AtomicDouble(),
//             sumReads = new AtomicDouble();
//
//     public void onConnectedComponent(Map<String, List<CloneTagTuple>> byTagsInComponent,
//                                      TIntObjectHashMap<List<CloneTagTuple>> byCloneIdInComponent) {
//         connectedComponents.incrementAndGet();
//
//         maxClonesInConnectedComponents.accumulateAndGet(byCloneIdInComponent.size(), Integer::max);
//         maxTagsInConnectedComponents.accumulateAndGet(byTagsInComponent.size(), Integer::max);
//
//         double reads = 0;
//         long umis = 0;
//         for (List<CloneTagTuple> list : byTagsInComponent.values())
//             for (CloneTagTuple tt : list) {
//                 reads += tt.readCount;
//                 umis += tt.umiCount;
//             }
//
//         double val;
//         do {
//             val = maxReadsInConnectedComponents.get();
//         } while (!maxReadsInConnectedComponents.compareAndSet(val, Double.max(val, reads)));
//
//         maxUMIsInConnectedComponents.accumulateAndGet(umis, Long::max);
//
//         sumReads.addAndGet(reads);
//         sumUMIs.addAndGet(umis);
//     }
//
//     public void before(CloneSet cs) {
//         clonesBefore.set(cs.size());
//         readsBefore.set(cs.getTotalCount());
//     }
//
//     public void onUngroupedClone(Clone c) {
//         ungroupedClones.incrementAndGet();
//         readsInUngroupedClones.addAndGet(c.getCount());
//     }
//
//     public void onCloneBeforeSplit(Clone c) {
//         groupedClonesBefore.incrementAndGet();
//     }
//
//     public void onGroupedClone(Clone c) {
//         readsInGroupedClones.addAndGet(c.getCount());
//         groupedClonesAfter.incrementAndGet();
//     }
//
//     public void onGroup(DropletCloneGraph.CliqueInfo topTagClique, DropletCloneGraph.CliqueInfo topCloneClique,
//                         CloneGroup group) {
//         numberOfGroups.incrementAndGet();
//         maxTagsClique.accumulateAndGet(topTagClique.clique.bitCount(), Integer::max);
//
//         int tagsCliqueDelta = topTagClique.clique.bitCount() - group.groupTags.size();
//         maxTagsCliqueDelta.accumulateAndGet(tagsCliqueDelta, Integer::max);
//         sumTagsCliqueDelta.addAndGet(tagsCliqueDelta);
//
//         maxClonesPerGroup.accumulateAndGet(group.groupClones.size(), Integer::max);
//         sumClonesPerGroup.addAndGet(group.groupClones.size());
//
//         maxTagsPerGroup.accumulateAndGet(group.groupTags.size(), Integer::max);
//         sumTagsPerGroup.addAndGet(group.groupTags.size());
//     }
//
//     public void onClustered(CloneGroup major, CloneGroup minor) {
//         groupsClustered.incrementAndGet();
//         readsClustered.addAndGet(minor.reads);
//         unisClustered.addAndGet(minor.umis);
//         tagsClustered.addAndGet(minor.groupTags.size());
//     }
//
//     double
//             effectiveMinReadsInTag = Double.NaN,
//             effectiveMinUMIsInTag = Double.NaN;
//
//     final AtomicInteger
//             tagTuplesTotal = new AtomicInteger(),
//             tagTuplesFiltered = new AtomicInteger(),
//             tagsTotal = new AtomicInteger(),
//             tagsFiltered = new AtomicInteger(),
//             tagsClustered = new AtomicInteger(),
//             groupsClustered = new AtomicInteger();
//
//     final AtomicDouble
//             readsTotal = new AtomicDouble(),
//             readsFiltered = new AtomicDouble(),
//             readsClustered = new AtomicDouble();
//
//     final AtomicLong
//             umisTotal = new AtomicLong(),
//             umisFiltered = new AtomicLong(),
//             unisClustered = new AtomicLong();
//
//     @JsonProperty("clonesBefore")
//     public int getClonesBefore() {
//         return clonesBefore.get();
//     }
//
//     @JsonProperty("connectedComponents")
//     public int getConnectedComponents() {
//         return connectedComponents.get();
//     }
//
//     @JsonProperty("maxClonesInConnectedComponents")
//     public int getMaxClonesInConnectedComponents() {
//         return maxClonesInConnectedComponents.get();
//     }
//
//     @JsonProperty("maxTagsInConnectedComponents")
//     public int getMaxTagsInConnectedComponents() {
//         return maxTagsInConnectedComponents.get();
//     }
//
//     @JsonProperty("numberOfGroups")
//     public int getNumberOfGroups() {
//         return numberOfGroups.get();
//     }
//
//     @JsonProperty("maxTagsClique")
//     public int getMaxTagsClique() {
//         return maxTagsClique.get();
//     }
//
//     @JsonProperty("maxTagsCliqueDelta")
//     public int getMaxTagsCliqueDelta() {
//         return maxTagsCliqueDelta.get();
//     }
//
//     @JsonProperty("sumTagsCliqueDelta")
//     public int getSumTagsCliqueDelta() {
//         return sumTagsCliqueDelta.get();
//     }
//
//     @JsonProperty("maxClonesPerGroup")
//     public int getMaxClonesPerGroup() {
//         return maxClonesPerGroup.get();
//     }
//
//     @JsonProperty("sumClonesPerGroup")
//     public int getSumClonesPerGroup() {
//         return sumClonesPerGroup.get();
//     }
//
//     @JsonProperty("maxTagsPerGroup")
//     public int getMaxTagsPerGroup() {
//         return maxTagsPerGroup.get();
//     }
//
//     @JsonProperty("sumTagsPerGroup")
//     public int getSumTagsPerGroup() {
//         return sumTagsPerGroup.get();
//     }
//
//     @JsonProperty("ungroupedClones")
//     public int getUngroupedClones() {
//         return ungroupedClones.get();
//     }
//
//     @JsonProperty("groupedClonesBefore")
//     public int getGroupedClonesBefore() {
//         return groupedClonesBefore.get();
//     }
//
//     @JsonProperty("groupedClonesAfter")
//     public int getGroupedClonesAfter() {
//         return groupedClonesAfter.get();
//     }
//
//     @JsonProperty("maxUMIsInConnectedComponents")
//     public long getMaxUMIsInConnectedComponents() {
//         return maxUMIsInConnectedComponents.get();
//     }
//
//     @JsonProperty("sumUMIs")
//     public long getSumUMIs() {
//         return sumUMIs.get();
//     }
//
//     @JsonProperty("readsBefore")
//     public double getReadsBefore() {
//         return readsBefore.get();
//     }
//
//     @JsonProperty("readsInUngroupedClones")
//     public double getReadsInUngroupedClones() {
//         return readsInUngroupedClones.get();
//     }
//
//     @JsonProperty("readsInGroupedClones")
//     public double getReadsInGroupedClones() {
//         return readsInGroupedClones.get();
//     }
//
//     @JsonProperty("maxReadsInConnectedComponents")
//     public double getMaxReadsInConnectedComponents() {
//         return maxReadsInConnectedComponents.get();
//     }
//
//     @JsonProperty("sumReads")
//     public double getSumReads() {
//         return sumReads.get();
//     }
//
//     @JsonProperty("effectiveMinReadsInTag")
//     public double getEffectiveMinReadsInTag() {
//         return effectiveMinReadsInTag;
//     }
//
//     @JsonProperty("effectiveMinUMIsInTag")
//     public double getEffectiveMinUMIsInTag() {
//         return effectiveMinUMIsInTag;
//     }
//
//     @JsonProperty("tagTuplesTotal")
//     public int getTagTuplesTotal() {
//         return tagTuplesTotal.get();
//     }
//
//     @JsonProperty("tagTuplesFiltered")
//     public int getTagTuplesFiltered() {
//         return tagTuplesFiltered.get();
//     }
//
//     @JsonProperty("tagsTotal")
//     public int getTagsTotal() {
//         return tagsTotal.get();
//     }
//
//     @JsonProperty("tagsFiltered")
//     public int getTagsFiltered() {
//         return tagsFiltered.get();
//     }
//
//     @JsonProperty("readsTotal")
//     public double getReadsTotal() {
//         return readsTotal.get();
//     }
//
//     @JsonProperty("readsFiltered")
//     public double getReadsFiltered() {
//         return readsFiltered.get();
//     }
//
//     @JsonProperty("umisTotal")
//     public long getUmisTotal() {
//         return umisTotal.get();
//     }
//
//     @JsonProperty("umisFiltered")
//     public long getUmisFiltered() {
//         return umisFiltered.get();
//     }
//
//     @JsonProperty("tagsClustered")
//     public int getTagsClustered() {
//         return tagsClustered.get();
//     }
//
//     @JsonProperty("groupsClustered")
//     public int getGroupsClustered() {
//         return groupsClustered.get();
//     }
//
//     @JsonProperty("readsClustered")
//     public double getReadsClustered() {
//         return readsClustered.get();
//     }
//
//     @JsonProperty("unisClustered")
//     public long getUnisClustered() {
//         return unisClustered.get();
//     }
//
//     @Override
//     public void writeReport(ReportHelper helper) {
//         int finalClones = ungroupedClones.get() + groupedClonesAfter.get();
//
//         helper
//                 .writePercentAndAbsoluteField("Clones after grouping", finalClones, clonesBefore.get())
//                 .writePercentAndAbsoluteField("Ungrouped clones", ungroupedClones.get(), finalClones)
//                 .writePercentAndAbsoluteField("Clones split", groupedClonesAfter.get(), groupedClonesBefore.get())
//                 .writePercentAndAbsoluteField("Reads in ungrouped clones", (long) readsInUngroupedClones.get(), readsBefore.get());
//
//         helper
//                 .writePercentAndAbsoluteField("Clone Tag associations after filtering", tagTuplesFiltered.get(), tagTuplesTotal.get())
//                 .writePercentAndAbsoluteField("Tags after filtering", tagsFiltered.get(), tagsTotal.get())
//                 .writePercentAndAbsoluteField("Reads after filtering", readsFiltered.get(), readsTotal.get())
//                 .writePercentAndAbsoluteField("UMIs after filtering", umisFiltered.get(), umisTotal.get());
//
//         helper
//                 .writeField("Connected components (CC)", connectedComponents.get())
//                 .writePercentAndAbsoluteField("Clones in max CC", maxClonesInConnectedComponents.get(), sumClonesPerGroup.get())
//                 .writePercentAndAbsoluteField("Reads in max CC", maxReadsInConnectedComponents.get(), sumReads.get())
//                 .writePercentAndAbsoluteField("UMIs in max CC", maxUMIsInConnectedComponents.get(), sumUMIs.get());
//
//         helper
//                 .writeField("Groups", numberOfGroups.get() - groupsClustered.get())
//                 .writeField("Groups before clustering", numberOfGroups.get())
//                 .writeField("Max clones per group", maxClonesPerGroup.get())
//                 .writeField("Mean clones per group", ReportHelper.PERCENT_FORMAT.format(1.0 * sumClonesPerGroup.get() / numberOfGroups.get()))
//                 .writeField("Max tags per group", maxTagsPerGroup.get())
//                 .writeField("Mean tags per group", ReportHelper.PERCENT_FORMAT.format(1.0 * sumTagsPerGroup.get() / numberOfGroups.get()))
//                 .writeField("Max tags clique", maxTagsClique.get())
//                 .writeField("Max tags clique delta", maxTagsCliqueDelta.get())
//                 .writeField("Mean tags clique delta", ReportHelper.PERCENT_FORMAT.format(1.0 * sumTagsCliqueDelta.get() / numberOfGroups.get()));
//
//         helper
//                 .writePercentAndAbsoluteField("Groups clustered", groupsClustered.get(), numberOfGroups.get())
//                 .writePercentAndAbsoluteField("Tags clustered", tagsClustered.get(), tagsTotal.get())
//                 .writePercentAndAbsoluteField("Reads clustered", readsClustered.get(), readsTotal.get())
//                 .writePercentAndAbsoluteField("UMIs clustered", unisClustered.get(), umisTotal.get());
//
//         helper
//                 .writeField("Effective reads in tags threshold", effectiveMinReadsInTag)
//                 .writeField("Effective UMIs in tags threshold", effectiveMinUMIsInTag);
//     }
// }
