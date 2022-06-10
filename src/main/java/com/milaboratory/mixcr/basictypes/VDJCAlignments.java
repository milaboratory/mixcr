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
package com.milaboratory.mixcr.basictypes;

import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.alignment.AlignmentUtils;
import com.milaboratory.core.io.sequence.SequenceRead;
import com.milaboratory.core.io.sequence.SequenceReadUtil;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.assembler.ReadToCloneMapping;
import com.milaboratory.mixcr.basictypes.tag.TagCount;
import com.milaboratory.primitivio.annotations.Serializable;
import com.milaboratory.util.ArraysUtils;
import gnu.trove.map.hash.TLongObjectHashMap;
import io.repseq.core.GeneType;

import java.util.*;
import java.util.function.Function;

@Serializable(by = IO.VDJCAlignmentsSerializer.class)
public final class VDJCAlignments extends VDJCObject {
    final SequenceHistory[] history;
    final SequenceRead[] originalReads;
    final byte mappingType;
    final long cloneIndex;
    private volatile long alignmentsIndex = -1;

    public VDJCAlignments(long alignmentsIndex,
                          EnumMap<GeneType, VDJCHit[]> hits,
                          TagCount tagCount,
                          NSequenceWithQuality[] targets,
                          SequenceHistory[] history,
                          SequenceRead[] originalReads,
                          byte mappingType, long cloneIndex) {
        super(hits, tagCount, targets);

        if (!ReadToCloneMapping.isCorrect(mappingType) ||
                (ReadToCloneMapping.isDropped(mappingType) && cloneIndex != -1))
            throw new IllegalArgumentException();

        this.alignmentsIndex = alignmentsIndex;
        this.history = history;
        this.originalReads = originalReads;
        this.mappingType = mappingType;
        this.cloneIndex = cloneIndex;
    }

    public VDJCAlignments(long alignmentsIndex,
                          EnumMap<GeneType, VDJCHit[]> hits,
                          TagCount tagCount,
                          NSequenceWithQuality[] targets,
                          SequenceHistory[] history,
                          SequenceRead[] originalReads) {
        this(alignmentsIndex, hits, tagCount, targets, history, originalReads,
                ReadToCloneMapping.DROPPED_MASK, -1);
    }

    public VDJCAlignments(EnumMap<GeneType, VDJCHit[]> hits,
                          TagCount tagCount,
                          NSequenceWithQuality[] targets,
                          SequenceHistory[] history,
                          SequenceRead[] originalReads,
                          byte mappingType, long cloneIndex) {
        this(-1, hits, tagCount, targets, history, originalReads, mappingType, cloneIndex);
    }

    public VDJCAlignments(EnumMap<GeneType, VDJCHit[]> hits,
                          TagCount tagCount,
                          NSequenceWithQuality[] targets,
                          SequenceHistory[] history,
                          SequenceRead[] originalReads) {
        this(-1, hits, tagCount, targets, history, originalReads);
    }

    public VDJCAlignments(VDJCHit[] vHits, VDJCHit[] dHits, VDJCHit[] jHits, VDJCHit[] cHits,
                          TagCount tagCount,
                          NSequenceWithQuality[] targets,
                          SequenceHistory[] history,
                          SequenceRead[] originalReads) {
        this(-1, createHits(vHits, dHits, jHits, cHits), tagCount, targets, history, originalReads);
    }

    public VDJCAlignments shiftIndelsAtHomopolymers(Set<GeneType> gts) {
        return mapHits(h -> gts.contains(h.getGeneType())
                ? h.mapAlignments(AlignmentUtils::shiftIndelsAtHomopolymers)
                : h);
    }

    public VDJCAlignments mapHits(Function<VDJCHit, VDJCHit> mapper) {
        EnumMap<GeneType, VDJCHit[]> result = new EnumMap<>(GeneType.class);
        for (Map.Entry<GeneType, VDJCHit[]> e : hits.entrySet())
            result.put(e.getKey(), Arrays.stream(e.getValue()).map(mapper).toArray(VDJCHit[]::new));
        return new VDJCAlignments(alignmentsIndex, result, tagCount, targets, history, originalReads, mappingType, cloneIndex);
    }

    public boolean isClustered() {
        return ReadToCloneMapping.isClustered(mappingType);
    }

    public boolean isMapped() {
        return ReadToCloneMapping.isMapped(mappingType);
    }

    public boolean isDroppedWithClone() {
        return ReadToCloneMapping.isDroppedWithClone(mappingType);
    }

    public boolean isDropped() {
        return ReadToCloneMapping.isDropped(mappingType);
    }

