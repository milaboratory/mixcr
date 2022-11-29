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
        public static final String TAG_TYPE = "<(Molecule|Cell|Sample)>";
    }

    public static final String OVERLAP_CRITERIA = "Overlap criteria. Defines the rules to treat clones as equal. It allows to specify gene feature for overlap (nucleotide or amino acid), and optionally use V and J hits.%nExamples: `CDR3|AA|V|J` (overlap by a.a. CDR3 and V and J), `VDJRegion|AA` (overlap by a.a. `VDJRegion`), `CDR3|NT|V` (overlap by nt CDR3 and V).";

    public static final String DOWNSAMPLING = "downsampling applied to normalize the clonesets. Possible values: %ncount-[reads|TAG]-[auto|min|fixed][-<number>]%n top-[reads|TAG]-[<number>]%n cumtop-[reads|TAG]-[percent]";

    public static final String METADATA = "Metadata file in a tab- (`.tsv`) or comma- (`.csv`) separated form. Must contain `sample` column which matches names of input files.";

    public static final String DOWNSAMPLING_DROP_OUTLIERS = "Drop samples which are below downsampling value as computed according to specified default downsampling option.";

    public static final String ONLY_PRODUCTIVE = "Filter out-of-frame sequences and sequences with stop-codons.";

    public static final String DEFAULT_VALUE_FROM_PRESET = "  Default value determined by the preset.";
}
