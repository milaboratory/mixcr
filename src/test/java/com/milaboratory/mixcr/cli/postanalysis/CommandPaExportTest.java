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
package com.milaboratory.mixcr.cli.postanalysis;

import com.milaboratory.mixcr.cli.Main;
import org.junit.Ignore;
import org.junit.Test;

public class CommandPaExportTest {
    @Ignore
    @Test
    public void test1() {
        Main.main(
                "exportPa",
                "biophysics",
                "--meta",
                "scratch/metadata.csv",
                "--primary-group",
                "Cat2",
                "scratch/pa/pa.json",
                "scratch/pa/bio.pdf"
        );
    }

    @Ignore
    @Test
    public void test2() {
        Main.main(
                "exportPa",
                "vUsage",
                "--chains",
                "IGH",
                "--color-key",
                "Cat2",
                "--meta",
                "scratch/metadata.csv",
                "scratch/pa/pa.json",
                "scratch/pa/vUsage.pdf"
        );
    }

    @Ignore
    @Test
    public void test3() {
        Main.main("exportPa", "overlap",
//                "--width", "2000",
//                "--height", "5000",
                //     "--chains", "TRA",
//                "--filter", "Chain=TRA",
//                "--filter", "CellPopulation=CD4mem",
                "--metadata", "/Users/poslavskysv/Projects/milab/mixcr-test-data/metadata.tsv",
                "--color-key", "x_Tissue", "--color-key", "x_Age(months)", "--color-key", "y_MouseID",
                "/Users/poslavskysv/Projects/milab/mixcr-test-data/pa/overlapPa.json",
                "/Users/poslavskysv/Projects/milab/mixcr-test-data/pa/overlap/overlap.pdf");
    }

    @Ignore
    @Test
    public void test4() {
        Main.main("exportPa", "preprocSummary",
//                "--width", "2000",
//                "--height", "5000",
                "/Users/poslavskysv/Projects/milab/mixcr-test-data/pa/overlapPa.json",
                "/Users/poslavskysv/Projects/milab/mixcr-test-data/pa/tables/preproc/overlap.tsv");
    }

