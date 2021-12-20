package com.milaboratory.mixcr.postanalysis.dataframe

import org.jetbrains.kotlinx.dataframe.DataColumn
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.DataRow
import org.jetbrains.kotlinx.dataframe.annotations.DataSchema
import org.jetbrains.kotlinx.dataframe.api.column

/**
 * DataFrame row for metadata
 */
@DataSchema
@Suppress("UNCHECKED_CAST")
interface MetadataRow {
    /** Sample */
    val sample: String

    companion object {
        ////// DSL

        val sample by column<String>()

        val DataFrame<MetadataRow>.sample get() = this[MetadataRow::sample.name] as DataColumn<String>
        val DataRow<MetadataRow>.sample get() = this[MetadataRow::sample.name] as String
    }
}

object Metadata {



}
