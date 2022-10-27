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
import com.milaboratory.mitool.pattern.search.BasicSerializer
import com.milaboratory.mitool.pattern.search.readObject
import com.milaboratory.mixcr.MiXCRCommandDescriptor
import com.milaboratory.mixcr.MiXCRParams
import com.milaboratory.mixcr.MiXCRParamsSpec
import com.milaboratory.mixcr.MiXCRStepParams
import com.milaboratory.mixcr.MiXCRStepReports
import com.milaboratory.mixcr.assembler.CloneAssemblerParameters
import com.milaboratory.mixcr.basictypes.tag.TagsInfo
import com.milaboratory.mixcr.cli.MiXCRCommandReport
import com.milaboratory.mixcr.util.toHexString
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters
import com.milaboratory.primitivio.PrimitivI
import com.milaboratory.primitivio.PrimitivO
import com.milaboratory.primitivio.Serializer
import com.milaboratory.primitivio.annotations.Serializable
import com.milaboratory.primitivio.readObjectOptional
import com.milaboratory.primitivio.readObjectRequired
import io.repseq.core.GeneFeature
import io.repseq.core.VDJCLibraryId
import io.repseq.dto.VDJCLibraryData
import java.security.MessageDigest

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
@Suppress("DuplicatedCode")
@Serializable(by = MiXCRHeader.SerializerV2Impl::class)
data class MiXCRHeader(
    /** Hash code of input files with raw sequencing data. */
    val inputHash: String?,
    /** Set by used on align step, used to deduce defaults on all downstream steps  */
    val paramsSpec: MiXCRParamsSpec,
    /** Actual step parameters */
    val stepParams: MiXCRStepParams = MiXCRStepParams(),
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
        copy(allFullyCoveredBy = GeneFeatures(allClonesAlignedBy.toList()))

    fun <P : MiXCRParams> addStepParams(cmd: MiXCRCommandDescriptor<P, *>, params: P) =
        copy(stepParams = stepParams.add(cmd, params))

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

    class SerializerV1Impl : BasicSerializer<MiXCRHeader>() {
        override fun write(output: PrimitivO, obj: MiXCRHeader) {
            output.writeObject(obj.paramsSpec)
            output.writeObject(obj.stepParams)
            output.writeObject(obj.tagsInfo)
            output.writeObject(obj.alignerParameters)
            output.writeObject(obj.assemblerParameters)
            output.writeObject(obj.foundAlleles)
            output.writeObject(obj.allFullyCoveredBy)
        }

        override fun read(input: PrimitivI): MiXCRHeader {
            val paramsSpec = input.readObjectRequired<MiXCRParamsSpec>()
            val stepParams = input.readObject<MiXCRStepParams>()
            val tagsInfo = input.readObjectRequired<TagsInfo>()
            val alignerParameters = input.readObjectRequired<VDJCAlignerParameters>()
            val assemblerParameters = input.readObjectOptional<CloneAssemblerParameters>()
            val foundAlleles = input.readObjectOptional<FoundAlleles>()
            val allFullyCoveredBy = input.readObjectOptional<GeneFeatures>()
            return MiXCRHeader(
                null,
                paramsSpec,
                stepParams,
                tagsInfo,
                alignerParameters,
                assemblerParameters,
                foundAlleles,
                allFullyCoveredBy
            )
        }
    }

    class SerializerV2Impl : BasicSerializer<MiXCRHeader>() {
        override fun write(output: PrimitivO, obj: MiXCRHeader) {
            output.writeObject(obj.inputHash)
            output.writeObject(obj.paramsSpec)
            output.writeObject(obj.stepParams)
            output.writeObject(obj.tagsInfo)
            output.writeObject(obj.alignerParameters)
            output.writeObject(obj.assemblerParameters)
            output.writeObject(obj.foundAlleles)
            output.writeObject(obj.allFullyCoveredBy)
        }

        override fun read(input: PrimitivI): MiXCRHeader {
            val inputHash = input.readObjectOptional<String>()
            val paramsSpec = input.readObjectRequired<MiXCRParamsSpec>()
            val stepParams = input.readObject<MiXCRStepParams>()
            val tagsInfo = input.readObjectRequired<TagsInfo>()
            val alignerParameters = input.readObjectRequired<VDJCAlignerParameters>()
            val assemblerParameters = input.readObjectOptional<CloneAssemblerParameters>()
            val foundAlleles = input.readObjectOptional<FoundAlleles>()
            val allFullyCoveredBy = input.readObjectOptional<GeneFeatures>()
            return MiXCRHeader(
                inputHash,
                paramsSpec,
                stepParams,
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
    private var inputHashAccumulator: MessageDigest? = MessageDigest.getInstance("MD5")
    private var upstreamParams = mutableListOf<MiXCRStepParams>()
    private var paramsSpec: MiXCRParamsSpec? = null
    private var tagsInfo: TagsInfo? = null
    private var alignerParameters: VDJCAlignerParameters? = null
    private var assemblerParameters: CloneAssemblerParameters? = null
    private var allFullyCoveredBy: GeneFeatures? = null
    // TODO something seems to be done with alleles here ?

    fun add(header: MiXCRHeader) = run {
        if (header.inputHash == null)
            inputHashAccumulator = null
        inputHashAccumulator?.update(header.inputHash!!.encodeToByteArray())
        upstreamParams += header.stepParams
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
        MiXCRHeader(
            inputHashAccumulator?.digest()?.toHexString(),
            paramsSpec!!,
            MiXCRStepParams.mergeUpstreams(upstreamParams),
            tagsInfo!!,
            alignerParameters!!,
            assemblerParameters,
            null,
            allFullyCoveredBy
        )
}

/** Information stored in the footer of */
@Serializable(by = MiXCRFooter.Companion.IO::class)
data class MiXCRFooter(
    /** Step reports in the order they were applied to the initial data */
    val reports: MiXCRStepReports = MiXCRStepReports(),
    /** Collection of thresholds automatically or manually determined, to better select default parameters on
     * downstream steps*/
    val thresholds: CriticalThresholdCollection = CriticalThresholdCollection(),
) {
    fun withThreshold(key: CriticalThresholdKey, value: Double) = copy(thresholds = thresholds + (key to value))

    fun withThresholds(thresholds: Map<CriticalThresholdKey, Double>) = copy(thresholds = this.thresholds + thresholds)

    fun <R : MiXCRCommandReport> addStepReport(step: MiXCRCommandDescriptor<*, R>, report: R) =
        copy(reports = reports.add(step, report))

    companion object {
        class IO : Serializer<MiXCRFooter> {
            override fun write(output: PrimitivO, obj: MiXCRFooter) {
                output.writeObject(obj.reports)
                output.writeObject(obj.thresholds)
            }

            override fun read(input: PrimitivI): MiXCRFooter {
                val reports = input.readObject<MiXCRStepReports>()
                val thresholds = input.readObject<CriticalThresholdCollection>()
                return MiXCRFooter(reports, thresholds)
            }

            override fun isReference() = false

            override fun handlesReference() = false
        }
    }
}

class MiXCRFooterMerger {
    private val upstreamReports = mutableListOf<MiXCRStepReports>()
    private var reports: MiXCRStepReports? = null

    fun addReportsFromInput(inputIdx: Int, inputName: String, footer: MiXCRFooter) = run {
        check(reports == null)
        upstreamReports += footer.reports
        this
    }

    fun <R : MiXCRCommandReport> addStepReport(step: MiXCRCommandDescriptor<*, R>, report: R) = run {
        if (reports == null)
            reports = MiXCRStepReports.mergeUpstreams(upstreamReports)
        reports = reports!!.add(step, report)
        this
    }

    fun build() = run {
        if (reports == null)
            reports = MiXCRStepReports.mergeUpstreams(upstreamReports)
        MiXCRFooter(reports!!)
    }
}
