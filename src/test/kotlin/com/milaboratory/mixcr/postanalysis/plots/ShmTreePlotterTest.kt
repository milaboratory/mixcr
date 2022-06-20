package com.milaboratory.mixcr.postanalysis.plots

import com.milaboratory.miplots.writePDF
import org.junit.Ignore
import org.junit.Test
import java.nio.file.Paths

class ShmTreePlotterTest {
    @Test
    @Ignore
    fun test1() {
        val plotter =
            ShmTreePlotter(
                Paths.get("/Users/poslavskysv/Projects/milab/mixcr-test-data-trees/trees.tree"),
                filter = TreeFilter(minNodes = 30, minHeight = 5, treeIds = setOf(1)),
                color = DefaultMeta.Isotype,
                size = DefaultMeta.Abundance
            )

        writePDF(
            Paths.get("/Users/poslavskysv/Projects/milab/mixcr-test-data-trees/plots/bp.pdf"),
            plotter.plots
        )
    }
}