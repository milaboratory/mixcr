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

import com.milaboratory.mitool.data.CriticalThresholdCollection
import com.milaboratory.mitool.data.CriticalThresholdKey
import com.milaboratory.mitool.helpers.readList
import com.milaboratory.mitool.helpers.writeList
import com.milaboratory.mitool.pattern.search.BasicSerializer
import com.milaboratory.mitool.pattern.search.readObject
import com.milaboratory.mixcr.MiXCRParamsSpec
import com.milaboratory.mixcr.assembler.CloneAssemblerParameters
import com.milaboratory.mixcr.basictypes.tag.TagsInfo
import com.milaboratory.mixcr.cli.MiXCRCommandReport
import com.milaboratory.mixcr.cli.MultipleInputsReportWrapper
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters
import com.milaboratory.primitivio.*
import com.milaboratory.primitivio.annotations.Serializable
import io.repseq.core.GeneFeature
import io.repseq.core.VDJCLibraryId
import io.repseq.dto.VDJCLibraryData

interface MiXCRFileInfo {
    /** Returns information from .vdjca/.clna/.clns file header  */
    val header: MiXCRHeader

    /** Returns information from .vdjca/.clna/.clns file footer  */
    val footer: MiXCRFooter
}

/**
 * This class represents common meta-information stored in the headers of vdjca/clna/clns files.
 * The information that is relevant for the downstream analysis.
 */
@Serializable(by = MiXCRHeader.SerializerImpl::class)
data class MiXCRHeader(
    /** Set by used on align step, used to deduce defaults on all downstream steps  */
    val paramsSpec: MiXCRParamsSpec,
    /** Positional descriptors for tag tuples attached to objects in the file */
    val tagsInfo: TagsInfo = TagsInfo.NO_TAGS,
    /** Aligner parameters */
    val alignerParameters: VDJCAlignerParameters,
    /** Clone assembler parameters  */
    val assemblerParameters: CloneAssemblerParameters? = null,
    /** Library produced by search of alleles */
    val foundAlleles: FoundAlleles?,
    /** If all clones cut by the same feature and cover this feature fully */
    val allFullyCoveredBy: GeneFeatures?
) {
    fun withTagInfo(tagsInfo: TagsInfo): MiXCRHeader =
        copy(tagsInfo = tagsInfo)

    fun updateTagInfo(tagsInfoUpdate: (TagsInfo) -> TagsInfo): MiXCRHeader =
        copy(tagsInfo = tagsInfoUpdate(tagsInfo))

    fun withAssemblerParameters(assemblerParameters: CloneAssemblerParameters): MiXCRHeader =
        copy(assemblerParameters = assemblerParameters)

    fun withAllClonesCutBy(allClonesAlignedBy: Array<GeneFeature>) =
        copy(allFullyCoveredBy = GeneFeatures(allClonesAlignedBy))

    @Serializable(by = FoundAlleles.SerializerImpl::class)
    data class FoundAlleles(
        val libraryName: String,
        val libraryData: VDJCLibraryData
    ) {
        val libraryIdWithoutChecksum: VDJCLibraryId get() = VDJCLibraryId(libraryName, libraryData.taxonId)

        class SerializerImpl : BasicSerializer<FoundAlleles>() {
            override fun write(output: PrimitivO, obj: FoundAlleles) {
                output.writeObject(obj.libraryName)
                output.writeObject(obj.libraryData)
            }

            override fun read(input: PrimitivI): FoundAlleles {
                val libraryName = input.readObjectRequired<String>()
                val libraryData = input.readObjectRequired<VDJCLibraryData>()
                return FoundAlleles(
                    libraryName,
                    libraryData
                )
            }

            override fun isReference(): Boolean = true
        }
    }

    class SerializerImpl : BasicSerializer<MiXCRHeader>() {
        override fun write(output: PrimitivO, obj: MiXCRHeader) {
            output.writeObject(obj.paramsSpec)
            output.writeObject(obj.tagsInfo)
            output.writeObject(obj.alignerParameters)
            output.writeObject(obj.assemblerParameters)
            output.writeObject(obj.foundAlleles)
            output.writeObject(obj.allFullyCoveredBy)
        }

        override fun read(input: PrimitivI): MiXCRHeader {
            val paramsSpec = input.readObjectRequired<MiXCRParamsSpec>()
            val tagsInfo = input.readObjectRequired<TagsInfo>()
            val alignerParameters = input.readObjectRequired<VDJCAlignerParameters>()
            val assemblerParameters = input.readObjectOptional<CloneAssemblerParameters>()
            val foundAlleles = input.readObjectOptional<FoundAlleles>()
            val allFullyCoveredBy = input.readObjectOptional<GeneFeatures>()
            return MiXCRHeader(
                paramsSpec,
                tagsInfo,
                alignerParameters,
                assemblerParameters,
                foundAlleles,
                allFullyCoveredBy
            )
        }
    }
}