    public boolean isPreClustered() {
        return ReadToCloneMapping.isPreClustered(mappingType);
    }

    public ReadToCloneMapping.MappingType getMappingType() {
        return ReadToCloneMapping.getMappingType(mappingType);
    }

    public long getCloneIndex() {
        return cloneIndex;
    }

    public VDJCAlignments setTagCount(TagCount tc) {
        return new VDJCAlignments(alignmentsIndex, hits, tc, targets, history, originalReads, mappingType, cloneIndex);
    }

    /** This strips all non-key information from tags */
    public VDJCAlignments ensureKeyTags() {
        TagCount count = getTagCount();
        if (count.isNonKeySingleton())
            return setTagCount(count.ensureIsKey());
        else
            return this;
    }

    public VDJCAlignments setMapping(ReadToCloneMapping mapping) {
        return new VDJCAlignments(alignmentsIndex, hits, tagCount, targets, history, originalReads,
                mapping.getMappingTypeByte(), mapping.getCloneIndex());
    }

    public VDJCAlignments withCloneIndex(long newCloneIndex) {
        return new VDJCAlignments(alignmentsIndex, hits, tagCount, targets, history, originalReads,
                mappingType, newCloneIndex);
    }

    public VDJCAlignments withCloneIndexAndMappingType(long newCloneIndex, byte newMappingType) {
        return new VDJCAlignments(alignmentsIndex, hits, tagCount, targets, history, originalReads,
                newMappingType, newCloneIndex);
    }

    public VDJCAlignments updateAlignments(Function<Alignment<NucleotideSequence>, Alignment<NucleotideSequence>> processor) {
        EnumMap<GeneType, VDJCHit[]> newHits = this.hits.clone();
        newHits.replaceAll((k, v) -> Arrays.stream(v).map(h -> h.mapAlignments(processor)).toArray(VDJCHit[]::new));
        return new VDJCAlignments(alignmentsIndex, newHits, tagCount, targets, history, originalReads, mappingType, cloneIndex);
    }

    public VDJCAlignments shiftReadId(long newAlignmentIndex, long shift) {
        return new VDJCAlignments(newAlignmentIndex, hits, tagCount, targets, shift(history, shift), shift(originalReads, shift));
    }

    public static SequenceRead[] mergeOriginalReads(VDJCAlignments... array) {
        boolean isNull = array[0].originalReads == null;
        TLongObjectHashMap<SequenceRead> map = new TLongObjectHashMap<>();
        for (VDJCAlignments a : array) {
            if (isNull != (a.originalReads == null))
                throw new IllegalArgumentException();
            if (a.originalReads != null)
                for (SequenceRead s : a.originalReads)
                    map.put(s.getId(), s);
        }
        if (isNull)
            return null;
        return map.values(new SequenceRead[map.size()]);
    }

    private static SequenceHistory[] shift(SequenceHistory[] data, long shift) {
        SequenceHistory[] r = new SequenceHistory[data.length];
        for (int i = 0; i < data.length; i++)
            r[i] = data[i].shiftReadId(shift);
        return r;
    }

    private static SequenceRead[] shift(SequenceRead[] data, long shift) {
        if (data == null)
            return null;
        SequenceRead[] r = new SequenceRead[data.length];
        for (int i = 0; i < data.length; i++)
            r[i] = SequenceReadUtil.setReadId(data[i].getId() + shift, data[i]);
        return r;
    }

    public SequenceHistory getHistory(int targetId) {
        return history[targetId];
    }

    public SequenceHistory[] getHistory() {
        return history.clone();
    }

    public NSequenceWithQuality getOriginalSequence(SequenceHistory.FullReadIndex index) {
        if (originalReads == null)
            throw new IllegalStateException("Original reads are not saved for the alignment object.");
        SequenceRead read = null;
        for (SequenceRead originalRead : originalReads)
            if (originalRead.getId() == index.readId) {
                read = originalRead;
                break;
            }
        if (read == null)
            throw new IllegalArgumentException("No such read index: " + index);
        NSequenceWithQuality seq = read.getRead(index.mateIndex).getData();
        return index.isReverseComplement ? seq.getReverseComplement() : seq;
    }

    public List<SequenceRead> getOriginalReads() {
        return originalReads == null ? null : Collections.unmodifiableList(Arrays.asList(originalReads));
    }

    private long[] readIds = null;

    /**
     * The result is always sorted.
     */
    public long[] getReadIds() {
        if (readIds == null) {
            long[] result = new long[0];
            for (SequenceHistory h : history)
                result = ArraysUtils.getSortedDistinct(ArraysUtils.concatenate(result, h.readIds()));
            readIds = result;
        }
        return readIds;
    }