    @Ignore
    @Test
    public void test5() {
        Main.main("postanalysis",
                "individual",
                "--metadata", "/Users/poslavskysv/Projects/milab/mixcr-test-data/metadata.tsv",
                "--only-productive", "-f", "--default-downsampling", "umi-count-auto",
                "-Odiversity.observed.downsampling=top-100",
                "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m10_ct_spl_CD4mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m10_ct_spl_CD4mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m10_ct_spl_CD4naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m10_ct_spl_CD4naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m10_ct_spl_CD8mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m10_ct_spl_CD8mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m10_ct_spl_CD8naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m10_ct_spl_CD8naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m10_ct_spl_Treg_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m10_ct_spl_Treg_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m10_ct_th_CD4mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m10_ct_th_CD4mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m10_ct_th_CD4naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m10_ct_th_CD4naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m10_ct_th_CD8mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m10_ct_th_CD8mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m10_ct_th_CD8naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m10_ct_th_CD8naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m10_ct_th_DP_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m10_ct_th_DP_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m11_ct_spl_CD4mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m11_ct_spl_CD4mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m11_ct_spl_CD4naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m11_ct_spl_CD4naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m11_ct_spl_CD8mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m11_ct_spl_CD8mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m11_ct_spl_CD8naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m11_ct_spl_CD8naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m11_ct_th_CD4mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m11_ct_th_CD4mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m11_ct_th_CD4naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m11_ct_th_CD4naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m11_ct_th_CD8mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m11_ct_th_CD8mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m11_ct_th_CD8naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m11_ct_th_CD8naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m11_ct_th_DP_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m11_ct_th_DP_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m12_ct_spl_CD4mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m12_ct_spl_CD4mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m12_ct_spl_CD4naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m12_ct_spl_CD4naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m12_ct_spl_CD8mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m12_ct_spl_CD8mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m12_ct_spl_CD8naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m12_ct_spl_CD8naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m12_ct_th_CD4mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m12_ct_th_CD4mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m12_ct_th_CD4naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m12_ct_th_CD4naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m12_ct_th_CD8mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m12_ct_th_CD8mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m12_ct_th_CD8naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m12_ct_th_CD8naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m12_ct_th_DP_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m12_ct_th_DP_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m13_ct_spl_CD4mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m13_ct_spl_CD4mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m13_ct_spl_CD4naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m13_ct_spl_CD4naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m13_ct_spl_CD8mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m13_ct_spl_CD8mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m13_ct_spl_CD8naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m13_ct_spl_CD8naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m13_ct_th_CD4mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m13_ct_th_CD4mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m13_ct_th_CD4naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m13_ct_th_CD4naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m13_ct_th_CD8mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m13_ct_th_CD8mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m13_ct_th_CD8naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m13_ct_th_CD8naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m14_ct_spl_CD4mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m14_ct_spl_CD4mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m14_ct_spl_CD4naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m14_ct_spl_CD4naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m14_ct_spl_CD8mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m14_ct_spl_CD8mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m14_ct_spl_CD8naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m14_ct_spl_CD8naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m14_ct_spl_Treg_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m14_ct_spl_Treg_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m14_ct_th_CD4mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m14_ct_th_CD4mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m14_ct_th_CD4naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m14_ct_th_CD4naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m14_ct_th_CD8mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m14_ct_th_CD8mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m14_ct_th_CD8naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m14_ct_th_CD8naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m14_ct_th_DP_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m14_ct_th_DP_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m1_young_spl_CD4mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m1_young_spl_CD4mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m1_young_spl_CD4naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m1_young_spl_CD4naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m1_young_spl_CD8mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m1_young_spl_CD8mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m1_young_spl_CD8naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m1_young_spl_CD8naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m1_young_th_CD4mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m1_young_th_CD4mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m1_young_th_CD4mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m1_young_th_CD4naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m1_young_th_CD4naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m1_young_th_CD8naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m1_young_th_CD8naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m1_young_th_DP_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m1_young_th_DP_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m2_young_spl_CD4mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m2_young_spl_CD4mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m2_young_spl_CD4naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m2_young_spl_CD4naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m2_young_spl_CD8mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m2_young_spl_CD8mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m2_young_spl_CD8naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m2_young_spl_CD8naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m2_young_th_CD4mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m2_young_th_CD4mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m2_young_th_CD4naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m2_young_th_CD4naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m2_young_th_CD8mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m2_young_th_CD8mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m2_young_th_CD8naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m2_young_th_CD8naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m2_young_th_DP_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m2_young_th_DP_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m3_young_spl_CD4mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m3_young_spl_CD4mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m3_young_spl_CD4naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m3_young_spl_CD4naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m3_young_spl_CD8mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m3_young_spl_CD8mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m3_young_spl_CD8naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m3_young_spl_CD8naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m3_young_th_CD4mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m3_young_th_CD4mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m3_young_th_CD4naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m3_young_th_CD4naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m3_young_th_CD8mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m3_young_th_CD8mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m3_young_th_CD8naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m3_young_th_CD8naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m3_young_th_DP_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m3_young_th_DP_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m4_young_spl_CD4mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m4_young_spl_CD4mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m4_young_spl_CD4naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m4_young_spl_CD4naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m4_young_spl_CD8mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m4_young_spl_CD8mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m4_young_spl_CD8naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m4_young_spl_CD8naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m4_young_th_CD4mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m4_young_th_CD4mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m4_young_th_CD4naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m4_young_th_CD4naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m4_young_th_CD8mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m4_young_th_CD8mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m4_young_th_CD8naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m4_young_th_CD8naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m4_young_th_DP_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m4_young_th_DP_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m5_ct_65w_spl_CD4mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m5_ct_65w_spl_CD4mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m5_ct_65w_spl_CD4naiv_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m5_ct_65w_spl_CD4naiv_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m5_ct_65w_spl_CD8mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m5_ct_65w_spl_CD8naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m5_ct_65w_spl_CD8naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m5_ct_65w_spl_Cd8mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m5_ct_65w_th_CD4mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m5_ct_65w_th_CD4mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m5_ct_65w_th_CD4naiv_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m5_ct_65w_th_CD4naiv_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m5_ct_65w_th_CD8mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m5_ct_65w_th_CD8naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m5_ct_65w_th_CD8naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m5_ct_65w_th_Cd8mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m5_ct_65w_th_DP_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m5_ct_65w_th_DP_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m5_young_spl_CD4mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m5_young_spl_CD4mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m5_young_spl_CD4naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m5_young_spl_CD4naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m5_young_spl_CD8naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m5_young_spl_CD8naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m5_young_th_CD4mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m5_young_th_CD4mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m5_young_th_CD4naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m5_young_th_CD4naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m5_young_th_CD8mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m5_young_th_CD8mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m5_young_th_CD8naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m5_young_th_CD8naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m5_young_th_DP_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m5_young_th_DP_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m6_ct_65w_spl_CD4mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m6_ct_65w_spl_CD4mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m6_ct_65w_spl_CD4naiv_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m6_ct_65w_spl_CD4naiv_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m6_ct_65w_spl_CD8mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m6_ct_65w_spl_CD8naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m6_ct_65w_spl_CD8naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m6_ct_65w_spl_Cd8mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m6_ct_65w_th_CD4mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m6_ct_65w_th_CD4mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m6_ct_65w_th_CD8mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m6_ct_65w_th_CD8naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m6_ct_65w_th_CD8naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m6_ct_65w_th_Cd8mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m6_ct_65w_th_DP_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m6_ct_65w_th_DP_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m7_ct_65w_spl_CD4mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m7_ct_65w_spl_CD4mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m7_ct_65w_spl_CD4naiv_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m7_ct_65w_spl_CD4naiv_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m7_ct_65w_spl_CD8mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m7_ct_65w_spl_CD8naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m7_ct_65w_spl_CD8naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m7_ct_65w_spl_Cd8mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m7_ct_65w_th_CD4m_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m7_ct_65w_th_CD4mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m7_ct_65w_th_CD4naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m7_ct_65w_th_CD4naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m7_ct_65w_th_CD8mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m7_ct_65w_th_CD8mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m7_ct_65w_th_CD8naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m7_ct_65w_th_CD8naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m7_ct_65w_th_dp_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m7_ct_65w_th_dp_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m8_ct_65w_spl_CD4mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m8_ct_65w_spl_CD4mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m8_ct_65w_spl_CD8mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m8_ct_65w_spl_CD8mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m8_ct_65w_spl_CD8naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m8_ct_65w_spl_CD8naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m8_ct_65w_th_CD4mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m8_ct_65w_th_CD4mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m8_ct_65w_th_CD4naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m8_ct_65w_th_CD4naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m8_ct_65w_th_CD8mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m8_ct_65w_th_CD8mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m8_ct_65w_th_CD8naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m8_ct_65w_th_CD8naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m8_ct_65w_th_DP_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m8_ct_65w_th_DP_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m9_ct_65w_spl_CD4mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m9_ct_65w_spl_CD4mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m9_ct_65w_spl_CD4naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m9_ct_65w_spl_CD4naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m9_ct_65w_spl_CD8mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m9_ct_65w_spl_CD8mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m9_ct_65w_spl_CD8naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m9_ct_65w_spl_CD8naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m9_ct_65w_th_CD4mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m9_ct_65w_th_CD4mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m9_ct_65w_th_CD8mem_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m9_ct_65w_th_CD8mem_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m9_ct_65w_th_CD8naive_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m9_ct_65w_th_CD8naive_b.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m9_ct_65w_th_DP_a.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data/results/m9_ct_65w_th_DP_b.clns",
                "/Users/poslavskysv/Projects/milab/mixcr-test-data/pa/individualPa.json.gz");
    }

