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

    public static class Labels {
        public static final String OVERRIDES = "<key=value>";

        public static final String ANCHOR_POINT = "<anchor_point>";

        public static final String GENE_FEATURE = "<gene_feature>";
        public static final String GENE_FEATURES = "<gene_features>";

        public static final String GENE_TYPE = "<gene_type>";

        public static final String CHAINS = "<chains>";
        public static final String CHAIN = "<chain>";
    }

    public static final String DOWNSAMPLING = "Choose downsampling. Possible values: \n count-[reads|TAG]-[auto|min|fixed][-<number>]\n top-[reads|TAG]-[<number>]\n cumtop-[reads|TAG]-[percent]";

    public static final String METADATA = "Metadata file (csv/tsv). Must have \"sample\" column.";

    public static final String DOWNSAMPLING_DROP_OUTLIERS = "Drop samples which have less abundance than the computed downsampling threshold.";

    public static final String ONLY_PRODUCTIVE = "Filter out-of-frame sequences and sequences with stop-codons.";

    public static final String DEFAULT_VALUE_FROM_PRESET = "  Default value determined by the preset.";
}
