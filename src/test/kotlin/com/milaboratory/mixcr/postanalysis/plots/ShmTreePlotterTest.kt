package com.milaboratory.mixcr.postanalysis.plots

import com.milaboratory.miplots.writePDF
import com.milaboratory.mixcr.trees.TreeFilter
import io.repseq.core.GeneFeature
import org.junit.Ignore
import org.junit.Test
import java.nio.file.Paths

class ShmTreePlotterTest {
    @Test
    @Ignore
    fun test1() {
        val plotter =
            ShmTreePlotter(
                Paths.get("/Users/poslavskysv/Projects/milab/mixcr-test-data-trees/D01.shmt"),
                Paths.get("/Users/poslavskysv/Projects/milab/mixcr-test-data-trees/metadata.tsv"),
                filter = TreeFilter(minNodes = 5, minHeight = 5),
//                limit = 100,

                nodeSize = DefaultMeta.Abundance,
                nodeLabel = DefaultMeta.Isotype,
//                lineColor = "CellType",
                nodeColor = "Replica",
                alignment = AlignmentOption(gf = GeneFeature.CDR3, isAA = true, fill = true),
                stats = listOf(StatOption("Time"))
            )

        writePDF(
            Paths.get("/Users/poslavskysv/Projects/milab/mixcr-test-data-trees/plots/bp.pdf"),
            plotter.plots
        )
    }

    @Test
    @Ignore
    fun test2() {
        val plotter =
            ShmTreePlotter(
                Paths.get("/Users/poslavskysv/Projects/milab/mixcr-test-data-trees/D01.shmt"),
                filter = TreeFilter(treeIds = setOf(11108)),
                alignment = AlignmentOption(gf = GeneFeature.CDR3, isAA = true, fill = true)
            )

        writePDF(
            Paths.get("/Users/poslavskysv/Projects/milab/mixcr-test-data-trees/plots/bp.pdf"),
            plotter.plots
        )
    }

    @Test
    @Ignore
    fun test3() {
        val plotter =
            ShmTreePlotter(
                Paths.get("/Users/poslavskysv/Projects/milab/mixcr-test-data-trees/D01.shmt"),
                Paths.get("/Users/poslavskysv/Projects/milab/mixcr-test-data-trees/metadata.tsv"),
                filter = TreeFilter(
                    minNodes = 30, minHeight = 15, seqPattern = SeqPattern(
                        seq = "ATGCTTGAAAA",
                        isAA = false,
                        feature = GeneFeature.CDR2,
                        maxErrors = 3
                    )
                ),
                limit = 100,

                nodeSize = DefaultMeta.Abundance,
                nodeLabel = DefaultMeta.Isotype,
                nodeColor = "Time",
                alignment = AlignmentOption(gf = GeneFeature.CDR3, isAA = true, fill = true)
            )

        writePDF(
            Paths.get("/Users/poslavskysv/Projects/milab/mixcr-test-data-trees/plots/bp.pdf"),
            plotter.plots
        )
    }
}