class MiXCRHeaderMerger {
    private var paramsSpec: MiXCRParamsSpec? = null
    private var tagsInfo: TagsInfo? = null
    private var alignerParameters: VDJCAlignerParameters? = null
    private var assemblerParameters: CloneAssemblerParameters? = null
    private var allFullyCoveredBy: GeneFeatures? = null
    // TODO something seems to be done with alleles here ?

    fun add(header: MiXCRHeader) = run {
        if (paramsSpec == null) {
            paramsSpec = header.paramsSpec
            tagsInfo = header.tagsInfo
            alignerParameters = header.alignerParameters
            assemblerParameters = header.assemblerParameters
            allFullyCoveredBy = header.allFullyCoveredBy
        } else {
            if (paramsSpec != header.paramsSpec)
                throw IllegalArgumentException("Different analysis specs")
            if (tagsInfo != header.tagsInfo)
                throw IllegalArgumentException("Different tag structure")
            if (alignerParameters != header.alignerParameters)
                throw IllegalArgumentException("Different alignment parameters")
            if (assemblerParameters != header.assemblerParameters)
                throw IllegalArgumentException("Different assemble parameters")
            if (allFullyCoveredBy != header.allFullyCoveredBy)
                throw IllegalArgumentException("Different covered region")
        }
        this
    }

    fun build() =
        MiXCRHeader(paramsSpec!!, tagsInfo!!, alignerParameters!!, assemblerParameters, null, allFullyCoveredBy)
}

/** Information stored in the footer of */
@Serializable(by = MiXCRFooter.Companion.IO::class)
data class MiXCRFooter(
    /** Step reports in the order they were applied to the initial data */
    val reports: List<MiXCRCommandReport>,
    /** Collection of thresholds automatically or manually determined, to better select default parameters on
     * downstream steps*/
    val thresholds: CriticalThresholdCollection = CriticalThresholdCollection(),
) {
    fun withThreshold(key: CriticalThresholdKey, value: Double) = copy(thresholds = thresholds + (key to value))

    fun withThresholds(thresholds: Map<CriticalThresholdKey, Double>) = copy(thresholds = this.thresholds + thresholds)

    fun addReport(report: MiXCRCommandReport) = copy(reports = reports + report)

    companion object {
        class IO : Serializer<MiXCRFooter> {
            override fun write(output: PrimitivO, obj: MiXCRFooter) {
                output.writeList(obj.reports) { writeObject(it) }
                output.writeObject(obj.thresholds)
            }

            override fun read(input: PrimitivI): MiXCRFooter {
                val reports = input.readList { readObject<MiXCRCommandReport>() }
                val thresholds = input.readObject<CriticalThresholdCollection>()
                return MiXCRFooter(reports, thresholds)
            }

            override fun isReference() = false

            override fun handlesReference() = false
        }
    }
}

class MiXCRFooterMerger {
    private val reports = mutableListOf<MiXCRCommandReport>()

    fun addReportsFromInput(inputIdx: Int, inputName: String, footer: MiXCRFooter) = run {
        footer.reports.forEach { r ->
            reports += MultipleInputsReportWrapper(inputIdx, inputName, r)
        }
        this
    }

    fun addReport(report: MiXCRCommandReport) = run {
        this.reports += report
        this
    }

    fun build() = MiXCRFooter(reports.toList())
}