    @Ignore
    @Test
    public void test6() {
        Main.main("exportPlots", "diversity",
                "--metadata", "/Users/poslavskysv/Projects/milab/mixcr-test-data/metadata.tsv",
                "--primary-group", "Tissue",
                "--secondary-group", "MouseIDNum",
                "--plot-type", "violin-bindot",
                "/Users/poslavskysv/Projects/milab/mixcr-test-data/pa/individualPa.json.gz",
                "/Users/poslavskysv/Projects/milab/mixcr-test-data/pa/i/diversity.pdf");
    }

    @Ignore
    @Test
    public void test7() {
        Main.main("exportPlots", "overlap",
                "/Users/poslavskysv/Projects/milab/mixcr-test-data/pa/overlapPa.json.gz",
                "/Users/poslavskysv/Projects/milab/mixcr-test-data/pa/o/overlap.pdf");
    }


    @Ignore
    @Test
    public void test8() {
        Main.main("exportPlots", "overlap", "--metadata",
                "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/metadata.tsv",
                "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/pa/overlap.json.gz",
                "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/plots/overlap.pdf");
    }

    @Ignore
    @Test
    public void test8a() {
        Main.main("overlapScatterPlot",
                "--downsampling", "count-reads-auto",
                "--chains", "TRB",
                "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample11_LN.clns",
                "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample14_LN.clns",
                "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/plots/overlap_11_14.pdf");
    }

