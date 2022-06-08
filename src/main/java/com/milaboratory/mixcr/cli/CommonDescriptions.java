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
package com.milaboratory.mixcr.cli;

public class CommonDescriptions {
    private CommonDescriptions() {
    }

    public static final String SPECIES =
            "Species (organism), as specified in library file or taxon id.%n" +
                    "Possible values: hs, HomoSapiens, musmusculus, mmu, hsa, 9606, 10090 etc.";

    public static final String REPORT = "Report file (human readable version, see -j / --json-report for machine readable report)";

    public static final String JSON_REPORT = "JSON formatted report file";

    public static final String DOWNSAMPLING = "Choose downsampling. Possible values: umi-count-[1000|auto]|cumulative-top-[percent]|top-[number]|none";

    public static final String METADATA = "Metadata file (csv/tsv). Must have \"sample\" column.";

    public static final String DOWNSAMPLING_DROPO_UTLIERS = "Drop samples which have less abundance than the computed downsampling threshold.";

    public static final String OVERLAP_CRITERIA = "Overlap criteria. Default CDR3|AA|V|J";

    public static final String ONLY_PRODUCTIVE = "Filter out-of-frame sequences and sequences with stop-codons";
}