    public int getNumberOfReads() {
        return getReadIds().length;
    }

    public VDJCAlignments setHistory(SequenceHistory[] history, SequenceRead[] originalReads) {
        return new VDJCAlignments(alignmentsIndex, hits, tagCount, targets, history, originalReads);
    }

    public VDJCAlignments removeBestHitAlignment(GeneType geneType, int targetId) {
        if (getBestHit(geneType) == null)
            return this;
        EnumMap<GeneType, VDJCHit[]> hits = new EnumMap<>(this.hits);
        VDJCHit[] gHits = hits.get(geneType).clone();
        Alignment<NucleotideSequence>[] als = gHits[0].getAlignments().clone();
        als[targetId] = null;
        gHits[0] = new VDJCHit(gHits[0].getGene(), als, gHits[0].getAlignedFeature());
        Arrays.sort(gHits);
        hits.put(geneType, gHits);
        return new VDJCAlignments(alignmentsIndex, hits, tagCount, targets, history, originalReads);
    }

    public boolean hasNoHitsInTarget(int i) {
        for (VDJCHit[] vdjcHits : hits.values()) {
            if (vdjcHits == null)
                continue;
            for (VDJCHit hit : vdjcHits)
                if (hit.getAlignment(i) != null)
                    return false;
        }
        return true;
    }

    private volatile long minReadId = -1;

    public long getMinReadId() {
        if (minReadId != -1)
            return minReadId;
        long min = Long.MAX_VALUE;
        for (SequenceHistory s : history)
            min = Math.min(s.minReadId(), min);
        return minReadId = min;
    }

    public long getAlignmentsIndex() {
        return alignmentsIndex;
    }

    public VDJCAlignments setAlignmentsIndex(long alignmentsIndex) {
        this.alignmentsIndex = alignmentsIndex;
        return this;
    }

    /**
     * Returns {@code true} if at least one V and one J hit among first {@code top} hits have same chain and false
     * otherwise (first {@code top} V hits have different chain from those have first {@code top} J hits).
     *
     * @param top numer of top hits to test
     * @return {@code true} if at least one V and one J hit among first {@code top} hits have same chain and false
     * otherwise (first {@code top} V hits have different chain from those have first {@code top} J hits)
     */
    public final boolean hasSameVJLoci(final int top) {
        final VDJCHit[] vHits = hits.get(GeneType.Variable),
                jHits = hits.get(GeneType.Joining),
                cHits = hits.get(GeneType.Constant);

        if (vHits.length > 0 && jHits.length > 0 && cHits.length > 0) {
            for (int v = 0; v < actualTop(vHits, top); ++v)
                for (int j = 0; j < actualTop(jHits, top); ++j)
                    for (int c = 0; c < actualTop(cHits, top); ++c)
                        if (hasCommonChain(vHits[v], jHits[j]) && hasCommonChain(vHits[v], cHits[c]))
                            return true;
            return false;
        }

        if (vHits.length > 0 && jHits.length > 0) {
            for (int v = 0; v < actualTop(vHits, top); ++v)
                for (int j = 0; j < actualTop(jHits, top); ++j)
                    if (hasCommonChain(vHits[v], jHits[j]))
                        return true;
            return false;
        }

        if (vHits.length > 0 && cHits.length > 0) {
            for (int v = 0; v < actualTop(vHits, top); ++v)
                for (int c = 0; c < actualTop(cHits, top); ++c)
                    if (hasCommonChain(vHits[v], cHits[c]))
                        return true;
            return false;
        }

        if (cHits.length > 0 && jHits.length > 0) {
            for (int c = 0; c < actualTop(cHits, top); ++c)
                for (int j = 0; j < actualTop(jHits, top); ++j)
                    if (hasCommonChain(cHits[c], jHits[j]))
                        return true;
            return false;
        }

        return true;
    }

    private static boolean hasCommonChain(VDJCHit g1, VDJCHit g2) {
        return g1.getGene().getChains().intersects(g2.getGene().getChains());
    }

    private static int actualTop(VDJCHit[] hits, int top) {
        if (hits.length <= top)
            return hits.length;
        float score = hits[top].getScore() - Float.MIN_VALUE;
        while (top < hits.length &&
                hits[top].getScore() >= score) {
            ++top;
        }
        return top;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        VDJCAlignments that = (VDJCAlignments) o;

        return Arrays.equals(history, that.history);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + Arrays.hashCode(history);
        return result;
    }
}
