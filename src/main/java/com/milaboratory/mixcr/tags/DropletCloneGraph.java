/*
 * Copyright (c) 2014-2020, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
package com.milaboratory.mixcr.tags;

import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.CloneSet;
import com.milaboratory.mixcr.basictypes.TagTuple;
import com.milaboratory.mixcr.util.AdjacencyMatrix;
import com.milaboratory.mixcr.util.BitArrayInt;
import com.milaboratory.util.ProgressAndStage;
import gnu.trove.impl.Constants;
import gnu.trove.iterator.TIntIntIterator;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.iterator.TObjectDoubleIterator;
import gnu.trove.iterator.TObjectIntIterator;
import gnu.trove.map.hash.*;
import gnu.trove.set.hash.TIntHashSet;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public final class DropletCloneGraph {

    private static TIntIntHashMap newTIntIntHashMap() {
        return new TIntIntHashMap(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, -1, -1);
    }

    private static <T> TObjectIntHashMap<T> newTObjectIntHashMap() {
        return new TObjectIntHashMap<>(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, -1);
    }

    private static <T> TObjectDoubleHashMap<T> newTObjectDoubleHashMap() {
        return new TObjectDoubleHashMap<>(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, -1);
    }

    public static List<CloneGroup> calculateGroups(CloneTagTupleList tuplesList,
                                                   DropletCloneGraphParameters parameters,
                                                   DropletCloneGraphReport report,
                                                   ProgressAndStage progressAndStage) {
        List<CloneTagTuple> tuples = parameters.filter.filter(tuplesList, report);
        Map<String, List<CloneTagTuple>> byTags = new HashMap<>();
        TIntObjectHashMap<List<CloneTagTuple>> byCloneId = new TIntObjectHashMap<>();
        List<CloneTagTuple> tmpList = new ArrayList<>();
        for (CloneTagTuple tup : tuples) {
            byTags.computeIfAbsent(tup.tag, __ -> new ArrayList<>()).add(tup);
            List<CloneTagTuple> list = byCloneId.putIfAbsent(tup.clone.getId(), tmpList);
            if (list == null) {
                tmpList.add(tup);
                tmpList = new ArrayList<>();
            } else
                list.add(tup);
        }

        ArrayList<CloneGroup> groups = new ArrayList<>();

        long initialTagCount = byTags.size();
        long counter = 0;

        int groupId = 0;
        // loop over connected components
        while (!byTags.isEmpty()) {
            Map<String, List<CloneTagTuple>> byTagsInComponent = new HashMap<>();
            TIntObjectHashMap<List<CloneTagTuple>> byCloneIdInComponent = new TIntObjectHashMap<>();

            // Calculating connected component starting from:
            // tag = byTags.keySet().iterator().next()
            {
                Set<String> currentTags = new HashSet<>();
                TIntHashSet currentClones = new TIntHashSet();
                currentTags.add(byTags.keySet().iterator().next());
                while (!currentTags.isEmpty()) {
                    for (String currentTag : currentTags) {
                        List<CloneTagTuple> inDroplet = byTags.remove(currentTag);
                        if (inDroplet == null)
                            continue;

                        for (CloneTagTuple tup : inDroplet) {
                            List<CloneTagTuple> list = byCloneIdInComponent.putIfAbsent(tup.clone.getId(), tmpList);
                            if (list == null) {
                                currentClones.add(tup.clone.getId());
                                tmpList.add(tup);
                                tmpList = new ArrayList<>();
                            } else
                                list.add(tup);
                        }
                    }
                    currentTags.clear();

                    TIntIterator cIt = currentClones.iterator();
                    while (cIt.hasNext())
                        for (CloneTagTuple tup : byCloneId.remove(cIt.next())) {
                            byTagsInComponent.computeIfAbsent(tup.tag, __ -> {
                                currentTags.add(tup.tag);
                                return new ArrayList<>();
                            }).add(tup);
                        }
                    currentClones.clear();
                }
            }

            report.onConnectedComponent(byTagsInComponent, byCloneIdInComponent);

            // form adjacency matrix of connected tags
            AdjacencyMatrix tagMatrix = new AdjacencyMatrix(byTagsInComponent.size());
            // tagId <-> tag
            TIntObjectHashMap<String> indexToTag = new TIntObjectHashMap<>();
            TObjectIntHashMap<String> tagToIndex = newTObjectIntHashMap();
            int idx = 0;
            for (String tag : byTagsInComponent.keySet()) {
                indexToTag.put(idx, tag);
                tagToIndex.put(tag, idx);
                ++idx;
            }

            for (int i = 0; i < indexToTag.size(); ++i) {
                tagMatrix.setConnected(i, i);
                for (CloneTagTuple tup : byTagsInComponent.get(indexToTag.get(i)))
                    for (CloneTagTuple end : byCloneIdInComponent.get(tup.clone.getId()))
                        tagMatrix.setConnected(i, tagToIndex.get(end.tag));
            }

            List<BitArrayInt> tagCliques = null;
            List<CliqueInfo> tagCliqueInfos = null;
            // searching all cliques in tags
            while (!tagToIndex.isEmpty()) {
                if (tagCliques == null) {
                    tagCliques = tagMatrix.calculateMaximalCliques();
                    tagCliqueInfos = new ArrayList<>();
                    for (BitArrayInt clique : tagCliques) {
                        int[] bits = clique.getBits();
                        if (bits.length == 1 && !tagMatrix.isConnected(bits[0], bits[0]))
                            continue;

                        TIntIntHashMap nTagsByClones = newTIntIntHashMap();

                        double readCount = 0;
                        for (int tagIndex : bits)
                            for (CloneTagTuple tup : byTagsInComponent.get(indexToTag.get(tagIndex))) {
                                nTagsByClones.adjustOrPutValue(tup.clone.getId(), 1, 1);
                                readCount += tup.readCount;
                            }


                        TIntIntIterator it = nTagsByClones.iterator();
                        double score = 0;
                        while (it.hasNext()) {
                            it.advance();
                            int count = it.value();

                            score += Math.pow(count - 1, parameters.tagCliqueScorePower);
                        }

                        tagCliqueInfos.add(new CliqueInfo(clique, score, readCount));
                    }
                }

                Optional<CliqueInfo> topTagCliqueO = tagCliqueInfos.stream().max(CliqueInfo::compareTo);
                if (!topTagCliqueO.isPresent())
                    break;

                CliqueInfo topTagClique = topTagCliqueO.get();

                Set<String> tags = new HashSet<>();
                int[] topTagCliqueBits = topTagClique.clique.getBits();
                for (int i : topTagCliqueBits)
                    tags.add(indexToTag.get(i));

                TIntIntHashMap indexToCloneId = newTIntIntHashMap();
                // !!! used as cloneId -> count in the loop below
                TIntIntHashMap cloneIdToIndex = newTIntIntHashMap();

                {
                    for (String tag : tags)
                        for (CloneTagTuple tup : byTagsInComponent.get(tag))
                            cloneIdToIndex.adjustOrPutValue(tup.clone.getId(), 1, 1);

                    idx = 0;
                    TIntIntIterator it = cloneIdToIndex.iterator();
                    while (it.hasNext()) {
                        it.advance();
                        assert it.value() > 0;
                        // if tags.size == 1 => single tag in clique, so just writing
                        // index (presence of clones is enough, i.e. do not require clones to link any tags)
                        if (tags.size() > 1 && it.value() == 1)
                            it.remove();
                        else {
                            indexToCloneId.put(idx, it.key());
                            it.setValue(idx);
                            ++idx;
                        }
                    }
                }

                AdjacencyMatrix clonesMatrix = new AdjacencyMatrix(cloneIdToIndex.size());

                for (String tag : tags) {
                    List<CloneTagTuple> tuplesForTag = byTagsInComponent.get(tag);
                    for (int i1 = 0; i1 < tuplesForTag.size(); i1++)
                        for (int i2 = i1 + 1; i2 < tuplesForTag.size(); i2++) {
                            int index1 = cloneIdToIndex.get(tuplesForTag.get(i1).clone.getId());
                            int index2 = cloneIdToIndex.get(tuplesForTag.get(i2).clone.getId());
                            if (index1 >= 0 && index2 >= 0)
                                clonesMatrix.setConnected(index1, index2);
                        }
                }

                List<BitArrayInt> cloneCliques = clonesMatrix.calculateMaximalCliques();
                List<CliqueInfo> cloneCliqueInfos = new ArrayList<>();

                for (BitArrayInt clique : cloneCliques) {
                    TObjectIntHashMap<String> nClonesByTags = newTObjectIntHashMap();

                    double readCount = 0;
                    for (int cloneIndex : clique.getBits())
                        for (CloneTagTuple tup : byCloneIdInComponent.get(indexToCloneId.get(cloneIndex)))
                            if (tags.contains(tup.tag)) {
                                nClonesByTags.adjustOrPutValue(tup.tag, 1, 1);
                                readCount += tup.readCount;
                            }

                    TObjectIntIterator<String> it = nClonesByTags.iterator();
                    double score = 0;
                    while (it.hasNext()) {
                        it.advance();
                        int count = it.value();

                        score += Math.pow(count - 1, parameters.cloneCliqueScorePower);
                    }

                    cloneCliqueInfos.add(new CliqueInfo(clique, score, readCount));
                }

                CliqueInfo topCloneClique = cloneCliqueInfos.stream().max(CliqueInfo::compareTo).get();

                // dropping tags

                Set<String> groupTags = new HashSet<>();
                TIntHashSet groupClones = new TIntHashSet();

                for (int cloneIdx : topCloneClique.clique.getBits()) {
                    for (CloneTagTuple tup : byCloneIdInComponent.get(indexToCloneId.get(cloneIdx))) {
                        if (tags.contains(tup.tag)) {
                            String tag = tup.tag;

                            int index = tagToIndex.remove(tag);
                            ++counter;
                            if (progressAndStage != null)
                                progressAndStage.setProgress(1.0 * counter / initialTagCount);

                            if (index >= 0) {
                                indexToTag.remove(index);
                                tagMatrix.clear(index);
                            }

                            groupClones.add(tup.clone.getId());
                            groupTags.add(tag);
                        }
                    }
                }

                double readTotal = 0.0;
                long umiTotal = 0;

                for (String tag : groupTags)
                    for (CloneTagTuple ctt : byTagsInComponent.get(tag)) {
                        readTotal += ctt.readCount;
                        umiTotal += ctt.umiCount;
                    }

                CloneGroup group = new CloneGroup(groupId, readTotal, umiTotal, groupTags, groupClones);
                report.onGroup(topTagClique, topCloneClique, group);
                groups.add(group);

                tagCliques = null;

                ++groupId;
            }
        }

        // Clustering
        TIntObjectHashMap<List<AtomicReference<CloneGroup>>> byClones = new TIntObjectHashMap<>();
        groups.sort(Comparator
                .<CloneGroup>comparingInt(c -> c.groupClones.size())
                .thenComparingInt(c -> c.groupTags.size())
                .reversed());

        ArrayList<AtomicReference<CloneGroup>> groupsAfterClustering = new ArrayList<>();
        ArrayList<AtomicReference<CloneGroup>> tmpList2 = new ArrayList<>();
        for (CloneGroup minor : groups) {
            // Major candidates
            Set<AtomicReference<CloneGroup>> majorCandidates = Collections.newSetFromMap(new IdentityHashMap<>());
            int[] minorCloneIds = minor.groupClones.toArray();
            for (int cId : minorCloneIds) {
                List<AtomicReference<CloneGroup>> grps = byClones.get(cId);
                if (grps == null)
                    break; // This guarantees that no candidates will be found (such clone was never seen before)
                for (AtomicReference<CloneGroup> preCandidate : grps)
                    if (preCandidate.get().groupClones.containsAll(minor.groupClones)
                            && 1.0 * minor.groupTags.size() / preCandidate.get().groupTags.size() <= parameters.maxTagCountRatio)
                        majorCandidates.add(preCandidate);
            }
            if (majorCandidates.size() != 1) {
                AtomicReference<CloneGroup> minorRef = new AtomicReference<>(minor);
                groupsAfterClustering.add(minorRef);
                for (int cId : minorCloneIds) {
                    List<AtomicReference<CloneGroup>> cloneGroups = byClones.putIfAbsent(cId, tmpList2);
                    if (cloneGroups != null)
                        cloneGroups.add(minorRef);
                    else {
                        tmpList2.add(minorRef);
                        tmpList2 = new ArrayList<>();
                    }
                }
            } else { // Clustering
                AtomicReference<CloneGroup> majorRef = majorCandidates.iterator().next();
                report.onClustered(majorRef.get(), minor);
                majorRef.set(majorRef.get().mergeFrom(minor));
            }
        }

        if (progressAndStage != null)
            progressAndStage.setFinished(true);

        return groupsAfterClustering.stream().map(AtomicReference::get).collect(Collectors.toList());
    }

    public static CloneTagTupleList calculateTuples(CloneSet cloneset, int tagIndex) {
        TObjectDoubleHashMap<String> readsPerDroplet = newTObjectDoubleHashMap();

        // Approximation: same UMIs in different clonotypes will be counted several times
        TObjectLongHashMap<String> umisPerDroplet = new TObjectLongHashMap<>();

        for (Clone clone : cloneset) {
            TObjectDoubleIterator<TagTuple> it = clone.getTagCounter().iterator();
            while (it.hasNext()) {
                it.advance();
                umisPerDroplet.adjustOrPutValue(it.key().tags[tagIndex], 1, 1);
                readsPerDroplet.adjustOrPutValue(it.key().tags[tagIndex], it.value(), it.value());
            }
        }

        List<CloneTagTuple> links = new ArrayList<>();
        for (Clone clone : cloneset) {
            Map<String, CloneTagTuple> mlink = new HashMap<>();
            TObjectDoubleIterator<TagTuple> it = clone.getTagCounter().iterator();
            while (it.hasNext()) {
                it.advance();
                CloneTagTuple link = mlink.computeIfAbsent(it.key().tags[tagIndex], tag -> new CloneTagTuple(clone, tag));
                link.umiCount++;
                link.readCount += it.value();
            }
            // ArrayList<DropletCloneLink> alinks = new ArrayList<>(mlink.size());
            for (CloneTagTuple link : mlink.values()) {
                link.readFractionInTag = link.readCount / readsPerDroplet.get(link.tag);
                link.umiFractionInTag = 1.0 * link.umiCount / umisPerDroplet.get(link.tag);
                links.add(link);
            }
        }
        links.sort(Comparator.
                <CloneTagTuple>comparingLong(l -> l.umiCount)
                .thenComparingDouble(l -> l.readCount)
                .reversed());
        TObjectIntHashMap<String> rankCounter = new TObjectIntHashMap<>();
        for (CloneTagTuple link : links)
            link.rank = rankCounter.adjustOrPutValue(link.tag, 1, 1);

        return new CloneTagTupleList(readsPerDroplet, umisPerDroplet, links);
    }

    public static final class CloneTagTupleList {
        final TObjectDoubleHashMap<String> readsPerDroplet;
        final TObjectLongHashMap<String> umisPerDroplet;
        final List<CloneTagTuple> tuples;

        public CloneTagTupleList(TObjectDoubleHashMap<String> readsPerDroplet,
                                 TObjectLongHashMap<String> umisPerDroplet,
                                 List<CloneTagTuple> tuples) {
            this.readsPerDroplet = readsPerDroplet;
            this.umisPerDroplet = umisPerDroplet;
            this.tuples = tuples;
        }

        public double readsTopQuantile(double quantile) {
            if (quantile > 1.0 || quantile < 0)
                throw new IllegalArgumentException();

            double[] values = readsPerDroplet.values();
            if (values.length == 0)
                return 0;

            Arrays.sort(values);
            int index = (int) Math.floor(quantile * values.length);
            if (index >= values.length)
                index = values.length - 1;

            return values[index];
        }

        public double umisTopQuantile(double quantile) {
            if (quantile > 1.0 || quantile < 0)
                throw new IllegalArgumentException();

            long[] values = umisPerDroplet.values();
            if (values.length == 0)
                return 0;

            Arrays.sort(values);
            int index = (int) Math.floor(quantile * values.length);
            if (index >= values.length)
                index = values.length - 1;

            return values[index];
        }
    }

    static final class CliqueInfo implements Comparable<CliqueInfo> {
        final BitArrayInt clique;
        final double score;
        final double readCount;

        public CliqueInfo(BitArrayInt clique, double score, double readCount) {
            this.clique = clique;
            this.score = score;
            this.readCount = readCount;
        }

        @Override
        public int compareTo(DropletCloneGraph.CliqueInfo o) {
            int c = Double.compare(score, o.score);
            if (c != 0)
                return c;
            return Double.compare(readCount, o.readCount);
        }
    }

}
