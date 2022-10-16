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
package com.milaboratory.mixcr.basictypes

import cc.redberry.primitives.Filter
import com.milaboratory.mixcr.assembler.CloneAssemblerParameters
import com.milaboratory.mixcr.basictypes.VDJCSProperties.CloneOrdering
import com.milaboratory.mixcr.basictypes.tag.TagCount
import com.milaboratory.mixcr.basictypes.tag.TagCountAggregator
import com.milaboratory.mixcr.basictypes.tag.TagsInfo
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import io.repseq.core.VDJCGene
import io.repseq.core.VDJCGeneId
import java.util.*
import java.util.function.Function
import java.util.stream.Collectors

/**
 * Created by poslavsky on 10/07/14.
 */
class CloneSet : Iterable<Clone?>, MiXCRFileInfo, HasFeatureToAlign {
    @JvmField
    var versionInfo: String? = null

    @JvmField
    override val header: MiXCRHeader?

    @JvmField
    override val footer: MiXCRFooter?

    @JvmField
    val ordering: CloneOrdering
    val usedGenes: List<VDJCGene>
    val clones: MutableList<Clone>
    val totalCount: Double
    val totalTagCounts: TagCount?

    /** Total number of unique tag combinations on each level  */
    private val tagDiversity: IntArray?

    constructor(
        clones: List<Clone>, usedGenes: Collection<VDJCGene>?,
        header: MiXCRHeader?, footer: MiXCRFooter?,
        ordering: CloneOrdering
    ) {
        val list = ArrayList(clones)
        list.sort(ordering.comparator())
        this.header = header
        this.footer = footer
        this.ordering = ordering
        this.usedGenes = Collections.unmodifiableList(ArrayList(usedGenes))
        this.clones = Collections.unmodifiableList(list)
        var totalCount: Long = 0
        val tagDiversity = IntArray(header!!.tagsInfo.size + 1)
        val tagCountAggregator = TagCountAggregator()
        for (clone in clones) {
            totalCount += clone.count.toLong()
            clone.parentCloneSet = this
            require(clone.tagCount.depth() == header.tagsInfo.size) { "Conflict in tags info and clone tag counter." }
            tagCountAggregator.add(clone.tagCount)
            for (d in tagDiversity.indices) tagDiversity[d] += clone.getTagCount().getTagDiversity(d)
        }
        totalTagCounts = if (clones.size == 0) null else tagCountAggregator.createAndDestroy()
        this.tagDiversity = tagDiversity
        this.totalCount = totalCount.toDouble()
    }

    /** To be used in tests only  */
    constructor(clones: List<Clone>) {
        this.clones = Collections.unmodifiableList(ArrayList(clones))
        var totalCount: Long = 0
        val genes = HashMap<VDJCGeneId, VDJCGene>()
        val alignedFeatures = EnumMap<GeneType, GeneFeature>(GeneType::class.java)
        val tagCountAggregator = TagCountAggregator()
        for (clone in clones) {
            totalCount += clone.count.toLong()
            tagCountAggregator.add(clone.tagCount)
            clone.parentCloneSet = this
            for (geneType in GeneType.values()) for (hit in clone.getHits(geneType)) {
                val gene = hit.gene
                genes[gene.id] = gene
                val alignedFeature = hit.alignedFeature
                val f = alignedFeatures.put(geneType, alignedFeature)
                require(!(f != null && f != alignedFeature)) { "Different aligned feature for clones." }
            }
        }
        totalTagCounts = if (clones.size == 0) null else tagCountAggregator.createAndDestroy()
        header = null
        footer = null
        tagDiversity = null
        ordering = CloneOrdering()
        usedGenes = Collections.unmodifiableList(ArrayList(genes.values))
        this.totalCount = totalCount.toDouble()
    }

    fun getClones(): List<Clone> {
        return clones
    }

    operator fun get(i: Int): Clone {
        return clones[i]
    }

    fun size(): Int {
        return clones.size
    }

    val isHeaderAvailable: Boolean
        get() = header != null

    override fun getHeader(): MiXCRHeader? {
        return Objects.requireNonNull(header)
    }

    val isFooterAvailable: Boolean
        get() = footer != null

    override fun getFooter(): MiXCRFooter? {
        return Objects.requireNonNull(footer)
    }

    fun withHeader(header: MiXCRHeader?): CloneSet {
        return CloneSet(clones, usedGenes, header, footer, ordering)
    }

    fun withFooter(footer: MiXCRFooter?): CloneSet {
        return CloneSet(clones, usedGenes, header, footer, ordering)
    }

    val assemblingFeatures: Array<GeneFeature>
        get() = header!!.assemblerParameters!!.assemblingFeatures
    val assemblerParameters: CloneAssemblerParameters?
        get() = header!!.assemblerParameters
    val alignmentParameters: VDJCAlignerParameters
        get() = header!!.alignerParameters
    val tagsInfo: TagsInfo
        get() = header!!.tagsInfo

    override fun getFeatureToAlign(geneType: GeneType): GeneFeature {
        return header!!.alignerParameters.getFeatureToAlign(geneType)
    }

    fun getTagDiversity(level: Int): Int {
        return tagDiversity!![level]
    }

    override fun iterator(): MutableIterator<Clone> {
        return clones.iterator()
    }

    companion object {
        /**
         * WARNING: current object (in) will be destroyed
         */
        fun reorder(`in`: CloneSet, newOrdering: CloneOrdering): CloneSet {
            val newClones = ArrayList(`in`.clones)
            newClones.sort(newOrdering.comparator())
            for (nc in newClones) nc.parent = null
            return CloneSet(newClones, `in`.usedGenes, `in`.header, `in`.footer, newOrdering)
        }

        /**
         * WARNING: current object (in) will be destroyed
         */
        fun transform(`in`: CloneSet, filter: Filter<Clone?>): CloneSet {
            val newClones: MutableList<Clone> = ArrayList(`in`.size())
            for (i in 0 until `in`.size()) {
                val c = `in`[i]
                if (filter.accept(c)) {
                    c.parent = null
                    newClones.add(c)
                }
            }
            return CloneSet(newClones, `in`.usedGenes, `in`.header, `in`.footer, `in`.ordering)
        }

        /**
         * WARNING: current object (in) will be destroyed
         */
        fun <T> split(`in`: CloneSet, splitter: Function<Clone?, T>): Map<T, CloneSet> {
            val clonesMap: MutableMap<T, MutableList<Clone>> = HashMap()
            for (i in 0 until `in`.size()) {
                val c = `in`[i]
                val key = splitter.apply(c)
                val cloneList = clonesMap.computeIfAbsent(key) { __: T -> ArrayList() }
                c.parent = null
                cloneList.add(c)
            }
            return clonesMap.entries
                .stream()
                .collect(
                    Collectors.toMap<Map.Entry<T, List<Clone>>, T, CloneSet>(
                        Function<Map.Entry<T, List<Clone>>, T> { (key, value) -> java.util.Map.Entry.key },
                        Function { (_, value): Map.Entry<T, List<Clone>> ->
                            CloneSet(
                                value, `in`.usedGenes, `in`.header, `in`.footer, `in`.ordering
                            )
                        }
                    ))
        }
    }
}