    @Ignore
    @Test
    public void test9() {
        Main.main("exportPlots", "diversity",
                "--chains", "TRB",
//                "--plot-type", "boxplot-jitter",
                "--method", "TTest",
                "--primary-group", "tissue",
                "-pv", "Spleen,Thymus,PBMC,LymphNode",
                "--secondary-group", "condition",
                "-sv", "tumor,contrlol",
//                "--p-adjust-method", "none",
//                "--hide-overall-p-value",
//                "--metric", "Observed",
                "--plot-type", "boxplot-jitter",
                "--metadata",
                "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/metadata.tsv",
                "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/pa/i.pa.json.gz",
                "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/plots/diversity.pdf");
    }

    @Ignore
    @Test
    public void test45() {
        Main.main("postanalysis", "individual",
                "--metadata", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/metadata.tsv",
                "--only-productive", "-f",
//                "--chains", "TRB",
                "--default-downsampling", "count-reads-auto",
//                "--default-weight-function", "read-count",
//                "-Odiversity.shannonWiener.downsampling=top-reads-100",
//                "-Odiversity.observed.downsampling=cumtop-reads-50.0",
//                "-Odiversity.chao1.weightFunction=none",
                "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample11_LN.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample11_PBMC.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample11_SPL.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample11_THY.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample12_LN.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample12_PBMC.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample12_SPL.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample12_THY.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample14_LN.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample14_PBMC.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample14_SPL.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample14_THY.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample15_LN.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample15_PBMC.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample15_SPL.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample15_THY.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample3_LN.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample3_PBMC.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample3_SPL.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample3_THY.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample8_LN.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample8_PBMC.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample8_SPL.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample8_THY.clns",
                "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/pa/i.pa.json.gz");
    }

    @Ignore
    @Test
    public void test452() {
        Main.main("postanalysis", "overlap",
                "--metadata", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/metadata.tsv",
                "--only-productive", "-f",
                "--default-downsampling", "count-reads-auto",
                "--default-weight-function", "read-count",
                "-Od.downsampling=top-reads-100",
                "-Of1.downsampling=cumtop-reads-50.0",
                "-Of2.weightFunction=none",
                "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample11_LN.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample11_PBMC.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample11_SPL.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample11_THY.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample12_LN.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample12_PBMC.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample12_SPL.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample12_THY.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample14_LN.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample14_PBMC.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample14_SPL.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample14_THY.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample15_LN.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample15_PBMC.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample15_SPL.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample15_THY.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample3_LN.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample3_PBMC.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample3_SPL.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample3_THY.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample8_LN.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample8_PBMC.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample8_SPL.clns", "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/result/sample8_THY.clns",
                "/Users/poslavskysv/Projects/milab/mixcr-test-data-mice-2014/pa/overlapPa.json.gz");
    }
}